import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { Readable } from 'node:stream';
import { collectBounded, downloadMedia, MediaDownloadError, validatedMediaUrl, publicIp, nativeRequestOptions, pinnedLookup, requestPinned } from '../src/media-download.js';
import { registerDocumentTools } from '../src/document-tools.js';

const url = 'https://media.boti.education/view/token/320/assets/schools/a/docs/x.pdf';
const pdf = Buffer.from('%PDF-1.7\nfixture\n%%EOF');
const transport = async (_url: URL, _ips: string[], _max: number) => ({ status: 200, headers: { 'content-type': 'application/pdf' }, bytes: pdf });
const liveDns = async () => [{ address: '8.8.8.8', family: 4 }, { address: '2606:4700:4700::1111', family: 6 }];

test('URL allowlist rejects userinfo, port, fragments and foreign path/host', () => {
  for (const bad of [url.replace('https://','https://evil@'), url.replace('.education/','.education:443/'), `${url}#x`, url.replace('/view/','/other/'), url.replace('media.boti','attacker.boti')]) {
    assert.throws(() => validatedMediaUrl(bad), MediaDownloadError);
  }
});

test('DNS validates every answer and transport receives a pinned public set', async () => {
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','dns-private',{ resolve: async () => [{address:'8.8.8.8',family:4},{address:'127.0.0.1',family:4}], request: transport }), (e:any) => e.code === 'forbidden');
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','dns-v6',{ resolve: async () => [{address:'::1',family:6}], request: transport }), (e:any) => e.code === 'forbidden');
  let pinned: string[] = [];
  const f = await downloadMedia(url,'x.pdf','application/pdf','dns-pin',{ resolve: liveDns, request: async (_u,ips) => { pinned=ips; return transport(_u,ips,1000); } });
  assert.deepEqual(pinned,['8.8.8.8','2606:4700:4700::1111']); assert.equal(f.mime,'application/pdf');
});

test('MIME and signature deception and unsupported executable files are rejected', async () => {
  await assert.rejects(downloadMedia(url,'x.pdf','image/png','mime',{resolve:liveDns,request:transport}), (e:any)=>e.code==='unsupported_type');
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','signature',{resolve:liveDns,request:async()=>({status:200,headers:{'content-type':'application/pdf'},bytes:Buffer.from('MZnotpdf')})}), (e:any)=>e.code==='unsupported_type');
  await assert.rejects(downloadMedia(url,'x.exe','application/pdf','extension',{resolve:liveDns,request:transport}), (e:any)=>e.code==='unsupported_type');
});

test('oversize and timeout failures return safe codes and release quota', async () => {
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','large',{resolve:liveDns, maxBytes:8,request:async()=>({status:200,headers:{},bytes:pdf})}), (e:any)=>e.code==='too_large');
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','timeout',{resolve:liveDns,request:async()=>{throw new MediaDownloadError('timeout','timed out');}}), (e:any)=>e.code==='timeout');
  assert.equal((await downloadMedia(url,'x.pdf','application/pdf','large',{resolve:liveDns,request:transport})).bytes.length,pdf.length);
});

test('chunked bodies are counted incrementally and abort the source at the limit', async () => {
  let aborted=false;
  async function* chunks() { yield Buffer.from('1234'); yield Buffer.from('5678'); yield Buffer.from('9'); }
  await assert.rejects(collectBounded(chunks(),8,()=>{aborted=true;}),(e:any)=>e.code==='too_large');
  assert.equal(aborted,true);
  async function* valid() { yield Buffer.from('12'); yield Buffer.from('34'); }
  assert.equal((await collectBounded(valid(),4)).toString(),'1234');
});

test('global and per-owner concurrency limits clear after upstream failure', async () => {
  let release!: () => void;
  const wait = new Promise<void>(r => { release=r; });
  const request = async () => { await wait; return {status:200,headers:{'content-type':'application/pdf'},bytes:pdf}; };
  const tasks = [0,1,2].map(() => downloadMedia(url,'x.pdf','application/pdf','owner-limit',{resolve:liveDns,request}));
  await new Promise(r=>setTimeout(r,10));
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','owner-limit',{resolve:liveDns,request:transport}), (e:any)=>e.code==='upstream_failure');
  release(); await Promise.all(tasks);
  assert.equal((await downloadMedia(url,'x.pdf','application/pdf','owner-limit',{resolve:liveDns,request:transport})).bytes.length,pdf.length);
});

