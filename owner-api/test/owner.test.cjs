'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const cdk = require('aws-cdk-lib');
const lambda = require('aws-cdk-lib/aws-lambda');
const { Template } = require('aws-cdk-lib/assertions');
const { createHandler } = require('../index.cjs');
const { OwnerStack } = require('../../infra/owner-stack.cjs');
const { assertOwnerTemplate, assertOwnerChanges, verifyOwnerDeployment } = require('../../infra/scripts/deploy-owner.cjs');
const { requestCode, endpointFromStack } = require('../../scripts/pair.cjs');
const account = '123456789012', table = 'ValidationMetadata';
test('owner update permits only unchanged URL dependency on a proven code-only function update', () => {
  const previous = { Resources: {
    Fn: { Type: 'AWS::Lambda::Function', Properties: { Code: { S3Key: 'old' }, Runtime: 'nodejs24.x' } },
    Url: { Type: 'AWS::Lambda::Url', Properties: { AuthType: 'AWS_IAM', TargetFunctionArn: { 'Fn::GetAtt': ['Fn', 'Arn'] } } }
  } };
  const next = structuredClone(previous); next.Resources.Fn.Properties.Code.S3Key = 'new';
  const cs = { Status: 'CREATE_COMPLETE', ExecutionStatus: 'AVAILABLE', Changes: [
    { Type: 'Resource', ResourceChange: { Action: 'Modify', LogicalResourceId: 'Fn', ResourceType: 'AWS::Lambda::Function',
      Replacement: 'False', Details: [{ Target: { Attribute: 'Properties', Name: 'Code', RequiresRecreation: 'Never' } }] } },
    { Type: 'Resource', ResourceChange: { Action: 'Modify', LogicalResourceId: 'Url', ResourceType: 'AWS::Lambda::Url',
      Replacement: 'Conditional', Scope: ['Properties'], Details: [{
        Target: { Attribute: 'Properties', Name: 'TargetFunctionArn', RequiresRecreation: 'Always' },
        Evaluation: 'Dynamic', ChangeSource: 'ResourceAttribute', CausingEntity: 'Fn.Arn' }] } }
  ] };
  assert.doesNotThrow(() => assertOwnerChanges(cs, next, () => previous));
  assert.equal(cs.Changes[1].ResourceChange.Replacement, 'Conditional');
  for (const mutate of [
    t => { t.Resources.Url.Properties.AuthType = 'NONE'; },
    t => { t.Resources.Fn.Properties.FunctionName = 'different'; },
    t => { t.Resources.Fn.Properties.Runtime = 'nodejs22.x'; },
    t => { t.Resources.Url.Properties.TargetFunctionArn = { 'Fn::GetAtt': ['Other', 'Arn'] }; }
  ]) {
    const bad = structuredClone(next); mutate(bad);
    assert.throws(() => assertOwnerChanges(cs, bad, () => previous));
  }
  const replacement = structuredClone(cs); replacement.Changes[0].ResourceChange.Replacement = 'True';
  assert.throws(() => assertOwnerChanges(replacement, next, () => previous));
  const direct = structuredClone(cs); direct.Changes[1].ResourceChange.Details[0].ChangeSource = 'DirectModification';
  assert.throws(() => assertOwnerChanges(direct, next, () => previous));
});
function event() { return { version: '2.0', rawPath: '/pairing-codes', body: '{}',
  requestContext: { requestId: 'unit-test', http: { method: 'POST' },
    authorizer: { iam: { accountId: account, userArn: `arn:aws:sts::${account}:assumed-role/Owner/session` } } } }; }
