import http from 'node:http';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {WebSocketServer,WebSocket} from 'ws';
import {readOrca} from './orca.mjs';

export const MAX_IMAGE=10*1024*1024;
const MAX_FRAME=32*1024*1024;
const safeId=x=>typeof x==='string'&&/^[a-zA-Z0-9._:-]{1,160}$/.test(x);
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
export function decodeImage(image){
  if(!image||!['image/png','image/jpeg','image/webp'].includes(image.mimeType)||typeof image.data!=='string'||image.data.length>Math.ceil(MAX_IMAGE/3)*4||image.data.length%4!==0||!/^[A-Za-z0-9+/]*={0,2}$/.test(image.data))throw Error('Invalid image');
  const b=Buffer.from(image.data,'base64');
  if(!b.length||b.length>MAX_IMAGE)throw Error('Image exceeds 10MB');
  if(b.toString('base64')!==image.data)throw Error('Invalid base64 encoding');
  const ok=image.mimeType==='image/png'?b.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex')):image.mimeType==='image/jpeg'?b[0]===255&&b[1]===216&&b[2]===255:b.toString('ascii',0,4)==='RIFF'&&b.toString('ascii',8,12)==='WEBP';
  if(!ok)throw Error('Image signature mismatch');
  return b;
}
export function validateCommand(c){
  if(!c||!safeId(c.requestId)||!safeId(c.sessionId)||!['prompt','abort','configure'].includes(c.command))throw Error('Invalid command');
  if(c.command==='abort')return {type:'command',sessionId:c.sessionId,requestId:c.requestId,command:'abort'};
  if(c.command==='configure'){
    const text=x=>typeof x==='string'&&x.length>0&&x.length<=200&&!/[\x00-\x1f]/.test(x);
    if((c.provider!==undefined||c.modelId!==undefined)&&(!text(c.provider)||!text(c.modelId)))throw Error('Invalid model');
    if(c.thinkingLevel!==undefined&&!['off','minimal','low','medium','high','xhigh','max'].includes(c.thinkingLevel))throw Error('Invalid effort');
    if(c.provider===undefined&&c.thinkingLevel===undefined)throw Error('Empty configuration');
    return {type:'command',sessionId:c.sessionId,requestId:c.requestId,command:'configure',...(c.provider!==undefined?{provider:c.provider,modelId:c.modelId}:{}),...(c.thinkingLevel!==undefined?{thinkingLevel:c.thinkingLevel}:{})};
  }
  if(typeof c.text!=='string'||c.text.length>100000||!Array.isArray(c.images??[])||(c.images?.length||0)>3)throw Error('Invalid prompt');
  const images=c.images||[]; let size=0;
  for(const image of images)size+=decodeImage(image).length;
  if(size>MAX_IMAGE)throw Error('Total image size exceeds 10MB');
  if(!c.text.trim()&&!images.length)throw Error('Empty prompt');
  if(c.behavior!==undefined&&!['steer','followUp'].includes(c.behavior))throw Error('Invalid behavior');
  return {type:'command',sessionId:c.sessionId,requestId:c.requestId,command:'prompt',text:c.text,images:images.map(i=>({type:'image',mimeType:i.mimeType,data:i.data})),behavior:c.behavior||'followUp'};
}