test('tool registration preserves scope and account closure isolation', async () => {
  const mkServer = () => { const tools = new Map<string,Function>(); return { tools, registerTool(name:string,_meta:any,handler:Function){tools.set(name,handler);} }; };
  const a = mkServer(), b=mkServer(); const seenA:string[]=[]; const seenB:string[]=[];
  registerDocumentTools(a as any, async (route,params) => {seenA.push(`${route}:${params?.devoir}`); return {data:{id:params?.devoir, files:[]}};}, scope=>{if(scope!=='homework:read')throw Error('forbidden');}, scope=>scope==='homework:read');
  registerDocumentTools(b as any, async (route,params) => {seenB.push(`${route}:${params?.devoir}`); return {data:{id:params?.devoir, files:[]}};}, ()=>{throw Error('forbidden');}, scope=>scope==='homework:read');
  const handlerA=a.tools.get('download_homework_attachment')!; const handlerB=b.tools.get('download_homework_attachment')!;
  // The two registered callbacks retain their own getData and grant closures.
  await handlerA({homework_id:'101',attachment_index:0});
  assert.deepEqual(seenA,['devoirs:101']); assert.deepEqual(seenB,[]);
  await assert.rejects(handlerB({homework_id:'202',attachment_index:0}), /forbidden/);
  assert.deepEqual(seenB,[]);
  assert.equal(a.tools.has('download_library_pdf'),false);
  assert.equal(b.tools.has('download_library_pdf'),false);
});

test('canonical public address classification rejects special IPv4 and IPv6 ranges', () => {
  for(const address of ['127.0.0.1','10.1.2.3','169.254.1.1','100.64.0.1','192.0.0.9','192.0.2.1','192.88.99.1','198.51.100.1','224.0.0.1','::1','fc00::1','fe80::1','ff02::1','2001:0db8::1','2001::1','2001:0000::1','2001:20::1','2002::1','::ffff:127.0.0.1']) assert.equal(publicIp(address),false,address);
  for(const address of ['8.8.8.8','2606:4700:4700::1111']) assert.equal(publicIp(address),true,address);
});

test('native HTTPS options pin family and support lookup all:true callbacks', () => {
  const options=nativeRequestOptions(new URL(url),['2606:4700:4700::1111']);
  assert.equal(options.autoSelectFamily,false);
  const lookup=options.lookup as ReturnType<typeof pinnedLookup>;
  let allResult:any,oneResult:any;
  lookup('media.boti.education',{all:true},(...args:any[])=>{allResult=args;});
  lookup('media.boti.education',{all:false},(...args:any[])=>{oneResult=args;});
  assert.deepEqual(allResult,[null,[{address:'2606:4700:4700::1111',family:6}]]);
  assert.deepEqual(oneResult,[null,'2606:4700:4700::1111',6]);
});

test('native transport destroys redirect and declared-oversize response streams before reading',async()=>{
  async function exercise(status:number,headers:Record<string,string>,limit:number){
    let response:any,options:any;
    const fakeRequest=((_url:URL,opts:any,onResponse:(res:any)=>void)=>{
      options=opts;
      const req:any=new EventEmitter();req.setTimeout=()=>{};req.end=()=>{};req.destroy=()=>{queueMicrotask(()=>req.emit('close'));};
      response=Readable.from([Buffer.from('unread response body')]) as any;response.statusCode=status;response.headers=headers;
      queueMicrotask(()=>onResponse(response));return req;
    }) as any;
    await assert.rejects(requestPinned(new URL(url),['8.8.8.8'],limit,1000,fakeRequest));
    assert.equal(response.destroyed,true);
    assert.equal(options.autoSelectFamily,false);
  }
  await exercise(302,{location:'https://attacker.invalid/', 'content-length':'20'},100);
  await exercise(200,{'content-length':'1000'},100);
});