function fixture(send) {
  const calls = [], audit = [];
  const handler = createHandler({ table, account, apiUrl: 'https://dashcam.example.com', now: () => 1000000,
    send: send || (async command => { calls.push(command); return {}; }), audit: message => audit.push(message) });
  return { handler, calls, audit };
}
test('owner issuance produces a one-use-compatible 256-bit code and stores only its verifier', async () => {
  const f = fixture(), r = await f.handler(event()), data = JSON.parse(r.body);
  assert.equal(r.statusCode, 201); assert.match(data.code, /^[A-Za-z0-9_-]{43}$/);
  assert.equal(data.expiresAt, 1000600); assert.equal(r.headers['cache-control'], 'no-store');
  assert.equal(f.calls.length, 2);
  const item = f.calls[1].input.Item;
  assert.equal(item.pk.S, 'PAIR#' + crypto.createHash('sha256').update(data.code).digest('hex'));
  assert.equal(item.sk.S, 'META'); assert.equal(item.used.BOOL, false);
  assert.equal(item.version.N, '1');
  assert.equal(Number(item.expiresAt.N), data.expiresAt);
  assert.ok(!JSON.stringify(f.calls).includes(data.code)); assert.ok(!f.audit.join('').includes(data.code));
});
test('a Lambda-issued code enrolls through the existing phone API exactly once', async () => {
  const f = fixture(), issued = JSON.parse((await f.handler(event())).body);
  const map = new Map();
  const k = key => key.pk + '|' + key.sk;
  const item = Object.fromEntries(Object.entries(f.calls[1].input.Item).map(([key, value]) =>
    [key, value.S ?? (value.N ? Number(value.N) : value.BOOL)]));
  // Use the actual Lambda item; do not inject a version that production did not write.
  map.set(k(item), item);
  const db = {
    get: async key => structuredClone(map.get(k(key)) || null),
    create: async (key, data) => { map.set(k(key), { ...key, ...data, version: 1 }); },
    transaction: async changes => {
      for (const c of changes) assert.equal(map.get(k(c.key))?.version || null, c.before?.version || null);
      for (const c of changes) map.set(k(c.key), { ...c.key, ...c.after, version: (c.before?.version || 0) + 1 });
    }
  };
  const api = require('../../server/core.cjs').createApi({ db, storage: {}, now: () => 1000000 });
  const enrolled = await api('POST', '/v1/enroll', { code: issued.code });
  assert.match(enrolled.deviceId, /^[a-f0-9-]{36}$/); assert.match(enrolled.refreshToken, /^[A-Za-z0-9_-]{43}$/);
  await assert.rejects(api('POST', '/v1/enroll', { code: issued.code }), { status: 401 });
});
test('missing or wrong-account IAM context cannot create a code', async () => {
  const f = fixture();
  const absent = event(); delete absent.requestContext.authorizer;
  assert.equal((await f.handler(absent)).statusCode, 403);
  const other = event(); other.requestContext.authorizer.iam.accountId = '999999999999';
  assert.equal((await f.handler(other)).statusCode, 403); assert.equal(f.calls.length, 0);
});
test('caller cannot control code lifetime, supply a target table, or use another route', async () => {
  const f = fixture();
  assert.equal((await f.handler({ ...event(), body: '{"expiresAt":9999999999}' })).statusCode, 400);
  assert.equal((await f.handler({ ...event(), body: '{"table":"other"}' })).statusCode, 400);
  assert.equal((await f.handler({ ...event(), body: '{' })).statusCode, 400);
  assert.equal((await f.handler({ ...event(), rawPath: '/admin' })).statusCode, 404);
  assert.equal((await f.handler({ ...event(), body: 'x'.repeat(1025) })).statusCode, 413);
  assert.equal(f.calls.length, 0);
});
test('issuance limit and storage failures do not leak exception details or create a code', async () => {
  for (const [name, status] of [['ConditionalCheckFailedException', 429], ['AccessDeniedException', 503]]) {
    const f = fixture(async () => { throw Object.assign(new Error('private details'), { name }); });
    const result = await f.handler(event()); assert.equal(result.statusCode, status);
    assert.ok(!result.body.includes('private details'));
  }
});
const app = new cdk.App();
const template = Template.fromStack(new OwnerStack(app, 'RB1OwnerStack', { env: { account, region: 'us-east-1' },
  tableName: table, apiUrl: 'https://dashcam.example.com', code: lambda.Code.fromInline('exports.handler=async()=>({statusCode:403})') })).toJSON();
