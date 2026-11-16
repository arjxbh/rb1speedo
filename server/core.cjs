'use strict';
const crypto = require('node:crypto');
const PART_SIZE = 32 * 1024 * 1024;
const hash = s => crypto.createHash('sha256').update(s).digest('hex');
const random = () => crypto.randomBytes(32).toString('base64url');
const key = (pk, sk = 'META') => ({ pk, sk });
function fail(status, message) { throw Object.assign(new Error(message), { status }); }
function check(ok, message, status = 400) { if (!ok) fail(status, message); }
function token(s) { return typeof s === 'string' && /^[A-Za-z0-9_-]{43}$/.test(s); }
function id(s) { return typeof s === 'string' && /^[a-f0-9-]{36}$/.test(s); }
function composite(parts) {
  return crypto.createHash('sha256').update(Buffer.concat(parts.map(p => Buffer.from(p, 'base64'))))
    .digest('base64') + '-' + parts.length;
}

// All authorization and state transitions are shared by production and fake-store tests.
function createApi({ db, storage, now = () => Math.floor(Date.now() / 1000) }) {
  async function auth(bearer) {
    check(token(bearer), 'Authentication required', 401);
    const session = await db.get(key('ACCESS#' + hash(bearer)));
    check(session && session.expiresAt > now(), 'Authentication expired', 401);
    const device = await db.get(key('DEVICE#' + session.device));
    check(device && !device.revoked, 'Device revoked', 401);
    return device;
  }
  async function access(device) {
    const accessToken = random();
    await db.create(key('ACCESS#' + hash(accessToken)), { device: device.id, expiresAt: now() + 900 });
    return { accessToken, expiresAt: now() + 900, deviceId: device.id };
  }
  async function clip(device, clipId) {
    check(id(clipId), 'Invalid clip ID');
    const c = await db.get(key('DEVICE#' + device.id, 'CLIP#' + clipId));
    check(c, 'Clip not found', 404); return c;
  }
  async function saveClip(c, updates) {
    return db.cas(key('DEVICE#' + c.device, 'CLIP#' + c.id), c, { ...c, ...updates });
  }
  async function verify(c) {
    const head = await storage.head(c.objectKey);
    check(head && head.size === c.size && head.checksum === c.composite, 'Stored object verification failed', 409);
    if (!c.verified) c = await saveClip(c, { verified: true, uploadedAt: now(), expiresAt: c.protected ? undefined : now() + 31 * 86400 });
    return c;
  }
  async function protect(c) {
    if (!c.verified) return saveClip(c, { protected: true, expiresAt: undefined });
    if (c.objectKey.startsWith('saved/')) return c;
    const next = c.objectKey.replace(/^routine\//, 'saved/');
    if (!await storage.head(c.objectKey)) {
      return saveClip(c, { protected: true, verified: false, objectKey: next, uploadId: undefined,
        composite: composite(c.partHashes), expiresAt: undefined });
    }
    await storage.copy(c.objectKey, next);
    const head = await storage.head(next);
    check(head && head.size === c.size, 'Protected copy failed verification', 409);
    // CopyObject may change the multipart checksum representation. Source was already verified;
    // persist the destination's returned checksum rather than pretending it remains composite.
    return saveClip(c, { protected: true, objectKey: next, composite: head.checksum, expiresAt: undefined });
  }
  return async function api(method, path, body = {}, bearer) {
    if (method === 'GET' && path === '/health') return { ok: true, service: 'rb1-dashcam' };
    if (method === 'POST' && path === '/v1/enroll') {
      check(token(body.code), 'Invalid enrollment code');
      const enrollmentKey = key('PAIR#' + hash(body.code));
      const enrollment = await db.get(enrollmentKey);
      check(enrollment && !enrollment.used && enrollment.expiresAt > now(), 'Enrollment expired or used', 401);
      const deviceId = crypto.randomUUID(), refreshToken = random();
      const device = { id: deviceId, refreshHash: hash(refreshToken), refreshExpires: now() + 90 * 86400, revoked: false };
      await db.transaction([
        { key: enrollmentKey, before: enrollment, after: { ...enrollment, used: true } },
        { key: key('DEVICE#' + deviceId), before: null, after: device }
      ]);
      return { ...await access(device), refreshToken };
    }
    if (method === 'POST' && path === '/v1/token') {
      check(id(body.deviceId) && token(body.refreshToken) && token(body.nextRefreshToken), 'Invalid refresh request');
      const deviceKey = key('DEVICE#' + body.deviceId), device = await db.get(deviceKey);
      check(device && !device.revoked && device.refreshExpires > now(), 'Pairing expired or revoked', 401);
      const oldHash = hash(body.refreshToken), nextHash = hash(body.nextRefreshToken);
      check(oldHash !== nextHash, 'Refresh token must rotate');
      const retry = device.refreshHash === nextHash && device.previousHash === oldHash && device.retryUntil > now();
      check(device.refreshHash === oldHash || retry, 'Invalid refresh token', 401);
      if (!retry) await db.cas(deviceKey, device, { ...device, refreshHash: nextHash, previousHash: oldHash,
        retryUntil: now() + 86400, refreshExpires: now() + 90 * 86400 });
      return access(device);
    }
    const device = await auth(bearer);
    if (method === 'GET' && path === '/v1/clips') {
      const clips = await db.clips(device.id);
      return { clips: clips.map(c => ({ id: c.id, startedAt: c.startedAt, size: c.size,
        verified: !!c.verified, protected: !!c.protected })) };
    }
    if (method === 'POST' && path === '/v1/clips') {
      check(id(body.id) && Number.isSafeInteger(body.size) && body.size > 0 && body.size <= 2 * 1024 ** 3,
        'Invalid clip ID or size');
      check(Number.isSafeInteger(body.startedAt) && body.startedAt > 0 && body.startedAt <= now() * 1000 + 86400000,
        'Invalid capture timestamp');
      const count = Math.ceil(body.size / PART_SIZE);
      check(Array.isArray(body.partHashes) && body.partHashes.length === count && body.partHashes.every(h =>
        typeof h === 'string' && /^[A-Za-z0-9+/]{43}=$/.test(h)), 'Invalid part checksums');
      const k = key('DEVICE#' + device.id, 'CLIP#' + body.id), existing = await db.get(k);
      if (existing) {
        check(existing.size === body.size && existing.composite === composite(body.partHashes), 'Clip ID conflict', 409);
        return { id: existing.id, partSize: PART_SIZE, verified: !!existing.verified };
      }
      const quotaKey = key('DEVICE#' + device.id, 'QUOTA#' + Math.floor(now() / 86400));
      const quota = await db.get(quotaKey);
      check((quota?.bytes || 0) + body.size <= 20 * 1024 ** 3, 'Daily device upload quota exceeded', 429);
      check((quota?.count || 0) < 512, 'Daily clip count quota exceeded', 429);
      const c = { id: body.id, device: device.id, size: body.size, startedAt: body.startedAt,
        partHashes: body.partHashes, composite: composite(body.partHashes), protected: !!body.protected,
        objectKey: `${body.protected ? 'saved' : 'routine'}/${device.id}/${body.id}.mp4`, expiresAt: now() + 31 * 86400 };
      await db.transaction([
        { key: k, before: null, after: c },
        { key: quotaKey, before: quota, after: { bytes: (quota?.bytes || 0) + body.size, count: (quota?.count || 0) + 1, expiresAt: now() + 2 * 86400 } }
      ]);
      return { id: c.id, partSize: PART_SIZE, verified: false };
    }
    const match = path.match(/^\/v1\/clips\/([a-f0-9-]{36})\/(upload|parts\/sign|complete|protect|download)$/);
    check(match, 'Not found', 404);
    let c = await clip(device, match[1]);
    const operation = match[2];
    if (method === 'POST' && operation === 'protect') {
      c = await protect(c); return { protected: true, verified: !!c.verified };
    }
    if (method === 'POST' && operation === 'download') {
      check(c.verified, 'Not uploaded', 409);
      return { url: await storage.download(c.objectKey) };
    }
    if (operation === 'upload' && (method === 'POST' || method === 'GET')) {
      if (c.verified) return { verified: true, parts: [] };
      if (!c.uploadId) {
        check(method === 'POST', 'Upload not started', 409);
        const uploadId = await storage.start(c.objectKey);
        try { c = await saveClip(c, { uploadId }); }
        catch (e) { await storage.abort(c.objectKey, uploadId); throw e; }
      }
      try { return { verified: false, parts: await storage.parts(c.objectKey, c.uploadId) }; }
      catch (e) {
        if (e.name !== 'NoSuchUpload') throw e;
        // Complete may have succeeded even when its HTTP response was lost.
        const head = await storage.head(c.objectKey);
        if (head) { c = await verify(c); return { verified: true, parts: [] }; }
        c = await saveClip(c, { uploadId: undefined });
        fail(409, 'Upload expired; start again');
      }
    }
    if (method === 'POST' && operation === 'complete' && c.verified) return { verified: true };
    check(!c.verified && c.uploadId, 'Upload not active', 409);
    if (method === 'POST' && operation === 'parts/sign') {
      const n = body.partNumber;
      check(Number.isInteger(n) && n >= 1 && n <= c.partHashes.length, 'Invalid part number');
      const length = Math.min(PART_SIZE, c.size - (n - 1) * PART_SIZE);
      return { url: await storage.sign(c.objectKey, c.uploadId, n, length, c.partHashes[n - 1]),
        checksum: c.partHashes[n - 1], length };
    }
    if (method === 'POST' && operation === 'complete') {
      let parts;
      try { parts = await storage.parts(c.objectKey, c.uploadId); }
      catch (e) { if (e.name === 'NoSuchUpload') { await verify(c); return { verified: true }; } throw e; }
      check(parts.length === c.partHashes.length && parts.every((p, i) => p.number === i + 1 &&
        p.checksum === c.partHashes[i] && p.size === Math.min(PART_SIZE, c.size - i * PART_SIZE)),
      'Incomplete or mismatched upload', 409);
      await storage.complete(c.objectKey, c.uploadId, parts);
      c = await verify(c);
      if (c.protected) await protect(c);
      return { verified: true };
    }
    fail(404, 'Not found');
  };
}
module.exports = { createApi, PART_SIZE, hash, random, key, composite };
