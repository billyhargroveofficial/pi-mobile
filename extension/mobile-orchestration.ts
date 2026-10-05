// Observes the installed runner's read-only accessor. Never calls spawn/abort/consumeResult.
const key=Symbol.for('pi-subagents:manager');
const clip=(v:any,n=300)=>typeof v==='string'?v.slice(0,n):'';
const num=(v:any)=>Number.isFinite(Number(v))?Math.max(0,Number(v)):0;
const status=(v:any)=>({running:'running',queued:'queued',completed:'completed',steered:'paused',aborted:'cancelled',stopped:'cancelled',error:'failed',failed:'failed',killed:'cancelled',paused:'paused'} as any)[v]||'unknown';
export function agentProjection(r:any){return {id:clip(r.id),name:clip(r.description||r.alias||r.type||'Agent'),description:clip(r.description,1000),parentId:clip(r.parentAgentId),workflowId:clip(r.workflowId),status:status(r.status),model:clip(r.invocation?.modelId||r.session?.model?.provider&&`${r.session.model.provider}/${r.session.model.id}`),thinkingLevel:clip(r.invocation?.thinking,20),startedAt:num(r.startedAt),finishedAt:num(r.completedAt),outputTokens:num(r.lifetimeUsage?.output),toolCalls:num(r.toolUses),canInspect:!!r.session||!!r.result,error:clip(r.error,1000)};}
export function projectState(state:any){
 let truncated=(state.agents?.length||0)>200||(state.workflows?.length||0)>50;
 const agents=(state.agents||[]).slice(-200).map(agentProjection),byId=new Map(agents.map((a:any)=>[a.id,a]));
 const workflows=(state.workflows||[]).slice(-50).map((w:any)=>{
  const entries=w.workflowProgress||[],latest=new Map<number,any>(),phases=new Map<string,any>(),phaseNames=new Map<number,string>();
  for(const p of (w.meta?.phases||[]))phases.set(clip(p.title),{title:clip(p.title),status:'queued',agentCount:0,completed:0});
  for(const e of entries){if(e.type==='workflow_phase'){const title=clip(e.title);phaseNames.set(e.index,title);phases.set(title,{...(phases.get(title)||{agentCount:0,completed:0,status:'waiting'}),title});}if(e.type==='workflow_agent')latest.set(e.index,e);}
  let completed=0;
  for(const e of latest.values()){
   const id=e.recordId||`${w.id}:slot:${e.index}`,known:any=byId.get(id);let st=e.state==='done'?'completed':e.state==='error'?'failed':e.startedAt?'running':'queued';if(e.skipped||e.interrupted)st='cancelled';if(['completed','failed','killed','paused'].includes(w.status)&&['running','queued'].includes(st))st='unknown';
   let a=known;if(!a&&agents.length<200){a={id,name:clip(e.label||e.agentId||'Agent'),description:'',parentId:'',workflowId:w.id,status:st,model:clip(e.modelId||e.model),thinkingLevel:clip(e.thinking),startedAt:num(e.startedAt),finishedAt:0,outputTokens:0,toolCalls:num(e.toolCalls),canInspect:false};agents.push(a);byId.set(id,a);}
   if(!a)truncated=true;
   if(a){a.workflowId=clip(w.id);a.phase=clip(e.phaseTitle);a.phaseIndex=num(e.phaseIndex);if(e.cached)a.cached=true;}
   if(st==='completed')completed++;
   if(e.phaseIndex!==undefined){const title=clip(e.phaseTitle||phaseNames.get(e.phaseIndex)||'Agents');let p=phases.get(title);if(!p){p={title,agentCount:0,completed:0,status:'queued'};phases.set(title,p);}p.agentCount++;if(st==='completed')p.completed++;if(st==='running')p.status='running';else if(st==='failed'&&p.status!=='running')p.status='failed';}
  }
  if(phases.size>40)truncated=true;
  for(const p of phases.values())if(p.agentCount&&p.completed===p.agentCount)p.status='completed';
  return {id:clip(w.id),title:clip(w.meta?.name||w.title||'Workflow'),description:clip(w.meta?.description,1000),status:status(w.status),startedAt:num(w.startTime),finishedAt:num(w.endTime),agentCount:latest.size,completed,phases:[...phases.values()].slice(0,40),error:clip(w.error,1000)};
 });
 // Nested children inherit workflow grouping from their recorded parent, never by timing guesses.
 for(let n=0;n<10;n++)for(const a of agents)if(!a.workflowId&&a.parentId){const parent:any=byId.get(a.parentId);if(parent?.workflowId)a.workflowId=parent.workflowId;}
 return {available:true,liveAvailable:true,observedAt:Date.now(),agents,workflows,truncated};
}
export function publicMessages(raw:any[],before=0){
 const end=before>0?Math.min(Math.floor(before),raw.length):raw.length,start=Math.max(0,end-60),rows:any[]=[];let truncated=false;
 const safeText=(s:any)=>{const str=typeof s==='string'?s:'';if(str.length>16000)truncated=true;return str.slice(0,16000);};
 for(let i=start;i<end;i++){const m=raw[i];if(!['user','assistant','toolResult'].includes(m?.role))continue;const blocks=typeof m.content==='string'?[{type:'text',text:m.content}]:Array.isArray(m.content)?m.content:[];
  const text=blocks.filter((b:any)=>b.type==='text').map((b:any)=>safeText(b.text)).join('\n');const calls=blocks.filter((b:any)=>b.type==='toolCall');
  if(text||m.role==='toolResult')rows.push({id:m.role==='toolResult'&&m.toolCallId?'tool:'+m.toolCallId:'message:'+i,role:m.role,text:safeText(text),images:[],turnId:'agent',phase:m.role==='assistant'&&(calls.length||i<raw.length-1)?'work':'answer',toolName:clip(m.toolName),toolStatus:m.isError?'error':'done'});
  for(const call of calls){let args='';try{args=JSON.stringify(call.arguments||{});}catch{}rows.push({id:'tool:'+call.id,role:'toolResult',text:'Arguments\n'+safeText(args),preview:args.slice(0,2000),toolName:clip(call.name),toolStatus:'running',images:[],turnId:'agent'});}
  if(blocks.some((b:any)=>b.type==='image'))rows.push({id:'image:'+i,role:'custom',text:'Image attachment · unavailable in agent inspection',images:[],turnId:'agent'});
 }
 const merged=new Map<string,any>();for(const row of rows){const previous=merged.get(row.id);merged.set(row.id,previous&&row.role==='toolResult'?{...row,preview:previous.preview||'',text:previous.text+'\n\nResult\n'+row.text}:row);}
 return {messages:[...merged.values()],before:start,hasMore:start>0,truncated};
}
export class MobileOrchestration {
 private state:any;private owner='';private agents=new Map<string,any>();private workflows=new Map<string,any>();private details=new Map<string,any>();private pending=new Map<string,any>();private cacheBytes=0;
 private recordDetail(record:any,before=0){const partial=record.session?.agent?.state?.streamingMessage;const raw=[...(record.session?.messages||[]),...(partial?[partial]:[])];const messages=raw.length?publicMessages(raw,before):{messages:record.result?[{id:'result',role:'assistant',text:clip(record.result,16000),images:[]}]:[],before:0,hasMore:false,truncated:!!record.result&&record.result.length>16000};return {type:'agentTranscript',agent:agentProjection(record),source:'live',...messages,childCount:this.state.agents.filter((r:any)=>r.parentAgentId===record.id).length};}
 snapshot(sessionId:string){const source=(globalThis as any)[key];try{const s=source?.mobileReadState?.();if(s?.version!==1||s.sessionId!==sessionId)return null;if(this.owner&&this.owner!==sessionId){this.agents.clear();this.workflows.clear();this.details.clear();this.pending.clear();this.cacheBytes=0;}this.owner=sessionId;this.state={...s,agents:s.agents.filter((r:any)=>!r.rootSessionId||r.rootSessionId===sessionId)};const data=projectState(this.state),present=new Set(data.agents.map((a:any)=>a.id));
  for(const a of data.agents){const cached=this.details.get(a.id);if(!a.canInspect&&cached){const workflowId=a.workflowId,phase=a.phase;Object.assign(a,cached.data.agent,{workflowId,phase,canInspect:true});}this.agents.set(a.id,a);}for(const w of data.workflows)this.workflows.set(w.id,w);
  for(const r of this.state.agents){if(['running','queued'].includes(r.status)||this.details.has(r.id))continue;const d=this.recordDetail(r);d.source='saved-recent';d.truncated=d.truncated||d.hasMore;d.hasMore=false;d.before=0;const bytes=Buffer.byteLength(JSON.stringify(d));if(bytes>2*1024*1024)continue;this.details.set(r.id,{data:d,bytes});this.pending.set(r.id,d);this.cacheBytes+=bytes;while(this.cacheBytes>8*1024*1024&&this.details.size){const id=this.details.keys().next().value!;this.cacheBytes-=this.details.get(id).bytes;this.details.delete(id);this.pending.delete(id);}}
  for(const a of this.agents.values())if(!present.has(a.id)){if(a.id.includes(':slot:')){this.agents.delete(a.id);continue;}if(['running','queued'].includes(a.status))a.status='unknown';a.canInspect=this.details.has(a.id);}
  while(this.agents.size>200)this.agents.delete(this.agents.keys().next().value!);while(this.workflows.size>50)this.workflows.delete(this.workflows.keys().next().value!);
  return {...data,agents:[...this.agents.values()],workflows:[...this.workflows.values()]};}catch{return null;}}
 drainCaches(){const out=[...this.pending.values()].slice(0,4);for(const d of out)this.pending.delete(d.agent.id);return out;}
 detail(sessionId:string,id:string,before=0){const snapshot=this.snapshot(sessionId);if(!snapshot)throw Error('Read-only observer unavailable; reload Pi only when idle');const record=this.state.agents.find((r:any)=>r.id===id);if(record)return this.recordDetail(record,before);const cached=this.details.get(id);if(cached)return cached.data;throw Error('Agent record expired');}
}
