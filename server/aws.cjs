'use strict';
const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, GetCommand, PutCommand, QueryCommand, TransactWriteCommand } = require('@aws-sdk/lib-dynamodb');
const { S3Client, CreateMultipartUploadCommand, UploadPartCommand, ListPartsCommand, CompleteMultipartUploadCommand,
  HeadObjectCommand, AbortMultipartUploadCommand, CopyObjectCommand, GetObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');

function database(table, region) {
  const client = DynamoDBDocumentClient.from(new DynamoDBClient({ region }), { marshallOptions: { removeUndefinedValues: true } });
  function write(k, before, after) {
    return { TableName: table, Item: { ...after, ...k, version: (before?.version || 0) + 1 },
      ConditionExpression: before ? '#v = :v' : 'attribute_not_exists(pk)',
      ...(before ? { ExpressionAttributeNames: { '#v': 'version' }, ExpressionAttributeValues: { ':v': before.version } } : {}) };
  }
  const db = {
    async get(k) { return (await client.send(new GetCommand({ TableName: table, Key: k, ConsistentRead: true }))).Item || null; },
    async create(k, item) { await client.send(new PutCommand(write(k, null, item))); },
    async cas(k, before, after) { await client.send(new PutCommand(write(k, before, after))); return { ...after, ...k, version: before.version + 1 }; },
    async transaction(changes) { await client.send(new TransactWriteCommand({ TransactItems: changes.map(c => ({ Put: write(c.key, c.before, c.after) })) })); },
    async clips(device) {
      const out = []; let cursor;
      do {
        const page = await client.send(new QueryCommand({ TableName: table, KeyConditionExpression: 'pk = :pk AND begins_with(sk, :prefix)',
          ExpressionAttributeValues: { ':pk': 'DEVICE#' + device, ':prefix': 'CLIP#' }, ExclusiveStartKey: cursor }));
        out.push(...page.Items); cursor = page.LastEvaluatedKey;
      } while (cursor);
      return out;
    }
  }; return db;
}
function objectStorage(bucket, region, options = {}) {
  const s3 = new S3Client({ region, maxAttempts: 3, ...options });
  const base = Key => ({ Bucket: bucket, Key });
  return {
    async start(Key) { return (await s3.send(new CreateMultipartUploadCommand({ ...base(Key), ContentType: 'video/mp4',
      ServerSideEncryption: 'AES256', ChecksumAlgorithm: 'SHA256', ChecksumType: 'COMPOSITE' }))).UploadId; },
    async abort(Key, UploadId) { await s3.send(new AbortMultipartUploadCommand({ ...base(Key), UploadId })); },
    async sign(Key, UploadId, PartNumber, ContentLength, ChecksumSHA256) {
      return getSignedUrl(s3, new UploadPartCommand({ ...base(Key), UploadId, PartNumber, ContentLength, ChecksumSHA256 }),
        { expiresIn: 900, unhoistableHeaders: new Set(['x-amz-checksum-sha256']), signableHeaders: new Set(['content-length']) });
    },
    async parts(Key, UploadId) {
      const parts = []; let marker;
      do {
        const page = await s3.send(new ListPartsCommand({ ...base(Key), UploadId, PartNumberMarker: marker }));
        parts.push(...(page.Parts || []).map(p => ({ number: p.PartNumber, etag: p.ETag, checksum: p.ChecksumSHA256, size: p.Size })));
        marker = page.IsTruncated ? page.NextPartNumberMarker : undefined;
      } while (marker);
      return parts.sort((a, b) => a.number - b.number);
    },
    async complete(Key, UploadId, parts) {
      await s3.send(new CompleteMultipartUploadCommand({ ...base(Key), UploadId, ChecksumType: 'COMPOSITE',
        MultipartUpload: { Parts: parts.map(p => ({ PartNumber: p.number, ETag: p.etag, ChecksumSHA256: p.checksum })) } }));
    },
    async head(Key) {
      try { const h = await s3.send(new HeadObjectCommand({ ...base(Key), ChecksumMode: 'ENABLED' }));
        return { size: h.ContentLength, checksum: h.ChecksumSHA256 }; }
      catch (e) { if (e.$metadata?.httpStatusCode === 404) return null; throw e; }
    },
    async copy(source, Key) { await s3.send(new CopyObjectCommand({ ...base(Key),
      CopySource: `${bucket}/${source.split('/').map(encodeURIComponent).join('/')}`, ChecksumAlgorithm: 'SHA256' })); },
    async download(Key) { return getSignedUrl(s3, new GetObjectCommand(base(Key)), { expiresIn: 300 }); }
  };
}
module.exports = { database, objectStorage };
