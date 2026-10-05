import {checkpoint} from './conversation-sync.mjs';

const tiers=values=>(Array.isArray(values)?values:[]).filter(x=>['standard','fast'].includes(x));
const execution=e=>e&&typeof e==='object'?{model:String(e.model||'').slice(0,200),provider:String(e.provider||'').slice(0,200),thinkingLevel:String(e.thinkingLevel||'off').slice(0,20),serviceTier:['standard','fast'].includes(e.serviceTier)?e.serviceTier:'unknown',tierConfirmed:e.tierConfirmed===true}:null;

/** Allowlisted bridge configuration: never forward provider credentials or global defaults. */
export const configuration=m=>({serviceTier:m?.serviceTier==='fast'?'fast':'standard',serviceTiers:tiers(m?.serviceTiers),execution:execution(m?.execution),capabilities:(Array.isArray(m?.capabilities)?m.capabilities:[]).filter(x=>['skills','mcp','name','service-tier'].includes(x)),skills:(Array.isArray(m?.skills)?m.skills:[]).slice(0,500).filter(x=>typeof x?.name==='string'&&/^[a-zA-Z0-9_-]{1,100}$/.test(x.name)).map(x=>({name:x.name,description:String(x.description||'').slice(0,1024)})),model:String(m?.model||'').slice(0,300),thinkingLevel:String(m?.thinkingLevel||'off'),models:(Array.isArray(m?.models)?m.models:[]).slice(0,1000).filter(x=>typeof x.provider==='string'&&typeof x.id==='string').map(x=>({provider:x.provider.slice(0,200),id:x.id.slice(0,200),name:String(x.name||x.id).slice(0,200),serviceTiers:tiers(x.serviceTiers),thinkingLevels:(Array.isArray(x.thinkingLevels)?x.thinkingLevels:[]).filter(x=>['off','minimal','low','medium','high','xhigh','max'].includes(x))})),modelsTruncated:!!m?.modelsTruncated});

export const pageMetadata=p=>({history:{before:String(p.history?.before||'').slice(0,500),hasMore:p.history?.hasMore===true},epoch:Number(p.epoch)||0,activeTurnId:String(p.activeTurnId||''),turns:(Array.isArray(p.turns)?p.turns:[]).slice(-100).map(t=>({id:String(t.id||''),startedAt:Number(t.startedAt)||0,finishedAt:Number(t.finishedAt)||null,outputTokens:Math.max(0,Number(t.outputTokens)||0),generationMs:Math.max(0,Number(t.generationMs)||0)}))});

const point=(session,stream)=>checkpoint(stream+':'+session.meta.id+':'+session.syncId,session.page.epoch,session.messages);
export const sessionSnapshot=(s,stream)=>({type:'snapshot',sessionId:s.meta.id,status:s.socket?s.status:'offline',connected:!!s.socket,messages:s.messages,truncated:s.truncated,configuration:s.configuration,...s.page,checkpoint:point(s,stream)});

/** Incremental live update. Prefix eviction from a bounded tail is not deletion. */
export function sessionChange(s,previous,stream,configChanged=false){
 if(!previous)return sessionSnapshot(s,stream);
 const old=new Map(previous.map(m=>[m.id,JSON.stringify(m)])),ids=new Set(s.messages.map(m=>m.id));
 const overlap=previous.findIndex(m=>m.id===s.messages[0]?.id);
 return {type:'messages',sessionId:s.meta.id,checkpoint:point(s,stream),order:s.messages.map(m=>m.id),status:s.socket?s.status:'offline',connected:!!s.socket,truncated:s.truncated,turns:s.page.turns,activeTurnId:s.page.activeTurnId,epoch:s.page.epoch,...(configChanged?{configuration:s.configuration}:{}),messages:s.messages.filter(m=>old.get(m.id)!==JSON.stringify(m)),removedIds:previous.filter((m,i)=>overlap>=0&&i>=overlap&&!ids.has(m.id)).map(m=>m.id)};
}
