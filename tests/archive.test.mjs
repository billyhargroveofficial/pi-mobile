import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
import net from 'node:net';import {once} from 'node:events';import {WebSocket} from 'ws';
import {Archive} from '../server/archive.mjs';import {visibleTerminals} from '../server/orca.mjs';import {createGateway,validateCommand} from '../server/gateway.mjs';
const w={id:'work',path:'/project',name:'Project',hostId:'local'};
function fixture(t){
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pi-archive-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const file=path.join(dir,'session.jsonl');fs.writeFileSync(file,JSON.stringify({type:'session',id:'saved',cwd:'/project'})+'\n');
 const row={id:'saved',cwd:'/project',path:file,title:'Saved',modified:'2026-10-05',messageCount:2};
 const inv={workspaces:[w],terminals:[],allTerminalIds:[],omittedHostIds:[],truncated:false};let owners=[],calls=0;
 const opts={dataDir:dir,inventory:async()=>inv,owners:()=>owners,list:async()=>[row],launch:async()=>{calls++;return 'term-new';}};
 return {dir,row,inv,opts,get calls(){return calls;},setOwners(value){owners=value;}};
}
test('visible Orca tabs exclude orphan/background/disconnected terminals, include inactive panes',()=>{
 const terminals=['shown','inactive','orphan','background','dead'].map(handle=>({handle,connected:handle!=='dead',orphaned:handle==='orphan'}));
 const visualLayouts=[{root:{type:'group',tabs:[{panes:{type:'split',children:['shown','inactive','orphan','dead'].map(handle=>({type:'terminal',handle}))}}]}}];
 assert.deepEqual(visibleTerminals({terminals,visualLayouts}).map(t=>t.handle),['shown','inactive']);assert.throws(()=>visibleTerminals({terminals}));
});
test('archive pages are separate, omit live owners and do not expose launch paths',async t=>{const f=fixture(t),a=new Archive(f.opts);const p=await a.page();assert.equal(p.sessions.length,1);assert.equal(p.sessions[0].path,undefined);f.setOwners([{id:'saved'}]);assert.equal((await a.page()).sessions.length,0);});
test('resume is single-flight and durable; existing owner is reused',async t=>{
 const f=fixture(t),a=new Archive(f.opts);const [x,y]=await Promise.all([a.resume('saved'),a.resume('saved')]);assert.deepEqual(x,y);assert.equal(f.calls,1);
 f.inv.allTerminalIds=['term-new'];const restarted=new Archive(f.opts);assert.equal((await restarted.resume('saved')).reused,true);assert.equal(f.calls,1);
 f.inv.terminals=[{id:'term-new',workspaceId:'work'}];f.setOwners([{id:'saved',terminalId:'term-new'}]);assert.equal((await restarted.resume('saved')).reused,true);assert.equal(f.calls,1);
});
test('closed terminal can resume again after complete inventory proves absence',async t=>{const f=fixture(t),a=new Archive(f.opts);await a.resume('saved');a.intents.saved.startedAt-=60000;await a.resume('saved');assert.equal(f.calls,2);});
test('terminal that switched to another session does not trap the old resume intent',async t=>{
 const f=fixture(t),a=new Archive(f.opts);await a.resume('saved');f.inv.allTerminalIds=['term-new'];f.inv.terminals=[{id:'term-new',workspaceId:'work'}];f.setOwners([{id:'another',terminalId:'term-new'}]);await a.resume('saved');assert.equal(f.calls,2);
});
test('unknown create result remains fenced across restart',async t=>{const f=fixture(t);f.opts.launch=async()=>{throw Error('timeout');};await assert.rejects(new Archive(f.opts).resume('saved'),/timeout/);await assert.rejects(new Archive(f.opts).resume('saved'),/неизвестен/);});
test('unbridged Pi, incomplete inventory and owner outside tabs cannot be duplicated',async t=>{
 const f=fixture(t),a=new Archive(f.opts);f.inv.terminals=[{id:'other',workspaceId:'work'}];await assert.rejects(a.resume('saved'),/без моста/);
 f.inv.terminals=[];f.inv.truncated=true;await assert.rejects(a.resume('saved'),/неполный/);f.inv.truncated=false;
 f.setOwners([{id:'saved',terminalId:'hidden'}]);await assert.rejects(a.resume('saved'),/вне открытой/);assert.equal(f.calls,0);
});
test('unknown ids, modified session headers and arbitrary command fields never launch',async t=>{
 const f=fixture(t),a=new Archive(f.opts);await assert.rejects(a.resume('../../evil'),/не найден/);fs.writeFileSync(f.row.path,JSON.stringify({type:'session',id:'other',cwd:'/project'})+'\n');await assert.rejects(a.resume('saved'),/identity changed/);assert.equal(f.calls,0);
 assert.deepEqual(validateCommand({requestId:'r',sessionId:'saved',command:'resume',path:'/evil',text:'rm'}),{type:'command',requestId:'r',sessionId:'saved',command:'resume'});
});
test('gateway exposes only open tabs and authenticated paged archive',async t=>{
 const f=fixture(t);f.inv.terminals=[{id:'t',workspaceId:'work',title:'Pi',agent:'pi'}];f.inv.workspaces.push({id:'empty',name:'Empty',path:'/empty',hostId:'remote'});
 const g=await createGateway({dataDir:f.dir,port:0,orcaReader:async()=>f.inv,archiveList:f.opts.list,archiveLaunch:f.opts.launch});t.after(()=>g.close());
 const base='http://127.0.0.1:'+g.port,headers={Authorization:'Bearer '+g.token};assert.equal((await fetch(base+'/api/archive')).status,401);
 const p=await(await fetch(base+'/api/archive',{headers})).json();assert.equal(p.sessions.length,1);assert.equal(p.sessions[0].path,undefined);
 assert.equal(g.catalog().workspaces.length,2,'empty workspaces remain available for the new-session button');assert.equal(g.catalog().terminals.length,1);
});

