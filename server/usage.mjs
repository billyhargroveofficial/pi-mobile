import fs from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';

const providers=['codex','cursor','grok'];
const number=v=>typeof v==='number'&&Number.isFinite(v)&&v>=0?v:null;
function window(value,name){
 if(!value||typeof value!=='object'||number(value.usedPercent)===null)return null;
 return {name,usedPercent:value.usedPercent,windowMinutes:number(value.windowMinutes),resetsAt:number(value.resetsAt)};
}
/** Strict allowlist: no accounts, credentials, raw provider errors or auth metadata cross the gateway. */
export function usageSnapshot(raw,observedAt=Date.now()){
 const limits=raw?.rateLimits;
 return {type:'usage',observedAt,providers:providers.map(provider=>{
  const source=limits?.[provider];
  const windows=['session','weekly','monthly'].map(key=>window(source?.[key],key)).filter(Boolean);
  if(Array.isArray(source?.buckets))source.buckets.slice(0,12).forEach((b,i)=>{const w=window(b,typeof b?.name==='string'?b.name.slice(0,80):'Bucket '+(i+1));if(w)windows.push(w);});
  const status=['ok','error','loading','unavailable','stale','unauthenticated','rate_limited'].includes(source?.status)?source.status:windows.length?'ok':'unavailable';
  return {provider,status,updatedAt:number(source?.updatedAt),windows};
 })};
}
export async function readUsage({runtimeFile=process.env.PI_MOBILE_ORCA_RUNTIME_FILE||path.join(os.homedir(),process.platform==='darwin'?'Library/Application Support/Orca':'.config/Orca','orca-runtime.json')}={}){
 const meta=JSON.parse(await fs.readFile(runtimeFile,'utf8'));
 const endpoint=meta.transports?.find(t=>t.kind==='unix')?.endpoint;
 if(typeof endpoint!=='string'||!path.isAbsolute(endpoint)||typeof meta.authToken!=='string')throw Error('Orca usage unavailable');
 const id=crypto.randomUUID();
 const result=await new Promise((resolve,reject)=>{
  const socket=net.connect(endpoint);let buffer='',done=false;
  const finish=(error,value)=>{if(done)return;done=true;clearTimeout(deadline);socket.destroy();error?reject(Error('Orca usage unavailable')):resolve(value);};
  const deadline=setTimeout(()=>finish(true),5000);deadline.unref();
  socket.on('error',()=>finish(true));socket.on('close',()=>{if(!done)finish(true);});
  // Read cached account usage only. Do not refresh credentials, select accounts or spend reset credits.
  socket.on('connect',()=>socket.write(JSON.stringify({id,method:'accounts.list',params:{refreshUsage:false},authToken:meta.authToken})+'\n'));
  socket.on('data',chunk=>{buffer+=chunk;if(Buffer.byteLength(buffer)>2*1024*1024)return finish(true);let end;while((end=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,end);buffer=buffer.slice(end+1);try{const response=JSON.parse(line);if(response.id===id)finish(!response.ok,response.result);}catch{return finish(true);}}});
 });
 return usageSnapshot(result);
}
export function cachedUsage(reader=readUsage,ttl=30000){
 let cached=null,expires=0,inflight=null;
 return async()=>{if(cached&&Date.now()<expires)return cached;if(!inflight)inflight=Promise.resolve().then(reader).catch(()=>usageSnapshot(null)).then(value=>{cached=value;expires=Date.now()+ttl;return value;}).finally(()=>{inflight=null;});return inflight;};
}
