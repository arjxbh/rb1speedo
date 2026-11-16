'use strict';
const { execFileSync } = require('node:child_process');
const { fromNodeProviderChain } = require('@aws-sdk/credential-providers');
const { SignatureV4 } = require('@smithy/signature-v4');
const { Hash } = require('@smithy/hash-node');
function endpointFromStack(stack) {
  if (!stack?.StackId?.startsWith('arn:aws:cloudformation:us-east-1:294281109519:stack/RB1OwnerStack/'))
    throw new Error('Pairing must use RB1OwnerStack in account 294281109519, us-east-1');
  const endpoint = stack.Outputs?.find(x => x.OutputKey === 'OwnerUrl')?.OutputValue;
  if (!endpoint) throw new Error('Deploy the owner pairing stack first');
  return endpoint;
}
async function requestCode(endpoint, { profile, region = 'us-east-1', credentials, request = fetch } = {}) {
  const url = new URL(endpoint);
  if (url.protocol !== 'https:' || url.username || url.password || url.port || url.pathname !== '/' || url.search || url.hash ||
    !new RegExp(`^[a-z0-9]+\\.lambda-url\\.${region}\\.on\\.aws$`).test(url.hostname))
    throw new Error('Expected the deployed HTTPS Lambda Function URL');
  const signer = new SignatureV4({ service: 'lambda', region,
    credentials: credentials || fromNodeProviderChain(profile ? { profile } : {}), sha256: Hash.bind(null, 'sha256') });
  const body = '{}';
  const signed = await signer.sign({ protocol: 'https:', hostname: url.hostname, method: 'POST', path: '/pairing-codes',
    headers: { host: url.hostname, 'content-type': 'application/json' }, body });
  const r = await request(url.origin + '/pairing-codes', { method: 'POST', headers: signed.headers, body,
    redirect: 'error', signal: AbortSignal.timeout(30000) });
  if (r.status === 403) throw new Error('IAM denied invocation. Your AWS profile needs the stack CallerPolicy permissions (InvokeFunctionUrl AND InvokeFunction).');
  if (!r.ok) throw new Error('Pairing endpoint returned HTTP ' + r.status);
  const result = await r.json();
  if (!/^[A-Za-z0-9_-]{43}$/.test(result.code || '') || !Number.isSafeInteger(result.expiresAt)) throw new Error('Invalid pairing response');
  return result;
}
async function main(args) {
  let profile;
  if (args.length) {
    if (args.length !== 2 || args[0] !== '--profile' || !args[1] || args[1].startsWith('-')) throw new Error('Usage: npm run pair -- [--profile NAME]');
    profile = args[1];
  }
  const stack = JSON.parse(execFileSync('aws', ['cloudformation', 'describe-stacks', '--stack-name', 'RB1OwnerStack',
    '--region', 'us-east-1', '--no-cli-pager', '--output', 'json', ...(profile ? ['--profile', profile] : [])], { encoding: 'utf8' })).Stacks[0];
  const endpoint = endpointFromStack(stack);
  const result = await requestCode(endpoint, { profile });
  // Printing the short-lived code is the explicit purpose of this owner command; never print AWS credentials/signatures.
  console.log('API address: ' + result.apiUrl);
  console.log('Pairing code: ' + result.code);
  console.log('Expires: ' + new Date(result.expiresAt * 1000).toISOString() + ' (one use)');
}
module.exports = { requestCode, endpointFromStack };
if (require.main === module) main(process.argv.slice(2)).catch(e => { console.error(e.message); process.exitCode = 1; });
