import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm,stat} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import net from 'node:net';
import {once} from 'node:events';
import {WebSocket} from 'ws';
import {createGateway,validateCommand,decodeImage} from '../server/gateway.mjs';
const png='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
const image={type:'image',data:png,mimeType:'image/png'};
const pause=()=>new Promise(r=>setTimeout(r,40));
function inbox(emitter,ev='message'){const queue=[],wait=[];emitter.on(ev,b=>{let v;try{v=JSON.parse(String(b))}catch{return;}const p=wait.shift();if(p)p(v);else queue.push(v);});return async(predicate=()=>true)=>{for(let i=0;i<100;i++){const v=queue.length?queue.shift():await new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(Error('message timeout')),3000);wait.push(v=>{clearTimeout(timeout);resolve(v)});});if(predicate(v))return v;}throw Error('Not found');};}
async function fixture(t){const dir=await mkdtemp(path.join(tmpdir(),'pim-'));const g=await createGateway({dataDir:dir,port:0,orca:false});t.after(async()=>{await g.close();await rm(dir,{recursive:true,force:true})});const base=`http://127.0.0.1:${g.port}`,headers={Authorization:`Bearer ${g.token}`};return {g,dir,base,headers};}
async function bridge(g,id){const s=net.connect(g.socketPath);await once(s,'connect');s.write(JSON.stringify({type:'register',meta:{id,title:id,cwd:'/tmp/'+id,terminalId:id,workspaceId:'workspace-'+id},messages:[{role:'user',content:'history '+id,timestamp:1},{role:'toolResult',toolName:'read',toolCallId:'img',content:[image]}],status:'idle'})+'\n');await pause();return s;}
test('image validation rejects MIME mismatch and unsupported formats',()=>{assert.ok(decodeImage(image).length);assert.throws(()=>decodeImage({...image,mimeType:'image/jpeg'}));assert.throws(()=>decodeImage({...image,data:'bad'}));assert.throws(()=>validateCommand({sessionId:'s',requestId:'r',command:'prompt',text:'',images:[]}));assert.throws(()=>validateCommand({sessionId:'../x',requestId:'r',command:'abort'}));});
test('auth HTTP/WS, private files, origin check, image scoping and bounds',async t=>{const {g,dir,base,headers}=await fixture(t);assert.equal((await stat(dir)).mode&0o777,0o700);assert.equal((await stat(g.socketPath)).mode&0o777,0o600);assert.equal((await fetch(base+'/api/catalog')).status,401);assert.equal((await fetch(base+'/health')).status,200);assert.equal((await fetch(base+'/api/catalog',{headers:{...headers,Origin:'https://evil.test'}})).status,403);
 await bridge(g,'s1');await bridge(g,'s2');const cat=await (await fetch(base+'/api/catalog',{headers})).json();assert.equal(cat.sessions.length,2);assert.equal(cat.workspaces.length,2);
 const snap=await (await fetch(base+'/api/sessions/s1',{headers})).json();const url=snap.messages[1].images[0].url;assert.equal((await fetch(base+url)).status,401);assert.equal((await fetch(base+url,{headers})).status,200);assert.equal((await fetch(base+'/api/sessions/s1/media/'+'0'.repeat(64),{headers})).status,404);
 const unauth=new WebSocket(base.replace('http','ws')+'/ws');const response=await new Promise(resolve=>{unauth.on('unexpected-response',(_q,r)=>{r.resume();unauth.terminate();resolve(r.statusCode)});unauth.on('error',()=>{});});assert.equal(response,401);
});
test('commands route to one owner, deduplicate, reject offline and update subscription',async t=>{const {g,base,headers}=await fixture(t);const a=await bridge(g,'a'),b=await bridge(g,'b');let countA=0,countB=0;a.on('data',raw=>{for(const line of String(raw).trim().split('\n')){const c=JSON.parse(line);countA++;a.write(JSON.stringify({type:'ack',requestId:c.requestId,ok:true})+'\n');}});b.on('data',()=>countB++);
 const ws=new WebSocket(base.replace('http','ws')+'/ws',{headers});const next=inbox(ws);await once(ws,'open');await next(x=>x.type==='catalog');ws.send(JSON.stringify({type:'subscribe',sessionId:'a'}));assert.equal((await next(x=>x.type==='snapshot')).sessionId,'a');
 const c={type:'command',command:'prompt',sessionId:'a',requestId:'r1',text:'hello',images:[image]};ws.send(JSON.stringify(c));assert.equal((await next(x=>x.type==='ack')).ok,true);ws.send(JSON.stringify(c));assert.equal((await next(x=>x.type==='ack')).ok,true);assert.equal(countA,1);assert.equal(countB,0);
 ws.send(JSON.stringify({...c,text:'changed'}));assert.equal((await next(x=>x.type==='ack')).ok,false);
 a.write(JSON.stringify({type:'snapshot',id:'a',messages:[{role:'assistant',content:'streaming',timestamp:4}],status:'running'})+'\n');assert.equal((await next(x=>x.type==='messages')).messages[0].text,'streaming');
 a.destroy();await next(x=>x.type==='snapshot'&&!x.connected);ws.send(JSON.stringify({...c,requestId:'r2'}));assert.equal((await next(x=>x.type==='ack')).ok,false);ws.close();
});
test('history pagination cannot exhaust prompt budget and two images reach the owner',async t=>{
 const {g,base,headers}=await fixture(t),owner=await bridge(g,'pages');let captured;
 let buffer='';owner.on('data',raw=>{buffer+=String(raw);let end;while((end=buffer.indexOf('\n'))>=0){const c=JSON.parse(buffer.slice(0,end));buffer=buffer.slice(end+1);if(c.command==='prompt')captured=c;owner.write(JSON.stringify({type:'ack',requestId:c.requestId,ok:true})+'\n');}});
 const ws=new WebSocket(base.replace('http','ws')+'/ws',{headers}),next=inbox(ws);await once(ws,'open');t.after(()=>ws.close());
 async function send(command,requestId,extra={}){ws.send(JSON.stringify({type:'command',command,sessionId:'pages',requestId,...(command==='history'?{before:'user:1'}:{}),...extra}));return next(x=>x.type==='ack'&&x.requestId===requestId);}
 for(let i=0;i<120;i++)assert.equal((await send('history','history-'+i)).ok,true);
 assert.equal((await send('history','history-over-limit')).ok,false);
 assert.equal((await send('prompt','pictures',{text:'Two references',images:[image,image]})).ok,true);
 assert.equal(captured.images.length,2);assert.deepEqual(captured.images,[image,image]);
 assert.equal((await send('abort','stop-after-history')).ok,true);
});
test('conditional subscribe sends only edits and new rows, restart epoch falls back to snapshot',async t=>{
 const {g,base,headers}=await fixture(t),owner=await bridge(g,'cached');
 const open=async()=>{const ws=new WebSocket(base.replace('http','ws')+'/ws',{headers}),next=inbox(ws);await once(ws,'open');t.after(()=>ws.close());await next(f=>f.type==='catalog');return {ws,next};};
 const first=await open();first.ws.send(JSON.stringify({type:'subscribe',sessionId:'cached'}));const initial=await first.next(f=>f.type==='snapshot');assert.ok(initial.checkpoint);first.ws.close();
 owner.write(JSON.stringify({type:'snapshot',id:'cached',epoch:0,messages:[{role:'user',content:'history cached',timestamp:1},{role:'assistant',content:'new answer',timestamp:4}],status:'idle'})+'\n');await pause();
 const second=await open();second.ws.send(JSON.stringify({type:'subscribe',sessionId:'cached',resume:initial.checkpoint}));const delta=await second.next(f=>f.type==='messages');assert.equal(delta.resumed,true);assert.equal(delta.messages.length,1);assert.equal(delta.messages[0].text,'new answer');assert.deepEqual(delta.removedIds,['tool:img']);
 second.ws.send(JSON.stringify({type:'subscribe',sessionId:'cached',resume:delta.checkpoint}));assert.equal((await second.next(f=>f.type==='messages')).messages.length,0);
 second.ws.send(JSON.stringify({type:'subscribe',sessionId:'cached',resume:{...delta.checkpoint,epoch:999}}));assert.equal((await second.next(f=>f.type==='snapshot')).messages.length,2);
});
test('duplicate live owner cannot steal a session; another gateway cannot unlink socket',async t=>{const {g,dir,base,headers}=await fixture(t);const original=await bridge(g,'same');const bad=net.connect(g.socketPath);await once(bad,'connect');bad.write(JSON.stringify({type:'register',meta:{id:'same',cwd:'/evil'},messages:[]})+'\n');await once(bad,'close');assert.equal(original.destroyed,false);const cat=await(await fetch(base+'/api/catalog',{headers})).json();assert.equal(cat.sessions[0].cwd,'/tmp/same');await assert.rejects(createGateway({dataDir:dir,port:0,orca:false}),/already running/);});
