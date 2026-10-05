// Shared, read-only projection for nested agent transcripts. No transport or
// runtime ownership crosses this boundary; both Pi extension and gateway use it.
const clip=(value,length=300)=>typeof value==='string'?value.slice(0,length):'';

export function publicMessages(raw,before=0){
 const end=before>0?Math.min(Math.floor(before),raw.length):raw.length,start=Math.max(0,end-60),rows=[];let truncated=false;
 const safeText=value=>{const text=typeof value==='string'?value:'';if(text.length>16000)truncated=true;return text.slice(0,16000);};
 for(let i=start;i<end;i++){
  const m=raw[i];if(!['user','assistant','toolResult'].includes(m?.role))continue;
  const blocks=typeof m.content==='string'?[{type:'text',text:m.content}]:Array.isArray(m.content)?m.content:[];
  const text=blocks.filter(b=>b.type==='text').map(b=>safeText(b.text)).join('\n'),calls=blocks.filter(b=>b.type==='toolCall');
  if(text||m.role==='toolResult')rows.push({id:m.role==='toolResult'&&m.toolCallId?'tool:'+m.toolCallId:'message:'+i,role:m.role,text:safeText(text),images:[],turnId:'agent',phase:m.role==='assistant'&&(calls.length||i<raw.length-1)?'work':'answer',toolName:clip(m.toolName),toolStatus:m.isError?'error':'done'});
  for(const call of calls){let args='';try{args=JSON.stringify(call.arguments||{});}catch{}rows.push({id:'tool:'+call.id,role:'toolResult',text:'Arguments\n'+safeText(args),preview:args.slice(0,2000),toolName:clip(call.name),toolStatus:'running',images:[],turnId:'agent'});}
  if(blocks.some(b=>b.type==='image'))rows.push({id:'image:'+i,role:'custom',text:'Image attachment · unavailable in agent inspection',images:[],turnId:'agent'});
 }
 const merged=new Map();for(const row of rows){const previous=merged.get(row.id);merged.set(row.id,previous&&row.role==='toolResult'?{...row,preview:previous.preview||'',text:previous.text+'\n\nResult\n'+row.text}:row);}
 return {messages:[...merged.values()],before:start,hasMore:start>0,truncated};
}
