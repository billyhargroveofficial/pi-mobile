import {test} from 'node:test';
import assert from 'node:assert/strict';
import {CommandReceipts} from '../contracts/command-receipts.mjs';

test('reads and unsupported commands never retain transcript/document/MCP payloads',()=>{
 const receipts=new CommandReceipts();
 for(const command of ['history','document','mcp','agent_transcript','unsupported']){
  receipts.remember(command,command,{ok:true,data:{text:'x'.repeat(1024*1024)}});
  receipts.remember(command,command+'-failure',{ok:false,error:'failed'});
 }
 assert.equal(receipts.size,0);
});
test('both successful and failed mutations have the same finite FIFO bound',()=>{
 const receipts=new CommandReceipts(3);
 for(let i=0;i<10000;i++)receipts.remember('configure',String(i),{ok:i%2===0});
 assert.equal(receipts.size,3);assert.equal(receipts.get('0'),undefined);
 assert.deepEqual(receipts.get('9999'),{ok:false});assert.deepEqual(receipts.get('9998'),{ok:true});
 receipts.remember('prompt','9999',{ok:true});assert.equal(receipts.size,3);
 receipts.clear();assert.equal(receipts.size,0);
 for(const capacity of [0,-1,1.5,Infinity])assert.throws(()=>new CommandReceipts(capacity));
});
