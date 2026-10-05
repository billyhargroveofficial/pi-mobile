// Pure, bounded control data: never expose server definitions, headers, or credentials.
export function skillCommands(commands:any[]) {
  return commands.filter(c=>c?.source==='skill'&&typeof c.name==='string').map(c=>({name:c.name.replace(/^skill:/,''),description:String(c.description||'').slice(0,1024)})).filter(c=>/^[a-zA-Z0-9_-]{1,100}$/.test(c.name)).slice(0,500);
}
export function skillPrompt(text:string,skills:{name:string}[]) {
  const match=/^\$([a-zA-Z0-9_-]+)(?=\s|$)/.exec(text);
  if(!match)return {text,expand:false};
  if(!skills.some(s=>s.name===match[1]))throw Error('Навык недоступен в текущем Pi: '+match[1]);
  return {text:'/skill:'+match[1]+text.slice(match[0].length),expand:true};
}
export function mcpSnapshot(value:any) {
  if(value?.version!==1||!Array.isArray(value.servers))return null;
  const states=['connected','not-connected','needs-auth','failed','cached','disabled','blocked'];
  return {version:1,servers:value.servers.slice(0,200).filter((s:any)=>typeof s?.name==='string'&&states.includes(s.status)).map((s:any)=>({name:s.name.slice(0,200),status:s.status,toolCount:Math.max(0,Math.min(100000,Number(s.toolCount)||0))}))};
}
