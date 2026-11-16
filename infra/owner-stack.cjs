'use strict';
const cdk = require('aws-cdk-lib');
const lambda = require('aws-cdk-lib/aws-lambda');
const iam = require('aws-cdk-lib/aws-iam');
const logs = require('aws-cdk-lib/aws-logs');
const path = require('node:path');
const fs = require('node:fs');
class OwnerStack extends cdk.Stack {
  constructor(scope, id, props) {
    super(scope, id, props);
    const tableArn = this.formatArn({ service: 'dynamodb', resource: 'table', resourceName: props.tableName });
    const group = new logs.LogGroup(this, 'OwnerLogs', { retention: logs.RetentionDays.ONE_WEEK, removalPolicy: cdk.RemovalPolicy.RETAIN });
    const role = new iam.Role(this, 'OwnerExecutionRole', { assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com') });
    const location = path.resolve(__dirname, '../owner-api');
    if (!props.code && !fs.existsSync(path.join(location, 'node_modules/@aws-sdk/client-dynamodb/package.json')))
      throw new Error('Run npm run build:owner before synthesizing the owner stack');
    const fn = new lambda.Function(this, 'OwnerFunction', { runtime: lambda.Runtime.NODEJS_24_X,
      architecture: lambda.Architecture.ARM_64, handler: 'index.handler',
      code: props.code || lambda.Code.fromAsset(location, { exclude: ['test', '*.local.json'] }),
      role, logGroup: group, memorySize: 128, timeout: cdk.Duration.seconds(10),
      environment: { RB1_TABLE: props.tableName, OWNER_ACCOUNT: this.account, RB1_API_URL: props.apiUrl } });
    role.addToPrincipalPolicy(new iam.PolicyStatement({ actions: ['dynamodb:PutItem', 'dynamodb:UpdateItem'], resources: [tableArn],
      conditions: { 'ForAllValues:StringLike': { 'dynamodb:LeadingKeys': ['PAIR#*', 'PAIR-RATE#*'] } } }));
    const url = fn.addFunctionUrl({ authType: lambda.FunctionUrlAuthType.AWS_IAM });
    new cdk.CfnOutput(this, 'OwnerUrl', { value: url.url });
    new cdk.CfnOutput(this, 'OwnerFunctionArn', { value: fn.functionArn });
    new cdk.CfnOutput(this, 'CallerPolicy', { value: this.toJsonString({ Version: '2012-10-17', Statement: [
      { Effect: 'Allow', Action: 'lambda:InvokeFunctionUrl', Resource: fn.functionArn,
        Condition: { StringEquals: { 'lambda:FunctionUrlAuthType': 'AWS_IAM' } } },
      { Effect: 'Allow', Action: 'lambda:InvokeFunction', Resource: fn.functionArn,
        Condition: { Bool: { 'lambda:InvokedViaFunctionUrl': 'true' } } },
      { Effect: 'Allow', Action: 'cloudformation:DescribeStacks', Resource: this.stackId }
    ] }) });
  }
}
module.exports = { OwnerStack };
