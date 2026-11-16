'use strict';
const cdk = require('aws-cdk-lib');
const { DashcamStack } = require('./stack.cjs');
const app = new cdk.App();
const instanceId = app.node.tryGetContext('instanceId');
const instanceRoleName = app.node.tryGetContext('instanceRoleName');
const domain = app.node.tryGetContext('domain') || 'dashcam.midlifemotor.club';
if (!/^i-[a-f0-9]{17}$/.test(instanceId || '')) throw new Error('Supply discovered -c instanceId=i-...');
if (!/^[a-zA-Z0-9+=,.@_-]{1,64}$/.test(instanceRoleName || '')) throw new Error('Supply discovered -c instanceRoleName=...');
if (!/^[a-z0-9]+([.-][a-z0-9]+)*\.[a-z]{2,}$/.test(domain)) throw new Error('Invalid API domain');
if (['piggyledger.net', 'midlifemotor.club', 'kaedamacycles.com', 'thecrankygoose.net'].some(d => domain === d || domain === 'www.' + d))
  throw new Error('RB1 must use its own hostname, never an existing site hostname');
new DashcamStack(app, 'RB1DashcamStack', {
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION || 'us-east-1' },
  terminationProtection: true, instanceId, instanceRoleName, domain
});
