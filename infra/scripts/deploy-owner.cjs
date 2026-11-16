'use strict';
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { isDeepStrictEqual } = require('node:util');
const { parseAwsOutput, discover, health, assertChanges, executionWaiter } = require('./deploy.cjs');
const ROOT = path.resolve(__dirname, '../..');
const STACK = 'RB1OwnerStack', REGION = 'us-east-1', ACCOUNT = '294281109519';
function assertOwnerTemplate(template, tableName) {
  const allowed = new Set(['AWS::Lambda::Function', 'AWS::Lambda::Url', 'AWS::IAM::Role', 'AWS::IAM::Policy', 'AWS::Logs::LogGroup', 'AWS::CDK::Metadata']);
  const resources = Object.entries(template.Resources || {});
  const roles = resources.filter(([, r]) => r.Type === 'AWS::IAM::Role').map(([id]) => id);
  if (roles.length !== 1) throw new Error('Expected one new Lambda execution role');
  for (const [, r] of resources) {
    if (!allowed.has(r.Type)) throw new Error('Owner stack cannot manage ' + r.Type);
    if (r.Type === 'AWS::Lambda::Url' && r.Properties.AuthType !== 'AWS_IAM') throw new Error('Owner URL must require IAM');
    if (r.Type === 'AWS::Lambda::Function' && (r.Properties.Environment?.Variables?.RB1_TABLE !== tableName ||
      r.Properties.VpcConfig || r.Properties.FunctionName)) throw new Error('Unexpected Lambda target or network configuration');
    if (r.Type === 'AWS::IAM::Role' && (r.Properties.RoleName || r.Properties.ManagedPolicyArns ||
      !isDeepStrictEqual(r.Properties.AssumeRolePolicyDocument.Statement.map(s => s.Principal), [{ Service: 'lambda.amazonaws.com' }])))
      throw new Error('Owner stack may create only its own Lambda role');
    if (r.Type === 'AWS::IAM::Policy') {
      if (!isDeepStrictEqual(r.Properties.Roles, [{ Ref: roles[0] }])) throw new Error('Cannot attach to an existing role');
      const grants = r.Properties.PolicyDocument.Statement;
      for (const s of grants) {
        const actions = [].concat(s.Action);
        if (s.Effect !== 'Allow' || actions.some(a => !['dynamodb:PutItem', 'dynamodb:UpdateItem', 'logs:CreateLogStream', 'logs:PutLogEvents', 'logs:DescribeLogStreams'].includes(a)))
          throw new Error('Unexpected owner execution privilege');
        if ([].concat(s.Resource).some(r => typeof r === 'string')) throw new Error('Unscoped owner execution resource');
        if (actions.some(a => a.startsWith('dynamodb:')) &&
          (!isDeepStrictEqual(s.Condition?.['ForAllValues:StringLike']?.['dynamodb:LeadingKeys'], ['PAIR#*', 'PAIR-RATE#*']) ||
          !JSON.stringify(s.Resource).includes('table/' + tableName))) throw new Error('Owner writes must be limited to pairing records');
      }
    }
  }
  for (const type of ['AWS::Lambda::Function', 'AWS::Lambda::Url', 'AWS::Logs::LogGroup']) {
    if (resources.filter(([, r]) => r.Type === type).length !== 1) throw new Error('Expected exactly one ' + type);
  }
}
function snapshot(aws) {
  const shared = discover(aws);
  const stack = aws('cloudformation', 'describe-stacks', ['--stack-name', 'RB1DashcamStack']).Stacks[0];
  if (!['CREATE_COMPLETE', 'UPDATE_COMPLETE'].includes(stack.StackStatus)) throw new Error('Existing RB1 stack is not healthy');
  const outputs = Object.fromEntries(stack.Outputs.map(o => [o.OutputKey, o.OutputValue]));
  if (!/^[A-Za-z0-9_.-]{3,255}$/.test(outputs.TableName || '')) throw new Error('Cannot discover RB1 metadata table');
  if (outputs.ApiUrl !== 'https://dashcam.midlifemotor.club') throw new Error('Unexpected phone API origin');
  const template = aws('cloudformation', 'get-template', ['--stack-name', 'RB1DashcamStack']).TemplateBody;
  return { shared, parent: { stackId: stack.StackId, outputs, template } };
}
async function checkPhoneApi(url) {
  const r = await fetch(url + '/health', { signal: AbortSignal.timeout(15000) });
  if (!r.ok || (await r.json()).service !== 'rb1-dashcam') throw new Error('Existing phone API is unhealthy');
}
async function verifyOwnerDeployment(aws, review, current, { checkSites = health, checkApi = checkPhoneApi, request = fetch } = {}) {
  if (!isDeepStrictEqual(current, review.snapshot)) throw new Error('Existing infrastructure differs from the owner review');
  const stack = aws('cloudformation', 'describe-stacks', ['--stack-name', STACK]).Stacks[0];
  if (!['CREATE_COMPLETE', 'UPDATE_COMPLETE'].includes(stack.StackStatus)) throw new Error('Owner stack is not complete: ' + stack.StackStatus);
  const actual = aws('cloudformation', 'get-template', ['--stack-name', STACK, '--template-stage', 'Original']).TemplateBody;
  if (!isDeepStrictEqual(typeof actual === 'string' ? JSON.parse(actual) : actual, review.template)) throw new Error('Deployed owner template differs from review');
  await checkSites(); await checkApi(current.parent.outputs.ApiUrl);
  const endpoint = stack.Outputs.find(o => o.OutputKey === 'OwnerUrl').OutputValue;
  const configArn = stack.Outputs.find(o => o.OutputKey === 'OwnerFunctionArn').OutputValue;
  const config = aws('lambda', 'get-function-url-config', ['--function-name', configArn]);
  if (config.AuthType !== 'AWS_IAM' || config.FunctionUrl !== endpoint) throw new Error('Owner URL IAM configuration differs');
  const unsigned = await request(endpoint, { method: 'POST', body: '{}', redirect: 'error', signal: AbortSignal.timeout(15000) });
  await unsigned.body?.cancel(); if (unsigned.status !== 403) throw new Error('Unsigned owner requests must be rejected');
}
async function main(args) {
  let profile; const execute = args.includes('--execute'), verify = args.includes('--verify');
  if (execute && verify) throw new Error('Choose --execute or read-only --verify, not both');
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--execute' || args[i] === '--verify') continue;
    if (args[i] === '--profile' && args[i + 1] && !args[i + 1].startsWith('-')) profile = args[++i];
    else throw new Error('Usage: npm run deploy:owner -- [--profile NAME] [--execute | --verify]');
  }
  const aws = (service, operation, rest = []) => parseAwsOutput(execFileSync('aws', [service, operation,
    '--region', REGION, '--no-cli-pager', '--output', 'json', ...(profile ? ['--profile', profile] : []), ...rest],
  { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 }), service, operation);
  if (aws('sts', 'get-caller-identity').Account !== ACCOUNT) throw new Error('Wrong AWS account');
  console.log('Checking the existing RB1 stack, shared host, all four applications, and their policies.');
  const before = snapshot(aws); await health(); await checkPhoneApi(before.parent.outputs.ApiUrl);
  const reviewFile = path.join(ROOT, 'infra/owner-review.json');
  if (verify) {
    const review = JSON.parse(fs.readFileSync(reviewFile));
    assertOwnerTemplate(review.template, before.parent.outputs.TableName);
    await verifyOwnerDeployment(aws, review, before);
    console.log('Read-only owner verification passed; existing infrastructure and all four applications are unchanged.');
    console.log('Generate a code with: npm run pair' + (profile ? ' -- --profile ' + profile : ''));
    return;
  }
  if (!execute) {
    execFileSync('npm', ['run', 'build:owner'], { cwd: ROOT, stdio: 'inherit' });
    execFileSync('npm', ['test'], { cwd: ROOT, stdio: 'inherit' });
    const cdk = rest => execFileSync(path.join(ROOT, 'node_modules/.bin/cdk'), [...rest, '--app', 'node infra/owner-app.cjs',
      '--output', 'cdk.out-owner', '-c', 'tableName=' + before.parent.outputs.TableName, '-c', 'apiUrl=' + before.parent.outputs.ApiUrl,
      ...(profile ? ['--profile', profile] : [])], { cwd: ROOT, stdio: 'inherit' });
    cdk(['synth', STACK]);
    const template = JSON.parse(fs.readFileSync(path.join(ROOT, 'cdk.out-owner', STACK + '.template.json')));
    assertOwnerTemplate(template, before.parent.outputs.TableName);
    cdk(['diff', STACK]);
    const name = 'rb1-owner-reviewed-' + Date.now();
    cdk(['deploy', STACK, '--no-execute', '--change-set-name', name, '--require-approval', 'broadening']);
    const cs = aws('cloudformation', 'describe-change-set', ['--stack-name', STACK, '--change-set-name', name]);
    assertChanges(cs, template);
    fs.writeFileSync(reviewFile, JSON.stringify({ preparedAt: Date.now(), snapshot: before, template,
      changeSet: cs.ChangeSetId, changes: cs.Changes }, null, 2), { mode: 0o600 });
    console.log('Prepared only. Review infra/owner-review.json, then npm run deploy:owner -- --execute with the same profile.');
    return;
  }
  const review = JSON.parse(fs.readFileSync(reviewFile));
  if (Date.now() - review.preparedAt > 3600000 || !isDeepStrictEqual(review.snapshot, before)) throw new Error('Review expired or dependencies changed; prepare again');
  assertOwnerTemplate(review.template, before.parent.outputs.TableName);
  const cs = aws('cloudformation', 'describe-change-set', ['--change-set-name', review.changeSet]);
  assertChanges(cs, review.template);
  const actual = aws('cloudformation', 'get-template', ['--stack-name', STACK, '--change-set-name', review.changeSet, '--template-stage', 'Original']).TemplateBody;
  if (!isDeepStrictEqual(typeof actual === 'string' ? JSON.parse(actual) : actual, review.template) || !isDeepStrictEqual(cs.Changes, review.changes)) throw new Error('Reviewed owner change set differs');
  const waiter = executionWaiter(aws('cloudformation', 'describe-stacks', ['--stack-name', STACK]).Stacks[0].StackStatus);
  aws('cloudformation', 'execute-change-set', ['--change-set-name', review.changeSet, '--client-request-token',
    'rb1-owner-' + crypto.createHash('sha256').update(review.changeSet).digest('hex')]);
  console.log(`Owner change set submitted. Waiting using ${waiter}; AWS may take a few minutes.`);
  execFileSync('aws', ['cloudformation', 'wait', waiter,
    '--stack-name', STACK, '--region', REGION, ...(profile ? ['--profile', profile] : [])], { stdio: 'inherit' });
  await verifyOwnerDeployment(aws, review, snapshot(aws));
  console.log('Owner pairing endpoint deployed with IAM authentication; RB1 and all four shared-host applications preserved.');
  console.log('Generate a code from your laptop with: npm run pair' + (profile ? ' -- --profile ' + profile : ''));
}
module.exports = { assertOwnerTemplate, snapshot, verifyOwnerDeployment };
if (require.main === module) main(process.argv.slice(2)).catch(e => { console.error(e.stderr?.toString().trim() || e.message); process.exitCode = 1; });