function zipFixture(items:Array<{name:string;data:Buffer;centralName?:string;localName?:string}>):Buffer {
  const locals:Buffer[]=[], centrals:Buffer[]=[]; let offset=0;
  const crc=(b:Buffer)=>{let c=0xffffffff;for(const x of b){c^=x;for(let j=0;j<8;j++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^0xffffffff)>>>0;};
  for(const item of items){const localName=Buffer.from(item.localName??item.name),centralName=Buffer.from(item.centralName??item.name),data=item.data,c=crc(data);
    const l=Buffer.alloc(30);l.writeUInt32LE(0x04034b50);l.writeUInt16LE(20,4);l.writeUInt32LE(c,14);l.writeUInt32LE(data.length,18);l.writeUInt32LE(data.length,22);l.writeUInt16LE(localName.length,26);locals.push(l,localName,data);
    const h=Buffer.alloc(46);h.writeUInt32LE(0x02014b50);h.writeUInt16LE(20,4);h.writeUInt16LE(20,6);h.writeUInt32LE(c,16);h.writeUInt32LE(data.length,20);h.writeUInt32LE(data.length,24);h.writeUInt16LE(centralName.length,28);h.writeUInt32LE(offset,42);centrals.push(h,centralName);offset+=l.length+localName.length+data.length;
  }
  const cd=Buffer.concat(centrals),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(items.length,8);end.writeUInt16LE(items.length,10);end.writeUInt32LE(cd.length,12);end.writeUInt32LE(offset,16);return Buffer.concat([...locals,cd,end]);
}
function officeParts(kind:'docx'|'xlsx'|'pptx',extra:Array<{name:string;data:Buffer}> = []) {
  const main=kind==='docx'?'word/document.xml':kind==='xlsx'?'xl/workbook.xml':'ppt/presentation.xml';
  const contentType=kind==='docx'?'wordprocessingml.document.main+xml':kind==='xlsx'?'spreadsheetml.sheet.main+xml':'presentationml.presentation.main+xml';
  const root=kind==='docx'?'<w:document><w:body/></w:document>':kind==='xlsx'?'<workbook></workbook>':'<p:presentation></p:presentation>';
  return [
    {name:'[Content_Types].xml',data:Buffer.from(`<?xml version="1.0"?><Types><Default Extension="xml" ContentType="application/xml"/><Override PartName="/${main}" ContentType="application/vnd.openxmlformats-officedocument.${contentType}"/></Types>`)},
    {name:main,data:Buffer.from(`<?xml version="1.0"?>${root}`)},...extra,
  ];
}

test('valid OOXML ZIPs require consistent local entries and bounded primary XML', async()=>{
  const formats=[['docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document'],['xlsx','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'],['pptx','application/vnd.openxmlformats-officedocument.presentationml.presentation']] as const;
  for(const [kind,mime] of formats){const valid=zipFixture(officeParts(kind));const good=await downloadMedia(url,`proof.${kind}`,mime,`zip-good-${kind}`,{resolve:liveDns,request:async()=>({status:200,headers:{'content-type':mime},bytes:valid})});assert.equal(good.bytes.equals(valid),true);}
  const malformed=zipFixture(officeParts('docx').map(x=>x.name==='word/document.xml'?{...x,localName:'word/forged.xml'}:x));
  const macro=zipFixture(officeParts('docx',[{name:'word/vbaProject.bin',data:Buffer.from('macro')} ]));
  for(const bytes of [malformed,macro]) {
    await assert.rejects(downloadMedia(url,'proof.docx','application/vnd.openxmlformats-officedocument.wordprocessingml.document','zip-bad',{resolve:liveDns,request:async()=>({status:200,headers:{'content-type':'application/vnd.openxmlformats-officedocument.wordprocessingml.document'},bytes})}),(e:any)=>e.code==='unsupported_type');
  }
});

test('content length, redirects, and response signatures fail closed',async()=>{
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','header-large',{resolve:liveDns,maxBytes:10,request:async()=>({status:200,headers:{'content-length':'999','content-type':'application/pdf'},bytes:pdf})}),(e:any)=>e.code==='too_large');
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','redirect',{resolve:liveDns,request:async()=>({status:302,headers:{location:'https://attacker.invalid/'},bytes:Buffer.alloc(0)})}),(e:any)=>e.code==='upstream_failure');
  await assert.rejects(downloadMedia(url,'x.bin','application/octet-stream','unknown',{resolve:liveDns,request:transport}),(e:any)=>e.code==='unsupported_type');
});

test('per-owner window rate and byte budgets expire and do not grow forever',async()=>{
  let now=1_000;
  const quota={windowMs:1000,maxDownloads:1,maxBytes:pdf.length*2};
  const opts={resolve:liveDns,request:transport,now:()=>now,quota};
  await downloadMedia(url,'x.pdf','application/pdf','window-owner',opts);
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','window-owner',opts),(e:any)=>e.code==='upstream_failure');
  now+=1001;
  assert.equal((await downloadMedia(url,'x.pdf','application/pdf','window-owner',opts)).bytes.length,pdf.length);
  const byteQuota={...opts,quota:{windowMs:1000,maxDownloads:10,maxBytes:pdf.length+1}};
  await downloadMedia(url,'x.pdf','application/pdf','byte-owner',byteQuota);
  await assert.rejects(downloadMedia(url,'x.pdf','application/pdf','byte-owner',byteQuota),(e:any)=>e.code==='upstream_failure');
});

test('homework tool accepts the observed singular file shape and preserves owner scope',async()=>{
  const srv:any={tools:new Map(),registerTool(name:string,_meta:any,handler:Function){this.tools.set(name,handler);}};
  let gotOwner='';
  registerDocumentTools(srv,async()=>({data:{id:'101',file:{text:'worksheet.pdf',link:url,mime:{type:'application/pdf'}}}}),()=>{},scope=>scope==='homework:read',()=> 'stable-owner',async(_url,_name,_mime,owner)=>{gotOwner=owner;return {filename:'worksheet.pdf',mime:'application/pdf',bytes:pdf};});
  const result=await srv.tools.get('download_homework_attachment')({homework_id:'101',attachment_index:0});
  assert.equal(result.content[0].text.includes('worksheet.pdf'),true);
  assert.equal(gotOwner,'stable-owner');
});

test('current flat Boti homework and library details resolve their own verified files', async () => {
  const srv:any={tools:new Map(),registerTool(name:string,_meta:any,handler:Function){this.tools.set(name,handler);}};
  registerDocumentTools(srv,async(route)=>route==='devoirs'
    ? {id:'101',files:[{filename:'worksheet.pdf',link:url,mime:{type:'application/pdf'}}]}
    : {title:'Library fixture',files:[{filename:'library.pdf',link:url,mime:{type:'application/pdf'}}]},
    ()=>{},()=>true,()=> 'flat-owner',async(_url,name)=>({filename:String(name),mime:'application/pdf',bytes:pdf}));
  const homework=await srv.tools.get('download_homework_attachment')({homework_id:'101',attachment_index:0});
  assert.equal(homework.isError,undefined);assert.equal(JSON.parse(homework.content[0].text).filename,'worksheet.pdf');
  const library=await srv.tools.get('download_library_pdf')({resource_id:'201'});
  assert.equal(library.isError,undefined);assert.equal(JSON.parse(library.content[0].text).filename,'library.pdf');
});

test('text files reject invalid UTF-8 and binary controls beyond the initial prefix', async () => {
  for(const bytes of [Buffer.from([0xff,0xfe]),Buffer.concat([Buffer.alloc(5000,65),Buffer.from([1])])]) {
    await assert.rejects(downloadMedia(url,'data.txt','text/plain','invalid-text-'+bytes.length,{
      resolve:liveDns,request:async()=>({status:200,headers:{'content-type':'text/plain'},bytes}),
    }),(e:any)=>e.code==='unsupported_type');
  }
});
