'use strict';
const crypto = require('node:crypto');
const { DynamoDBClient, PutItemCommand, UpdateItemCommand } = require('@aws-sdk/client-dynamodb');
function response(statusCode, data) {
  return { statusCode, headers: { 'content-type': 'application/json', 'cache-control': 'no-store',
    'x-content-type-options': 'nosniff' }, body: JSON.stringify(data) };
}
function createHandler({ send, table, account, apiUrl, now = () => Math.floor(Date.now() / 1000), audit = console.log }) {
  return async (event) => {
    // Lambda validates SigV4 and IAM before building this trusted request context.
    const identity = event?.requestContext?.authorizer?.iam;
    if (!identity || identity.accountId !== account || typeof identity.userArn !== 'string' ||
      !identity.userArn.startsWith(`arn:aws:`) || !identity.userArn.includes(`::${account}:`))
      return response(403, { error: 'An authorized same-account IAM identity is required' });
    if (event.requestContext.http?.method !== 'POST' || event.rawPath !== '/pairing-codes')
      return response(404, { error: 'Use POST /pairing-codes' });
    if ((event.body || '').length > 1024) return response(413, { error: 'Request too large' });
    try {
      const text = event.body ? Buffer.from(event.body, event.isBase64Encoded ? 'base64' : 'utf8').toString('utf8') : '{}';
      const body = JSON.parse(text);
      if (!body || Array.isArray(body) || typeof body !== 'object' || Object.keys(body).length)
        return response(400, { error: 'Send an empty JSON object; pairing settings are server-controlled' });
    } catch { return response(400, { error: 'Invalid JSON' }); }
    const time = now();
    try {
      // Bound issuance globally to twelve codes/hour. No reads or device/clip writes allowed.
      await send(new UpdateItemCommand({ TableName: table,
        Key: { pk: { S: `PAIR-RATE#${Math.floor(time / 3600)}` }, sk: { S: 'META' } },
        UpdateExpression: 'SET expiresAt = :expiry ADD #issued :one',
        ConditionExpression: 'attribute_not_exists(#issued) OR #issued < :limit',
        ExpressionAttributeNames: { '#issued': 'issued' },
        ExpressionAttributeValues: { ':expiry': { N: String(time + 7200) }, ':one': { N: '1' }, ':limit': { N: '12' } } }));
      const code = crypto.randomBytes(32).toString('base64url');
      const expiresAt = time + 600;
      await send(new PutItemCommand({ TableName: table, Item: {
        pk: { S: 'PAIR#' + crypto.createHash('sha256').update(code).digest('hex') }, sk: { S: 'META' },
        expiresAt: { N: String(expiresAt) }, used: { BOOL: false }, version: { N: '1' }, createdBy: { S: identity.userArn }
      }, ConditionExpression: 'attribute_not_exists(pk)' }));
      // Never log the event, response, credentials, code or code verifier.
      audit(JSON.stringify({ event: 'pairing_code_issued', principal: identity.userArn,
        requestId: event.requestContext.requestId, expiresAt }));
      return response(201, { code, expiresAt, apiUrl });
    } catch (e) {
      if (e.name === 'ConditionalCheckFailedException') return response(429, { error: 'Pairing issuance limit reached; retry next hour' });
      audit(JSON.stringify({ event: 'pairing_code_failed', errorType: e.name, requestId: event.requestContext.requestId }));
      return response(503, { error: 'Unable to issue a code; retry later' });
    }
  };
}
let production;
exports.handler = async event => {
  if (!production) {
    const { RB1_TABLE, OWNER_ACCOUNT, RB1_API_URL, AWS_REGION } = process.env;
    if (!RB1_TABLE || !OWNER_ACCOUNT || !RB1_API_URL) throw new Error('Owner pairing environment is missing');
    const client = new DynamoDBClient({ region: AWS_REGION, maxAttempts: 3 });
    production = createHandler({ send: command => client.send(command), table: RB1_TABLE, account: OWNER_ACCOUNT, apiUrl: RB1_API_URL });
  }
  return production(event);
};
exports.createHandler = createHandler;