test('catalog drops old session on same terminal and closed-tab owners; WS resume deduplicates',async t=>{
 const f=fixture(t);f.inv.terminals=[{id:'open',workspaceId:'work',agent:'pi'}];f.inv.allTerminalIds=['open'];
 const g=await createGateway({dataDir:f.dir,port:0,orcaReader:async()=>f.inv,archiveList:f.opts.list,archiveLaunch:f.opts.launch});t.after(()=>g.close());
 const bridge=net.connect(g.socketPath);await once(bridge,'connect');t.after(()=>bridge.destroy());
 const register=(id,terminalId)=>bridge.write(JSON.stringify({type:'register',meta:{id,cwd:'/project',terminalId,workspaceId:'work'},messages:[],status:'idle'})+'\n');
 const wait=()=>new Promise(r=>setTimeout(r,25));register('old','open');await wait();assert.equal(g.catalog().sessions[0].id,'old');
 register('current','open');await wait();assert.deepEqual(g.catalog().sessions.map(s=>s.id),['current']);assert.equal(g.catalog().terminals.length,0);
 register('background','hidden');await wait();assert.equal(g.catalog().sessions.length,0);assert.equal(g.catalog().terminals.length,1);
 bridge.destroy();await wait();f.inv.terminals=[];f.inv.allTerminalIds=[];
 const ws=new WebSocket(`ws://127.0.0.1:${g.port}/ws`,{headers:{Authorization:'Bearer '+g.token}});await once(ws,'open');t.after(()=>ws.close());
 const acks=[];const completed=new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('missing ack')),2000);ws.on('message',raw=>{const p=JSON.parse(raw);if(p.type==='ack'){acks.push(p);if(acks.length===2){clearTimeout(timer);resolve();}}});});
 const command={type:'command',command:'resume',sessionId:'saved',requestId:'repeat'};ws.send(JSON.stringify(command));ws.send(JSON.stringify({...command,requestId:'repeat-2'}));await completed;
 assert.equal(f.calls,1);assert.equal(acks[0].data.terminalId,acks[1].data.terminalId);assert.equal(acks[0].ok,true);assert.equal(acks[0].data.terminalId,'term-new');
});
