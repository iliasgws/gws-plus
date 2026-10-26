#!/usr/bin/env node
const timeoutMs = Number(process.env.HEALTH_TIMEOUT_MS ?? '5000');
const base = process.env.PUBLIC_URL ?? '';
const authOverride = process.env.AUTH_METADATA_URL;
const protectedOverride = process.env.PROTECTED_METADATA_URL;
const healthUrl = process.env.HEALTH_URL ?? (base ? new URL('/healthz', base).href : '');
const authUrl = authOverride ?? (base ? new URL('/.well-known/oauth-authorization-server', base).href : '');
const protectedUrl = protectedOverride ?? (base ? new URL('/.well-known/oauth-protected-resource/mcp', base).href : '');

async function json(url) {
  if (!url) throw new Error('Required health or metadata URL is not configured');
  const response = await fetch(url, { signal: AbortSignal.timeout(timeoutMs), redirect: 'error', headers: { accept: 'application/json' } });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}
const failures = [];
try {
  if (new URL(healthUrl).protocol !== 'https:' && !['localhost', '127.0.0.1', '::1'].includes(new URL(healthUrl).hostname)) throw new Error('Health URL must use HTTPS outside localhost');
  const response = await fetch(healthUrl, { signal: AbortSignal.timeout(timeoutMs), redirect: 'error' });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  await response.body?.cancel();
} catch (error) { failures.push(`healthz: ${error.name === 'TimeoutError' ? 'timeout' : error.message}`); }
try {
  const url = new URL(authUrl);
  if (url.protocol !== 'https:' && !['localhost', '127.0.0.1', '::1'].includes(url.hostname)) throw new Error('Authorization metadata URL must use HTTPS outside localhost');
  const metadata = await json(authUrl);
  if (!Array.isArray(metadata.scopes_supported) || !metadata.scopes_supported.includes('offline_access')) throw new Error('offline_access missing from authorization scopes_supported');
} catch (error) { failures.push(`authorization metadata: ${error.name === 'TimeoutError' ? 'timeout' : error.message}`); }
try {
  const url = new URL(protectedUrl);
  if (url.protocol !== 'https:' && !['localhost', '127.0.0.1', '::1'].includes(url.hostname)) throw new Error('Protected metadata URL must use HTTPS outside localhost');
  const metadata = await json(protectedUrl);
  if (!Array.isArray(metadata.scopes_supported) || metadata.scopes_supported.includes('offline_access')) throw new Error('offline_access must be absent from protected-resource scopes_supported');
} catch (error) { failures.push(`protected-resource metadata: ${error.name === 'TimeoutError' ? 'timeout' : error.message}`); }
if (failures.length) {
  for (const failure of failures) console.error(`Health check failed: ${failure}`);
  process.exitCode = 1;
} else console.log('Health check passed');
