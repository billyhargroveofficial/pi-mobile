import {test} from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {projectMessages} from '../server/message-projection.mjs';
import {MediaCache} from '../server/media-cache.mjs';

const context=()=>({dataDir:'/private/pi-mobile',media:new MediaCache(1024)});

test('assistant progress, tool call and final answer remain ordered with one tool row',()=>{
 const raw=[
  {id:'u',role:'user',turnId:'t',content:'Check the source'},
  {id:'p',role:'assistant',turnId:'t',content:[{type:'text',text:'Reading now'},{type:'toolCall',id:'call-1',name:'read',arguments:{path:'docs/architecture.md'}}]},
  {id:'r',role:'toolResult',turnId:'t',toolCallId:'call-1',toolName:'read',content:'Read 12 lines'},
  {id:'f',role:'assistant',turnId:'t',content:'Done'}
 ];
 const rows=projectMessages('owner',raw,'',context()).messages;
 assert.deepEqual(rows.map(r=>r.id),['u','p','tool:call-1','f']);
 assert.equal(rows[1].phase,'work');assert.equal(rows[3].phase,'answer');
 assert.equal(rows[2].toolName,'read');assert.equal(rows[2].documentPath,'docs/architecture.md');
 assert.match(rows[2].text,/Аргументы/);assert.match(rows[2].text,/Результат\nRead 12 lines/);
 assert.equal(projectMessages('owner',raw,'t',context()).messages.at(-1).phase,'work');
});

test('image bytes remain private and media references are scoped to their registered owner',()=>{
 const data=Buffer.from('89504e470d0a1a0a','hex'),digest=crypto.createHash('sha256').update(data).digest('hex'),ctx=context();
 const row=projectMessages('owner:one',[{id:'image',role:'user',content:[{type:'image',mimeType:'image/png',data:data.toString('base64')}]}],'',ctx).messages[0];
 assert.deepEqual(row.images,[{url:`/api/sessions/owner%3Aone/media/${digest}`,mimeType:'image/png'}]);
 assert.equal(JSON.stringify(row).includes(data.toString('base64')),false);
 assert.equal(ctx.media.get('owner:one/'+digest).data.equals(data),true);
 assert.equal(ctx.media.get('owner:other/'+digest),undefined);
});

test('uploaded host paths are replaced with display names; long histories are bounded',()=>{
 const text='Please review\n\nПрикреплённые файлы Pi Mobile (локальные пути для чтения инструментами):\n'+JSON.stringify({name:'brief.txt',path:'/private/pi-mobile/uploads/brief.txt'});
 const result=projectMessages('owner',[{id:'u',role:'user',content:text}],'',context());
 assert.equal(result.messages[0].text,'Please review\n📎 brief.txt');
 assert.equal(JSON.stringify(result).includes('/private/pi-mobile/uploads'),false);
 const many=Array.from({length:201},(_,i)=>({id:`u${i}`,role:'user',content:'x'}));
 const tail=projectMessages('owner',many,'',context());
 assert.equal(tail.messages.length,200);assert.equal(tail.messages[0].id,'u1');assert.equal(tail.truncated,true);
});
