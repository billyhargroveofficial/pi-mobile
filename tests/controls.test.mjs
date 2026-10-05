import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {once} from 'node:events';
import {WebSocket} from 'ws';
import mobile from '../extension/mobile.ts';
import {createGateway,validateCommand} from '../server/gateway.mjs';
const wait=ms=>new Promise(r=>setTimeout(r,ms));

test('configure rejects invalid provider, effort, incomplete or empty payloads',()=>{
 const base={type:'command',command:'configure',sessionId:'session',requestId:'r'};
 for(const invalid of [{},{provider:'test'},{thinkingLevel:'ultra'},{provider:'a\n',modelId:'x'}])assert.throws(()=>validateCommand({...base,...invalid}));
 assert.equal(validateCommand({...base,thinkingLevel:'high'}).thinkingLevel,'high');
 assert.equal(validateCommand({...base,provider:'test',modelId:'model'}).modelId,'model');
});

test('live tool updates keep one stable row; configuration changes only selected idle runtime',async t=>{
 const dir=await mkdtemp(path.join(os.tmpdir(),'pim-controls-')),g=await createGateway({dataDir:dir,port:0,orca:false});process.env.PI_MOBILE_SOCKET=g.socketPath;
 const handlers={},models=[{provider:'test',id:'a',name:'A',api:'openai-responses',reasoning:true},{provider:'test',id:'b',name:'B',api:'openai-responses',reasoning:false}];
 let effort='medium',changed=0,idle=true;
 const ctx={mode:'tui',cwd:'/test',model:models[0],modelRegistry:{getAvailable:()=>models},isIdle:()=>idle,abort:()=>{},sessionManager:{getSessionFile:()=>'/test/session.jsonl',getSessionId:()=> 'control-session',getBranch:()=>[]},ui:{setStatus:()=>{},notify:()=>{}}};
 const pi={events:{on:()=>{}},getCommands:()=>[],on:(n,f)=>handlers[n]=f,registerCommand:()=>{},getSessionName:()=> 'Test',getThinkingLevel:()=>effort,setThinkingLevel:v=>{effort=v;handlers.thinking_level_select({},ctx);},setModel:async m=>{changed++;ctx.model=m;handlers.model_select({},ctx);return true;},sendUserMessage:()=>{}};
 mobile(pi);t.after(async()=>{handlers.session_shutdown({},ctx);delete process.env.PI_MOBILE_SOCKET;await g.close();await rm(dir,{recursive:true,force:true});});handlers.session_start({},ctx);await wait(80);
 const ws=new WebSocket(`ws://127.0.0.1:${g.port}/ws`,{headers:{Authorization:'Bearer '+g.token}});await once(ws,'open');t.after(()=>ws.close());
 const snapshot=async()=>await(await fetch(`http://127.0.0.1:${g.port}/api/sessions/control-session`,{headers:{Authorization:'Bearer '+g.token}})).json();
 async function command(data){const promise=new Promise(resolve=>{const listener=raw=>{const f=JSON.parse(raw);if(f.type==='ack'&&f.requestId===data.requestId){ws.off('message',listener);resolve(f);}};ws.on('message',listener);});ws.send(JSON.stringify({type:'command',sessionId:'control-session',command:'configure',...data}));return promise;}
 let s=await snapshot();assert.equal(s.configuration.models.length,2);assert.deepEqual(s.configuration.models[1].thinkingLevels,['off']);
 assert.equal((await command({requestId:'config-1',provider:'test',modelId:'a',thinkingLevel:'high'})).ok,true);await wait(130);assert.equal(effort,'high');assert.equal((await snapshot()).configuration.thinkingLevel,'high');
 assert.equal((await command({requestId:'config-1',provider:'test',modelId:'a',thinkingLevel:'high'})).ok,true);assert.equal(changed,1,'deduplicates model switch');
 assert.equal((await command({requestId:'config-2',provider:'test',modelId:'missing',thinkingLevel:'off'})).ok,false);
 assert.equal((await command({requestId:'config-3',provider:'test',modelId:'b',thinkingLevel:'high'})).ok,false);assert.equal(changed,1,'invalid combination does not partially switch model');
 idle=false;handlers.agent_start({},ctx);
 assert.equal((await command({requestId:'config-busy',provider:'test',modelId:'b',thinkingLevel:'off'})).ok,false);assert.equal(effort,'high');
 assert.equal((await command({requestId:'effort-busy',thinkingLevel:'low'})).ok,true);assert.equal(effort,'low');assert.equal(idle,false,'effort update must not stop the running agent');
 handlers.message_end({message:{role:'assistant',timestamp:1,content:[{type:'toolCall',id:'call-one',name:'bash',arguments:{command:'echo one'}}]}},ctx);
 handlers.tool_execution_start({toolCallId:'call-one',toolName:'bash',args:{command:'echo one'}},ctx);
 handlers.tool_execution_update({toolCallId:'call-one',toolName:'bash',partialResult:{content:[{type:'text',text:'one\npartial'}]}},ctx);await wait(130);
 s=await snapshot();assert.equal(s.messages.length,1);assert.equal(s.messages[0].id,'tool:call-one');assert.equal(s.messages[0].toolStatus,'running');assert.match(s.messages[0].text,/echo one/);assert.match(s.messages[0].text,/partial/);
 handlers.tool_execution_end({toolCallId:'call-one',toolName:'bash',result:{content:[{type:'text',text:'complete'}]},isError:false},ctx);
 handlers.message_end({message:{role:'toolResult',toolCallId:'call-one',toolName:'bash',timestamp:2,content:[{type:'text',text:'complete'}]}},ctx);idle=true;handlers.agent_settled({},ctx);await wait(130);
 s=await snapshot();assert.equal(s.messages.length,1);assert.equal(s.messages[0].id,'tool:call-one');assert.equal(s.messages[0].toolStatus,'done');assert.match(s.messages[0].text,/complete/);assert.doesNotMatch(s.messages[0].text,/partial/);
 assert.equal((await command({requestId:'config-4',provider:'test',modelId:'b',thinkingLevel:'off'})).ok,true);assert.equal(ctx.model.id,'b');assert.equal(effort,'off');
});
