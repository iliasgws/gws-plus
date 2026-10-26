import dns from 'node:dns/promises';
import https from 'node:https';
import net from 'node:net';
import ipaddr from 'ipaddr.js';
import { inflateRawSync } from 'node:zlib';

export type MediaKind = 'pdf' | 'docx' | 'xlsx' | 'pptx' | 'png' | 'jpeg' | 'gif' | 'webp' | 'text' | 'csv';
export type MediaDownloadErrorCode = 'not_found' | 'forbidden' | 'unsupported_type' | 'too_large' | 'upstream_failure' | 'timeout';
export class MediaDownloadError extends Error {
  constructor(readonly code: MediaDownloadErrorCode, message: string) { super(message); }
}
export type MediaFile = { filename: string; mime: string; bytes: Buffer };
export type DownloadOptions = {
  resolve?: (hostname: string) => Promise<Array<{ address: string; family: number }>>;
  request?: (url: URL, pinned: string[], maxBytes: number, timeoutMs: number) => Promise<{ status: number; headers: Record<string, string | undefined>; bytes: Buffer }>;
  maxBytes?: number; timeoutMs?: number;
  now?: () => number;
  quota?: { windowMs: number; maxDownloads: number; maxBytes: number };
};

const MAX_BYTES = boundedEnv('MEDIA_MAX_BYTES', 15 * 1024 * 1024, 1024, 50 * 1024 * 1024);
const TIMEOUT_MS = 20_000;
const HOST = 'media.boti.education';
const activeOwners = new Map<string, number>();
type OwnerBudget = { started: number; downloads: number; bytes: number; reserved: number };
const ownerBudgets = new Map<string, OwnerBudget>();
let active = 0;

function boundedEnv(name: string, fallback: number, min: number, max: number): number {
  const n = Number(process.env[name]);
  return Number.isSafeInteger(n) && n >= min && n <= max ? n : fallback;
}
const DEFAULT_QUOTA = {
  windowMs: boundedEnv('MEDIA_QUOTA_WINDOW_MS', 60 * 60 * 1000, 60_000, 24 * 60 * 60 * 1000),
  maxDownloads: boundedEnv('MEDIA_OWNER_MAX_DOWNLOADS', 20, 1, 500),
  maxBytes: boundedEnv('MEDIA_OWNER_MAX_BYTES', 60 * 1024 * 1024, 1024, 500 * 1024 * 1024),
};
const DEFAULT_ALLOWED = new Set((process.env.MEDIA_ALLOWED_TYPES ?? 'pdf,docx,xlsx,pptx,png,jpeg,gif,webp,text,csv')
  .split(',').map(x => x.trim().toLowerCase()).filter(x => /^[a-z]{2,8}$/.test(x)));
const OWNER_MAP_LIMIT = 10_000;

