import test from 'node:test';
import assert from 'node:assert/strict';
import { parseResponse } from '../src/boti.js';

test('Boti JSON bodies are capped for declared and chunked responses', async () => {
  let canceled=false;
  const declared=new Response(new ReadableStream({cancel(){canceled=true;}}),{headers:{'content-length':String(6*1024*1024)}});
  await assert.rejects(parseResponse(declared),/size limit/);assert.ok(canceled);
  canceled=false;
  const chunks=new Response(new ReadableStream({pull(controller){controller.enqueue(new Uint8Array(1024*1024));},cancel(){canceled=true;}}));
  await assert.rejects(parseResponse(chunks),/size limit/);assert.ok(canceled);
  const normal=await parseResponse(new Response(JSON.stringify({id:'fixture',files:[]})));
  assert.equal(normal.id,'fixture');
});

test('malformed upstream JSON never exposes response bytes in an error', async () => {
  await assert.rejects(parseResponse(new Response('upstream-secret-marker is not JSON')),error=>{
    assert.ok(error instanceof Error);assert.equal(error.message,'Boti returned invalid JSON');assert.ok(!error.message.includes('upstream-secret-marker'));return true;
  });
});
