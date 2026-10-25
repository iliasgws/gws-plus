import { createHash } from 'node:crypto';
import express, { type Request, type Response, type NextFunction } from 'express';
import { createMcpHandler, McpServer } from '@modelcontextprotocol/server';
import { toNodeHandler } from '@modelcontextprotocol/node';
import { get, login, sendPlainTextMessage, postVerifiedForm, type BotiAccount, type BotiStudent } from './boti.js';
import { db, decrypt, encrypt, fingerprint, opaque, sha256 } from './store.js';
import { registerReadTools } from './read-tools.js';
import { registerMessageTools } from './message-tools.js';
import { registerShopWriteTools } from './shop-write-tools.js';
import { registerQuizWriteTools } from './quiz-write-tools.js';
import { registerDocumentTools } from './document-tools.js';

const publicUrl = new URL(process.env.PUBLIC_URL ?? 'http://localhost:3000');
const prod = process.env.NODE_ENV === 'production';
if (prod && publicUrl.protocol !== 'https:') throw new Error('PUBLIC_URL must use HTTPS in production');
const mcpUrl = new URL('/mcp', publicUrl).href;
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '256kb', type: ['application/json', 'application/*+json'] }));
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
const supportedScopes = [
  'homework:read', 'documents:read', 'courses:read', 'news:read',
  'messages:read', 'messages:write', 'quizzes:write', 'requests:read', 'school:read', 'shop:read', 'shop:write',
];
const defaultScopes = ['homework:read', 'documents:read'];
const agentInstructions = `Greenwood School + gives access to one parent account and the single student selected during OAuth authorization. Do not attempt to change the student identity or pass identity fields; every request is bound to that selected student. Available tools vary with OAuth consent scopes, so inspect tools/list rather than assuming every tool is available. Use read tools with returned IDs: get_homework and get_homework_details; get_timetable; list_quizzes/get_quiz_details; get_document_units then list_documents/get_document_details/download_library_pdf; list_news/get_news_post; list_conversations; list_administrative_requests; get_school_contact; list_shop_products/get_shop_product/get_shop_order_history and get_guest_meal_schedule. Results may contain sensitive student and family information; retrieve only what the user asks for and summarize narrowly. Signed file URLs expire. Write tools submit real school actions. Call them only when the user explicitly asks for that action, and never treat OAuth consent as action approval: send_message, submit_quiz_attempt, place_shop_order, and delete_shop_order each require a fresh human elicitation showing the exact content or transaction. download_library_pdf returns only a bounded PDF from a verified Bibliothèque attachment. If elicitation is unsupported, the action cannot proceed. Do not invent absent school data, answers, availability, reminder delivery, or write capabilities. Attachments/voice messaging and unverified homework/demande/community writes are not supported.`;
function cleanExpiredRows() {
  const timestamp = now();
  db.prepare('DELETE FROM browser_sessions WHERE expires_at<=?').run(timestamp);
  db.prepare('DELETE FROM authorizations WHERE expires_at<=?').run(timestamp);
  db.prepare('DELETE FROM codes WHERE expires_at<=?').run(timestamp);
  db.prepare('DELETE FROM tokens WHERE expires_at<=? OR revoked=1').run(timestamp);
  db.prepare(`DELETE FROM accounts
    WHERE account_id NOT IN (SELECT account_id FROM tokens WHERE revoked=0 AND expires_at>?)
      AND account_id NOT IN (SELECT account_id FROM browser_sessions WHERE expires_at>?)`).run(timestamp, timestamp);
}
cleanExpiredRows();
const cleanupTimer = setInterval(cleanExpiredRows, 60 * 60 * 1000);
cleanupTimer.unref();
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
function accountFor(accountId: string) {
  const row = db.prepare('SELECT user_cipher,parent_cipher,parent_hash,students_cipher,key_cipher FROM accounts WHERE account_id=?').get(accountId) as
    { user_cipher: string; parent_cipher: string; parent_hash: string; students_cipher: string; key_cipher: string | null } | undefined;
  if (!row) return undefined;
  return {
    userId: decrypt(row.user_cipher), parentId: decrypt(row.parent_cipher), parentHash: row.parent_hash,
    keyToken: row.key_cipher ? decrypt(row.key_cipher) : '',
    students: JSON.parse(decrypt(row.students_cipher)) as BotiStudent[]
  };
}
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
  scopes_supported: supportedScopes
}));
app.get('/.well-known/oauth-protected-resource', (_req, res) => res.json({ resource: mcpUrl, authorization_servers: [publicUrl.origin], bearer_methods_supported: ['header'], scopes_supported: supportedScopes }));
app.get('/.well-known/oauth-protected-resource/mcp', (_req, res) => res.json({ resource: mcpUrl, authorization_servers: [publicUrl.origin], bearer_methods_supported: ['header'], scopes_supported: supportedScopes }));

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
  showConsent(res, session.session_hash, session.account_id, clientId, redirect, state, challenge, resource, scope);
});
function requestedScope(value: unknown): string | null {
  const raw = string(value).trim();
  if (!raw) return defaultScopes.join(' ');
  const requested = [...new Set(raw.split(/\s+/).filter(Boolean))];
  if (!requested.length || requested.some(s => !supportedScopes.includes(s))) return null;
  return requested.join(' ');
}
function showConsent(res: Response, sessionHash: string, accountId: string, clientId: string, redirect: string, state: string, challenge: string, resource: string, scope: string) {
  const account = accountFor(accountId);
  if (!account?.students.length) return res.status(400).send(html('Élève indisponible', '<p>La liste des élèves est introuvable. Reconnectez-vous pour continuer.</p>'));
  const authorizationId = opaque();
  db.prepare('INSERT INTO authorizations(auth_id,session_hash,client_id,redirect_uri,state,challenge,resource,scope,expires_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(authorizationId, sessionHash, clientId, redirect, state, challenge, resource, scope, now() + 600);
  showStudentChoice(res, authorizationId, account.students);
}
function showStudentChoice(res: Response, authorizationId: string, students: BotiStudent[]) {
  const control = students.length === 1
    ? `<p>Cette autorisation sera liée à l’élève <strong>${escapeHtml(students[0].name)}</strong>.</p><input type="hidden" name="student_id" value="${escapeHtml(students[0].id)}"><label><input type="checkbox" name="confirm_student" value="yes" required> Je confirme cet élève</label>`
    : `<label>Choisir l’élève auquel l’accès sera lié<select name="student_id" required><option value="">Choisir un élève</option>${students.map(student => `<option value="${escapeHtml(student.id)}">${escapeHtml(student.name)}</option>`).join('')}</select></label>`;
  res.send(html('Choisir un élève', `<p>Choisissez l’élève dont les données pourront être lues par le client MCP.</p><form method="post" action="/student">${control}<input type="hidden" name="auth_id" value="${authorizationId}"><p><button>Continuer</button></p></form>`));
}
function renderConsent(res: Response, auth: any, account: ReturnType<typeof accountFor>) {
  if (!account || typeof auth.student_cipher !== 'string') return res.status(400).send(html('Demande expirée', '<p>Relancez la demande depuis le client MCP.</p>'));
  const selectedStudent = decrypt(auth.student_cipher);
  const student = account.students.find(item => item.id === selectedStudent);
  if (!student) return res.status(400).send(html('Choix de l’élève invalide', '<p>Relancez la demande depuis le client MCP.</p>'));
  const client = db.prepare('SELECT name FROM clients WHERE client_id=?').get(auth.client_id) as { name: string };
  const labels: Record<string, string> = {
    'homework:read': 'Lire les devoirs et leurs détails',
    'documents:read': 'Lire les documents, ressources et quiz',
    'courses:read': 'Lire l’emploi du temps',
    'news:read': 'Lire les actualités',
    'messages:read': 'Lire les conversations et messages',
    'messages:write': 'Envoyer des messages texte après confirmation ponctuelle (nécessite aussi messages:read)',
    'quizzes:write': 'Enregistrer une tentative de quiz après confirmation ponctuelle (nécessite aussi documents:read)',
    'shop:write': 'Créer, modifier ou supprimer une commande après confirmation ponctuelle (nécessite aussi shop:read)',
    'requests:read': 'Lire les demandes administratives',
    'school:read': 'Lire les coordonnées de l’école',
    'shop:read': 'Lire le catalogue, les repas invités et l’historique des commandes',
  };
  const scopeList = auth.scope.split(' ').map((s: string) => `<li>${labels[s]}</li>`).join('');
  res.send(html('Autoriser Greenwood School +', `<p><strong>${escapeHtml(client.name)}</strong> demande un accès au compte scolaire et aux données de l’élève <strong>${escapeHtml(student.name)}</strong> :</p><ul>${scopeList}</ul><p>Votre mot de passe scolaire ne sera pas conservé. Vous pouvez révoquer cet accès sur la page <a href="/account">Gestion des accès MCP</a>.</p><form method="post" action="/consent"><input type="hidden" name="auth_id" value="${auth.auth_id}"><button name="decision" value="approve">Autoriser</button> <button name="decision" value="deny" formnovalidate>Refuser</button></form>`));
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
    db.prepare('INSERT INTO accounts(account_id,user_cipher,parent_cipher,parent_hash,students_cipher,key_cipher,created_at) VALUES(?,?,?,?,?,?,?)')
      .run(accountId, encrypt(botiAccount.userId), encrypt(botiAccount.parentId), fingerprint(`${botiAccount.userId}:${botiAccount.parentId}`), encrypt(JSON.stringify(botiAccount.students)), encrypt(botiAccount.keyToken), now());
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
  showConsent(res, session.session_hash, session.account_id, auth.client_id, auth.redirect_uri, auth.state, auth.challenge, auth.resource, auth.scope);
  db.prepare('DELETE FROM authorizations WHERE auth_id=?').run(tx);
});
app.post('/student', (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Relancez l’autorisation depuis votre client MCP.</p>'));
  const authId = string(req.body?.auth_id);
  const auth = db.prepare('SELECT * FROM authorizations WHERE auth_id=? AND expires_at>?').get(authId, now()) as any;
  const session = getSession(req);
  if (!auth || !session?.account_id || auth.session_hash !== session.session_hash) return res.status(400).send(html('Demande expirée', '<p>Relancez l’autorisation depuis le client.</p>'));
  const account = accountFor(session.account_id);
  const selectedStudent = string(req.body?.student_id);
  if (!account?.students.length || !account.students.some(student => student.id === selectedStudent)
    || (account.students.length === 1 && req.body?.confirm_student !== 'yes')) {
    return res.status(400).send(html('Choix de l’élève invalide', '<p>Le choix de l’élève n’a pas pu être confirmé. Relancez la demande.</p>'));
  }
  const studentCipher = encrypt(selectedStudent);
  db.prepare('UPDATE authorizations SET student_cipher=? WHERE auth_id=?').run(studentCipher, authId);
  renderConsent(res, { ...auth, student_cipher: studentCipher }, account);
});
app.post('/consent', (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Relancez l’autorisation depuis votre client MCP.</p>'));
  const authId = string(req.body?.auth_id);
  const auth = db.prepare('SELECT * FROM authorizations WHERE auth_id=? AND expires_at>?').get(authId, now()) as any;
  const session = getSession(req);
  if (!auth || !session?.account_id || auth.session_hash !== session.session_hash) return res.status(400).send(html('Demande expirée', '<p>Relancez l’autorisation depuis le client.</p>'));
  const account = accountFor(session.account_id);
  if (!account || typeof auth.student_cipher !== 'string' || !account.students.some(student => student.id === decrypt(auth.student_cipher))) {
    return res.status(400).send(html('Demande expirée', '<p>Relancez l’autorisation depuis le client.</p>'));
  }
  db.prepare('DELETE FROM authorizations WHERE auth_id=?').run(authId);
  if (req.body?.decision !== 'approve') return res.redirect(303, safeReturn(auth.redirect_uri, { error: 'access_denied', state: auth.state }));
  const code = opaque();
  db.prepare('INSERT INTO authorizations(auth_id,session_hash,client_id,redirect_uri,state,challenge,resource,scope,student_cipher,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?)')
    .run(authId, auth.session_hash, auth.client_id, auth.redirect_uri, auth.state, auth.challenge, auth.resource, auth.scope, auth.student_cipher, now() + 120);
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
    if (typeof row.student_cipher !== 'string') return oauthError(res, 'invalid_grant');
    return issueTokens(res, clientId, session.account_id, row.student_cipher, row.resource, row.scope, familyId);
  }
  if (grant === 'refresh_token') {
    const token = string(req.body?.refresh_token);
    const row = db.prepare('SELECT * FROM tokens WHERE token_hash=? AND token_type=? AND client_id=? AND expires_at>? AND revoked=0')
      .get(sha256(token), 'refresh', string(req.body?.client_id), now()) as any;
    if (!row) return oauthError(res, 'invalid_grant');
    db.prepare('UPDATE tokens SET revoked=1 WHERE family_id=?').run(row.family_id);
    return issueTokens(res, row.client_id, row.account_id, row.student_cipher, row.resource, row.scope, row.family_id);
  }
  return oauthError(res, 'unsupported_grant_type');
});
function issueTokens(res: Response, clientId: string, accountId: string, studentCipher: string, resource: string, scope: string, familyId: string) {
  const access = opaque();
  const refresh = opaque();
  db.prepare('INSERT INTO tokens(token_hash,token_type,client_id,account_id,student_cipher,resource,scope,family_id,expires_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(sha256(access), 'access', clientId, accountId, studentCipher, resource, scope, familyId, now() + 3600);
  db.prepare('INSERT INTO tokens(token_hash,token_type,client_id,account_id,student_cipher,resource,scope,family_id,expires_at) VALUES(?,?,?,?,?,?,?,?,?)')
    .run(sha256(refresh), 'refresh', clientId, accountId, studentCipher, resource, scope, familyId, now() + 30 * 86400);
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
  if (!session?.account_id) return res.send(html('Gestion des accès MCP', `<p>Connectez-vous à votre compte scolaire pour consulter et révoquer les accès accordés à vos assistants.</p><form method="post" action="/account/login"><label>Téléphone ou identifiant<input name="login" autocomplete="username" required maxlength="128"></label><label>Mot de passe<input name="password" type="password" autocomplete="current-password" required maxlength="256"></label><button>Se connecter</button></form><small>Le mot de passe sert uniquement à ouvrir cette session de gestion et n’est pas conservé.</small>`));
  const account = accountFor(session.account_id);
  const connections = db.prepare(`SELECT DISTINCT c.name,t.scope,t.student_cipher,a.students_cipher FROM tokens t JOIN clients c ON c.client_id=t.client_id JOIN accounts a ON a.account_id=t.account_id WHERE a.parent_hash=? AND t.revoked=0 AND t.expires_at>?`)
    .all(account?.parentHash ?? '', now()) as Array<{ name: string; scope: string; student_cipher: string; students_cipher: string }>;
  const scopeLabels: Record<string, string> = {
    'homework:read': 'devoirs', 'documents:read': 'documents et quiz', 'courses:read': 'emploi du temps',
    'news:read': 'actualités', 'messages:read': 'messages', 'requests:read': 'demandes',
    'messages:write': 'envoi de messages après confirmation ponctuelle',
    'quizzes:write': 'enregistrement de quiz après confirmation ponctuelle',
    'shop:write': 'commandes boutique après confirmation ponctuelle',
    'school:read': 'coordonnées de l’école', 'shop:read': 'boutique et repas invités',
  };
  const list = connections.length
    ? `<ul>${connections.map(c => {
      const studentId = decrypt(c.student_cipher);
      const roster = JSON.parse(decrypt(c.students_cipher)) as BotiStudent[];
      const studentName = roster.find(s => s.id === studentId)?.name ?? 'élève';
      const scopes = c.scope.split(' ').map(s => scopeLabels[s] ?? 'accès').join(', ');
      return `<li>${escapeHtml(c.name)} — ${escapeHtml(studentName)} — ${escapeHtml(scopes)}</li>`;
    }).join('')}</ul><form method="post" action="/account/revoke"><button>Révoquer tous les accès de ce compte scolaire</button></form>`
    : '<p>Aucun accès MCP actif pour cette session.</p>';
  res.send(html('Gestion des accès MCP', `<p>Les accès ci-dessous sont liés à ce compte scolaire.</p>${list}<form method="post" action="/account/logout"><button>Fermer cette session de gestion</button></form>`));
});
app.post('/account/login', async (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Rechargez la page puis réessayez.</p>'));
  const loginName = string(req.body?.login).trim();
  const password = string(req.body?.password);
  if (!loginName || loginName.length > 128 || !password || password.length > 256) return res.status(400).send(html('Connexion invalide', '<p>Vérifiez le formulaire puis réessayez.</p>'));
  try {
    const botiAccount = await login(loginName, password);
    const accountId = opaque();
    db.prepare('INSERT INTO accounts(account_id,user_cipher,parent_cipher,parent_hash,students_cipher,key_cipher,created_at) VALUES(?,?,?,?,?,?,?)')
      .run(accountId, encrypt(botiAccount.userId), encrypt(botiAccount.parentId), fingerprint(`${botiAccount.userId}:${botiAccount.parentId}`), encrypt(JSON.stringify(botiAccount.students)), encrypt(botiAccount.keyToken), now());
    const browserToken = opaque();
    db.prepare('INSERT INTO browser_sessions(session_hash,account_id,expires_at) VALUES(?,?,?)').run(sha256(browserToken), accountId, now() + 1800);
    setSession(res, browserToken);
    res.redirect(303, '/account');
  } catch (error) {
    res.status(401).send(html('Connexion impossible', `<p>${escapeHtml(error instanceof Error ? error.message : 'Boti a refusé la connexion.')}</p><p><a href="/account">Réessayer</a></p>`));
  }
});
app.post('/account/logout', (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Rechargez la page puis réessayez.</p>'));
  const session = getSession(req);
  if (session) db.prepare('DELETE FROM browser_sessions WHERE session_hash=?').run(session.session_hash);
  clearSession(res);
  res.redirect(303, '/account');
});
app.post('/account/revoke', (req, res) => {
  if (req.get('origin') !== publicUrl.origin) return res.status(403).send(html('Requête refusée', '<p>Ouvrez la page de gestion des accès puis réessayez.</p>'));
  const session = getSession(req);
  if (!session?.account_id) return res.status(401).send(html('Connexion requise', '<p>La session a expiré. Reconnectez-vous pour gérer les accès.</p>'));
  const account = accountFor(session.account_id);
  if (!account) return res.status(401).send(html('Connexion requise', '<p>La session a expiré. Reconnectez-vous pour gérer les accès.</p>'));
  db.prepare('DELETE FROM accounts WHERE parent_hash=?').run(account.parentHash);
  clearSession(res);
  res.send(html('Accès révoqués', '<p>Tous les accès MCP de cette session ont été révoqués et la clé scolaire a été supprimée.</p>'));
});

