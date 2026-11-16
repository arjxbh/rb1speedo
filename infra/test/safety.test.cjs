'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const cdk = require('aws-cdk-lib');
const { Template } = require('aws-cdk-lib/assertions');
const { DashcamStack } = require('../stack.cjs');
const { assertTemplate, assertChanges, discover, parseAwsOutput, readStackPolicy } = require('../scripts/deploy.cjs');
const host = 'i-00000000000000000', role = 'ValidationOnlyRole';
const app = new cdk.App();
const template = Template.fromStack(new DashcamStack(app, 'RB1DashcamStack', { env: { account: '123456789012', region: 'us-east-1' },
  instanceId: host, instanceRoleName: role, domain: 'dashcam.example.com' })).toJSON();
test('RB1 owns no EC2, network, existing IAM role or DNS resources', () => {
  assertTemplate(template, host, role);
  assert.equal(template.Resources.HostAccess.Properties.Roles[0], role);
  assert.ok(!Object.values(template.Resources).some(r => r.Type.startsWith('AWS::EC2::') || r.Type === 'AWS::IAM::Role'));
});
test('30-day routine lifecycle, no expiry for saved prefix, incomplete upload cleanup', () => {
  const rules = template.Resources.Clips.Properties.LifecycleConfiguration.Rules;
  assert.ok(rules.some(r => r.Prefix === 'routine/' && r.ExpirationInDays === 30));
  assert.ok(!rules.some(r => r.Prefix === 'saved/' && r.ExpirationInDays));
  assert.ok(rules.some(r => r.AbortIncompleteMultipartUpload?.DaysAfterInitiation === 7));
});
test('exact change-set guard rejects removal, conditional replacement, host and foreign-resource changes', () => {
  const valid = { Status: 'CREATE_COMPLETE', ExecutionStatus: 'AVAILABLE', Changes: [{ Type: 'Resource',
    ResourceChange: { Action: 'Modify', Replacement: 'False', LogicalResourceId: 'ApiDeployment', ResourceType: 'AWS::SSM::Association' } }] };
  assertChanges(valid, template);
  for (const patch of [{ Action: 'Remove' }, { Replacement: 'Conditional' }, { Replacement: 'True' },
    { ResourceType: 'AWS::EC2::Instance' }, { LogicalResourceId: 'PiggyLedger' }]) {
    const bad = structuredClone(valid); Object.assign(bad.Changes[0].ResourceChange, patch);
    assert.throws(() => assertChanges(bad, template));
  }
});
test('template guard rejects broad deployment targets or modifications to role ownership', () => {
  const bad = structuredClone(template); bad.Resources.ApiDeployment.Properties.Targets = [{ Key: 'tag:all', Values: ['*'] }];
  assert.throws(() => assertTemplate(bad, host, role));
  const other = structuredClone(template); other.Resources.HostAccess.Properties.Roles = ['OtherRole'];
  assert.throws(() => assertTemplate(other, host, role));
  const policy = structuredClone(template);
  const bucketPolicy = Object.values(policy.Resources).find(r => r.Type === 'AWS::S3::BucketPolicy');
  bucketPolicy.Properties.Bucket = 'an-existing-application-bucket';
  assert.throws(() => assertTemplate(policy, host, role));
  const grant = structuredClone(template);
  grant.Resources.HostAccess.Properties.PolicyDocument.Statement[0].Resource = '*';
  assert.throws(() => assertTemplate(grant, host, role));
});
test('audit data events and storage monitoring are limited to RB1 resources', () => {
  const trail = Object.values(template.Resources).find(r => r.Type === 'AWS::CloudTrail::Trail');
  assert.ok(trail); assert.equal(trail.Properties.IsMultiRegionTrail, false);
  assert.ok(trail.Properties.EventSelectors.every(s => s.IncludeManagementEvents === false));
  const logPolicy = Object.values(template.Resources).find(r => r.Type === 'AWS::S3::BucketPolicy' && r.Properties.Bucket.Ref === 'AccessLogs');
  for (const statement of logPolicy.Properties.PolicyDocument.Statement.filter(s => s.Principal?.Service === 'cloudtrail.amazonaws.com')) {
    assert.ok(statement.Condition.StringEquals['aws:SourceArn']);
    assert.ok(statement.Condition.StringEquals['aws:SourceAccount']);
  }
  assert.ok(Object.values(template.Resources).some(r => r.Type === 'AWS::CloudWatch::Alarm'));
});
test('host install script preserves root Caddy configuration and isolates service operations', () => {
  const shell = fs.readFileSync(require.resolve('../scripts/install-api.sh'), 'utf8');
  assert.match(shell, /shared-caddy-deploy.lock/); assert.match(shell, /thecrankygoose.net/);
  assert.match(shell, /sha256sum --check/); assert.match(shell, /trap rollback EXIT/);
  assert.doesNotMatch(shell, />\s*\/etc\/caddy\/Caddyfile|systemctl (?:restart|stop) (?:caddy|piggyledger|kaedama|cranky)/);
});
test('only a successful optional stack-policy lookup accepts empty CLI output', () => {
  assert.deepEqual(parseAwsOutput('', 'cloudformation', 'get-stack-policy'), {});
  assert.deepEqual(parseAwsOutput(' \n', 'cloudformation', 'get-stack-policy'), {});
  for (const response of [{}, null, { StackPolicyBody: null }]) {
    assert.equal(readStackPolicy(() => response, 'MidlifeMotorClubStack'), null);
  }
  const policy = '{"Statement":[]}';
  assert.equal(readStackPolicy(() => ({ StackPolicyBody: policy }), 'MidlifeMotorClubStack'), policy);
  assert.throws(() => parseAwsOutput('', 'cloudformation', 'describe-stacks'), /empty output/);
  assert.throws(() => parseAwsOutput('not JSON', 'cloudformation', 'get-stack-policy'));
  assert.throws(() => readStackPolicy(() => [], 'MidlifeMotorClubStack'), /Unexpected stack policy response/);
  assert.throws(() => readStackPolicy(() => { throw Object.assign(new Error('CLI failed'), { stderr: Buffer.from('AccessDenied') }); },
    'MidlifeMotorClubStack'), /MidlifeMotorClubStack: AccessDenied/);
  assert.throws(() => readStackPolicy(() => { throw new Error('ExpiredToken'); }, 'MidlifeMotorClubStack'), /ExpiredToken/);
});
function fakeDiscovery({ piggyPolicy = true, policyError } = {}) {
  return (service, operation, args = []) => {
    const name = args[1];
    if (operation === 'describe-stack-resources') return { StackResources: [{ ResourceType: 'AWS::EC2::Instance', PhysicalResourceId: host }] };
    if (operation === 'describe-instances') return { Reservations: [{ Instances: [{ InstanceId: host, ImageId: 'ami-test',
      State: { Name: 'running' }, IamInstanceProfile: { Arn: 'arn:aws:iam::123456789012:instance-profile/test' }, PublicIpAddress: '192.0.2.1' }] }] };
    if (operation === 'get-instance-profile') return { InstanceProfile: { Roles: [{ RoleName: role }] } };
    if (operation === 'list-role-policies') return { PolicyNames: [] };
    if (operation === 'list-attached-role-policies') return { AttachedPolicies: [] };
    if (operation === 'list-associations') return { Associations: ['PiggyLedgerStack-app-deployment', 'MidlifeMotorClubStack-site-deployment',
      'KaedamaStack-site-deployment', 'CrankyGooseStack-site-deployment'].map(AssociationName => ({ AssociationName,
      Overview: { Status: 'Success' }, Targets: [{ Key: 'InstanceIds', Values: [host] }] })) };
    if (operation === 'describe-stacks') return { Stacks: [{ StackId: name, StackStatus: 'UPDATE_COMPLETE' }] };
    if (operation === 'get-template') return { TemplateBody: { Resources: {} } };
    if (operation === 'get-stack-policy') {
      if (name === 'MidlifeMotorClubStack' && policyError) throw new Error(policyError);
      if (name === 'PiggyLedgerStack' && piggyPolicy) return { StackPolicyBody: fs.readFileSync(require.resolve('../expected-shared-stack-policy.json'), 'utf8') };
      return parseAwsOutput('', service, operation);
    }
    throw new Error('Unexpected discovery operation: ' + operation);
  };
}
test('discovery preserves absent optional policies while keeping PiggyLedger protection mandatory', () => {
  const snapshot = discover(fakeDiscovery());
  assert.equal(snapshot.stacks.MidlifeMotorClubStack.policy, null);
  assert.equal(snapshot.stacks.KaedamaStack.policy, null);
  assert.equal(snapshot.stacks.CrankyGooseStack.policy, null);
  assert.ok(snapshot.stacks.PiggyLedgerStack.policy);
  assert.throws(() => discover(fakeDiscovery({ piggyPolicy: false })), /Shared host stack protection missing or changed/);
  assert.throws(() => discover(fakeDiscovery({ policyError: 'AccessDenied' })), /MidlifeMotorClubStack: AccessDenied/);
});
