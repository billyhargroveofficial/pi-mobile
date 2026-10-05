// Session-local preference. No global model configuration or account settings are changed.
export type MobileTier='standard'|'fast';
export const TIER_ENTRY='pi-mobile-tier-v1';
export function supportedTiers(model:any):MobileTier[]{
 return model&&((model.provider==='openai-codex'&&model.api==='openai-codex-responses')||(model.provider==='openai'&&model.api==='openai-responses'))?['standard','fast']:[];
}
export function observedTier(value:unknown):MobileTier|null{
 if(value==='priority'||value==='fast'||value==='ultrafast')return 'fast';
 if(value==='default')return 'standard';return null;
}
export function restoredTier(branch:any[]):MobileTier|null{
 let tier:MobileTier|null=null;for(const entry of branch)if(entry?.type==='custom'&&entry.customType===TIER_ENTRY&&['standard','fast'].includes(entry.data?.serviceTier))tier=entry.data.serviceTier;return tier;
}
export function tierPayload(payload:any,model:any,preference:MobileTier|null){
 if(!preference||!supportedTiers(model).length||!payload||typeof payload!=='object'||Array.isArray(payload))return undefined;
 return {...payload,service_tier:preference==='fast'?'priority':'default'};
}
