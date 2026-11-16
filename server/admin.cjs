'use strict';
const { database, objectStorage } = require('./aws.cjs');
const { key, hash, random } = require('./core.cjs');
async function main(args) {
  const db = database(process.env.RB1_TABLE, process.env.AWS_REGION || 'us-east-1');
  if (!process.env.RB1_TABLE) throw new Error('Set RB1_TABLE');
  if (args[0] === 'pair' && args.length === 1) {
    const code = random(), expiresAt = Math.floor(Date.now() / 1000) + 600;
    await db.create(key('PAIR#' + hash(code)), { expiresAt, used: false });
    console.log(JSON.stringify({ code, expiresAt }));
  } else if (args[0] === 'list' && /^[a-f0-9-]{36}$/.test(args[1] || '') && args.length === 2) {
    const clips = await db.clips(args[1]);
    console.log(JSON.stringify(clips.map(c => ({ id: c.id, startedAt: c.startedAt, size: c.size, protected: !!c.protected, verified: !!c.verified })), null, 2));
  } else if (args[0] === 'download' && args.slice(1).length === 2 && args.slice(1).every(s => /^[a-f0-9-]{36}$/.test(s))) {
    const c = await db.get(key('DEVICE#' + args[1], 'CLIP#' + args[2]));
    if (!c?.verified || !process.env.RB1_BUCKET) throw new Error('Verified clip and RB1_BUCKET required');
    console.log(await objectStorage(process.env.RB1_BUCKET, process.env.AWS_REGION || 'us-east-1').download(c.objectKey));
  } else if (args[0] === 'revoke' && /^[a-f0-9-]{36}$/.test(args[1] || '')) {
    const k = key('DEVICE#' + args[1]), d = await db.get(k);
    if (!d) throw new Error('Unknown device');
    await db.cas(k, d, { ...d, revoked: true }); console.log('Device revoked');
  } else throw new Error('Usage: node admin.cjs pair | revoke DEVICE_ID | list DEVICE_ID | download DEVICE_ID CLIP_ID');
}
if (require.main === module) main(process.argv.slice(2)).catch(e => { console.error(e.message); process.exitCode = 1; });
module.exports = { main };
