import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawn, execFileSync } from 'node:child_process';
import { createServer } from 'node:net';

async function unusedPort(): Promise<number> {
  const listener = createServer();
  await new Promise<void>(done => listener.listen(0, '127.0.0.1', done));
  const port = (listener.address() as { port: number }).port;
  await new Promise<void>(done => listener.close(() => done()));
  return port;
}
function rpc(text: string): any {
  return JSON.parse(text.startsWith('event:') || text.startsWith('data:')
    ? text.split('\n').find(line => line.startsWith('data:'))!.slice(5) : text);
}

test('OAuth consent, identity, scopes, PKCE, rotation and revocation stay compatible', async t => {
  const dir = await mkdtemp(`${tmpdir()}/gws-oauth-test-`);
  const base = `http://127.0.0.1:${await unusedPort()}`;
  const env = { ...process.env, NODE_ENV: 'development', PUBLIC_URL: base,
    HOST: '127.0.0.1', PORT: new URL(base).port, DATABASE_PATH: `${dir}/test.sqlite`,
    DATA_ENCRYPTION_KEY: randomBytes(32).toString('base64'), HTTP_AUDIT_LOG: 'true' };
  const store = pathToFileURL(resolve('src/store.ts')).href;
  const seed = `const {db,encrypt,sha256}=await import(${JSON.stringify(store)});const n=Math.floor(Date.now()/1000);
    db.prepare('INSERT INTO clients VALUES(?,?,?,?)').run('fixture-client','Test',JSON.stringify(['https://assistant.example/callback']),n);
    db.prepare('INSERT INTO accounts VALUES(?,?,?,?,?,?,?)').run('fixture-account',encrypt('user'),encrypt('parent'),'parent-hash',encrypt(JSON.stringify([{id:'student',name:'Élève de test'}])),encrypt('not-a-school-session'),n);
    db.prepare('INSERT INTO browser_sessions VALUES(?,?,?)').run(sha256('fixture-cookie'),'fixture-account',n+1800);db.close();`;
  execFileSync(process.execPath, ['--import','tsx','--input-type=module','--eval', seed], { env });
  const child = spawn(process.execPath, ['--import','tsx','src/index.ts'], { env, stdio: ['ignore','pipe','pipe'] });
  let logs = ''; child.stdout.on('data', chunk => { logs += chunk; }); child.stderr.on('data', () => {});
  t.after(async () => { child.kill(); await new Promise(done => child.once('close', done)); await rm(dir,{recursive:true,force:true}); });
  for (let i = 0; i < 80; i++) {
    try { if ((await fetch(`${base}/healthz`)).ok) break; } catch {}
    await new Promise(done => setTimeout(done, 50));
  }
  const post = (path: string, values: Record<string,string>, cookie = false) => fetch(base+path, {
    method:'POST', redirect:'manual', headers: cookie ? {cookie:'gws_mcp=fixture-cookie'} : {}, body:new URLSearchParams(values),
  });
  const { createHash } = await import('node:crypto');
  const verifier = 'v'.repeat(64);
  const params = new URLSearchParams({response_type:'code',client_id:'fixture-client',redirect_uri:'https://assistant.example/callback',
    state:'fixture-state',code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',resource:base+'/mcp',scope:'homework:read offline_access'});
  const authorize = () => fetch(`${base}/authorize?${params}`, {headers:{cookie:'gws_mcp=fixture-cookie'}});
  for (const path of ['/privacy','/terms','/account']) {
    const response=await fetch(base+path);assert.equal(response.status,200);
    assert.match(response.headers.get('content-security-policy')!, /form-action 'self';/);
  }
  const authMeta=await (await fetch(base+'/.well-known/oauth-authorization-server')).json() as any;
  assert.ok(authMeta.scopes_supported.includes('offline_access'));assert.ok(authMeta.grant_types_supported.includes('refresh_token'));
  for(const path of ['/.well-known/oauth-protected-resource','/.well-known/oauth-protected-resource/mcp']) {
    const meta=await (await fetch(base+path)).json() as any;assert.ok(!meta.scopes_supported.includes('offline_access'));
  }
  params.set('scope','bogus:scope');assert.equal((await authorize()).status,400);params.set('scope','homework:read offline_access');
  async function consent(decision='approve'): Promise<string> {
    const response=await authorize();assert.equal(response.status,200);
    const id=(await response.text()).match(/name="auth_id" value="([^"]+)"/)![1];
    assert.equal((await post('/student',{auth_id:id,student_id:'another-family',confirm_student:'yes'},true)).status,400);
    const selected=await post('/student',{auth_id:id,student_id:'student',confirm_student:'yes'},true);
    assert.equal(selected.status,200);assert.match(selected.headers.get('content-security-policy')!,/form-action 'self' https:\/\/assistant\.example;/);
    const page=await selected.text();assert.match(page,/href="\/privacy"/);assert.match(page,/href="\/terms"/);
    const returned=await post('/consent',{auth_id:id,decision},true);assert.equal(returned.status,303);
    const callback=new URL(returned.headers.get('location')!);assert.equal(callback.origin,'https://assistant.example');assert.equal(callback.searchParams.get('state'),'fixture-state');
    if(decision==='approve'){assert.equal(callback.searchParams.get('iss'),base);return callback.searchParams.get('code')!;}
    assert.equal(callback.searchParams.get('error'),'access_denied');return '';
  }
  await consent('deny');
  const invalidCode=await consent();
  assert.equal((await post('/token',{grant_type:'authorization_code',code:invalidCode,client_id:'fixture-client',redirect_uri:'https://assistant.example/callback',code_verifier:'x'.repeat(64)})).status,400);
  const code=await consent();const fields={grant_type:'authorization_code',code,client_id:'fixture-client',redirect_uri:'https://assistant.example/callback',code_verifier:verifier};
  const exchange=await post('/token',fields);assert.equal(exchange.status,200);const tokens=await exchange.json() as any;
  assert.ok(tokens.access_token);assert.ok(tokens.refresh_token);assert.equal((await post('/token',fields)).status,400);
  const mcpHeaders={authorization:`Bearer ${tokens.access_token}`,accept:'application/json, text/event-stream','content-type':'application/json'};
  const init=await fetch(base+'/mcp',{method:'POST',headers:mcpHeaders,body:JSON.stringify({jsonrpc:'2.0',id:1,method:'initialize',params:{protocolVersion:'2025-06-18',capabilities:{},clientInfo:{name:'test',version:'1'}}})});
  assert.equal(init.status,200);const initialized=rpc(await init.text()).result;
  assert.equal(initialized.serverInfo.title,'Greenwood School +');assert.equal(initialized.serverInfo.icons[0].src,base+'/assets/icon-512.png');
  assert.match(initialized.instructions,/READ → PREVIEW/);
  const icon=await fetch(initialized.serverInfo.icons[0].src,{redirect:'manual'});assert.equal(icon.status,200);assert.match(icon.headers.get('content-type')!,/image\/png/);
  const png=Buffer.from(await icon.arrayBuffer());assert.equal(png.readUInt32BE(16),512);assert.equal(png.readUInt32BE(20),512);
  const session=init.headers.get('mcp-session-id')!;
  const list=await fetch(base+'/mcp',{method:'POST',headers:{...mcpHeaders,'mcp-session-id':session},body:JSON.stringify({jsonrpc:'2.0',id:2,method:'tools/list',params:{}})});
  const names=rpc(await list.text()).result.tools.map((tool:any)=>tool.name);
  assert.ok(names.includes('download_homework_attachment'));assert.ok(!names.includes('send_message'));assert.ok(!names.includes('download_library_pdf'));
  const refresh=await post('/token',{grant_type:'refresh_token',refresh_token:tokens.refresh_token,client_id:'fixture-client'});assert.equal(refresh.status,200);
  const next=await refresh.json() as any;assert.notEqual(next.refresh_token,tokens.refresh_token);
  assert.equal((await post('/token',{grant_type:'refresh_token',refresh_token:tokens.refresh_token,client_id:'fixture-client'})).status,400);
  assert.equal((await fetch(base+'/mcp',{method:'POST',headers:mcpHeaders,body:'{}'})).status,401);
  await post('/revoke',{token:next.refresh_token,client_id:'fixture-client'});
  assert.equal((await fetch(base+'/mcp',{method:'POST',headers:{...mcpHeaders,authorization:`Bearer ${next.access_token}`},body:'{}'})).status,401);
  assert.ok(!logs.includes(tokens.access_token));assert.ok(!logs.includes(code));assert.ok(!logs.includes('fixture-cookie'));
});
