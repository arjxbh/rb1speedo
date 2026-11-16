'use strict';
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { isDeepStrictEqual } = require('node:util');
const ROOT = path.resolve(__dirname, '../..');
const ACCOUNT = '294281109519', REGION = 'us-east-1', STACK = 'RB1DashcamStack';
const SITES = ['piggyledger.net', 'midlifemotor.club', 'kaedamacycles.com', 'thecrankygoose.net'];
const DEPENDENTS = ['PiggyLedgerStack-app-deployment', 'MidlifeMotorClubStack-site-deployment',
  'KaedamaStack-site-deployment', 'CrankyGooseStack-site-deployment'];
const ALLOWED = new Set(['AWS::S3::Bucket', 'AWS::S3::BucketPolicy', 'AWS::DynamoDB::Table',
  'AWS::IAM::Policy', 'AWS::SSM::Association', 'AWS::CDK::Metadata', 'AWS::CloudTrail::Trail', 'AWS::CloudWatch::Alarm']);
const digest = value => crypto.createHash('sha256').update(typeof value === 'string' ? value : JSON.stringify(value)).digest('hex');
function parseAwsOutput(output, service, operation) {
  const text = output.trim();
  // These successful operations may have no response body. execFileSync still throws
  // on a failed AWS command; empty discovery responses remain errors everywhere else.
  if (!text && service === 'cloudformation' && ['get-stack-policy', 'execute-change-set'].includes(operation)) return {};
  if (!text) throw new Error(`AWS CLI returned empty output for ${service} ${operation}`);
  return JSON.parse(text);
}
function readStackPolicy(aws, name) {
  try {
    const result = aws('cloudformation', 'get-stack-policy', ['--stack-name', name]);
    if (result == null) return null;
    if (typeof result !== 'object' || Array.isArray(result)) throw new Error('Unexpected stack policy response');
    if (result.StackPolicyBody == null || result.StackPolicyBody === '') return null;
    if (typeof result.StackPolicyBody !== 'string') throw new Error('Unexpected StackPolicyBody response');
    return result.StackPolicyBody;
  } catch (e) {
    const detail = e.stderr?.toString().trim() || e.message;
    throw new Error(`Unable to inspect dependent stack policy: ${name}: ${detail}`, { cause: e });
  }
}
function assertTemplate(template, host, role) {
  for (const [logical, r] of Object.entries(template.Resources || {})) {
    if (!ALLOWED.has(r.Type)) throw new Error('Forbidden resource type: ' + r.Type);
    if (r.Type === 'AWS::S3::Bucket' && (!['Clips', 'AccessLogs'].includes(logical) || r.Properties.BucketName))
      throw new Error('Only newly named RB1 buckets may be managed');
    if (r.Type === 'AWS::DynamoDB::Table' && (logical !== 'Metadata' || r.Properties.TableName))
      throw new Error('Only a newly named RB1 table may be managed');
    if (r.Type === 'AWS::S3::BucketPolicy' && !['Clips', 'AccessLogs'].includes(r.Properties.Bucket?.Ref))
      throw new Error('Cannot attach a policy to an existing application bucket');
    if (r.Type === 'AWS::IAM::Policy' && (logical !== 'HostAccess' || !isDeepStrictEqual(r.Properties.Roles, [role])))
      throw new Error('Only the new RB1 policy may attach to the discovered host role');
    if (r.Type === 'AWS::IAM::Policy') {
      if (r.Properties.PolicyName !== 'rb1-dashcam-host-access') throw new Error('Unexpected inline policy name');
      const actions = new Set(['s3:GetObject', 's3:PutObject', 's3:AbortMultipartUpload', 's3:ListMultipartUploadParts',
        'dynamodb:GetItem', 'dynamodb:PutItem', 'dynamodb:Query']);
      for (const statement of r.Properties.PolicyDocument.Statement) {
        const a = Array.isArray(statement.Action) ? statement.Action : [statement.Action];
        const resources = Array.isArray(statement.Resource) ? statement.Resource : [statement.Resource];
        if (statement.Effect !== 'Allow' || a.some(x => !actions.has(x)) || resources.some(x => typeof x === 'string'))
          throw new Error('Unexpected or broad IAM grant');
      }
    }
    if (r.Type === 'AWS::SSM::Association' && (logical !== 'ApiDeployment' || r.Properties.Name !== 'AWS-RunShellScript' ||
      r.Properties.AssociationName !== 'RB1DashcamStack-api-deployment' ||
      !isDeepStrictEqual(r.Properties.Targets, [{ Key: 'InstanceIds', Values: [host] }])))
      throw new Error('Unexpected SSM deployment target');
    if (['AWS::S3::Bucket', 'AWS::DynamoDB::Table'].includes(r.Type) &&
      (r.DeletionPolicy !== 'Retain' || r.UpdateReplacePolicy !== 'Retain')) throw new Error('Data retention protection missing');
  }
  for (const required of ['Clips', 'AccessLogs', 'Metadata', 'HostAccess', 'ApiDeployment']) {
    if (!template.Resources?.[required]) throw new Error('Missing RB1 resource: ' + required);
  }
}
function assertChanges(cs, template) {
  if (cs.Status !== 'CREATE_COMPLETE' || cs.ExecutionStatus !== 'AVAILABLE' || !cs.Changes?.length)
    throw new Error('Change set is not complete and executable');
  for (const change of cs.Changes) {
    const r = change.ResourceChange;
    if (change.Type !== 'Resource' || !r || !template.Resources[r.LogicalResourceId] ||
      template.Resources[r.LogicalResourceId].Type !== r.ResourceType ||
      !['Add', 'Modify'].includes(r.Action) || (r.Action === 'Modify' && r.Replacement !== 'False'))
      throw new Error('Blocked removal/replacement/unrelated change: ' + JSON.stringify(r || change));
  }
}
async function health() {
  for (const site of SITES) {
    const r = await fetch('https://' + site + '/', { redirect: 'manual', signal: AbortSignal.timeout(15000) });
    await r.body?.cancel(); if (r.status !== 200) throw new Error(site + ' unhealthy');
    const w = await fetch('https://www.' + site + '/', { redirect: 'manual', signal: AbortSignal.timeout(15000) });
    await w.body?.cancel();
    if (![301, 308].includes(w.status) || w.headers.get('location') !== 'https://' + site + '/') throw new Error(site + ' redirect changed');
  }
  for (const [url, status] of [['https://piggyledger.net/api/health', 200], ['https://kaedamacycles.com/api/quotes', 405]]) {
    const r = await fetch(url, { redirect: 'manual', signal: AbortSignal.timeout(15000) });
    await r.body?.cancel(); if (r.status !== status) throw new Error('Dependent API unhealthy: ' + url);
  }
}
function discover(aws) {
  const resources = aws('cloudformation', 'describe-stack-resources', ['--stack-name', 'PiggyLedgerStack']).StackResources;
  const hosts = resources.filter(r => r.ResourceType === 'AWS::EC2::Instance');
  if (hosts.length !== 1) throw new Error('Expected one shared EC2 host');
  const instance = aws('ec2', 'describe-instances', ['--instance-ids', hosts[0].PhysicalResourceId]).Reservations[0].Instances[0];
  if (instance.State.Name !== 'running' || !instance.IamInstanceProfile) throw new Error('Shared host not ready');
  const profileName = instance.IamInstanceProfile.Arn.split('/').pop();
  const roles = aws('iam', 'get-instance-profile', ['--instance-profile-name', profileName]).InstanceProfile.Roles;
  if (roles.length !== 1) throw new Error('Ambiguous shared host role');
  const role = roles[0].RoleName, rolePolicies = {};
  for (const name of aws('iam', 'list-role-policies', ['--role-name', role]).PolicyNames.sort()) {
    if (name === 'rb1-dashcam-host-access') continue;
    rolePolicies['inline:' + name] = aws('iam', 'get-role-policy', ['--role-name', role, '--policy-name', name]).PolicyDocument;
  }
  for (const p of aws('iam', 'list-attached-role-policies', ['--role-name', role]).AttachedPolicies.sort((a, b) => a.PolicyArn.localeCompare(b.PolicyArn))) {
    const version = aws('iam', 'get-policy', ['--policy-arn', p.PolicyArn]).Policy.DefaultVersionId;
    rolePolicies[p.PolicyArn] = aws('iam', 'get-policy-version', ['--policy-arn', p.PolicyArn, '--version-id', version]).PolicyVersion.Document;
  }
  const associations = aws('ssm', 'list-associations').Associations;
  for (const name of DEPENDENTS) {
    const a = associations.find(x => x.AssociationName === name);
    if (!a || a.Overview?.Status !== 'Success' || !isDeepStrictEqual(a.Targets, [{ Key: 'InstanceIds', Values: [instance.InstanceId] }]))
      throw new Error('Unhealthy/mismatched dependent deployment: ' + name);
  }
  // Preserve each existing production stack and its installed policy verbatim.
  const stacks = {};
  for (const name of ['PiggyLedgerStack', 'MidlifeMotorClubStack', 'KaedamaStack', 'CrankyGooseStack']) {
    const s = aws('cloudformation', 'describe-stacks', ['--stack-name', name]).Stacks[0];
    if (!['CREATE_COMPLETE', 'UPDATE_COMPLETE', 'UPDATE_ROLLBACK_COMPLETE'].includes(s.StackStatus)) throw new Error('Dependent stack busy: ' + name);
    const template = aws('cloudformation', 'get-template', ['--stack-name', name]).TemplateBody;
    const policy = readStackPolicy(aws, name);
    if (name === 'PiggyLedgerStack') {
      const expected = JSON.parse(fs.readFileSync(path.join(ROOT, 'infra/expected-shared-stack-policy.json')));
      if (!policy || !isDeepStrictEqual(JSON.parse(policy), expected)) throw new Error('Shared host stack protection missing or changed');
    }
    stacks[name] = { id: s.StackId, template: digest(template), policy };
  }
  return { host: instance.InstanceId, image: instance.ImageId, profile: instance.IamInstanceProfile.Arn,
    publicIp: instance.PublicIpAddress, role, rolePolicies, stacks };
}
async function verifyCompletedDeployment(aws, reviewed, domain, snapshot, { checkSites = health, request = fetch } = {}) {
  if (reviewed.domain !== domain || !isDeepStrictEqual(reviewed.snapshot, snapshot))
    throw new Error('Shared infrastructure differs from the reviewed deployment');
  const stack = aws('cloudformation', 'describe-stacks', ['--stack-name', STACK]).Stacks[0];
  if (!['CREATE_COMPLETE', 'UPDATE_COMPLETE'].includes(stack.StackStatus))
    throw new Error('RB1 stack is not complete: ' + stack.StackStatus + '; inspect stack events before proceeding');
  const actual = aws('cloudformation', 'get-template', ['--stack-name', STACK, '--template-stage', 'Original']).TemplateBody;
  if (!isDeepStrictEqual(typeof actual === 'string' ? JSON.parse(actual) : actual, reviewed.template))
    throw new Error('Deployed template differs from the reviewed template');
  const own = aws('ssm', 'list-associations').Associations.find(a => a.AssociationName === 'RB1DashcamStack-api-deployment');
  if (own?.Overview?.Status !== 'Success' || !isDeepStrictEqual(own.Targets, [{ Key: 'InstanceIds', Values: [snapshot.host] }]))
    throw new Error('RB1 SSM deployment has not succeeded on the reviewed host');
  await checkSites();
  const r = await request('https://' + domain + '/health', { signal: AbortSignal.timeout(15000) });
  if (!r.ok || (await r.json()).service !== 'rb1-dashcam') throw new Error('RB1 endpoint unhealthy');
  console.log('RB1 deployed; shared instance, AMI, IP, all existing templates/policies and all four sites verified unchanged.');
}
async function main(args) {
  const execute = args.includes('--execute');
  const verify = args.includes('--verify');
  if (execute && verify) throw new Error('Choose --execute or read-only --verify, not both');
  let profile, domain = 'dashcam.midlifemotor.club';
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--execute' || args[i] === '--verify') continue;
    if (args[i] === '--profile' && args[i + 1]) profile = args[++i];
    else if (args[i] === '--domain' && args[i + 1]) domain = args[++i];
    else throw new Error('Only --profile NAME, --domain HOSTNAME, --execute and --verify are supported');
  }
  if (!/^[a-z0-9]+([.-][a-z0-9]+)*\.[a-z]{2,}$/.test(domain)) throw new Error('Invalid domain');
  if (SITES.some(site => domain === site || domain === 'www.' + site)) throw new Error('Cannot take over an existing site hostname');
  const aws = (service, operation, rest = []) => parseAwsOutput(execFileSync('aws', [service, operation,
    '--region', REGION, ...(profile ? ['--profile', profile] : []), '--no-cli-pager', '--output', 'json', ...rest],
  { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 }), service, operation);
  if (aws('sts', 'get-caller-identity').Account !== ACCOUNT) throw new Error('Wrong AWS account');
  const snapshot = discover(aws); await health();
  const reviewFile = path.join(ROOT, 'infra/review.json');
  if (verify) {
    const reviewed = JSON.parse(fs.readFileSync(reviewFile));
    assertTemplate(reviewed.template, snapshot.host, snapshot.role);
    await verifyCompletedDeployment(aws, reviewed, domain, snapshot);
    return;
  }
  if (!execute) {
    execFileSync('npm', ['test'], { cwd: ROOT, stdio: 'inherit' });
    const cdk = rest => execFileSync(path.join(ROOT, 'node_modules/.bin/cdk'), [...rest,
      '--app', 'node infra/app.cjs', '-c', 'instanceId=' + snapshot.host, '-c', 'instanceRoleName=' + snapshot.role,
      '-c', 'domain=' + domain, ...(profile ? ['--profile', profile] : [])], { cwd: ROOT, stdio: 'inherit' });
    cdk(['synth', STACK]);
    const template = JSON.parse(fs.readFileSync(path.join(ROOT, 'cdk.out', STACK + '.template.json')));
    assertTemplate(template, snapshot.host, snapshot.role);
    cdk(['diff', STACK]);
    const name = 'rb1-reviewed-' + Date.now();
    cdk(['deploy', STACK, '--no-execute', '--change-set-name', name, '--require-approval', 'broadening']);
    const cs = aws('cloudformation', 'describe-change-set', ['--stack-name', STACK, '--change-set-name', name]);
    assertChanges(cs, template);
    fs.writeFileSync(reviewFile, JSON.stringify({ preparedAt: Date.now(), snapshot, template,
      changeSet: cs.ChangeSetId, changes: cs.Changes, domain }, null, 2), { mode: 0o600 });
    console.log('Prepared only. Review infra/review.json, then use npm run deploy -- --execute with the same profile/domain.');
    return;
  }
  const reviewed = JSON.parse(fs.readFileSync(reviewFile));
  if (Date.now() - reviewed.preparedAt > 3600000 || reviewed.domain !== domain || !isDeepStrictEqual(reviewed.snapshot, snapshot))
    throw new Error('Review expired or shared infrastructure changed; prepare again');
  assertTemplate(reviewed.template, snapshot.host, snapshot.role);
  const cs = aws('cloudformation', 'describe-change-set', ['--change-set-name', reviewed.changeSet]);
  if (['EXECUTE_IN_PROGRESS', 'EXECUTE_COMPLETE'].includes(cs.ExecutionStatus))
    throw new Error('This change set was already submitted. Use --verify to check it without executing again.');
  assertChanges(cs, reviewed.template);
  const actual = aws('cloudformation', 'get-template', ['--stack-name', STACK, '--change-set-name', reviewed.changeSet,
    '--template-stage', 'Original']).TemplateBody;
  const parsed = typeof actual === 'string' ? JSON.parse(actual) : actual;
  if (!isDeepStrictEqual(parsed, reviewed.template) || !isDeepStrictEqual(cs.Changes, reviewed.changes)) throw new Error('Reviewed change set differs');
  aws('cloudformation', 'execute-change-set', ['--change-set-name', reviewed.changeSet,
    '--client-request-token', 'rb1-' + digest(reviewed.changeSet)]);
  const waiter = cs.ChangeSetType === 'CREATE' ? 'stack-create-complete' : 'stack-update-complete';
  execFileSync('aws', ['cloudformation', 'wait', waiter, '--stack-name', STACK, '--region', REGION,
    ...(profile ? ['--profile', profile] : [])], { stdio: 'inherit' });
  const after = discover(aws);
  await verifyCompletedDeployment(aws, reviewed, domain, after);
}
module.exports = { assertTemplate, assertChanges, discover, health, parseAwsOutput, readStackPolicy, verifyCompletedDeployment };
if (require.main === module) main(process.argv.slice(2)).catch(e => { console.error(e.message); process.exitCode = 1; });
