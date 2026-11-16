'use strict';
const cdk = require('aws-cdk-lib');
const s3 = require('aws-cdk-lib/aws-s3');
const dynamodb = require('aws-cdk-lib/aws-dynamodb');
const iam = require('aws-cdk-lib/aws-iam');
const assets = require('aws-cdk-lib/aws-s3-assets');
const ssm = require('aws-cdk-lib/aws-ssm');
const cloudtrail = require('aws-cdk-lib/aws-cloudtrail');
const cloudwatch = require('aws-cdk-lib/aws-cloudwatch');
const fs = require('node:fs');
const path = require('node:path');

class DashcamStack extends cdk.Stack {
  constructor(scope, id, props) {
    super(scope, id, props);
    const logs = new s3.Bucket(this, 'AccessLogs', { encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL, enforceSSL: true,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_ENFORCED, removalPolicy: cdk.RemovalPolicy.RETAIN,
      lifecycleRules: [{ expiration: cdk.Duration.days(30) }] });
    const bucket = new s3.Bucket(this, 'Clips', { encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL, enforceSSL: true,
      objectOwnership: s3.ObjectOwnership.BUCKET_OWNER_ENFORCED, removalPolicy: cdk.RemovalPolicy.RETAIN,
      lifecycleRules: [{ prefix: 'routine/', expiration: cdk.Duration.days(30) },
        { abortIncompleteMultipartUploadAfter: cdk.Duration.days(7) }] });
    bucket.node.defaultChild.loggingConfiguration = { destinationBucketName: logs.bucketName, logFilePrefix: 'clips/' };
    logs.addToResourcePolicy(new iam.PolicyStatement({ principals: [new iam.ServicePrincipal('logging.s3.amazonaws.com')],
      actions: ['s3:PutObject'], resources: [logs.arnForObjects('clips/*')],
      conditions: { StringEquals: { 'aws:SourceAccount': this.account }, ArnLike: { 'aws:SourceArn': bucket.bucketArn } } }));
    const auditArn = this.formatArn({ service: 'cloudtrail', resource: 'trail', resourceName: 'rb1-dashcam-audit' });
    // Use explicit scoped log-delivery grants; the Trail L2's default grants omit SourceArn.
    logs.addToResourcePolicy(new iam.PolicyStatement({ principals: [new iam.ServicePrincipal('cloudtrail.amazonaws.com')],
      actions: ['s3:GetBucketAcl'], resources: [logs.bucketArn],
      conditions: { StringEquals: { 'aws:SourceArn': auditArn, 'aws:SourceAccount': this.account } } }));
    logs.addToResourcePolicy(new iam.PolicyStatement({ principals: [new iam.ServicePrincipal('cloudtrail.amazonaws.com')],
      actions: ['s3:PutObject'], resources: [logs.arnForObjects(`audit/AWSLogs/${this.account}/*`)],
      conditions: { StringEquals: { 's3:x-amz-acl': 'bucket-owner-full-control', 'aws:SourceArn': auditArn, 'aws:SourceAccount': this.account } } }));
    const audit = new cloudtrail.CfnTrail(this, 'ClipAudit', { trailName: 'rb1-dashcam-audit', s3BucketName: logs.bucketName,
      s3KeyPrefix: 'audit', isLogging: true, isMultiRegionTrail: false, includeGlobalServiceEvents: false,
      enableLogFileValidation: true, eventSelectors: [{ includeManagementEvents: false, readWriteType: 'All',
        dataResources: [{ type: 'AWS::S3::Object', values: [bucket.arnForObjects('routine/'), bucket.arnForObjects('saved/')] }] }] });
    audit.node.addDependency(logs.policy);
    new cloudwatch.Alarm(this, 'StorageBudget', { metric: new cloudwatch.Metric({ namespace: 'AWS/S3',
      metricName: 'BucketSizeBytes', dimensionsMap: { BucketName: bucket.bucketName, StorageType: 'StandardStorage' },
      period: cdk.Duration.days(1), statistic: 'Average' }), threshold: 100 * 1024 ** 3, evaluationPeriods: 1,
      treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      alarmDescription: 'RB1 footage exceeds the expected rolling storage budget. Inspect retention and protected clips.' });
    const table = new dynamodb.Table(this, 'Metadata', { partitionKey: { name: 'pk', type: dynamodb.AttributeType.STRING },
      sortKey: { name: 'sk', type: dynamodb.AttributeType.STRING }, billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      encryption: dynamodb.TableEncryption.AWS_MANAGED, timeToLiveAttribute: 'expiresAt',
      pointInTimeRecoverySpecification: { pointInTimeRecoveryEnabled: true }, removalPolicy: cdk.RemovalPolicy.RETAIN });
    const asset = new assets.Asset(this, 'ApiAsset', { path: path.resolve(__dirname, '../server'),
      exclude: ['node_modules', 'test', '*.local.json'] });
    const role = iam.Role.fromRoleName(this, 'ExistingHostRole', props.instanceRoleName);
    // A new policy owned by THIS stack. No editing/replacing PiggyLedger's role or its policies.
    const policy = new iam.Policy(this, 'HostAccess', { policyName: 'rb1-dashcam-host-access', roles: [role], statements: [
      new iam.PolicyStatement({ actions: ['s3:GetObject'], resources: [asset.bucket.arnForObjects(asset.s3ObjectKey)] }),
      new iam.PolicyStatement({ actions: ['dynamodb:GetItem', 'dynamodb:PutItem', 'dynamodb:Query'], resources: [table.tableArn] }),
      new iam.PolicyStatement({ actions: ['s3:PutObject', 's3:GetObject', 's3:AbortMultipartUpload', 's3:ListMultipartUploadParts'],
        resources: [bucket.arnForObjects('routine/*'), bucket.arnForObjects('saved/*')] })
    ] });
    const association = new ssm.CfnAssociation(this, 'ApiDeployment', {
      name: 'AWS-RunShellScript', associationName: 'RB1DashcamStack-api-deployment',
      targets: [{ key: 'InstanceIds', values: [props.instanceId] }],
      waitForSuccessTimeoutSeconds: 900,
      parameters: { commands: [
        `export RB1_ASSET_URI='s3://${asset.s3BucketName}/${asset.s3ObjectKey}'`,
        `export RB1_TABLE='${table.tableName}'`, `export RB1_BUCKET='${bucket.bucketName}'`,
        `export RB1_DOMAIN='${props.domain}'`, `export AWS_REGION='${this.region}'`,
        fs.readFileSync(path.join(__dirname, 'scripts/install-api.sh'), 'utf8')
      ] }
    });
    association.node.addDependency(policy);
    for (const [construct, logical] of [[logs, 'AccessLogs'], [bucket, 'Clips'], [table, 'Metadata'],
      [policy, 'HostAccess'], [association, 'ApiDeployment']]) (construct.node.defaultChild || construct).overrideLogicalId(logical);
    new cdk.CfnOutput(this, 'ApiUrl', { value: `https://${props.domain}` });
    new cdk.CfnOutput(this, 'BucketName', { value: bucket.bucketName });
    new cdk.CfnOutput(this, 'TableName', { value: table.tableName });
  }
}
module.exports = { DashcamStack };
