// Explicitly scoped live test. Never target a user's working conversation.
import fs from 'node:fs';import os from 'node:os';import {WebSocket} from 'ws';import {randomUUID} from 'node:crypto';import {once} from 'node:events';
const id=process.argv[2];if(!id)throw Error('Supply the dedicated UI verification session ID');
const base='https://billyhargrove.ru',headers={Authorization:'Bearer '+fs.readFileSync(os.homedir()+'/.pi/agent/pi-mobile/device-token','utf8').trim()};
const catalog=await(await fetch(base+'/api/catalog',{headers})).json();
if(!catalog.sessions.some(s=>s.id===id&&s.title==='Pi Mobile UI verification'&&s.connected&&s.status==='idle'))throw Error('Not the dedicated idle test session');
const get=async()=>await(await fetch(base+'/api/sessions/'+id,{headers})).json();
const initial=await get(),original=initial.configuration;
const ws=new WebSocket(base.replace('https:','wss:')+'/ws',{headers});await once(ws,'open');
let runningTool=false,updates=0;const pending=new Map();
ws.on('message',raw=>{const p=JSON.parse(raw);if(p.type==='ack')pending.get(p.requestId)?.(p);for(const m of p.messages||[])if(m.role==='toolResult'&&m.toolStatus==='running'){runningTool=true;updates++;}});
ws.send(JSON.stringify({type:'subscribe',sessionId:id}));
async function command(data){const requestId=randomUUID();const reply=new Promise((resolve,reject)=>{const timer=setTimeout(()=>{pending.delete(requestId);reject(Error('Ack timeout'))},20000);pending.set(requestId,p=>{clearTimeout(timer);pending.delete(requestId);p.ok?resolve(p):reject(Error(p.error));});});ws.send(JSON.stringify({type:'command',sessionId:id,requestId,...data}));await reply;}
const current=original.models.find(m=>m.provider+'/'+m.id===original.model);
const alternative=original.models.find(m=>m.provider===current.provider&&m.id!==current.id);
try{
 if(alternative){await command({command:'configure',provider:alternative.provider,modelId:alternative.id,thinkingLevel:alternative.thinkingLevels.includes('low')?'low':alternative.thinkingLevels[0]});await new Promise(r=>setTimeout(r,250));const switched=await get();if(switched.configuration.model!==alternative.provider+'/'+alternative.id)throw Error('Model did not switch');console.log('Model switch verified:',switched.configuration.model);}
 await command({command:'configure',provider:current.provider,modelId:current.id,thinkingLevel:'minimal'});
 await command({command:'prompt',text:'Mobile UI verification only. Run this harmless bash command exactly: for n in 1 2 3; do echo "MOBILE_TOOL_PROGRESS_$n"; sleep 1; done . Then use read to open /Users/billy/repos/pi-mobile/artifacts/green-square.png . Do not change any files or run any other commands. Finish with UI_FEATURES_OK.',behavior:'followUp'});
 const deadline=Date.now()+180000;let complete=false;
 while(Date.now()<deadline){await new Promise(r=>setTimeout(r,1000));const s=await get();if(s.status==='idle'&&s.messages.some(m=>m.role==='assistant'&&m.text.includes('UI_FEATURES_OK'))){console.log(JSON.stringify({sameSession:id,runningTool,streamUpdates:updates,toolResult:s.messages.some(m=>m.role==='toolResult'&&m.toolStatus==='done'),imageResult:s.messages.some(m=>m.role==='toolResult'&&m.images.length),finalMarker:true}));complete=true;break;}}
 if(!complete)throw Error('Timed out waiting for test response');
}finally{
 const latest=await get();if(latest.status==='idle')await command({command:'configure',provider:current.provider,modelId:current.id,thinkingLevel:original.thinkingLevel});
 ws.close();
}
