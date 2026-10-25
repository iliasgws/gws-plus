import { createHash } from 'node:crypto';
import express, { type Request, type Response, type NextFunction } from 'express';
import { createMcpHandler, McpServer } from '@modelcontextprotocol/server';
import { toNodeHandler } from '@modelcontextprotocol/node';
import * as z from 'zod/v4';
import { get, login, type BotiAccount } from './boti.js';
import { db, decrypt, encrypt, opaque, sha256 } from './store.js';

const publicUrl = new URL(process.env.PUBLIC_URL ?? 'http://localhost:3000');
const prod = process.env.NODE_ENV === 'production';
if (prod && publicUrl.protocol !== 'https:') throw new Error('PUBLIC_URL must use HTTPS in production');
const mcpUrl = new URL('/mcp', publicUrl).href;
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '16kb', type: ['application/json', 'application/*+json'] }));
app.use(express.urlencoded({ extended: false, limit: '16kb' }));
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
  const origins = (process.env.ALLOWED_ORIGINS ?? '').split(',').map(s => s.trim()).filter(Boolean);
  const origin = req.get('origin');
  if (origin && origins.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, Mcp-Session-Id, Mcp-Protocol-Version, Last-Event-Id');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
    res.setHeader('Access-Control-Expose-Headers', 'WWW-Authenticate, Mcp-Session-Id, Mcp-Protocol-Version');
  }
  if (req.method === 'OPTIONS') return res.sendStatus(204);
  next();
});

const now = () => Math.floor(Date.now() / 1000);
const cookieName = prod ? '__Host-gws_mcp' : 'gws_mcp';
function setSession(res: Response, session: string) {
  res.setHeader('Set-Cookie', `${cookieName}=${session}; Path=/; HttpOnly; SameSite=Lax; Max-Age=1800${prod ? '; Secure' : ''}`);
}
function clearSession(res: Response) { res.setHeader('Set-Cookie', `${cookieName}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${prod ? '; Secure' : ''}`); }
function html(title: string, body: string) {
  return `<!doctype html><html lang="fr"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${title}</title><style>body{font:16px system-ui;max-width:36rem;margin:3rem auto;padding:0 1rem;color:#24221f}input,button{font:inherit;padding:.65rem;margin:.3rem 0}input{width:100%;box-sizing:border-box}button{cursor:pointer}label{display:block;margin-top:1rem}.card{border:1px solid #ddd;border-radius:12px;padding:1.2rem}small{color:#555}</style><main class="card"><h1>${title}</h1>${body}</main></html>`;
}
function string(value: unknown): string { return typeof value === 'string' ? value : ''; }
function clientRedirects(clientId: string): string[] {
  const row = db.prepare('SELECT redirects FROM clients WHERE client_id=?').get(clientId) as { redirects: string } | undefined;
  return row ? JSON.parse(row.redirects) : [];
}
function validRedirect(clientId: string, redirect: string): boolean { return clientRedirects(clientId).includes(redirect); }
function safeReturn(url: string, values: Record<string, string>): string {
  const target = new URL(url);
  for (const [k, v] of Object.entries(values)) target.searchParams.set(k, v);
  return target.href;
}
function getSession(req: Request) {
  const value = req.cookies?.[cookieName] as string | undefined;
  if (!value) return undefined;
  return db.prepare('SELECT * FROM browser_sessions WHERE session_hash=? AND expires_at>?').get(sha256(value), now()) as
    { session_hash: string; account_id: string | null; expires_at: number } | undefined;
}

// Minimal cookie parser; no credential or token values are logged.
app.use((req, _res, next) => {
  const cookie = req.get('cookie') ?? '';
  (req as any).cookies = Object.fromEntries(cookie.split(';').map(p => p.trim().split(/=(.*)/s).slice(0, 2)).filter(p => p.length === 2));
  next();
});