function escapeHtml(value: string) { return value.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]!)); }

type AuthContext = { account: BotiAccount; scopes: string[]; clientId: string };
const authByToken = db.prepare(`SELECT t.*,a.user_cipher,a.parent_cipher,a.key_cipher FROM tokens t JOIN accounts a ON a.account_id=t.account_id WHERE t.token_hash=? AND t.token_type='access' AND t.expires_at>? AND t.revoked=0`);
function buildMcp(ctx: { authInfo?: { token: string; clientId: string; scopes: string[] } }) {
  const server = new McpServer({ name: 'greenwood-school-plus', version: '0.1.0' }, {
    instructions: agentInstructions,
  });
  server.registerPrompt('greenwood_agent_guide', {
    title: 'Greenwood School + agent guide',
    description: 'How to use this parent-authorized school account safely and effectively.',
  }, () => ({ messages: [{ role: 'user', content: { type: 'text', text: agentInstructions } }] }));
  const row = ctx.authInfo ? authByToken.get(sha256(ctx.authInfo.token), now()) as any : undefined;
  if (!row) return server;
  const auth: AuthContext = {
    account: { userId: decrypt(row.user_cipher), parentId: decrypt(row.parent_cipher), studentId: decrypt(row.student_cipher), keyToken: decrypt(row.key_cipher) },
    scopes: row.scope.split(' '), clientId: row.client_id
  };
  const requireScope = (scope: string) => { if (!auth.scopes.includes(scope)) throw new Error(`Access requires ${scope}`); };
  registerReadTools(server, (route, params) => get(auth.account, route, params), requireScope);
  registerDocumentTools(server, (route, params) => get(auth.account, route, params), requireScope);
  registerMessageTools(server, (route, params) => get(auth.account, route, params),
    (route, fields) => {
      if (route !== 'nouveau-message') throw new Error('Unsupported write route');
      return sendPlainTextMessage(auth.account, fields);
    }, requireScope);
  registerShopWriteTools(server, (route, params) => get(auth.account, route, params),
    (route, fields) => {
      if (route !== 'shop') throw new Error('Unsupported write route');
      return postVerifiedForm(auth.account, 'shop', fields);
    }, requireScope);
  registerQuizWriteTools(server, (route, params) => get(auth.account, route, params),
    (route, fields) => {
      if (route !== 'quiz') throw new Error('Unsupported write route');
      return postVerifiedForm(auth.account, 'quiz', fields);
    }, requireScope);
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