export function safeFilename(value: unknown): string | undefined {
  if (typeof value !== 'string') return;
  let candidate = value;
  try { if (/^https?:\/\//i.test(value)) candidate = new URL(value).pathname; } catch { return; }
  try { candidate = decodeURIComponent(candidate); } catch { /* Keep literal filename. */ }
  const leaf = candidate.replaceAll('\\', '/').split('/').at(-1)?.trim() ?? '';
  if (!leaf || leaf === '.' || leaf === '..') return;
  const clean = leaf.normalize('NFKC').replace(/[^\p{L}\p{N}._ -]/gu, '_').replace(/[. ]+$/g, '').slice(0, 160);
  return clean || undefined;
}

export function validatedMediaUrl(raw: string): URL {
  let url: URL;
  try { url = new URL(raw); } catch { throw new MediaDownloadError('forbidden', 'Attachment URL is not approved'); }
  const authority = /^https:\/\/([^/?#]+)/i.exec(raw)?.[1];
  if (url.protocol !== 'https:' || url.hostname !== HOST || !url.pathname.startsWith('/view/') ||
      url.username || url.password || url.port || url.hash || authority !== HOST) {
    throw new MediaDownloadError('forbidden', 'Attachment URL is not approved');
  }
  return url;
}

export function publicIp(address: string): boolean {
  if (!net.isIP(address)) return false;
  let parsed: ipaddr.IPv4 | ipaddr.IPv6;
  try { parsed = ipaddr.parse(address); } catch { return false; }
  if (parsed.kind() === 'ipv4') {
    const v4=parsed as ipaddr.IPv4;
    const blocked:Array<[string,number]>=[['0.0.0.0',8],['10.0.0.0',8],['100.64.0.0',10],['127.0.0.0',8],['169.254.0.0',16],['172.16.0.0',12],['192.0.0.0',24],['192.0.2.0',24],['192.88.99.0',24],['192.168.0.0',16],['198.18.0.0',15],['198.51.100.0',24],['203.0.113.0',24],['224.0.0.0',4],['240.0.0.0',4]];
    return v4.range()==='unicast'&&!blocked.some(([base,bits])=>v4.match(ipaddr.parse(base),bits));
  }
  const v6 = parsed as ipaddr.IPv6;
  if (v6.isIPv4MappedAddress()) return publicIp(v6.toIPv4Address().toString());
  // Public global unicast only. Exclude special-purpose and transition ranges,
  // including expanded spellings that textual prefix tests miss.
  const blocked: Array<[string, number]> = [
    ['2001:db8::',32], ['2001::',23], ['2001::',32], ['2002::',16],
    ['2001:10::',28], ['2001:20::',28], ['2001:2::',48], ['2001:4:112::',48], ['3fff::',20], ['5f00::',16],
  ];
  if (v6.range() !== 'unicast' || !v6.match(ipaddr.parse('2000::'), 3)) return false;
  return !blocked.some(([base, bits]) => v6.match(ipaddr.parse(base), bits));
}

const MIME_BY_EXT: Record<string, { mime: string; kind: MediaKind }> = {
  pdf: { mime: 'application/pdf', kind: 'pdf' }, docx: { mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', kind: 'docx' },
  xlsx: { mime: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', kind: 'xlsx' }, pptx: { mime: 'application/vnd.openxmlformats-officedocument.presentationml.presentation', kind: 'pptx' },
  png: { mime: 'image/png', kind: 'png' }, jpg: { mime: 'image/jpeg', kind: 'jpeg' }, jpeg: { mime: 'image/jpeg', kind: 'jpeg' }, gif: { mime: 'image/gif', kind: 'gif' }, webp: { mime: 'image/webp', kind: 'webp' }, txt: { mime: 'text/plain', kind: 'text' }, csv: { mime: 'text/csv', kind: 'csv' },
};
const MIME_ALIASES: Record<string, string[]> = {
  pdf: ['application/pdf'], docx: ['application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
  xlsx: ['application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'], pptx: ['application/vnd.openxmlformats-officedocument.presentationml.presentation'],
  png: ['image/png'], jpeg: ['image/jpeg'], gif: ['image/gif'], webp: ['image/webp'], text: ['text/plain'], csv: ['text/csv', 'application/csv'],
};
const ZIP_MAX_ENTRIES = 4096, ZIP_MAX_EXPANDED = 40 * 1024 * 1024, ZIP_MAX_ENTRY = 20 * 1024 * 1024;
function zipEntries(buf: Buffer): Map<string, Buffer> | undefined {
  const min = Math.max(0, buf.length - 65_557); let eocd = -1;
  for (let i = buf.length - 22; i >= min; i--) if (buf.readUInt32LE(i) === 0x06054b50 && i + 22 + buf.readUInt16LE(i + 20) === buf.length) { eocd = i; break; }
  if (eocd < 0 || buf.readUInt16LE(eocd+4)!==0 || buf.readUInt16LE(eocd+6)!==0) return;
  const diskCount=buf.readUInt16LE(eocd+8), count=buf.readUInt16LE(eocd+10), cdSize=buf.readUInt32LE(eocd+12), offset=buf.readUInt32LE(eocd+16);
  if (!count || count !== diskCount || count > ZIP_MAX_ENTRIES || cdSize === 0xffffffff || offset === 0xffffffff || offset + cdSize !== eocd) return;
  const entries = new Map<string,{data:Buffer; size:number; compressed:number; method:number; crc:number}>(); let at=offset,total=0;
  const ranges:Array<[number,number]>=[];
  for (let i = 0; i < count; i++) {
    if (at + 46 > eocd || buf.readUInt32LE(at) !== 0x02014b50) return;
    const flags=buf.readUInt16LE(at+8), method=buf.readUInt16LE(at+10), crc=buf.readUInt32LE(at+16), compressed=buf.readUInt32LE(at+20), size=buf.readUInt32LE(at+24);
    const n = buf.readUInt16LE(at + 28), x = buf.readUInt16LE(at + 30), c = buf.readUInt16LE(at + 32);
    const localOffset=buf.readUInt32LE(at+42), diskStart=buf.readUInt16LE(at+34);
    if (at + 46 + n + x + c > eocd || !n || diskStart!==0 || (flags & 0x2041) || (method!==0 && method!==8) || compressed===0xffffffff || size===0xffffffff || size>ZIP_MAX_ENTRY || compressed>buf.length) return;
    const nameBytes=buf.subarray(at+46,at+46+n), name=nameBytes.toString('utf8');
    if (!Buffer.from(name,'utf8').equals(nameBytes) || name.includes('\\') || name.startsWith('/') || /^[A-Za-z]:/.test(name) || name.split('/').some(part=>part==='..') || entries.has(name)) return;
    if (/(^|\/)(vbaProject\.bin|embeddings\/)/i.test(name) || /\.(exe|dll|com|scr|js|hta)$/i.test(name)) return;
    total += size; if(total>ZIP_MAX_EXPANDED || localOffset+30>offset || buf.readUInt32LE(localOffset)!==0x04034b50) return;
    const lf=buf.readUInt16LE(localOffset+6), lm=buf.readUInt16LE(localOffset+8), ln=buf.readUInt16LE(localOffset+26), lx=buf.readUInt16LE(localOffset+28);
    if (lf!==flags || lm!==method || (lf&8)===0 && (buf.readUInt32LE(localOffset+14)!==crc || buf.readUInt32LE(localOffset+18)!==compressed || buf.readUInt32LE(localOffset+22)!==size)) return;
    const localName=buf.subarray(localOffset+30,localOffset+30+ln);
    if (!localName.equals(nameBytes)) return;
    const start=localOffset+30+ln+lx,end=start+compressed;
    if(end>offset || start>buf.length || end<start) return;
    if(ranges.some(([a,b])=>localOffset<b&&a<end)) return;
    let data:Buffer;
    try { const input=buf.subarray(start,end); data=method===0?Buffer.from(input):inflateRawSync(input,{maxOutputLength:ZIP_MAX_ENTRY}); } catch { return; }
    if(data.length!==size || (crc32(data)!==crc)) return;
    ranges.push([localOffset,end]);
    entries.set(name,{data,size,compressed,method,crc}); at += 46+n+x+c;
  }
  if(at!==eocd) return;
  return new Map([...entries].map(([name,value])=>[name,value.data]));
}
function crc32(buf:Buffer):number { let crc=0xffffffff; for(const byte of buf){crc^=byte;for(let k=0;k<8;k++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0; }
function validOfficeXml(kind:MediaKind, files:Map<string,Buffer>):boolean {
  const main=kind==='docx'?'word/document.xml':kind==='xlsx'?'xl/workbook.xml':'ppt/presentation.xml';
  const types=files.get('[Content_Types].xml')?.toString('utf8'), xml=files.get(main)?.toString('utf8');
  if(!types||!xml||types.length>1024*1024||xml.length>ZIP_MAX_ENTRY) return false;
  if(!/^\s*(?:<\?xml\b[^?]*\?>\s*)?<Types\b[\s\S]*<\/Types>\s*$/i.test(types) || /<!DOCTYPE|<!ENTITY|<\s*script\b|macroEnabled|vbaProject|embeddedObject/i.test(types+xml)) return false;
  const officeType=kind==='docx'?'wordprocessingml.document.main+xml':kind==='xlsx'?'spreadsheetml.sheet.main+xml':'presentationml.presentation.main+xml';
  return types.includes(officeType) && /<([\w.-]+:)?(?:document|workbook|presentation)\b/i.test(xml) && /<\/([\w.-]+:)?(?:document|workbook|presentation)\s*>/i.test(xml);
}
function signature(kind: MediaKind, b: Buffer): boolean {
  if (kind === 'pdf') return b.length >= 8 && b.subarray(0, 5).toString() === '%PDF-' && b.includes(Buffer.from('%%EOF'));
  if (kind === 'png') return b.length >= 24 && b.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])) && b.toString('ascii',12,16)==='IHDR';
  if (kind === 'jpeg') return b.length >= 4 && b[0]===255 && b[1]===216 && b[b.length-2]===255 && b[b.length-1]===217;
  if (kind === 'gif') return b.length >= 14 && ['GIF87a','GIF89a'].includes(b.toString('ascii',0,6)) && b[b.length-1]===0x3b;
  if (kind === 'webp') return b.length >= 16 && b.toString('ascii',0,4)==='RIFF' && b.toString('ascii',8,12)==='WEBP';
  if (kind === 'docx' || kind === 'xlsx' || kind === 'pptx') { const files=zipEntries(b); return !!files && validOfficeXml(kind,files); }
  if (kind === 'text' || kind === 'csv') {
    if (!b.length || b.some(x => x < 9 || (x > 13 && x < 32))) return false;
    try { new TextDecoder('utf-8', { fatal: true }).decode(b); return true; } catch { return false; }
  }
  return false;
}

export async function collectBounded(source: AsyncIterable<Uint8Array>, maxBytes: number, abort: () => void = () => {}): Promise<Buffer> {
  const chunks: Buffer[] = []; let total = 0;
  for await (const value of source) {
    total += value.byteLength;
    if (total > maxBytes) { abort(); throw new MediaDownloadError('too_large','Attachment exceeds size limit'); }
    chunks.push(Buffer.from(value));
  }
  return Buffer.concat(chunks,total);
}

export function pinnedLookup(address: string) {
  const family=net.isIP(address);
  return (_host:string, opts: { all?: boolean }, cb: (...args:any[])=>void) => {
    if(opts.all) cb(null,[{address,family}]); else cb(null,address,family);
  };
}
export function nativeRequestOptions(url:URL,pinned:string[]) {
  return { method:'GET' as const, headers:{accept:'*/*'}, servername:url.hostname, rejectUnauthorized:true,
    autoSelectFamily:false, lookup:pinnedLookup(pinned[0]) };
}
export async function requestPinned(url: URL, pinned: string[], maxBytes: number, timeoutMs: number, requestImpl: typeof https.request = https.request) {
  return new Promise<{ status: number; headers: Record<string, string | undefined>; bytes: Buffer }>((resolve, reject) => {
    let settled=false;
    const fail=(error:unknown)=>{if(!settled){settled=true;reject(error);}};
    const req = requestImpl(url, nativeRequestOptions(url,pinned), res => {
      const status=res.statusCode??0;
      const rawLength=res.headers['content-length'];
      const length=typeof rawLength==='string' && /^\d+$/.test(rawLength)?Number(rawLength):undefined;
      if(status<200||status>=300){res.destroy();req.destroy();fail(new MediaDownloadError('upstream_failure','Attachment server returned an error'));return;}
      if(length!==undefined&&(!Number.isSafeInteger(length)||length>maxBytes)){res.destroy();req.destroy();fail(new MediaDownloadError('too_large','Attachment exceeds size limit'));return;}
      collectBounded(res, maxBytes, () => req.destroy(new MediaDownloadError('too_large','Attachment exceeds size limit')))
        .then(bytes => {if(!settled){settled=true;resolve({ status, headers: { 'content-type': res.headers['content-type'], 'content-length': rawLength }, bytes });}}, fail);
    });
    const deadline = setTimeout(() => req.destroy(new MediaDownloadError('timeout','Attachment download timed out')), timeoutMs);
    req.on('close', () => clearTimeout(deadline));
    req.setTimeout(timeoutMs, () => req.destroy(new MediaDownloadError('timeout','Attachment download timed out')));
    req.on('error', fail); req.end();
  });
}

function bounded<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => reject(new MediaDownloadError('timeout','Attachment lookup timed out')), ms);
    promise.then(value => { clearTimeout(timer); resolve(value); }, error => { clearTimeout(timer); reject(error); });
  });
}

export async function downloadMedia(rawUrl: string, filenameValue: unknown, declaredMime: unknown, ownerKey = 'default', options: DownloadOptions = {}): Promise<MediaFile> {
  const now=(options.now??Date.now)(), quota=options.quota??DEFAULT_QUOTA;
  for(const [key,budget] of ownerBudgets) if(now-budget.started>=quota.windowMs && (activeOwners.get(key)??0)===0) ownerBudgets.delete(key);
  if(!ownerBudgets.has(ownerKey)&&ownerBudgets.size>=OWNER_MAP_LIMIT) throw new MediaDownloadError('upstream_failure','Download capacity is temporarily unavailable');
  let budget=ownerBudgets.get(ownerKey);
  if(!budget||(now-budget.started>=quota.windowMs&&(activeOwners.get(ownerKey)??0)===0)){budget={started:now,downloads:0,bytes:0,reserved:0};ownerBudgets.set(ownerKey,budget);}
  if(budget.downloads>=quota.maxDownloads || budget.bytes+budget.reserved>=quota.maxBytes) throw new MediaDownloadError('upstream_failure','Attachment download budget is temporarily exhausted');
  if (active >= 8 || (activeOwners.get(ownerKey) ?? 0) >= 3) throw new MediaDownloadError('upstream_failure','Download capacity is temporarily unavailable');
  budget.downloads++;
  active++; activeOwners.set(ownerKey,(activeOwners.get(ownerKey) ?? 0)+1);
  let reservedBytes=0;
  try {
    const url = validatedMediaUrl(rawUrl); const filename = safeFilename(filenameValue);
    if (!filename) throw new MediaDownloadError('unsupported_type','Attachment filename is unsupported');
    const ext = filename.split('.').at(-1)?.toLowerCase() ?? ''; const spec = MIME_BY_EXT[ext];
    if (!spec || !DEFAULT_ALLOWED.has(spec.kind)) throw new MediaDownloadError('unsupported_type','Attachment file type is unsupported');
    const declared = typeof declaredMime === 'string' ? declaredMime.split(';')[0].trim().toLowerCase() : '';
    if ((declared && !MIME_ALIASES[spec.kind].includes(declared)) || (declared && declared === 'application/zip')) throw new MediaDownloadError('unsupported_type','Attachment MIME type does not match its filename');
    const resolve = options.resolve ?? (async host => dns.lookup(host,{all:true,verbatim:true}));
    const addresses = await bounded(resolve(url.hostname), options.timeoutMs ?? TIMEOUT_MS);
    if (!addresses.length || addresses.some(x => !publicIp(x.address))) throw new MediaDownloadError('forbidden','Attachment server address is not public');
    const configuredMax=options.maxBytes ?? MAX_BYTES;
    const ownerLimited=quota.maxBytes-budget.bytes-budget.reserved<configuredMax;
    const maxBytes=Math.min(configuredMax,quota.maxBytes-budget.bytes-budget.reserved);
    if(maxBytes<=0) throw new MediaDownloadError('upstream_failure','Attachment download byte budget is temporarily exhausted');
    reservedBytes=maxBytes;budget.reserved+=reservedBytes;
    const transport = options.request ?? requestPinned;
    let response;
    try { response=await transport(url,addresses.map(x=>x.address),maxBytes,options.timeoutMs ?? TIMEOUT_MS); }
    catch(e) { if(ownerLimited && e instanceof MediaDownloadError && e.code==='too_large') throw new MediaDownloadError('upstream_failure','Attachment download byte budget is temporarily exhausted'); throw e; }
    budget.bytes+=response.bytes.length;
    const headerLength=response.headers['content-length'];
    if(typeof headerLength==='string'&&/^\d+$/.test(headerLength)&&Number(headerLength)>maxBytes) throw new MediaDownloadError(ownerLimited?'upstream_failure':'too_large',ownerLimited?'Attachment download byte budget is temporarily exhausted':'Attachment exceeds size limit');
    if (response.bytes.length > maxBytes) throw new MediaDownloadError(ownerLimited?'upstream_failure':'too_large',ownerLimited?'Attachment download byte budget is temporarily exhausted':'Attachment exceeds size limit');
    if (response.status < 200 || response.status >= 300) throw new MediaDownloadError('upstream_failure','Attachment server returned an error');
    const contentType = response.headers['content-type']?.split(';')[0].trim().toLowerCase();
    if (!contentType || !MIME_ALIASES[spec.kind].includes(contentType)) throw new MediaDownloadError('unsupported_type','Attachment response MIME type does not match its filename');
    if (!signature(spec.kind,response.bytes)) throw new MediaDownloadError('unsupported_type','Attachment content does not match its declared file type');
    return { filename, mime: spec.mime, bytes: response.bytes };
  } catch (e) {
    if (e instanceof MediaDownloadError) throw e;
    throw new MediaDownloadError('upstream_failure','Attachment could not be downloaded');
  } finally {
    if(reservedBytes) budget.reserved-=reservedBytes;
    active--; const n=(activeOwners.get(ownerKey) ?? 1)-1; if(n) activeOwners.set(ownerKey,n); else activeOwners.delete(ownerKey);
  }
}
