// Local-only browser regression fixture: contains no school credentials or data.
// Run from mcp-server: node --import tsx test/browser-fixture.mjs
import { randomBytes, createHash } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { createServer } from 'node:http';
const directory=mkdtempSync(tmpdir()+'/gws-browser-fixture-');
const port=Number(process.env.FIXTURE_PORT??3137),callbackPort=port+1;
const origin=`http://127.0.0.1:${port}`,callback=`http://127.0.0.1:${callbackPort}/callback`;
Object.assign(process.env,{NODE_ENV:'development',PUBLIC_URL:origin,PORT:String(port),HOST:'127.0.0.1',DATABASE_PATH:directory+'/fixture.sqlite',DATA_ENCRYPTION_KEY:randomBytes(32).toString('base64')});
const {db,encrypt,sha256}=await import('../src/store.ts');
const now=Math.floor(Date.now()/1000),verifier='v'.repeat(64);
db.prepare('INSERT INTO clients VALUES(?,?,?,?)').run('synthetic-client','Client de test',JSON.stringify([callback]),now);
db.prepare('INSERT INTO accounts VALUES(?,?,?,?,?,?,?)').run('synthetic-account',encrypt('fake-user'),encrypt('fake-parent'),'fake-parent-hash',encrypt(JSON.stringify([{id:'fake-student',name:'Élève de test'}])),encrypt('not-a-school-token'),now);
db.prepare('INSERT INTO browser_sessions VALUES(?,?,?)').run(sha256('synthetic-browser'),'synthetic-account',now+1800);
let result={callbackReceived:false};
createServer(async(req,res)=>{
 res.setHeader('Content-Type','application/json; charset=utf-8');
 const u=new URL(req.url,callback);
 if(u.pathname==='/result')return res.end(JSON.stringify(result));
 if(u.pathname!=='/callback'){res.statusCode=404;return res.end();}
 result={callbackReceived:true,stateMatches:u.searchParams.get('state')==='synthetic-state'};
 if(u.searchParams.has('error')){result.denied=u.searchParams.get('error')==='access_denied';return res.end(JSON.stringify(result));}
 result.issuerMatches=u.searchParams.get('iss')===origin;
 const exchange=await fetch(origin+'/token',{method:'POST',body:new URLSearchParams({grant_type:'authorization_code',code:u.searchParams.get('code')??'',code_verifier:verifier,client_id:'synthetic-client',redirect_uri:callback})});
 const tokens=await exchange.json();result.tokenStatus=exchange.status;result.tokensIssued=!!tokens.access_token&&!!tokens.refresh_token;
 if(tokens.refresh_token){const refresh=await fetch(origin+'/token',{method:'POST',body:new URLSearchParams({grant_type:'refresh_token',refresh_token:tokens.refresh_token,client_id:'synthetic-client'})});const rotated=await refresh.json();result.refreshStatus=refresh.status;result.refreshRotated=!!rotated.refresh_token&&rotated.refresh_token!==tokens.refresh_token;}
 res.end(JSON.stringify(result));
}).listen(callbackPort,'127.0.0.1');
await import('../src/index.ts');
console.log('Synthetic fixture ready. Set gws_mcp=synthetic-browser on the local origin only.');
console.log(origin+'/authorize?'+new URLSearchParams({response_type:'code',client_id:'synthetic-client',redirect_uri:callback,state:'synthetic-state',code_challenge:createHash('sha256').update(verifier).digest('base64url'),code_challenge_method:'S256',scope:'homework:read offline_access',resource:origin+'/mcp'}));
const cleanup=()=>{db.close();rmSync(directory,{recursive:true,force:true});process.exit(0)};
process.once('SIGINT',cleanup);process.once('SIGTERM',cleanup);
