import fs from 'node:fs/promises';
import {constants} from 'node:fs';
import path from 'node:path';

export const PAGE_SIZE=40;
export const messageId=(m:any,fallback='')=>m.role==='toolResult'&&m.toolCallId?'tool:'+m.toolCallId:m.timestamp?`${m.role}:${m.timestamp}`:m.id||fallback;

/** Source cursors are stable across live events, reloads and history pages. */
export function branchData(entries:any[]){
  let turnId='legacy';const messages:any[]=[],turns:any[]=[];
  for(const e of entries){
    if(e.type==='custom'&&e.customType==='pi-mobile-turn-v1'&&e.data?.id)turns.push(e.data);
    if(e.type!=='message')continue;
    const m={...e.message,id:messageId(e.message,e.id)};
    if(m.role==='user')turnId=m.id;
    m.turnId=turnId;messages.push(m);
  }
  const lastAssistant=new Map<string,any>();for(const m of messages)if(m.role==='assistant')lastAssistant.set(m.turnId,m);
  for(const m of messages)if(m.role==='assistant')m.phase=lastAssistant.get(m.turnId)!==m||(Array.isArray(m.content)&&m.content.some((b:any)=>b.type==='toolCall'))?'work':'answer';
  return {messages,turns};
}
export function historyPage(entries:any[],before?:string,limit=PAGE_SIZE){
  const data=branchData(entries);
  const end=before?data.messages.findIndex(m=>m.id===before):data.messages.length;
  if(end<0)throw Error('История изменилась. Переподключись к диалогу.');
  const start=Math.max(0,end-Math.min(PAGE_SIZE,Math.max(1,limit)));
  let messages=data.messages.slice(start,end),bytes=0,index=messages.length;
  for(let i=messages.length-1;i>=0;i--){const n=Buffer.byteLength(JSON.stringify(messages[i]));if(bytes+n>20*1024*1024)break;bytes+=n;index=i;}
  if(index===messages.length&&messages.length)throw Error('Сообщение истории слишком велико для передачи');
  messages=messages.slice(index);
  const turnIds=new Set(messages.map(m=>m.turnId));
  return {messages,history:{before:messages[0]?.id||before||'',hasMore:start+index>0},turns:data.turns.filter(t=>turnIds.has(t.id))};
}

/** Explicit user-requested preview only, within this session's real workspace. */
export async function readMarkdown(cwd:string,requested:string){
  if(typeof requested!=='string'||requested.length>2000||/[\x00-\x1f]/.test(requested)||! /\.(md|markdown)$/i.test(requested))throw Error('Разрешены только .md/.markdown файлы рабочего пространства');
  const root=await fs.realpath(cwd),target=await fs.realpath(path.resolve(root,requested));
  const relative=path.relative(root,target);
  if(relative==='..'||relative.startsWith('..'+path.sep)||path.isAbsolute(relative))throw Error('Файл вне рабочего пространства этой сессии');
  const handle=await fs.open(target,constants.O_RDONLY|constants.O_NOFOLLOW);
  try{
    const stat=await handle.stat();if(!stat.isFile()||stat.size>1024*1024)throw Error('Предпросмотр ограничен Markdown-файлами до 1 MiB');
    const buffer=Buffer.alloc(1024*1024+1);const {bytesRead}=await handle.read(buffer,0,buffer.length,0);
    if(bytesRead>1024*1024)throw Error('Файл превышает 1 MiB');
    const text=buffer.subarray(0,bytesRead).toString('utf8');if(text.includes('\0'))throw Error('Файл не является текстом');
    return {type:'document',path:relative,text};
  }finally{await handle.close();}
}
