import {test} from 'node:test';
import assert from 'node:assert/strict';
import {configuration,pageMetadata,sessionSnapshot,sessionChange} from '../server/session-projection.mjs';

const row=(id,text=id)=>({id,role:'assistant',text,images:[]});
const session=(messages=[row('one')])=>({meta:{id:'agent-one'},syncId:'bridge-1',socket:{},status:'running',messages,truncated:false,configuration:configuration({model:'provider/model'}),page:pageMetadata({epoch:3,activeTurnId:'turn',history:{before:'cursor',hasMore:true}})});

test('bridge configuration forwards only bounded public capabilities, never secrets',()=>{
 const c=configuration({apiKey:'secret',model:'m',serviceTier:'fast',serviceTiers:['fast','bogus'],execution:{provider:'p',model:'m',serviceTier:'fast',tierConfirmed:false,headers:{authorization:'secret'}},skills:[{name:'safe',description:'Available',secret:'secret'},{name:'bad name'}],models:[{provider:'p',id:'id',serviceTiers:['standard','bad'],thinkingLevels:['high','bad'],token:'secret'}],capabilities:['mcp','admin']});
 assert.equal(JSON.stringify(c).includes('secret'),false);
 assert.deepEqual(c.serviceTiers,['fast']);assert.deepEqual(c.capabilities,['mcp']);
 assert.deepEqual(c.skills,[{name:'safe',description:'Available'}]);
 assert.deepEqual(c.models,[{provider:'p',id:'id',name:'id',serviceTiers:['standard'],thinkingLevels:['high']}]);
 assert.equal(c.execution.tierConfirmed,false);
});

test('page metadata preserves cursor and bounded most recent turn metrics',()=>{
 const p=pageMetadata({history:{before:'x'.repeat(600),hasMore:1},epoch:7,activeTurnId:'turn',turns:Array.from({length:102},(_,i)=>({id:`t${i}`,outputTokens:-2,generationMs:10}))});
 assert.equal(p.history.before.length,500);assert.equal(p.history.hasMore,false);assert.equal(p.turns.length,100);
 assert.equal(p.turns[0].id,'t2');assert.equal(p.turns[0].outputTokens,0);assert.equal(p.epoch,7);
});

test('malformed nullable registry and timeline rows do not take down the registered bridge',()=>{
 const c=configuration({thinkingLevel:'secret'.repeat(10000),skills:[null,3,[],{name:'valid',description:'safe'}],models:[null,undefined,4,[],{provider:'p',id:'m'}],execution:[]});
 assert.equal(c.thinkingLevel,'off');assert.equal(c.execution,null);assert.equal(c.models.length,1);assert.equal(c.skills.length,1);
 for(const input of [null,undefined,[],3]){assert.doesNotThrow(()=>configuration(input));assert.doesNotThrow(()=>pageMetadata(input));}
 const p=pageMetadata({epoch:Infinity,activeTurnId:'t'.repeat(1000),turns:[null,7,[],{id:'ok',startedAt:Infinity,finishedAt:-1,outputTokens:Infinity,generationMs:NaN}]});
 assert.equal(p.activeTurnId.length,500);assert.equal(p.epoch,0);assert.equal(p.turns.length,1);
 assert.deepEqual(p.turns[0],{id:'ok',startedAt:0,finishedAt:null,outputTokens:0,generationMs:0});
 assert.equal(configuration({models:Array(1001).fill({provider:'p',id:'m'})}).modelsTruncated,true);
});

test('snapshot stream is scoped to session, bridge ownership and epoch',()=>{
 const s=session(),snap=sessionSnapshot(s,'gateway');
 assert.equal(snap.type,'snapshot');assert.equal(snap.sessionId,'agent-one');assert.equal(snap.checkpoint.stream,'gateway:agent-one:bridge-1');
 assert.equal(snap.checkpoint.epoch,3);assert.equal(snap.checkpoint.rows.length,1);
 s.socket=null;assert.equal(sessionSnapshot(s,'gateway').status,'offline');assert.equal(sessionSnapshot(s,'gateway').connected,false);
});

test('incremental projection transmits changed rows and configuration only when changed',()=>{
 const before=[row('a'),row('b')],s=session([row('a','edited'),row('c')]);
 const delta=sessionChange(s,before,'gateway');
 assert.equal(delta.type,'messages');assert.deepEqual(delta.order,['a','c']);assert.deepEqual(delta.messages,s.messages);assert.deepEqual(delta.removedIds,['b']);
 assert.equal(Object.hasOwn(delta,'configuration'),false);
 assert.deepEqual(sessionChange(s,before,'gateway',true).configuration,s.configuration);
 assert.equal(sessionChange(s,null,'gateway').type,'snapshot');
});

test('tail eviction is not interpreted as conversation deletion',()=>{
 const s=session([row('c'),row('d')]);
 const delta=sessionChange(s,[row('a'),row('b'),row('c')],'gateway');
 assert.deepEqual(delta.removedIds,[]);assert.deepEqual(delta.messages,[row('d')]);
});
