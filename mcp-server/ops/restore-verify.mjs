#!/usr/bin/env node
import Database from 'better-sqlite3';
import { access, mkdir, stat, rm } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { decryptFile, loadKey } from './crypto-format.mjs';

process.umask(0o077);
const backupArg = process.argv[2];
const outputArg = process.argv[3];
if (!backupArg || !outputArg) { console.error('Usage: node ops/restore-verify.mjs <encrypted-backup> <new-output-file> [--key-file <path>]'); process.exit(2); }
const keyFlag = process.argv.indexOf('--key-file');
if (keyFlag >= 0 && (process.argv.length !== keyFlag + 2 || !process.argv[keyFlag + 1])) {
  console.error('Invalid --key-file option'); process.exit(2);
}
const backup = resolve(backupArg);
const output = resolve(outputArg);
const keyPath = resolve(keyFlag >= 0 ? process.argv[keyFlag + 1] : process.env.BACKUP_ENCRYPTION_KEY_FILE ?? '/etc/gws-mcp/backup.key');
const parent = dirname(output);
await mkdir(parent, { recursive: true, mode: 0o700 });
if ((await stat(parent)).mode & 0o077) throw new Error('Restore destination must be in a private directory (mode 0700)');
try { await access(output); throw new Error('Restore output already exists; refusing to overwrite'); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
const temp = `${output}.restore-${process.pid}-${Math.random().toString(16).slice(2)}.tmp`;
const key = await loadKey(keyPath);
try {
  await decryptFile(backup, temp, key);
  const db = new Database(temp, { fileMustExist: true });
  try {
    const row = db.pragma('quick_check', { simple: true });
    if (row !== 'ok') throw new Error('SQLite quick_check failed');
    // Restoring an old snapshot must never resurrect a revoked authorization
    // or permit replay of a previously consumed code/confirmation.
    db.transaction(() => {
      for (const table of ['codes', 'authorizations', 'browser_sessions', 'tokens', 'action_confirmations']) {
        if (db.prepare("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").get(table)) db.exec(`DELETE FROM ${table}`);
      }
    })();
  } finally { db.close(); }
  // Hard-link publication is atomic and refuses an existing destination.
  const { link, unlink } = await import('node:fs/promises');
  await link(temp, output);
  await unlink(temp);
  console.log(`Restore verified and written: ${output}`);
} finally {
  await rm(temp, { force: true });
  key.fill(0);
}