app.get('/healthz', (_req, res) => res.json({ status: 'ok' }));
app.get('/.well-known/oauth-authorization-server', (_req, res) => res.json({
  issuer: publicUrl.origin,
  authorization_endpoint: new URL('/authorize', publicUrl).href,
  token_endpoint: new URL('/token', publicUrl).href,
  registration_endpoint: new URL('/register', publicUrl).href,
  revocation_endpoint: new URL('/revoke', publicUrl).href,
  response_types_supported: ['code'],
  grant_types_supported: ['authorization_code', 'refresh_token'],
  code_challenge_methods_supported: ['S256'],
  token_endpoint_auth_methods_supported: ['none'],
  scopes_supported: ['homework:read', 'documents:read']
}));
app.get('/.well-known/oauth-protected-resource', (_req, res) => res.json({ resource: mcpUrl, authorization_servers: [publicUrl.origin], bearer_methods_supported: ['header'], scopes_supported: ['homework:read', 'documents:read'] }));
app.get('/.well-known/oauth-protected-resource/mcp', (_req, res) => res.json({ resource: mcpUrl, authorization_servers: [publicUrl.origin], bearer_methods_supported: ['header'], scopes_supported: ['homework:read', 'documents:read'] }));

app.post('/register', (req, res) => {
  const redirects = req.body?.redirect_uris;
  if (!Array.isArray(redirects) || redirects.length < 1 || redirects.length > 10 || redirects.some((u: unknown) => typeof u !== 'string' || !isAllowedRedirect(u))) {
    return res.status(400).json({ error: 'invalid_client_metadata' });
  }
  const clientId = opaque(24);
  const name = string(req.body.client_name).slice(0, 120) || 'MCP client';
  db.prepare('INSERT INTO clients(client_id,name,redirects,created_at) VALUES(?,?,?,?)').run(clientId, name, JSON.stringify(redirects), now());
  res.status(201).json({ client_id: clientId, client_name: name, redirect_uris: redirects, token_endpoint_auth_method: 'none', grant_types: ['authorization_code', 'refresh_token'], response_types: ['code'] });
});
function isAllowedRedirect(raw: string): boolean {
  try {
    const u = new URL(raw);
    if (u.username || u.password || u.hash) return false;
    if (u.protocol === 'https:') return true;
    return u.protocol === 'http:' && ['127.0.0.1', 'localhost', '[::1]'].includes(u.hostname);
  } catch { return false; }
}