test('owner stack uses IAM-only URL and cannot manage EC2, existing roles, tables or SSM', () => {
  assertOwnerTemplate(template, table);
  const types = Object.values(template.Resources).map(r => r.Type);
  assert.ok(!types.some(t => t.startsWith('AWS::EC2::') || t.startsWith('AWS::SSM::') || t === 'AWS::DynamoDB::Table' || t === 'AWS::Lambda::Permission'));
  const url = Object.values(template.Resources).find(r => r.Type === 'AWS::Lambda::Url'); assert.equal(url.Properties.AuthType, 'AWS_IAM');
  const policy = JSON.stringify(template.Outputs.CallerPolicy);
  assert.ok(policy.includes('lambda:InvokeFunctionUrl') && policy.includes('lambda:InvokeFunction') && policy.includes('lambda:InvokedViaFunctionUrl'));
});
test('owner guard rejects public URL, existing role attachment, and broader DynamoDB access', () => {
  const open = structuredClone(template); Object.values(open.Resources).find(r => r.Type === 'AWS::Lambda::Url').Properties.AuthType = 'NONE';
  assert.throws(() => assertOwnerTemplate(open, table), /require IAM/);
  const broad = structuredClone(template);
  const policy = Object.values(broad.Resources).find(r => r.Type === 'AWS::IAM::Policy');
  policy.Properties.Roles = ['ExistingHostRole']; assert.throws(() => assertOwnerTemplate(broad, table), /existing role/);
  const tableWrite = structuredClone(template);
  const statements = Object.values(tableWrite.Resources).find(r => r.Type === 'AWS::IAM::Policy').Properties.PolicyDocument.Statement;
  const ddb = statements.find(s => [].concat(s.Action).includes('dynamodb:PutItem')); delete ddb.Condition;
  assert.throws(() => assertOwnerTemplate(tableWrite, table), /pairing records/);
});
test('completed owner verification performs reads only and checks IAM, unchanged dependencies and template', async () => {
  const current = { parent: { outputs: { ApiUrl: 'https://dashcam.example.com' } }, shared: { host: 'i-original' } };
  const review = { template, snapshot: current }, endpoint = 'https://test.lambda-url.us-east-1.on.aws/';
  const calls = [];
  const aws = (service, operation) => {
    calls.push(operation);
    if (operation === 'describe-stacks') return { Stacks: [{ StackStatus: 'CREATE_COMPLETE', Outputs: [
      { OutputKey: 'OwnerUrl', OutputValue: endpoint }, { OutputKey: 'OwnerFunctionArn', OutputValue: 'unit-test-function' }] }] };
    if (operation === 'get-template') return { TemplateBody: template };
    if (operation === 'get-function-url-config') return { FunctionUrl: endpoint, AuthType: 'AWS_IAM' };
    throw new Error('Unexpected mutation or lookup: ' + operation);
  };
  const options = { checkSites: async () => {}, checkApi: async () => {}, request: async () => ({ status: 403 }) };
  await verifyOwnerDeployment(aws, review, current, options);
  assert.deepEqual(calls, ['describe-stacks', 'get-template', 'get-function-url-config']);
  await assert.rejects(verifyOwnerDeployment(aws, review, { ...current, shared: { host: 'i-changed' } }, options), /differs/);
  await assert.rejects(verifyOwnerDeployment(aws, review, current, { ...options, request: async () => ({ status: 200 }) }), /must be rejected/);
});
test('laptop helper signs with lambda SigV4 and temporary session credentials without printing keys', async () => {
  const code = crypto.randomBytes(32).toString('base64url'); let captured;
  const result = await requestCode('https://unittest.lambda-url.us-east-1.on.aws/', {
    credentials: { accessKeyId: 'UNIT_TEST_ONLY', secretAccessKey: 'not-a-real-secret', sessionToken: 'test-session' },
    request: async (url, options) => { captured = { url, options }; return { ok: true, status: 201,
      json: async () => ({ code, expiresAt: 1000600, apiUrl: 'https://dashcam.example.com' }) }; }
  });
  assert.equal(result.code, code); assert.equal(captured.url, 'https://unittest.lambda-url.us-east-1.on.aws/pairing-codes');
  assert.match(captured.options.headers.authorization, /AWS4-HMAC-SHA256/);
  assert.match(captured.options.headers.authorization, /us-east-1\/lambda\/aws4_request/);
  assert.equal(captured.options.headers['x-amz-security-token'], 'test-session');
  assert.match(captured.options.headers['x-amz-content-sha256'], /^[a-f0-9]{64}$/);
  assert.equal(captured.options.redirect, 'error'); assert.equal(captured.options.body, '{}');
});
test('laptop helper rejects unexpected endpoints and explains missing IAM invocation permissions', async () => {
  const stack = { StackId: 'arn:aws:cloudformation:us-east-1:294281109519:stack/RB1OwnerStack/test',
    Outputs: [{ OutputKey: 'OwnerUrl', OutputValue: 'https://test.lambda-url.us-east-1.on.aws/' }] };
  assert.equal(endpointFromStack(stack), stack.Outputs[0].OutputValue);
  assert.throws(() => endpointFromStack({ ...stack, StackId: stack.StackId.replace('294281109519', '999999999999') }), /account/);
  assert.equal(typeof require('@aws-sdk/credential-providers').fromNodeProviderChain({ profile: 'unit-test' }), 'function');
  await assert.rejects(requestCode('https://attacker.example/'), /Expected/);
  await assert.rejects(requestCode('https://test.lambda-url.us-east-1.on.aws/', { credentials: { accessKeyId: 'TEST', secretAccessKey: 'TEST' },
    request: async () => ({ ok: false, status: 403 }) }), /InvokeFunctionUrl AND InvokeFunction/);
});
