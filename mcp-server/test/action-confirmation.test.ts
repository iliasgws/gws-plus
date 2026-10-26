import test from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';

test('confirmations reject tampering, other grants, altered payload, expiry and replay', async t => {
  const dir=await mkdtemp(`${tmpdir()}/gws-confirmation-test-`);
  process.env.DATABASE_PATH=dir+'/db.sqlite';process.env.DATA_ENCRYPTION_KEY=randomBytes(32).toString('base64');
  const {db}=await import('../src/store.js');
  const {actionDigest,actionRequestState,verifyActionRequestState,consumeActionConfirmation,discardActionConfirmation}=await import('../src/action-confirmation.js');
  t.after(async()=>{db.close();await rm(dir,{recursive:true,force:true});});
  const ctx=(binding='g'.repeat(64),payload:unknown=undefined):any=>({
    http:{authInfo:{clientId:'client',extra:{account_id:'account',grant_binding:binding}}},
    mcpReq:{method:'tools/call',requestState:()=>payload},
  });
  const digest=actionDigest('send_message',{text:'reviewed message'});
  const original=ctx();const token=await actionRequestState(original,'send_message',digest);
  const state=await verifyActionRequestState(token,original);
  const parts=token.split('.');parts[2]=(parts[2][0]==='A'?'B':'A')+parts[2].slice(1);const altered=parts.join('.');
  await assert.rejects(verifyActionRequestState(altered,original));
  await assert.rejects(verifyActionRequestState(token,ctx('other-grant'.repeat(8))));
  const confirmed=ctx(undefined,state);
  await assert.rejects(actionRequestState(confirmed,'send_message',actionDigest('send_message',{text:'changed message'})));
  consumeActionConfirmation(confirmed,'send_message',digest);
  assert.throws(()=>consumeActionConfirmation(confirmed,'send_message',digest));
  const pending=await actionRequestState(original,'send_message',digest);
  const pendingState=await verifyActionRequestState(pending,original);
  discardActionConfirmation(ctx(undefined,pendingState),'send_message',digest);
  assert.throws(()=>consumeActionConfirmation(ctx(undefined,pendingState),'send_message',digest));
  const expired=await actionRequestState(original,'send_message',digest);
  const expiredState=await verifyActionRequestState(expired,original);
  db.prepare('UPDATE action_confirmations SET expires_at=0').run();
  assert.throws(()=>consumeActionConfirmation(ctx(undefined,expiredState),'send_message',digest));
});