app.get('/authorize', (req, res) => {
  const clientId = string(req.query.client_id);
  const redirect = string(req.query.redirect_uri);
  const state = string(req.query.state);
  const challenge = string(req.query.code_challenge);
  const method = string(req.query.code_challenge_method);
  const resource = string(req.query.resource) || mcpUrl;
  if (!validRedirect(clientId, redirect) || !state || state.length > 512 || !/^[A-Za-z0-9_-]{43,128}$/.test(challenge) || method !== 'S256' || resource !== mcpUrl || (req.query.response_type !== 'code')) {
    return res.status(400).send(html('Requête OAuth invalide', '<p>Le client, la redirection ou la preuve PKCE est invalide.</p>'));
  }
  const scope = requestedScope(req.query.scope);
  if (scope === null) return res.status(400).send(html('Autorisations inconnues', '<p>Le client a demandé une autorisation qui n’est pas proposée par Greenwood School +.</p>'));
  const session = getSession(req);
  if (!session?.account_id) {
    const tx = opaque();
    db.prepare('INSERT INTO authorizations(auth_id,session_hash,client_id,redirect_uri,state,challenge,resource,scope,expires_at) VALUES(?,?,?,?,?,?,?,?,?)')
      .run(tx, session?.session_hash ?? '', clientId, redirect, state, challenge, resource, scope, now() + 600);
    return res.redirect(303, `/login?tx=${encodeURIComponent(tx)}`);
  }
  showConsent(res, session.session_hash, clientId, redirect, state, challenge, resource, scope);
});
function requestedScope(value: unknown): string | null {
  const allowed = new Set(['homework:read', 'documents:read']);
  const raw = string(value).trim();
  if (!raw) return [...allowed].join(' ');
  const requested = [...new Set(raw.split(/\s+/).filter(Boolean))];
  if (!requested.length || requested.some(s => !allowed.has(s))) return null;
  return requested.join(' ');
}
function showConsent(res: Response, sessionHash: string, clientId: string, redirect: string, state: string, challenge: string, resource: string, scope: string) {
  const authorizationId = opaque();
  db.prepare('INSERT INTO authorizations(auth_id,session_hash,client_id,redirect_uri,state,challenge,resource,scope,expires_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(authorizationId, sessionHash, clientId, redirect, state, challenge, resource, scope, now() + 600);
  const client = db.prepare('SELECT name FROM clients WHERE client_id=?').get(clientId) as { name: string };
  const labels: Record<string, string> = { 'homework:read': 'Lire les devoirs', 'documents:read': 'Lire les documents et ressources' };
  const scopeList = scope.split(' ').map(s => `<li>${labels[s]}</li>`).join('');
  res.send(html('Autoriser Greenwood School +', `<p><strong>${escapeHtml(client.name)}</strong> demande un accès à votre compte scolaire :</p><ul>${scopeList}</ul><p>Votre mot de passe scolaire ne sera pas conservé. Vous pouvez révoquer cet accès sur la page <a href="/account">Gestion des accès MCP</a>.</p><form method="post" action="/consent"><input type="hidden" name="auth_id" value="${authorizationId}"><button name="decision" value="approve">Autoriser</button> <button name="decision" value="deny">Refuser</button></form>`));
}

app.get('/login', (req, res) => {
  const tx = string(req.query.tx);
  const auth = db.prepare('SELECT * FROM authorizations WHERE auth_id=? AND expires_at>?').get(tx, now()) as any;
  if (!auth) return res.status(400).send(html('Connexion expirée', '<p>Relancez la connexion depuis votre client MCP.</p>'));
  res.send(html('Connexion à Greenwood School', `<p>Les identifiants sont envoyés à Boti Education pour créer une session de lecture.</p><form method="post" action="/login"><input type="hidden" name="tx" value="${auth.auth_id}"><label>Téléphone ou identifiant<input name="login" autocomplete="username" required maxlength="128"></label><label>Mot de passe<input name="password" type="password" autocomplete="current-password" required maxlength="256"></label><button>Se connecter</button></form><small>Le mot de passe est utilisé uniquement pour cette demande et n’est pas enregistré.</small>`));
});
app.post('/login', async (req, res) => {
  const tx = string(req.body?.tx);
  const auth = db.prepare('SELECT * FROM authorizations WHERE auth_id=? AND expires_at>?').get(tx, now()) as any;
  const loginName = string(req.body?.login).trim();
  const password = string(req.body?.password);
  if (!auth || !loginName || loginName.length > 128 || !password || password.length > 256) return res.status(400).send(html('Connexion invalide', '<p>Vérifiez le formulaire puis relancez la demande.</p>'));
  try {
    const botiAccount = await login(loginName, password);
    const accountId = opaque();
    db.prepare('INSERT INTO accounts(account_id,user_id,parent_id,student_id,key_cipher,created_at) VALUES(?,?,?,?,?,?)')
      .run(accountId, botiAccount.userId, botiAccount.parentId, botiAccount.studentId, encrypt(botiAccount.keyToken), now());
    const browserToken = opaque();
    db.prepare('INSERT INTO browser_sessions(session_hash,account_id,expires_at) VALUES(?,?,?)').run(sha256(browserToken), accountId, now() + 1800);
    db.prepare('UPDATE authorizations SET session_hash=? WHERE auth_id=?').run(sha256(browserToken), tx);
    setSession(res, browserToken);
    res.redirect(303, `/authorize/continue?tx=${encodeURIComponent(tx)}`);
  } catch (error) {
    res.status(401).send(html('Connexion impossible', `<p>${escapeHtml(error instanceof Error ? error.message : 'Boti a refusé la connexion.')}</p><p><a href="/login?tx=${encodeURIComponent(tx)}">Réessayer</a></p>`));
  }
});
app.get('/authorize/continue', (req, res) => {
  const tx = string(req.query.tx);
  const auth = db.prepare('SELECT * FROM authorizations WHERE auth_id=? AND expires_at>?').get(tx, now()) as any;
  const session = getSession(req);
  if (!auth || !session?.account_id || auth.session_hash !== session.session_hash) return res.status(400).send(html('Connexion expirée', '<p>Relancez la demande dans votre client.</p>'));
  showConsent(res, session.session_hash, auth.client_id, auth.redirect_uri, auth.state, auth.challenge, auth.resource, auth.scope);
  db.prepare('DELETE FROM authorizations WHERE auth_id=?').run(tx);
});
app.post('/consent', (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Relancez l’autorisation depuis votre client MCP.</p>'));
  const authId = string(req.body?.auth_id);
  const auth = db.prepare('SELECT * FROM authorizations WHERE auth_id=? AND expires_at>?').get(authId, now()) as any;
  const session = getSession(req);
  if (!auth || !session?.account_id || auth.session_hash !== session.session_hash) return res.status(400).send(html('Demande expirée', '<p>Relancez l’autorisation depuis le client.</p>'));
  db.prepare('DELETE FROM authorizations WHERE auth_id=?').run(authId);
  if (req.body?.decision !== 'approve') return res.redirect(303, safeReturn(auth.redirect_uri, { error: 'access_denied', state: auth.state }));
  const code = opaque();
  db.prepare('INSERT INTO authorizations(auth_id,session_hash,client_id,redirect_uri,state,challenge,resource,scope,expires_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(authId, auth.session_hash, auth.client_id, auth.redirect_uri, auth.state, auth.challenge, auth.resource, auth.scope, now() + 120);
  db.prepare('INSERT INTO codes(code_hash,auth_id,expires_at) VALUES(?,?,?)').run(sha256(code), authId, now() + 120);
  res.redirect(303, safeReturn(auth.redirect_uri, { code, state: auth.state, iss: publicUrl.origin }));
});

app.post('/token', (req, res) => {
  const grant = string(req.body?.grant_type);
  if (grant === 'authorization_code') {
    const code = string(req.body?.code);
    const verifier = string(req.body?.code_verifier);
    const redirect = string(req.body?.redirect_uri);
    const clientId = string(req.body?.client_id);
    if (!/^[A-Za-z0-9._~-]{43,128}$/.test(verifier)) return oauthError(res, 'invalid_grant');
    const row = db.prepare(`SELECT c.code_hash,a.* FROM codes c JOIN authorizations a ON a.auth_id=c.auth_id WHERE c.code_hash=? AND c.expires_at>? AND a.expires_at>?`)
      .get(sha256(code), now(), now()) as any;
    db.prepare('DELETE FROM codes WHERE code_hash=?').run(sha256(code));
    const challenge = createHash('sha256').update(verifier).digest('base64url');
    if (!row || row.client_id !== clientId || row.redirect_uri !== redirect || row.challenge !== challenge) return oauthError(res, 'invalid_grant');
    const session = db.prepare('SELECT account_id FROM browser_sessions WHERE session_hash=? AND expires_at>?').get(row.session_hash, now()) as { account_id: string } | undefined;
    if (!session?.account_id) return oauthError(res, 'invalid_grant');
    const familyId = opaque();
    return issueTokens(res, clientId, session.account_id, row.resource, row.scope, familyId);
  }
  if (grant === 'refresh_token') {
    const token = string(req.body?.refresh_token);
    const row = db.prepare('SELECT * FROM tokens WHERE token_hash=? AND token_type=? AND client_id=? AND expires_at>? AND revoked=0')
      .get(sha256(token), 'refresh', string(req.body?.client_id), now()) as any;
    if (!row) return oauthError(res, 'invalid_grant');
    db.prepare('UPDATE tokens SET revoked=1 WHERE family_id=?').run(row.family_id);
    return issueTokens(res, row.client_id, row.account_id, row.resource, row.scope, row.family_id);
  }
  return oauthError(res, 'unsupported_grant_type');
});
function issueTokens(res: Response, clientId: string, accountId: string, resource: string, scope: string, familyId: string) {
  const access = opaque();
  const refresh = opaque();
  db.prepare('INSERT INTO tokens(token_hash,token_type,client_id,account_id,resource,scope,family_id,expires_at) VALUES(?,?,?,?,?,?,?,?)')
    .run(sha256(access), 'access', clientId, accountId, resource, scope, familyId, now() + 3600);
  db.prepare('INSERT INTO tokens(token_hash,token_type,client_id,account_id,resource,scope,family_id,expires_at) VALUES(?,?,?,?,?,?,?,?)')
    .run(sha256(refresh), 'refresh', clientId, accountId, resource, scope, familyId, now() + 30 * 86400);
  res.json({ access_token: access, token_type: 'Bearer', expires_in: 3600, refresh_token: refresh, scope, resource });
}
function oauthError(res: Response, error: string) { return res.status(400).json({ error }); }
app.post('/revoke', (req, res) => {
  const token = string(req.body?.token);
  const clientId = string(req.body?.client_id);
  const row = db.prepare('SELECT family_id FROM tokens WHERE token_hash=? AND client_id=?').get(sha256(token), clientId) as { family_id: string } | undefined;
  if (row) db.prepare('UPDATE tokens SET revoked=1 WHERE family_id=?').run(row.family_id);
  res.status(200).end();
});

app.get('/account', (req, res) => {
  const session = getSession(req);
  if (!session?.account_id) return res.status(401).send(html('Connexion requise', '<p>Connectez-vous à Greenwood School + depuis un assistant pour gérer ses accès.</p>'));
  const connections = db.prepare(`SELECT DISTINCT c.name,t.scope FROM tokens t JOIN clients c ON c.client_id=t.client_id WHERE t.account_id=? AND t.revoked=0 AND t.expires_at>?`)
    .all(session.account_id, now()) as Array<{ name: string; scope: string }>;
  const list = connections.length
    ? `<ul>${connections.map(c => `<li>${escapeHtml(c.name)} — ${escapeHtml(c.scope.split(' ').map(s => ({ 'homework:read': 'devoirs', 'documents:read': 'documents' }[s] ?? 'accès')).join(', '))}</li>`).join('')}</ul><form method="post" action="/account/revoke"><button>Révoquer tous les accès et effacer cette session</button></form>`
    : '<p>Aucun accès MCP actif pour cette session.</p>';
  res.send(html('Gestion des accès MCP', `<p>Les accès ci-dessous sont liés à cette connexion scolaire.</p>${list}`));
});
app.post('/account/revoke', (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Ouvrez la page de gestion des accès puis réessayez.</p>'));
  const session = getSession(req);
  if (!session?.account_id) return res.status(401).send(html('Connexion requise', '<p>La session a expiré. Reconnectez-vous pour gérer les accès.</p>'));
  // Each account record is created for one Boti login. Deleting it revokes
  // every OAuth grant attached to that login and removes its encrypted key.
  db.prepare('DELETE FROM accounts WHERE account_id=?').run(session.account_id);
  clearSession(res);
  res.send(html('Accès révoqués', '<p>Tous les accès MCP de cette session ont été révoqués et la clé scolaire a été supprimée.</p>'));
});

function escapeHtml(value: string) { return value.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]!)); }

