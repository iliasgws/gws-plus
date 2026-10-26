import test from 'node:test';
import assert from 'node:assert/strict';
import Database from 'better-sqlite3';
import { mkdtemp, readFile, writeFile, readdir, stat, utimes, rm, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const ops = dirname(dirname(fileURLToPath(import.meta.url)));
const backupScript = join(ops, 'backup.mjs');
const restoreScript = join(ops, 'restore-verify.mjs');
const healthScript = join(ops, 'health-check.mjs');
const node = process.execPath;

async function fixture(t) {
  const dir = await mkdtemp(join(tmpdir(), 'gws-ops-'));
  t.after(() => rm(dir, { recursive: true, force: true }));
  const dbPath = join(dir, 'data.sqlite');
  const db = new Database(dbPath);
  db.exec("CREATE TABLE private_data(value TEXT); INSERT INTO private_data VALUES ('classified fixture'); CREATE TABLE tokens(value TEXT); INSERT INTO tokens VALUES('old grant');");
  db.close();
  const keyPath = join(dir, 'backup.key');
  const key = Buffer.alloc(32, 0x42);
  await writeFile(keyPath, key, { mode: 0o600 });
  return { dir, dbPath, keyPath, key };
}
function run(script, args = [], env = {}) {
  return new Promise(resolve => {
    const child = spawn(node, [script, ...args], { encoding: 'utf8', env: { ...process.env, ...env } });
    let stdout = ''; let stderr = '';
    child.stdout.setEncoding('utf8').on('data', chunk => { stdout += chunk; });
    child.stderr.setEncoding('utf8').on('data', chunk => { stderr += chunk; });
    child.on('close', status => resolve({ status, stdout, stderr }));
  });
}
async function createBackup(f) {
  const out = join(f.dir, 'backups');
  await mkdir(out, { mode: 0o700 });
  const result = await run(backupScript, ['--database', f.dbPath, '--output-dir', out, '--key-file', f.keyPath]);
  assert.equal(result.status, 0, result.stderr);
  const name = (await readdir(out)).find(x => x.endsWith('.sqlite.aesgcm'));
  return { out, file: join(out, name) };
}

test('encrypted online backup decrypts to a valid SQLite database', async t => {
  const f = await fixture(t);
  const b = await createBackup(f);
  const encrypted = await readFile(b.file);
  assert.equal(encrypted.includes(Buffer.from('classified fixture')), false);
  assert.equal((await stat(b.file)).mode & 0o777, 0o600);
  const restored = join(f.dir, 'restored.sqlite');
  const result = await run(restoreScript, [b.file, restored, '--key-file', f.keyPath]);
  assert.equal(result.status, 0, result.stderr);
  const db = new Database(restored, { readonly: true });
  assert.equal(db.prepare('SELECT value FROM private_data').get().value, 'classified fixture');
  assert.equal(db.prepare('SELECT COUNT(*) n FROM tokens').get().n, 0, 'restore invalidates old grants');
  db.close();
});

test('wrong key and tampering are rejected without leaving plaintext output', async t => {
  const f = await fixture(t);
  const b = await createBackup(f);
  const wrongKey = join(f.dir, 'wrong.key');
  await writeFile(wrongKey, Buffer.alloc(32, 0x24), { mode: 0o600 });
  const wrongOut = join(f.dir, 'wrong.sqlite');
  assert.notEqual((await run(restoreScript, [b.file, wrongOut, '--key-file', wrongKey])).status, 0);
  await assert.rejects(readFile(wrongOut));
  const damaged = join(f.dir, 'tampered.sqlite.aesgcm');
  const bytes = await readFile(b.file); bytes[bytes.length - 18] ^= 0x80; await writeFile(damaged, bytes);
  const tamperedOut = join(f.dir, 'tampered.sqlite');
  assert.notEqual((await run(restoreScript, [damaged, tamperedOut, '--key-file', f.keyPath])).status, 0);
  await assert.rejects(readFile(tamperedOut));
});

test('retention only removes expired files matching the exact owned naming pattern', async t => {
  const f = await fixture(t);
  const b = await createBackup(f);
  const staleOwned = join(b.out, 'gws-mcp-20200101T000000Z-deadbeef.sqlite.aesgcm');
  const staleOther = join(b.out, 'other-20200101T000000Z-deadbeef.sqlite.aesgcm');
  const malformed = join(b.out, 'gws-mcp-not-a-backup.sqlite.aesgcm');
  for (const path of [staleOwned, staleOther, malformed]) await writeFile(path, 'fixture');
  const old = new Date('2020-01-01T00:00:00Z');
  for (const path of [staleOwned, staleOther, malformed]) await utimes(path, old, old);
  const result = await run(backupScript, ['--database', f.dbPath, '--output-dir', b.out, '--key-file', f.keyPath, '--retention-days', '14']);
  assert.equal(result.status, 0, result.stderr);
  const remaining = await readdir(b.out);
  assert.equal(remaining.includes('gws-mcp-20200101T000000Z-deadbeef.sqlite.aesgcm'), false);
  assert.equal(remaining.includes('other-20200101T000000Z-deadbeef.sqlite.aesgcm'), true);
  assert.equal(remaining.includes('gws-mcp-not-a-backup.sqlite.aesgcm'), true);
});

test('backup failure leaves no temporary plaintext or ciphertext files', async t => {
  const f = await fixture(t);
  const out = join(f.dir, 'backups'); await mkdir(out);
  const result = await run(backupScript, ['--database', join(f.dir, 'missing.sqlite'), '--output-dir', out, '--key-file', f.keyPath]);
  assert.notEqual(result.status, 0);
  assert.deepEqual(await readdir(out), []);
});

test('restore refuses to overwrite an existing destination', async t => {
  const f = await fixture(t);
  const b = await createBackup(f);
  const output = join(f.dir, 'existing.sqlite'); await writeFile(output, 'keep me');
  const result = await run(restoreScript, [b.file, output, '--key-file', f.keyPath]);
  assert.notEqual(result.status, 0);
  assert.equal(await readFile(output, 'utf8'), 'keep me');
});

test('health monitor reports success and fails unhealthy metadata, status, or timeout', async t => {
  const server = await import('node:http').then(({ createServer }) => createServer());
  let badMode = '';
  server.on('request', (req, res) => {
    if (req.url === '/healthz') { res.statusCode = badMode === 'status' ? 503 : 200; res.end('private response body'); return; }
    res.setHeader('content-type', 'application/json');
    if (badMode === 'timeout') return;
    if (req.url === '/auth') res.end(JSON.stringify({ scopes_supported: badMode === 'auth' ? ['homework:read'] : ['homework:read', 'offline_access'] }));
    else res.end(JSON.stringify({ scopes_supported: badMode === 'protected' ? ['offline_access'] : ['homework:read'] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const base = `http://127.0.0.1:${server.address().port}`;
  const env = { HEALTH_URL: `${base}/healthz`, AUTH_METADATA_URL: `${base}/auth`, PROTECTED_METADATA_URL: `${base}/protected`, HEALTH_TIMEOUT_MS: '100' };
  const healthy = await run(healthScript, [], env);
  assert.equal(healthy.status, 0, healthy.stderr);
  for (const mode of ['auth', 'protected', 'status', 'timeout']) {
    badMode = mode;
    const result = await run(healthScript, [], env);
    assert.notEqual(result.status, 0, mode);
    assert.equal(`${result.stdout}${result.stderr}`.includes('private response body'), false);
  }
});

test('restore rejects a shared destination without changing its permissions', async t => {
  const f=await fixture(t);const b=await createBackup(f);
  const shared=join(f.dir,'shared');await mkdir(shared,{mode:0o755});
  const before=(await stat(shared)).mode&0o777;
  const result=await run(restoreScript,[b.file,join(shared,'restore.sqlite'),'--key-file',f.keyPath]);
  assert.notEqual(result.status,0);assert.equal((await stat(shared)).mode&0o777,before);
});
