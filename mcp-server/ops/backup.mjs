#!/usr/bin/env node
import Database from 'better-sqlite3';
import { mkdir, mkdtemp, readdir, stat, link, unlink, rm, chmod } from 'node:fs/promises';
import { basename, dirname, join, resolve } from 'node:path';
import { randomBytes } from 'node:crypto';
import { tmpdir } from 'node:os';
import { encryptFile, loadKey } from './crypto-format.mjs';

process.umask(0o077);
const args = process.argv.slice(2);
function option(name, fallback) {
  const i = args.indexOf(name);
  return i < 0 ? fallback : args[i + 1];
}
const dbPath = resolve(option('--database', process.env.DATABASE_PATH ?? '/var/lib/gws-mcp/data.sqlite'));
const outDir = resolve(option('--output-dir', '/var/lib/gws-mcp/backups'));
const keyPath = resolve(option('--key-file', process.env.BACKUP_ENCRYPTION_KEY_FILE ?? '/etc/gws-mcp/backup.key'));
const retentionDays = Number(option('--retention-days', '14'));
if (!Number.isInteger(retentionDays) || retentionDays < 1 || retentionDays > 365) throw new Error('Retention days must be between 1 and 365');
const key = await loadKey(keyPath);
await mkdir(outDir, { recursive: true, mode: 0o700 });
await chmod(outDir, 0o700);
const stamp = new Date().toISOString().replaceAll(':', '').replaceAll('-', '').replace(/\.\d{3}Z$/, 'Z');
const filename = `gws-mcp-${stamp}-${randomBytes(4).toString('hex')}.sqlite.aesgcm`;
const finalPath = join(outDir, filename);
const tempDir = await mkdtemp(join(process.env.BACKUP_TEMP_DIR ?? tmpdir(), 'gws-mcp-backup-'));
const tempPlain = join(tempDir, 'snapshot.sqlite');
const tempEncrypted = join(outDir, `.backup-encrypted-${process.pid}-${randomBytes(8).toString('hex')}.tmp`);
try {
  const db = new Database(dbPath, { readonly: true, fileMustExist: true });
  try { await db.backup(tempPlain); } finally { db.close(); }
  await chmod(tempPlain, 0o600);
  await encryptFile(tempPlain, tempEncrypted, key);
  // link is atomic and fails if a final path already exists.
  await link(tempEncrypted, finalPath);
  await unlink(tempEncrypted);
  const cutoff = Date.now() - retentionDays * 86400_000;
  const ownedName = /^gws-mcp-\d{8}T\d{6}Z-[a-f0-9]{8}\.sqlite\.aesgcm$/;
  for (const name of await readdir(outDir)) {
    if (!ownedName.test(name)) continue;
    const path = join(outDir, name);
    if ((await stat(path)).mtimeMs < cutoff) await unlink(path);
  }
  process.stdout.write(`Encrypted backup created: ${basename(finalPath)}\n`);
} finally {
  await Promise.all([rm(tempDir, { recursive: true, force: true }), rm(tempEncrypted, { force: true })]);
  key.fill(0);
}
