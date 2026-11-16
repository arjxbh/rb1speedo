'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { createApi, hash, random, key, composite, PART_SIZE } = require('../core.cjs');
class MemoryDb {
  data = new Map();
  k(k) { return k.pk + '|' + k.sk; }
  async get(k) { return structuredClone(this.data.get(this.k(k)) || null); }
  async create(k, item) { return this.transaction([{ key: k, before: null, after: item }]); }
  async cas(k, before, after) { await this.transaction([{ key: k, before, after }]); return this.get(k); }
  async transaction(changes) {
    for (const c of changes) {
      const current = this.data.get(this.k(c.key));
      if ((current?.version || null) !== (c.before?.version || null)) throw Object.assign(new Error('Conflict'), { status: 409 });
    }
    for (const c of changes) this.data.set(this.k(c.key), structuredClone({ ...c.after, ...c.key, version: (c.before?.version || 0) + 1 }));
  }
  async clips(device) { return [...this.data.values()].filter(c => c.pk === 'DEVICE#' + device && c.sk.startsWith('CLIP#')); }
}
class Storage {
  sessions = new Map(); objects = new Map();
  async start(k) { const id = random(); this.sessions.set(id, { key: k, parts: [] }); return id; }
  async abort(k, id) { this.sessions.delete(id); }
  async parts(k, id) { if (!this.sessions.has(id)) throw Object.assign(new Error(), { name: 'NoSuchUpload' }); return this.sessions.get(id).parts; }
  async sign(k, id, number, size, checksum) { return `https://test.invalid/${number}?size=${size}&checksum=${checksum}`; }
  async complete(k, id, parts) { this.objects.set(k, { size: parts.reduce((n, p) => n + p.size, 0), checksum: composite(parts.map(p => p.checksum)) }); this.sessions.delete(id); }
  async head(k) { return this.objects.get(k) || null; }
  async copy(a, b) { this.objects.set(b, structuredClone(this.objects.get(a))); }
  async download(k) { return 'https://download.invalid/' + k; }
}
async function fixture() {
  let time = 1000000;
  const db = new MemoryDb(), storage = new Storage();
  const api = createApi({ db, storage, now: () => time });
  async function enroll() {
    const code = random(); await db.create(key('PAIR#' + hash(code)), { expiresAt: time + 600 });
    return { ...await api('POST', '/v1/enroll', { code }), code };
  }
  const device = await enroll();
  const request = (m, p, b = {}, t = device.accessToken) => api(m, p, b, t);
  return { db, storage, api, request, device, enroll, advance: seconds => { time += seconds; } };
}
test('one-time enrollment; expiry is enforced independently of DynamoDB TTL cleanup', async () => {
  const f = await fixture();
  await assert.rejects(f.api('POST', '/v1/enroll', { code: f.device.code }), { status: 401 });
  f.advance(901); await assert.rejects(f.request('GET', '/v1/clips'), { status: 401 });
});
test('refresh rotation survives a lost response and rejects unrelated credentials', async () => {
  const f = await fixture(), next = random();
  const body = { deviceId: f.device.deviceId, refreshToken: f.device.refreshToken, nextRefreshToken: next };
  await f.api('POST', '/v1/token', body);
  const retry = await f.api('POST', '/v1/token', body);
  assert.equal((await f.api('GET', '/v1/clips', {}, retry.accessToken)).clips.length, 0);
  await assert.rejects(f.api('POST', '/v1/token', { ...body, nextRefreshToken: random() }), { status: 401 });
  f.advance(86401);
  await assert.rejects(f.api('POST', '/v1/token', body), { status: 401 });
  await f.api('POST', '/v1/token', { ...body, refreshToken: next, nextRefreshToken: random() });
});
test('revocation invalidates an existing access token immediately', async () => {
  const f = await fixture(), k = key('DEVICE#' + f.device.deviceId), d = await f.db.get(k);
  await f.db.cas(k, d, { ...d, revoked: true });
  await assert.rejects(f.request('GET', '/v1/clips'), { status: 401 });
});
async function register(f, size = 1024) {
  const id = crypto.randomUUID(), partHashes = Array.from({ length: Math.ceil(size / PART_SIZE) }, () => crypto.randomBytes(32).toString('base64'));
  const body = { id, size, startedAt: 900000000, partHashes };
  await f.request('POST', '/v1/clips', body);
  const path = '/v1/clips/' + id;
  await f.request('POST', path + '/upload');
  return { id, path, body, c: await f.db.get(key('DEVICE#' + f.device.deviceId, 'CLIP#' + id)) };
}
test('ownership, exact part range, and clip idempotency', async () => {
  const f = await fixture(), c = await register(f), other = await f.enroll();
  await assert.rejects(f.request('POST', c.path + '/parts/sign', { partNumber: 1 }, other.accessToken), { status: 404 });
  await assert.rejects(f.request('POST', c.path + '/parts/sign', { partNumber: 0 }), { status: 400 });
  await assert.rejects(f.request('POST', c.path + '/parts/sign', { partNumber: 2 }), { status: 400 });
  assert.equal((await f.request('POST', c.path + '/parts/sign', { partNumber: 1 })).length, 1024);
  await f.request('POST', '/v1/clips', c.body);
  await assert.rejects(f.request('POST', '/v1/clips', { ...c.body, size: 2048 }), { status: 409 });
});
test('incomplete or corrupt parts cannot become verified; successful completion resumes after response loss', async () => {
  const f = await fixture(), c = await register(f);
  await assert.rejects(f.request('POST', c.path + '/complete'), { status: 409 });
  const session = f.storage.sessions.get(c.c.uploadId);
  session.parts = [{ number: 1, size: 1024, etag: 'etag-is-not-a-checksum', checksum: random() }];
  await assert.rejects(f.request('POST', c.path + '/complete'), { status: 409 });
  session.parts[0].checksum = c.body.partHashes[0];
  // S3 completion succeeds, but the server never commits the verified metadata.
  await f.storage.complete(c.c.objectKey, c.c.uploadId, session.parts);
  assert.equal((await f.request('POST', c.path + '/upload')).verified, true);
  const stored = await f.db.get(key('DEVICE#' + f.device.deviceId, 'CLIP#' + c.id)); assert.equal(stored.verified, true);
});
test('normal multipart completion and incident copy leave saved objects outside routine expiry prefix', async () => {
  const f = await fixture(), c = await register(f, PART_SIZE + 25);
  f.storage.sessions.get(c.c.uploadId).parts = c.body.partHashes.map((checksum, i) => ({ number: i + 1, checksum,
    etag: 'part-' + i, size: i === 0 ? PART_SIZE : 25 }));
  assert.equal((await f.request('POST', c.path + '/complete')).verified, true);
  await f.request('POST', c.path + '/protect');
  const protectedClip = await f.db.get(key('DEVICE#' + f.device.deviceId, 'CLIP#' + c.id));
  assert.ok(protectedClip.objectKey.startsWith('saved/')); assert.equal(protectedClip.expiresAt, undefined);
});
test('aborted or lifecycle-expired uploads reset without falsely verifying a missing object', async () => {
  const f = await fixture(), c = await register(f); f.storage.sessions.delete(c.c.uploadId);
  await assert.rejects(f.request('POST', c.path + '/upload'), { status: 409 });
  assert.equal((await f.request('POST', c.path + '/upload')).verified, false);
});
test('protection after routine expiry requests a new protected upload rather than claiming success', async () => {
  const f = await fixture(), c = await register(f);
  f.storage.sessions.get(c.c.uploadId).parts = [{ number: 1, checksum: c.body.partHashes[0], etag: 'etag', size: 1024 }];
  await f.request('POST', c.path + '/complete');
  assert.equal((await f.request('POST', c.path + '/complete')).verified, true);
  f.storage.objects.delete(c.c.objectKey);
  assert.equal((await f.request('POST', c.path + '/protect')).verified, false);
  const row = await f.db.get(key('DEVICE#' + f.device.deviceId, 'CLIP#' + c.id));
  assert.ok(row.objectKey.startsWith('saved/')); assert.equal(row.verified, false);
});
test('concurrent enrollment redemption can create only one identity', async () => {
  const f = await fixture(), code = random();
  await f.db.create(key('PAIR#' + hash(code)), { expiresAt: 1000600 });
  const attempts = await Promise.allSettled([f.api('POST', '/v1/enroll', { code }), f.api('POST', '/v1/enroll', { code })]);
  assert.equal(attempts.filter(x => x.status === 'fulfilled').length, 1);
});
test('server rejects oversized declarations and daily quota exhaustion before allocating more storage', async () => {
  const f = await fixture();
  await assert.rejects(f.request('POST', '/v1/clips', { id: crypto.randomUUID(), size: 2 * 1024 ** 3 + 1 }), { status: 400 });
  const size = 2 * 1024 ** 3;
  for (let n = 0; n < 10; n++) await register(f, size);
  await assert.rejects(register(f, 1), { status: 429 });
});
test('HTTP layer rejects malformed JSON and oversized bodies without logging request contents', async () => {
  const { server } = require('../main.cjs'), { Readable } = require('node:stream');
  const s = server(async () => ({ ok: true }));
  async function request(body) {
    return new Promise(resolve => {
      const req = Readable.from([Buffer.from(body)]); Object.assign(req, { method: 'POST', url: '/v1/enroll', headers: {} });
      const res = { statusCode: 200, setHeader() {}, end(text) { resolve({ status: this.statusCode, body: JSON.parse(text) }); } };
      s.emit('request', req, res);
    });
  }
  assert.equal((await request('{')).status, 400);
  assert.equal((await request('x'.repeat(32769))).status, 413);
  assert.equal((await request('[]')).status, 400);
});
test('real SDK presigning binds part checksums and byte length without contacting AWS', async () => {
  const { objectStorage } = require('../aws.cjs');
  const storage = objectStorage('unit-test-bucket', 'us-east-1', {
    credentials: { accessKeyId: 'UNIT_TEST_ONLY', secretAccessKey: 'not-a-real-credential' }
  });
  const checksum = Buffer.alloc(32, 1).toString('base64');
  const url = new URL(await storage.sign('routine/device/clip.mp4', 'test-upload', 1, 100, checksum));
  const signed = url.searchParams.get('X-Amz-SignedHeaders');
  assert.match(signed, /content-length/); assert.match(signed, /x-amz-checksum-sha256/);
  assert.equal(url.searchParams.get('partNumber'), '1');
  assert.equal(url.searchParams.get('X-Amz-Expires'), '900');
});
module.exports = { MemoryDb, Storage };
