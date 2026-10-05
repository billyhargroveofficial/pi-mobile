import {test} from 'node:test';
import assert from 'node:assert/strict';
import {usageSnapshot,cachedUsage,readUsage} from '../server/usage.mjs';
import {createGateway} from '../server/gateway.mjs';
import {mkdtemp,rm,writeFile} from 'node:fs/promises';
import os from 'node:os';import path from 'node:path';import net from 'node:net';import {once} from 'node:events';

test('usage allowlist exposes only requested provider windows, not account secrets or raw errors',()=>{
 const raw={accounts:{token:'SECRET'},rateLimits:{codex:{status:'ok',updatedAt:123,weekly:{usedPercent:42,windowMinutes:10080,resetsAt:900,secret:'SECRET'}},cursor:{monthly:{usedPercent:12},buckets:[{name:'Auto',usedPercent:5}]},grok:{status:'error',error:'SECRET',authToken:'SECRET'},claude:{session:{usedPercent:50}}}};
 const snapshot=usageSnapshot(raw,1000);assert.equal(snapshot.providers.length,3);assert.equal(snapshot.providers[0].windows[0].usedPercent,42);assert.equal(snapshot.providers[1].windows.length,2);assert.equal(snapshot.providers[2].status,'error');assert.doesNotMatch(JSON.stringify(snapshot),/SECRET|claude|accounts|authToken/);
});
test('unknown usage never becomes a fabricated zero; malformed windows are discarded',()=>{
 assert.deepEqual(usageSnapshot(null).providers.map(p=>p.windows),[[],[],[]]);
 const p=usageSnapshot({rateLimits:{codex:{weekly:{usedPercent:'25'},session:{usedPercent:NaN},monthly:{usedPercent:-1}}}}).providers[0];assert.equal(p.windows.length,0);assert.equal(p.status,'unavailable');
});
test('usage reader cache is single-flight and failure is explicit',async()=>{
 let calls=0;const load=cachedUsage(async()=>{calls++;return usageSnapshot(null,12);});const values=await Promise.all([load(),load(),load()]);assert.equal(calls,1);assert.equal(values[0].observedAt,12);await load();assert.equal(calls,1);
 const bad=cachedUsage(async()=>{throw Error('secret');});assert.equal((await bad()).providers[0].status,'unavailable');
});
test('local RPC requests cached usage without mutating accounts and sanitizes response',async t=>{
 const dir=await mkdtemp(path.join(os.tmpdir(),'usage-rpc-'));const endpoint=path.join(dir,'rpc.sock'),runtimeFile=path.join(dir,'runtime.json');let request;
 const server=net.createServer(socket=>{let buffer='';socket.on('data',chunk=>{buffer+=chunk;if(!buffer.includes('\n'))return;request=JSON.parse(buffer);socket.end(JSON.stringify({id:request.id,ok:true,result:{rateLimits:{codex:{weekly:{usedPercent:35}},grok:{error:'SECRET'}}}})+'\n');});});server.listen(endpoint);await once(server,'listening');
 t.after(async()=>{await new Promise(r=>server.close(r));await rm(dir,{recursive:true,force:true});});await writeFile(runtimeFile,JSON.stringify({authToken:'PRIVATE',transports:[{kind:'unix',endpoint}]}));
 const result=await readUsage({runtimeFile});assert.equal(request.method,'accounts.list');assert.deepEqual(request.params,{refreshUsage:false});assert.equal(request.authToken,'PRIVATE');assert.equal(result.providers[0].windows[0].usedPercent,35);assert.doesNotMatch(JSON.stringify(result),/PRIVATE|SECRET/);
});
test('usage endpoint is authenticated and rejects browser origins',async t=>{
 const dir=await mkdtemp(path.join(os.tmpdir(),'usage-api-'));const g=await createGateway({dataDir:dir,port:0,orca:false});t.after(async()=>{await g.close();await rm(dir,{recursive:true,force:true});});const url=`http://127.0.0.1:${g.port}/api/usage`,headers={Authorization:'Bearer '+g.token};
 assert.equal((await fetch(url)).status,401);assert.equal((await fetch(url,{headers:{...headers,Origin:'https://example.test'}})).status,403);const result=await(await fetch(url,{headers})).json();assert.equal(result.type,'usage');assert.equal(result.providers.length,3);assert.equal(result.providers[0].status,'unavailable');
});
