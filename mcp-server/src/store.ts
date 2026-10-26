import Database from 'better-sqlite3';
import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes } from 'node:crypto';

const dbPath = process.env.DATABASE_PATH ?? './data.sqlite';
const rawKey = process.env.DATA_ENCRYPTION_KEY;
if (!rawKey) throw new Error('DATA_ENCRYPTION_KEY is required');
const encryptionKey = Buffer.from(rawKey, 'base64');
if (encryptionKey.length !== 32) throw new Error('DATA_ENCRYPTION_KEY must be 32 bytes encoded as base64');

export const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');
db.exec(`
CREATE TABLE IF NOT EXISTS clients (
  client_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  redirects TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS accounts (
  account_id TEXT PRIMARY KEY,
  user_cipher TEXT NOT NULL,
  parent_cipher TEXT NOT NULL,
  parent_hash TEXT NOT NULL,
  students_cipher TEXT NOT NULL,
  key_cipher TEXT,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS browser_sessions (
  session_hash TEXT PRIMARY KEY,
  account_id TEXT,
  expires_at INTEGER NOT NULL,
  FOREIGN KEY(account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS authorizations (
  auth_id TEXT PRIMARY KEY,
  session_hash TEXT NOT NULL,
  client_id TEXT NOT NULL,
  redirect_uri TEXT NOT NULL,
  state TEXT NOT NULL,
  challenge TEXT NOT NULL,
  resource TEXT NOT NULL,
  scope TEXT NOT NULL,
  student_cipher TEXT,
  expires_at INTEGER NOT NULL,
  FOREIGN KEY(client_id) REFERENCES clients(client_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS codes (
  code_hash TEXT PRIMARY KEY,
  auth_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  FOREIGN KEY(auth_id) REFERENCES authorizations(auth_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS tokens (
  token_hash TEXT PRIMARY KEY,
  token_type TEXT NOT NULL,
  client_id TEXT NOT NULL,
  account_id TEXT NOT NULL,
  student_cipher TEXT NOT NULL,
  resource TEXT NOT NULL,
  scope TEXT NOT NULL,
  family_id TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY(client_id) REFERENCES clients(client_id) ON DELETE CASCADE,
  FOREIGN KEY(account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS rate_limits (
  key_hash TEXT PRIMARY KEY,
  window_started INTEGER NOT NULL,
  hit_count INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS reminders (
  reminder_id TEXT PRIMARY KEY,
  account_id TEXT NOT NULL,
  student_hash TEXT NOT NULL,
  title_cipher TEXT NOT NULL,
  note_cipher TEXT,
  due_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  completed_at INTEGER,
  canceled_at INTEGER,
  FOREIGN KEY(account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS reminders_owner_due ON reminders(account_id,student_hash,due_at);
`);

// Encrypted account/token columns were initialized on a fresh database before
// the hosted deployment. Do not reuse a pre-parity plaintext database without
// a migration that decrypts and reseals its
// old plaintext user_id/parent_id/student_id fields.

export const sha256 = (value: string) => createHash('sha256').update(value).digest('hex');
const fingerprintKey = createHmac('sha256', encryptionKey).update('gws-plus:mcp:parent-fingerprint:v1').digest();
export const fingerprint = (value: string) => createHmac('sha256', fingerprintKey).update(value).digest('hex');
export const opaque = (bytes = 32) => randomBytes(bytes).toString('base64url');

const rateLimitLookup = db.prepare('SELECT window_started,hit_count FROM rate_limits WHERE key_hash=?');
const rateLimitStart = db.prepare(`INSERT INTO rate_limits(key_hash,window_started,hit_count) VALUES(?,?,1)
  ON CONFLICT(key_hash) DO UPDATE SET window_started=excluded.window_started,hit_count=1`);
const rateLimitIncrement = db.prepare('UPDATE rate_limits SET hit_count=hit_count+1 WHERE key_hash=?');
const consumeRateLimitTransaction = db.transaction((keyHash: string, timestamp: number, maxHits: number, windowSeconds: number) => {
  const row = rateLimitLookup.get(keyHash) as { window_started: number; hit_count: number } | undefined;
  if (!row || timestamp - row.window_started >= windowSeconds || timestamp < row.window_started) {
    rateLimitStart.run(keyHash, timestamp);
    return { allowed: true, retryAfter: 0 };
  }
  if (row.hit_count >= maxHits) return { allowed: false, retryAfter: Math.max(1, row.window_started + windowSeconds - timestamp) };
  rateLimitIncrement.run(keyHash);
  return { allowed: true, retryAfter: 0 };
});
export function consumeRateLimit(action: string, ipAddress: string, maxHits: number, windowSeconds: number, timestamp: number) {
  return consumeRateLimitTransaction(fingerprint(`rate-limit:${action}:${ipAddress}`), timestamp, maxHits, windowSeconds);
}

export function encrypt(value: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', encryptionKey, iv);
  const encrypted = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()]);
  return `${iv.toString('base64url')}.${cipher.getAuthTag().toString('base64url')}.${encrypted.toString('base64url')}`;
}

export function decrypt(value: string): string {
  const [ivPart, tagPart, bodyPart] = value.split('.');
  if (!ivPart || !tagPart || !bodyPart) throw new Error('Invalid encrypted value');
  const decipher = createDecipheriv('aes-256-gcm', encryptionKey, Buffer.from(ivPart, 'base64url'));
  decipher.setAuthTag(Buffer.from(tagPart, 'base64url'));
  return Buffer.concat([decipher.update(Buffer.from(bodyPart, 'base64url')), decipher.final()]).toString('utf8');
}
