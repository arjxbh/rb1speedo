'use strict';
const http = require('node:http');
const { createApi } = require('./core.cjs');
const { database, objectStorage } = require('./aws.cjs');
function server(api) {
  let count = 0, window = Date.now();
  return http.createServer(async (req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    try {
      if (Date.now() - window > 60000) { count = 0; window = Date.now(); }
      if (++count > 300) throw Object.assign(new Error('Rate limit exceeded'), { status: 429 });
      let bytes = 0, chunks = [];
      for await (const chunk of req) {
        bytes += chunk.length;
        if (bytes > 32768) throw Object.assign(new Error('Request too large'), { status: 413 });
        chunks.push(chunk);
      }
      let body = {};
      try { if (bytes) body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
      catch { throw Object.assign(new Error('Invalid JSON'), { status: 400 }); }
      if (!body || Array.isArray(body) || typeof body !== 'object') throw Object.assign(new Error('Invalid body'), { status: 400 });
      const bearer = req.headers.authorization?.match(/^Bearer ([A-Za-z0-9_-]+)$/)?.[1];
      const result = await api(req.method, req.url.split('?')[0], body, bearer);
      res.end(JSON.stringify(result));
    } catch (e) {
      const conflict = ['ConditionalCheckFailedException', 'TransactionCanceledException'].includes(e.name);
      res.statusCode = e.status || (conflict ? 409 : 503);
      res.end(JSON.stringify({ error: e.status ? e.message : conflict ? 'Concurrent update; retry' : 'Service unavailable; retry' }));
      // No request bodies, tokens, URL query strings, or AWS error objects in logs.
      if (res.statusCode >= 500) console.error(JSON.stringify({ event: 'request_failed', errorType: e.name }));
    }
  });
}
if (require.main === module) {
  const { RB1_TABLE, RB1_BUCKET, AWS_REGION = 'us-east-1' } = process.env;
  if (!RB1_TABLE || !RB1_BUCKET) throw new Error('RB1_TABLE and RB1_BUCKET are required');
  const instance = server(createApi({ db: database(RB1_TABLE, AWS_REGION), storage: objectStorage(RB1_BUCKET, AWS_REGION) }));
  instance.requestTimeout = 15000; instance.headersTimeout = 10000;
  instance.listen(3081, '127.0.0.1', () => console.log('RB1 API listening on loopback:3081'));
}
module.exports = { server };