type AuthContext = { account: BotiAccount; scopes: string[]; clientId: string };
const authByToken = db.prepare(`SELECT t.*,a.user_id,a.parent_id,a.student_id,a.key_cipher FROM tokens t JOIN accounts a ON a.account_id=t.account_id WHERE t.token_hash=? AND t.token_type='access' AND t.expires_at>? AND t.revoked=0`);
function buildMcp(ctx: { authInfo?: { token: string; clientId: string; scopes: string[] } }) {
  const server = new McpServer({ name: 'greenwood-school-plus', version: '0.1.0' });
  const row = ctx.authInfo ? authByToken.get(sha256(ctx.authInfo.token), now()) as any : undefined;
  if (!row) return server;
  const auth: AuthContext = {
    account: { userId: row.user_id, parentId: row.parent_id, studentId: row.student_id, keyToken: decrypt(row.key_cipher) },
    scopes: row.scope.split(' '), clientId: row.client_id
  };
  const requireScope = (scope: string) => { if (!auth.scopes.includes(scope)) throw new Error(`Access requires ${scope}`); };
  server.registerTool('get_homework', {
    description: 'Read the authenticated parent’s homework list from Greenwood School.',
    inputSchema: { start: z.number().int().min(0).max(10000).default(0), limit: z.number().int().min(1).max(100).default(30) }
  }, async ({ start, limit }) => {
    requireScope('homework:read');
    const data = await get(auth.account, 'devoirs', { start: String(start), limit: String(limit) });
    return { content: [{ type: 'text', text: JSON.stringify(data) }] };
  });
  server.registerTool('get_document_units', { description: 'List the authenticated parent’s library subjects.', inputSchema: z.object({}) }, async () => {
    requireScope('documents:read');
    return { content: [{ type: 'text', text: JSON.stringify(await get(auth.account, 'bibliotheque')) }] };
  });
  server.registerTool('list_documents', {
    description: 'List library documents for one subject unit ID returned by get_document_units.',
    inputSchema: { unit_id: z.string().min(1).max(64), start: z.number().int().min(0).max(10000).default(0), limit: z.number().int().min(1).max(50).default(10) }
  }, async ({ unit_id, start, limit }) => {
    requireScope('documents:read');
    return { content: [{ type: 'text', text: JSON.stringify(await get(auth.account, 'bibliotheque', { unite: unit_id, start: String(start), limit: String(limit) })) }] };
  });
  server.registerTool('get_document_details', {
    description: 'Read details and currently signed file URLs for a library resource. URLs expire and should not be treated as stable links.',
    inputSchema: { resource_id: z.string().min(1).max(64) }
  }, async ({ resource_id }) => {
    requireScope('documents:read');
    return { content: [{ type: 'text', text: JSON.stringify(await get(auth.account, 'ressource_details', { ressource: resource_id })) }] };
  });
  return server;
}
const handler = createMcpHandler(buildMcp);
const nodeHandler = toNodeHandler(handler);
app.all('/mcp', (req: Request, res: Response, next: NextFunction) => {
  const authorization = req.get('authorization') ?? '';
  const token = authorization.match(/^Bearer ([A-Za-z0-9_-]+)$/)?.[1];
  const row = token ? authByToken.get(sha256(token), now()) as any : undefined;
  if (!row || row.resource !== mcpUrl) {
    res.setHeader('WWW-Authenticate', `Bearer resource_metadata="${new URL('/.well-known/oauth-protected-resource/mcp', publicUrl).href}", error="invalid_token"`);
    return res.status(401).json({ error: 'unauthorized' });
  }
  (req as any).auth = { token, clientId: row.client_id, scopes: row.scope.split(' '), extra: { account_id: row.account_id } };
  void nodeHandler(req, res, req.body).catch(next);
});

const port = Number(process.env.PORT ?? 3000);
const host = process.env.HOST ?? '0.0.0.0';
app.listen(port, host, () => console.log(`Greenwood MCP listening on ${publicUrl.origin}; MCP endpoint ${mcpUrl}`));
