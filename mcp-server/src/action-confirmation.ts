import { createRequestStateCodec, type ServerContext } from '@modelcontextprotocol/server';
import { db, opaque, sha256 } from './store.js';

const timestamp = () => Math.floor(Date.now() / 1000);
const rawKey = process.env.DATA_ENCRYPTION_KEY;
if (!rawKey) throw new Error('DATA_ENCRYPTION_KEY is required');
const codec = createRequestStateCodec<{ nonce: string; action: string; digest: string }>({
  key: Buffer.from(rawKey, 'base64'),
  ttlSeconds: 600,
  bind: ctx => {
    const auth = ctx.http?.authInfo;
    const extra = auth?.extra as Record<string, unknown> | undefined;
    return `${ctx.mcpReq.method}\0${auth?.clientId ?? ''}\0${String(extra?.account_id ?? '')}\0${String(extra?.grant_binding ?? '')}`;
  },
});
export const verifyActionRequestState = codec.verify;

db.exec(`CREATE TABLE IF NOT EXISTS action_confirmations (
  nonce_hash TEXT PRIMARY KEY,
  action TEXT NOT NULL,
  digest TEXT NOT NULL,
  grant_binding TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER
);`);

function grantBinding(ctx: ServerContext): string {
  const value = (ctx.http?.authInfo?.extra as Record<string, unknown> | undefined)?.grant_binding;
  if (typeof value !== 'string' || value.length < 32) throw new Error('Confirmation is not bound to a valid OAuth grant');
  return value;
}

export function actionDigest(action: string, value: unknown): string {
  return sha256(JSON.stringify({ action, value }));
}

/** Mint or re-issue bounded integrity-protected state for exactly one reviewed action. */
export async function actionRequestState(ctx: ServerContext, action: string, digest: string): Promise<string> {
  const binding = grantBinding(ctx);
  const existing = ctx.mcpReq.requestState<{ nonce: string; action: string; digest: string }>();
  if (existing) {
    if (existing.action !== action || existing.digest !== digest || typeof existing.nonce !== 'string') {
      throw new Error('The action changed after confirmation began; review it again');
    }
    const pending = db.prepare(`SELECT action,digest,grant_binding,expires_at,consumed_at
      FROM action_confirmations WHERE nonce_hash=?`).get(sha256(existing.nonce)) as
      { action: string; digest: string; grant_binding: string; expires_at: number; consumed_at: number | null } | undefined;
    if (!pending || pending.action !== action || pending.digest !== digest || pending.grant_binding !== binding ||
        pending.expires_at <= timestamp() || pending.consumed_at !== null) {
      throw new Error('This confirmation expired or was already used; review the action again');
    }
    return codec.mint(existing, ctx);
  }

  const nonce = opaque(24);
  const expiresAt = timestamp() + 600;
  const pendingCount = db.prepare(`SELECT COUNT(*) AS count FROM action_confirmations
    WHERE grant_binding=? AND expires_at>? AND consumed_at IS NULL`).get(binding, timestamp()) as { count: number };
  if (pendingCount.count >= 100) throw new Error('This MCP connection has too many pending confirmations; wait and retry');
  db.prepare(`INSERT INTO action_confirmations(nonce_hash,action,digest,grant_binding,expires_at)
    VALUES(?,?,?,?,?)`).run(sha256(nonce), action, digest, binding, expiresAt);
  return codec.mint({ nonce, action, digest }, ctx);
}

/** Atomically consume a confirmed action, preventing replay and concurrent duplicate submissions. */
export function consumeActionConfirmation(ctx: ServerContext, action: string, digest: string): void {
  const binding = grantBinding(ctx);
  const state = ctx.mcpReq.requestState<{ nonce: string; action: string; digest: string }>();
  if (!state || state.action !== action || state.digest !== digest || typeof state.nonce !== 'string') {
    throw new Error('A fresh confirmation for this exact action is required');
  }
  const changed = db.prepare(`UPDATE action_confirmations SET consumed_at=?
    WHERE nonce_hash=? AND action=? AND digest=? AND grant_binding=? AND expires_at>? AND consumed_at IS NULL`)
    .run(timestamp(), sha256(state.nonce), action, digest, binding, timestamp());
  if (changed.changes !== 1) throw new Error('This confirmation expired or was already used; review the action again');
}

/** A decline or cancellation permanently invalidates the pending action nonce. */
export function discardActionConfirmation(ctx: ServerContext, action: string, digest: string): void {
  if (ctx.mcpReq.requestState() !== undefined) consumeActionConfirmation(ctx, action, digest);
}
