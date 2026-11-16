'use strict';
const cdk = require('aws-cdk-lib');
const { OwnerStack } = require('./owner-stack.cjs');
const app = new cdk.App();
const tableName = app.node.tryGetContext('tableName');
const apiUrl = app.node.tryGetContext('apiUrl') || 'https://dashcam.midlifemotor.club';
if (!/^[A-Za-z0-9_.-]{3,255}$/.test(tableName || '')) throw new Error('Supply the discovered -c tableName=...');
const parsed = new URL(apiUrl);
if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.search || parsed.hash || parsed.pathname !== '/')
  throw new Error('apiUrl must be an HTTPS origin');
new OwnerStack(app, 'RB1OwnerStack', { tableName, apiUrl: parsed.origin,
  terminationProtection: true, env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION || 'us-east-1' } });
