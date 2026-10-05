import {test} from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs/promises';import os from 'node:os';import path from 'node:path';
import {historyPage,messageId,readMarkdown} from '../extension/session-data.ts';

test('history starts at tail, walks chunks without gaps or duplicate cursor ids',()=>{
 const entries=Array.from({length:131},(_,i)=>({type:'message',id:'entry-'+i,message:{role:i%2?'assistant':'user',timestamp:1000+i,content:'message '+i}}));
 let page=historyPage(entries),all=[...page.messages];assert.equal(page.messages.length,40);assert.equal(page.messages[0].content,'message 91');
 while(page.history.hasMore){page=historyPage(entries,page.history.before);all=[...page.messages,...all];}
 assert.equal(all.length,131);assert.equal(new Set(all.map(m=>m.id)).size,131);assert.equal(all[0].content,'message 0');assert.equal(all[130].content,'message 130');
 assert.equal(messageId(entries[3].message),all[3].id);assert.throws(()=>historyPage(entries,'missing-cursor'));
});
test('turn boundaries and saved metrics survive paged branch reconstruction',()=>{
 const entries=[{type:'message',id:'a',message:{role:'user',timestamp:1,content:'hi'}},{type:'message',id:'b',message:{role:'assistant',timestamp:2,content:'answer'}},{type:'custom',customType:'pi-mobile-turn-v1',data:{id:'user:1',startedAt:100,finishedAt:200,outputTokens:20,generationMs:50}}];
 const page=historyPage(entries);assert.equal(page.messages[1].turnId,'user:1');assert.equal(page.turns[0].outputTokens,20);
});
test('Markdown preview is workspace-scoped, text-only, bounded and rejects symlink escapes',async t=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'pi-md-')),outside=await fs.mkdtemp(path.join(os.tmpdir(),'pi-md-out-'));t.after(()=>Promise.all([fs.rm(root,{recursive:true,force:true}),fs.rm(outside,{recursive:true,force:true})]));
 await fs.mkdir(path.join(root,'docs'));await fs.writeFile(path.join(root,'docs/note.md'),'# Hello\n\n$$E=mc^2$$');
 assert.equal((await readMarkdown(root,'docs/note.md')).text,'# Hello\n\n$$E=mc^2$$');
 await fs.writeFile(path.join(outside,'private.md'),'not allowed');await fs.symlink(path.join(outside,'private.md'),path.join(root,'escape.md'));
 await assert.rejects(readMarkdown(root,'escape.md'),/вне/);await assert.rejects(readMarkdown(root,path.join(outside,'private.md')),/вне/);await assert.rejects(readMarkdown(root,'../.ssh/id_rsa'));
 await fs.writeFile(path.join(root,'huge.md'),Buffer.alloc(1024*1024+1));await assert.rejects(readMarkdown(root,'huge.md'),/1 MiB/);
 await fs.writeFile(path.join(root,'binary.md'),'x\0y');await assert.rejects(readMarkdown(root,'binary.md'),/текстом/);
});
