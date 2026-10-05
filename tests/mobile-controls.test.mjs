import test from 'node:test';import assert from 'node:assert/strict';
import {skillCommands,skillPrompt,mcpSnapshot} from '../extension/mobile-controls.ts';
import {validateCommand} from '../server/gateway.mjs';
test('skills are taken only from Pi registry and expand only an explicit leading invocation',()=>{
 const skills=skillCommands([{name:'skill:design',source:'skill',description:'Design'},{name:'bash',source:'extension'},{name:'../bad',source:'skill'}]);
 assert.deepEqual(skills,[{name:'design',description:'Design'}]);
 assert.deepEqual(skillPrompt('$design make this',skills),{text:'/skill:design make this',expand:true});
 assert.deepEqual(skillPrompt('cost $design',skills),{text:'cost $design',expand:false});
 assert.throws(()=>skillPrompt('$unknown',skills),/недоступен/);
});
test('MCP snapshot cannot expose definitions or equate cache with connection',()=>{
 const clean=mcpSnapshot({version:1,servers:[{name:'a',status:'connected',toolCount:2,headers:{secret:'no'}},{name:'b',status:'cached',toolCount:5,definition:{url:'secret'}},{name:'c',status:'nonsense'}]});
 assert.deepEqual(clean,{version:1,servers:[{name:'a',status:'connected',toolCount:2},{name:'b',status:'cached',toolCount:5}]});assert.equal(mcpSnapshot({}),null);
});
test('name and MCP commands are explicitly validated',()=>{
 const base={sessionId:'session-1',requestId:'request-1'};
 assert.equal(validateCommand({...base,command:'mcp'}).command,'mcp');
 assert.equal(validateCommand({...base,command:'name',name:' New name '}).name,'New name');
 for(const name of ['', 'bad\nname','x'.repeat(201)])assert.throws(()=>validateCommand({...base,command:'name',name}));
});