export async function createGateway({dataDir,port=8788,host='127.0.0.1',orca=true,token:providedToken}={}){
  if(!dataDir)throw Error('dataDir required');
  if(!['127.0.0.1','::1'].includes(host))throw Error('Bind gateway to loopback; use an authenticated HTTPS reverse proxy');
  fs.mkdirSync(dataDir,{recursive:true,mode:0o700}); fs.chmodSync(dataDir,0o700);
  const tokenFile=path.join(dataDir,'device-token');
  let token=providedToken;
  if(!token){if(!fs.existsSync(tokenFile))fs.writeFileSync(tokenFile,crypto.randomBytes(32).toString('base64url'),{mode:0o600,flag:'wx'}); token=fs.readFileSync(tokenFile,'utf8').trim(); fs.chmodSync(tokenFile,0o600);}
  if(typeof token!=='string'||token.length<32)throw Error('Token too short');
  const expected=hash(token),sessions=new Map(),pending=new Map(),receipts=new Map(),bridges=new Set(),media=new Map();
  let mediaBytes=0, inventory={workspaces:[],terminals:[],truncated:false,omittedHostIds:[]},inventoryError=null,closed=false;
  const authorized=req=>{const a=req.headers.authorization||'';return a.startsWith('Bearer ')&&crypto.timingSafeEqual(Buffer.from(hash(a.slice(7))),Buffer.from(expected));};
  const send=(ws,obj)=>{if(ws.readyState===WebSocket.OPEN){if(ws.bufferedAmount>8*1024*1024){ws.close(1013,'Slow client');return;}ws.send(JSON.stringify(obj));}};
  function catalog(){
    const workspaces=new Map(inventory.workspaces.map(w=>[w.id,w]));
    const list=[...sessions.values()].map(s=>{
      const terminal=inventory.terminals.find(t=>t.id===s.meta.terminalId);
      const workspaceId=terminal?.workspaceId||s.meta.workspaceId||'folder:'+s.meta.cwd;
      if(!workspaces.has(workspaceId))workspaces.set(workspaceId,{id:workspaceId,name:path.basename(s.meta.cwd)||s.meta.cwd,path:s.meta.cwd});
      return {...s.meta,workspaceId,title:s.meta.title||terminal?.title||path.basename(s.meta.cwd),connected:!!s.socket,status:s.socket?s.status:'offline'};
    });
    return {type:'catalog',workspaces:[...workspaces.values()],sessions:list,terminals:inventory.terminals.filter(t=>!list.some(s=>s.connected&&s.terminalId===t.id)),truncated:inventory.truncated,omittedHostIds:inventory.omittedHostIds,inventoryError};
  }
  const wss=new WebSocketServer({noServer:true,maxPayload:16*1024*1024,perMessageDeflate:false});
  const broadcastCatalog=()=>{const c=catalog();for(const ws of wss.clients)send(ws,c);};
  const config=m=>({model:String(m?.model||'').slice(0,300),thinkingLevel:String(m?.thinkingLevel||'off'),models:(Array.isArray(m?.models)?m.models:[]).slice(0,1000).filter(x=>typeof x.provider==='string'&&typeof x.id==='string').map(x=>({provider:x.provider.slice(0,200),id:x.id.slice(0,200),name:String(x.name||x.id).slice(0,200),thinkingLevels:(Array.isArray(x.thinkingLevels)?x.thinkingLevels:[]).filter(x=>['off','minimal','low','medium','high','xhigh','max'].includes(x))})),modelsTruncated:!!m?.modelsTruncated});
  const snapshot=s=>({type:'snapshot',sessionId:s.meta.id,status:s.socket?s.status:'offline',connected:!!s.socket,messages:s.messages,truncated:s.truncated,configuration:s.configuration});
  const broadcastSnapshot=(s,previous,configChanged=false)=>{for(const ws of wss.clients)if(ws.sessionId===s.meta.id){
    if(!previous){send(ws,snapshot(s));continue;}
    const old=new Map(previous.map(m=>[m.id,JSON.stringify(m)])),ids=new Set(s.messages.map(m=>m.id));
    send(ws,{type:'messages',sessionId:s.meta.id,status:s.socket?s.status:'offline',connected:!!s.socket,truncated:s.truncated,...(configChanged?{configuration:s.configuration}:{}),messages:s.messages.filter(m=>old.get(m.id)!==JSON.stringify(m)),removedIds:previous.filter(m=>!ids.has(m.id)).map(m=>m.id)});
  }};
  function normalize(id,messages){
    let truncated=messages.length>200;
    const tail=messages.slice(-200),results=new Map(),emitted=new Set();
    for(const m of tail)if(m.role==='toolResult'&&m.toolCallId)results.set(m.toolCallId,m);
    function render(m,i){
      const blocks=typeof m.content==='string'?[{type:'text',text:m.content}]:Array.isArray(m.content)?m.content:[];
      const images=[]; const text=[];
      for(const b of blocks){
        if(b.type==='text')text.push(String(b.text||''));
        if(b.type==='image')try{
          const bytes=decodeImage(b),digest=hash(bytes),key=id+'/'+digest;
          if(!media.has(key)){
            // Bound total in-memory image cache. Old references yield explicit 404.
            while(mediaBytes+bytes.length>128*1024*1024&&media.size){const k=media.keys().next().value;mediaBytes-=media.get(k).data.length;media.delete(k);}
            media.set(key,{data:bytes,mimeType:b.mimeType});mediaBytes+=bytes.length;
          }
          images.push({url:`/api/sessions/${encodeURIComponent(id)}/media/${digest}`,mimeType:b.mimeType});
        }catch{text.push('[Изображение пропущено: неподдерживаемый формат или размер]');}
      }
      if(m.role==='assistant'&&m.errorMessage)text.push(`[Ошибка] ${m.errorMessage}`);
      let body=text.join('\n');if(body.length>30000){body=body.slice(0,30000)+'\n[Обрезано]';truncated=true;}
      return {id:String(m.role==='toolResult'&&m.toolCallId?'tool:'+m.toolCallId:m.id||`${m.role}-${m.timestamp||0}-${i}`),role:m.role,text:body,images,...(m.toolName?{toolName:m.toolName,toolStatus:m.toolStatus||(m.isError?'error':'done')}:{})};
    }
    const result=[];
    function tool(call,m,i){
      const key=call.id||m?.toolCallId||`unknown-${i}`;if(emitted.has(key))return;emitted.add(key);
      const value=m||{role:'toolResult',toolCallId:key,toolName:call.name,content:[],toolStatus:'pending'};
      const row=render(value,i);row.toolName=call.name||value.toolName||'tool';
      const args=call.arguments??value.toolArgs;
      if(args!==undefined){let text=JSON.stringify(args,null,2);if(text.length>8000){text=text.slice(0,8000)+'\n[Аргументы обрезаны]';truncated=true;}row.text='Аргументы\n'+text+(row.text?'\n\nРезультат\n'+row.text:'');}
      result.push(row);
    }
    for(let i=0;i<tail.length;i++){
      const m=tail[i];if(m.role==='toolResult'){tool({id:m.toolCallId,name:m.toolName},m,i);continue;}
      const row=render(m,i);if(['user','assistant','custom'].includes(row.role)&&(row.text||row.images.length))result.push(row);
      if(Array.isArray(m.content))for(const b of m.content)if(b.type==='toolCall')tool(b,results.get(b.id),i);
    }
    if(result.length>400)truncated=true;
    return {messages:result.slice(-400),truncated};
  }
  const json=(res,status,body)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(body));};
  const server=http.createServer((req,res)=>{
    try{
      const u=new URL(req.url,'http://localhost');
      if(req.method!=='GET')return json(res,405,{error:'Method not allowed'});
      if(u.pathname==='/health')return json(res,200,{ok:true});
      if(!authorized(req))return json(res,401,{error:'Unauthorized'});
      if(req.headers.origin)return json(res,403,{error:'Browser origins are not allowed'});
      if(u.pathname==='/api/catalog')return json(res,200,catalog());
      const m=u.pathname.match(/^\/api\/sessions\/([^/]+)(?:\/media\/([a-f0-9]{64}))?$/);
      if(m){const id=decodeURIComponent(m[1]),s=sessions.get(id);if(!s)return json(res,404,{error:'Session not found'});
        if(m[2]){const b=media.get(id+'/'+m[2]);if(!b)return json(res,404,{error:'Image expired or unavailable'});res.writeHead(200,{'Content-Type':b.mimeType,'Content-Length':b.data.length,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});return res.end(b.data);}
        return json(res,200,snapshot(s));
      }
      json(res,404,{error:'Not found'});
    }catch{json(res,400,{error:'Bad request'});}
  });
  server.on('upgrade',(req,socket,head)=>{
    if(req.url!=='/ws'||!authorized(req)||req.headers.origin||wss.clients.size>=8){socket.end('HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n');return;}
    wss.handleUpgrade(req,socket,head,ws=>wss.emit('connection',ws,req));
  });
  function complete(requestId,ack){
    const p=pending.get(requestId);if(!p)return;
    clearTimeout(p.timer);pending.delete(requestId);
    const result={type:'ack',requestId,sessionId:p.sessionId,...ack};receipts.set(requestId,{result,hash:p.hash});
    if(receipts.size>1000)receipts.delete(receipts.keys().next().value);
    for(const ws of p.clients)send(ws,result);
  }
  wss.on('connection',ws=>{
    send(ws,catalog());ws.alive=true;ws.on('pong',()=>ws.alive=true);ws.on('error',()=>{});
    let recent=[];
    ws.on('message',raw=>{
      let c;try{
        c=JSON.parse(raw);if(c.type==='subscribe'){const s=sessions.get(c.sessionId);if(!s)throw Error('Session not found');ws.sessionId=c.sessionId;send(ws,snapshot(s));return;}
        if(c.type!=='command')throw Error('Unknown message');
        c=validateCommand(c);const fingerprint=hash(JSON.stringify(c));
        const receipt=receipts.get(c.requestId);if(receipt){if(receipt.hash!==fingerprint)throw Error('Request ID reused');send(ws,receipt.result);return;}
        const inflight=pending.get(c.requestId);if(inflight){if(inflight.hash!==fingerprint)throw Error('Request ID reused');inflight.clients.add(ws);return;}
        recent=recent.filter(t=>Date.now()-t<60000);if(recent.length>=30)throw Error('Too many commands');recent.push(Date.now());
        const s=sessions.get(c.sessionId);if(!s?.socket||s.socket.destroyed)throw Error('Pi offline: reload mobile extension in terminal');
        if(pending.size>=100)throw Error('Too many pending commands');
        if(s.socket.writableLength>MAX_FRAME)throw Error('Pi connection is congested');
        const timer=setTimeout(()=>complete(c.requestId,{ok:false,error:'Acceptance unknown: no automatic retry; inspect chat before sending again'}),15000);timer.unref();
        pending.set(c.requestId,{clients:new Set([ws]),sessionId:c.sessionId,timer,hash:fingerprint,socket:s.socket});
        s.socket.write(JSON.stringify(c)+'\n');
      }catch(e){send(ws,{type:'ack',requestId:c?.requestId,sessionId:c?.sessionId,ok:false,error:e.message});}
    });
  });
  const socketPath=path.join(dataDir,'bridge.sock');
  // Never unlink a live instance's socket.
  if(fs.existsSync(socketPath)){
    const live=await new Promise(resolve=>{const s=net.connect(socketPath);s.on('connect',()=>{s.destroy();resolve(true)});s.on('error',()=>resolve(false));});
    if(live)throw Error('Gateway already running');fs.unlinkSync(socketPath);
  }
  const bridge=net.createServer(socket=>{
    bridges.add(socket);let buffer='',id=null;socket.setEncoding('utf8');socket.on('error',()=>{});
    socket.on('data',chunk=>{
      buffer+=chunk;if(Buffer.byteLength(buffer)>MAX_FRAME){socket.destroy();return;}
      let pos;while((pos=buffer.indexOf('\n'))!==-1){const line=buffer.slice(0,pos);buffer=buffer.slice(pos+1);try{
        const packet=JSON.parse(line);
        if(packet.type==='register'){
          const m=packet.meta;if(!m||!safeId(m.id)||typeof m.cwd!=='string'||m.cwd.length>4000||!Array.isArray(packet.messages))throw Error('Bad registration');
          const existing=sessions.get(m.id);if(existing?.socket&&existing.socket!==socket)throw Error('Duplicate live session owner');
          if(!existing&&sessions.size>=100)throw Error('Too many sessions');
          if(id&&id!==m.id){const prior=sessions.get(id);if(prior?.socket===socket){prior.socket=null;broadcastSnapshot(prior);}for(const [rid,p]of pending)if(p.socket===socket)complete(rid,{ok:false,error:'Session changed; acceptance unknown'});}
          id=m.id;
          const meta={id,title:String(m.title||'').slice(0,200),cwd:m.cwd,terminalId:String(m.terminalId||''),workspaceId:String(m.workspaceId||''),model:String(m.model||'')};
          const normalized=normalize(id,packet.messages);
          const s={meta,configuration:config(m),socket,status:packet.status==='running'?'running':'idle',...normalized,truncated:!!packet.truncated||normalized.truncated};sessions.set(id,s);broadcastCatalog();broadcastSnapshot(s);
        }else if(packet.type==='snapshot'){
          if(!id||packet.id!==id||!Array.isArray(packet.messages))throw Error('Wrong session');const s=sessions.get(id);if(s?.socket!==socket)throw Error('Wrong owner');
          const old=s.status,previous=s.messages; const normalized=normalize(id,packet.messages);Object.assign(s,normalized,{status:packet.status==='running'?'running':'idle',truncated:!!packet.truncated||normalized.truncated});
          if(packet.meta?.title!==undefined)s.meta.title=String(packet.meta.title).slice(0,200);
          if(packet.meta?.model!==undefined)s.meta.model=String(packet.meta.model).slice(0,200);
          const nextConfig=config(packet.meta),configChanged=JSON.stringify(nextConfig)!==JSON.stringify(s.configuration);
          s.configuration=nextConfig;
          broadcastSnapshot(s,previous,configChanged);if(old!==s.status||configChanged)broadcastCatalog();
        }else if(packet.type==='ack'){
          const p=pending.get(packet.requestId);if(p?.socket===socket&&p.sessionId===id)complete(packet.requestId,{ok:packet.ok===true,...(packet.error?{error:String(packet.error).slice(0,500)}:{})});
        }else throw Error('Unknown bridge frame');
      }catch{socket.destroy();return;}}
    });
    socket.on('close',()=>{bridges.delete(socket);if(id){const s=sessions.get(id);if(s?.socket===socket){s.socket=null;broadcastSnapshot(s);broadcastCatalog();}}
      for(const [rid,p]of pending)if(p.socket===socket)complete(rid,{ok:false,error:'Pi disconnected: acceptance unknown; inspect chat before retry'});
    });
  });
  await new Promise((resolve,reject)=>{bridge.once('error',reject);bridge.listen(socketPath,resolve);});fs.chmodSync(socketPath,0o600);
  try{await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,host,resolve);});}catch(e){bridge.close();throw e;}
  let refreshing=false;
  async function refresh(){if(!orca||refreshing||closed)return;refreshing=true;try{inventory=await readOrca();inventoryError=null;}catch{inventoryError='Orca unavailable; showing last known inventory';}finally{refreshing=false;if(!closed)broadcastCatalog();}}
  await refresh();const poll=setInterval(refresh,10000);poll.unref();
  const heartbeat=setInterval(()=>{for(const ws of wss.clients){if(!ws.alive){ws.terminate();continue;}ws.alive=false;ws.ping();}},30000);heartbeat.unref();
  return {port:server.address().port,socketPath,token,catalog,async close(){closed=true;clearInterval(poll);clearInterval(heartbeat);for(const p of pending.values())clearTimeout(p.timer);for(const ws of wss.clients)ws.terminate();for(const s of bridges)s.destroy();await Promise.all([new Promise(r=>server.close(r)),new Promise(r=>bridge.close(r))]);wss.close();}};
}
