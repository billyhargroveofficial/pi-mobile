import {checkpoint} from './conversation-sync.mjs';

const tiers=values=>(Array.isArray(values)?values:[]).filter(x=>['standard','fast'].includes(x));
const record=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
const objects=value=>(Array.isArray(value)?value:[]).filter(record);
const text=(value,limit)=>typeof value==='string'?value.slice(0,limit):'';
const levels=['off','minimal','low','medium','high','xhigh','max'];
const level=value=>levels.includes(value)?value:'off';
const positive=value=>{const n=Number(value);return Number.isFinite(n)?Math.min(Number.MAX_SAFE_INTEGER,Math.max(0,n)):0;};
const execution=e=>record(e)?{model:text(e.model,200),provider:text(e.provider,200),thinkingLevel:level(e.thinkingLevel),serviceTier:['standard','fast'].includes(e.serviceTier)?e.serviceTier:'unknown',tierConfirmed:e.tierConfirmed===true}:null;

/** Allowlisted bridge configuration: never forward provider credentials or global defaults. */
export function configuration(meta){
 const m=record(meta)?meta:{};
 const models=(Array.isArray(m.models)?m.models:[]).slice(0,1000).filter(x=>record(x)&&typeof x.provider==='string'&&typeof x.id==='string');
 return {
  serviceTier:m.serviceTier==='fast'?'fast':'standard',serviceTiers:tiers(m.serviceTiers),execution:execution(m.execution),
  capabilities:(Array.isArray(m.capabilities)?m.capabilities:[]).filter(x=>['skills','mcp','name','service-tier'].includes(x)),
  skills:objects(m.skills).slice(0,500).filter(x=>typeof x.name==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(x.name)).map(x=>({name:x.name,description:text(x.description,1024)})),
  model:text(m.model,300),thinkingLevel:level(m.thinkingLevel),
  models:models.map(x=>({provider:text(x.provider,200),id:text(x.id,200),name:text(x.name||x.id,200),serviceTiers:tiers(x.serviceTiers),thinkingLevels:(Array.isArray(x.thinkingLevels)?x.thinkingLevels:[]).filter(x=>levels.includes(x))})),
  modelsTruncated:m.modelsTruncated===true||(Array.isArray(m.models)&&m.models.length>1000),
 };
}

export function pageMetadata(packet){
 const p=record(packet)?packet:{};
 return {history:{before:text(p.history?.before,500),hasMore:p.history?.hasMore===true},
  epoch:Number.isSafeInteger(p.epoch)&&p.epoch>=0?p.epoch:0,activeTurnId:text(p.activeTurnId,500),
  turns:objects(p.turns).slice(-100).map(t=>({id:text(t.id,500),startedAt:positive(t.startedAt),finishedAt:positive(t.finishedAt)||null,
   outputTokens:positive(t.outputTokens),generationMs:positive(t.generationMs)}))};
}

const point=(session,stream)=>checkpoint(stream+':'+session.meta.id+':'+session.syncId,session.page.epoch,session.messages);
export const sessionSnapshot=(s,stream)=>({type:'snapshot',sessionId:s.meta.id,status:s.socket?s.status:'offline',connected:!!s.socket,messages:s.messages,truncated:s.truncated,configuration:s.configuration,...s.page,checkpoint:point(s,stream)});

/** Incremental live update. Prefix eviction from a bounded tail is not deletion. */
export function sessionChange(s,previous,stream,configChanged=false){
 if(!previous)return sessionSnapshot(s,stream);
 const old=new Map(previous.map(m=>[m.id,JSON.stringify(m)])),ids=new Set(s.messages.map(m=>m.id));
 const overlap=previous.findIndex(m=>m.id===s.messages[0]?.id);
 return {type:'messages',sessionId:s.meta.id,checkpoint:point(s,stream),order:s.messages.map(m=>m.id),status:s.socket?s.status:'offline',connected:!!s.socket,truncated:s.truncated,turns:s.page.turns,activeTurnId:s.page.activeTurnId,epoch:s.page.epoch,...(configChanged?{configuration:s.configuration}:{}),messages:s.messages.filter(m=>old.get(m.id)!==JSON.stringify(m)),removedIds:previous.filter((m,i)=>overlap>=0&&i>=overlap&&!ids.has(m.id)).map(m=>m.id)};
}
