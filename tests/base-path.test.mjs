import test from 'node:test';import assert from 'node:assert/strict';import {mkdtemp,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import {WebSocket} from 'ws';import {createGateway} from '../server/gateway.mjs';
test('mounted relay requires prefix for REST and WSS and still enforces auth',async()=>{
 const dir=await mkdtemp(path.join(os.tmpdir(),'pi-mobile-prefix-'));let g,ws;
 try{g=await createGateway({dataDir:dir,port:0,orca:false,basePath:'/pi-mobile'});let base='http://127.0.0.1:'+g.port;
 assert.equal((await fetch(base+'/health')).status,404);assert.equal((await fetch(base+'/pi-mobile/health')).status,200);
 assert.equal((await fetch(base+'/pi-mobile/api/catalog')).status,401);
 assert.equal((await fetch(base+'/pi-mobile/api/catalog',{headers:{Authorization:'Bearer '+g.token}})).status,200);
 const packet=await new Promise((resolve,reject)=>{ws=new WebSocket('ws://127.0.0.1:'+g.port+'/pi-mobile/ws',{headers:{Authorization:'Bearer '+g.token}});ws.once('message',v=>resolve(JSON.parse(v)));ws.once('error',reject);});assert.equal(packet.type,'catalog');
 }finally{ws?.terminate();if(g)await g.close();await rm(dir,{recursive:true,force:true});}
});
