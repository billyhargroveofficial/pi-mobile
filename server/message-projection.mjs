import crypto from 'node:crypto';
import {decodeImage} from './command-policy.mjs';
import {attachmentDisplay} from './attachments.mjs';

/** A bounded public transcript for one owner. The binary image cache stays host-private. */
export function projectMessages(id,messages,activeTurn='',{dataDir,media}){
  let truncated=messages.length>200;
  const tail=messages.slice(-200),results=new Map(),emitted=new Set(),lastAssistant=new Map();
  for(let i=0;i<tail.length;i++)if(tail[i].role==='assistant')lastAssistant.set(tail[i].turnId||'legacy',i);
  for(const m of tail)if(m.role==='toolResult'&&m.toolCallId)results.set(m.toolCallId,m);
  function render(m,i){
    const blocks=typeof m.content==='string'?[{type:'text',text:m.content}]:Array.isArray(m.content)?m.content:[];
    const images=[];const text=[];
    for(const b of blocks){
      if(b.type==='text')text.push(String(b.text||''));
      if(b.type==='image')try{
        const bytes=decodeImage(b),digest=crypto.createHash('sha256').update(bytes).digest('hex'),key=id+'/'+digest;
        // Prefix by owner; evicted references yield an explicit 404.
        media.put(key,bytes,b.mimeType);
        images.push({url:`/api/sessions/${encodeURIComponent(id)}/media/${digest}`,mimeType:b.mimeType});
      }catch{text.push('[Изображение пропущено: неподдерживаемый формат или размер]');}
    }
    if(m.role==='assistant'&&m.errorMessage)text.push(`[Ошибка] ${m.errorMessage}`);
    let body=text.join('\n');if(m.role==='user')body=attachmentDisplay(body,dataDir);if(body.length>30000){body=body.slice(0,30000)+'\n[Обрезано]';truncated=true;}
    return {id:String(m.role==='toolResult'&&m.toolCallId?'tool:'+m.toolCallId:m.id||`${m.role}-${m.timestamp||0}-${i}`),role:m.role,text:body,images,turnId:String(m.turnId||'legacy'),phase:m.role==='assistant'&&(m.turnId===activeTurn||m.phase==='work'||i!==lastAssistant.get(m.turnId||'legacy')||blocks.some(b=>b.type==='toolCall'))?'work':'answer',...(m.toolName?{toolName:m.toolName,toolStatus:m.toolStatus||(m.isError?'error':'done')}:{})};
  }
  const result=[];
  function tool(call,m,i){
    const key=call.id||m?.toolCallId||`unknown-${i}`;if(emitted.has(key))return;emitted.add(key);
    const value=m||{role:'toolResult',turnId:call.turnId||'legacy',toolCallId:key,toolName:call.name,content:[],toolStatus:'pending'};
    const row=render(value,i);row.toolName=call.name||value.toolName||'tool';
    const args=call.arguments??value.toolArgs;
    row.preview=args===undefined?'':JSON.stringify(args).slice(0,2000);
    const file=args?.path||args?.file_path;
    if(typeof file==='string'&&/\.(md|markdown)$/i.test(file))row.documentPath=file.slice(0,2000);
    if(args!==undefined){let text=JSON.stringify(args,null,2);if(text.length>8000){text=text.slice(0,8000)+'\n[Аргументы обрезаны]';truncated=true;}row.text='Аргументы\n'+text+(row.text?'\n\nРезультат\n'+row.text:'');}
    result.push(row);
  }
  for(let i=0;i<tail.length;i++){
    const m=tail[i];if(m.role==='toolResult'){tool({id:m.toolCallId,name:m.toolName},m,i);continue;}
    const row=render(m,i);if(['user','assistant','custom'].includes(row.role)&&(row.text||row.images.length))result.push(row);
    if(Array.isArray(m.content))for(const b of m.content)if(b.type==='toolCall')tool({...b,turnId:m.turnId},results.get(b.id),i);
  }
  if(result.length>400)truncated=true;
  return {messages:result.slice(-400),truncated};
}
