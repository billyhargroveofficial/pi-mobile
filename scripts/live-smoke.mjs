// Explicit opt-in test against a separately created, named interactive Pi session.
import fs from 'node:fs';
import path from 'node:path';
import {homedir} from 'node:os';
import {WebSocket} from 'ws';
const target=process.argv[2];if(!target)throw Error('Usage: node scripts/live-smoke.mjs <test-session-id>');
const token=fs.readFileSync(path.join(homedir(),'.pi/agent/pi-mobile/device-token'),'utf8').trim();
const base=process.env.PI_MOBILE_TEST_URL||'http://127.0.0.1:8788';
const ws=new WebSocket(base.replace(/^http/,'ws')+'/ws',{headers:{Authorization:'Bearer '+token}});
const requestId=crypto.randomUUID();const imagePath=path.resolve('artifacts/green-square.png');
const timer=setTimeout(()=>{console.error('Timed out: inspect test chat; do not replay blindly');ws.terminate();process.exitCode=1;},90000);
let sent=false,ack=false,haveImage=false,sawRunning=false,last='';
const messages=new Map();
ws.on('open',()=>ws.send(JSON.stringify({type:'subscribe',sessionId:target})));
ws.on('message',raw=>{
 const p=JSON.parse(raw);
 if(p.type==='ack'&&p.requestId===requestId){ack=p.ok;console.log('Command accepted:',ack,p.error||'');}
 if(['snapshot','messages'].includes(p.type)&&p.sessionId===target){
   if(p.type==='snapshot')messages.clear();for(const id of p.removedIds||[])messages.delete(id);for(const m of p.messages||[])messages.set(m.id,m);
   if(!sent){sent=true;ws.send(JSON.stringify({type:'command',command:'prompt',sessionId:target,requestId,text:`Integration test: describe the colour in attached image in one word. Then use read tool to read exactly ${imagePath} so its image appears in tool output. Finish with MOBILE_IMAGE_OK. Do not read any other files.`,images:[{type:'image',mimeType:'image/png',data:fs.readFileSync(imagePath).toString('base64')}]}));return;}
   if(p.status==='running')sawRunning=true;
   haveImage=[...messages.values()].some(m=>m.role==='toolResult'&&m.images?.length);
   last=[...messages.values()].filter(m=>m.role==='assistant').map(m=>m.text).join('\n');
   if(ack&&sawRunning&&p.status==='idle'){
      console.log(JSON.stringify({sawRunning,haveToolImage:haveImage,marker:last.includes('MOBILE_IMAGE_OK'),messageCount:messages.size}));
      clearTimeout(timer);ws.close();if(!haveImage||!last.includes('MOBILE_IMAGE_OK'))process.exitCode=1;
   }
 }
});ws.on('error',e=>{clearTimeout(timer);console.error(e.message);process.exitCode=1;});
