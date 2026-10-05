import fs from 'node:fs';
import path from 'node:path';
import {orcaCommand} from './orca.mjs';
const quote=v=>"'"+String(v).replaceAll("'","'\\''")+"'";
export async function launchNew(workspace,id){
 const r=await orcaCommand(['terminal','create','--worktree','id:'+workspace.id,'--title','Pi — новый диалог','--command','pi --session-id '+quote(id)]);
 if(!r.terminal?.handle)throw Error('Результат запуска неизвестен. Проверь Orca перед повтором');
 return r.terminal.handle;
}
export class Lifecycle {
 constructor({dataDir,inventory,owners,launch=launchNew,close=handle=>orcaCommand(['terminal','close','--terminal',handle])}){
  Object.assign(this,{inventory,owners,launch,closeTerminal:close});this.file=path.join(dataDir,'new-session-intents.json');this.intents=fs.existsSync(this.file)?JSON.parse(fs.readFileSync(this.file,'utf8')):{};this.locks=new Map();
 }
 save(){const tmp=this.file+'.tmp';fs.writeFileSync(tmp,JSON.stringify(this.intents),{mode:0o600});fs.renameSync(tmp,this.file);}
 create(id,workspaceId){const key=id+'|'+workspaceId;if(this.locks.has(key))return this.locks.get(key);const p=this.open(id,workspaceId).finally(()=>this.locks.delete(key));this.locks.set(key,p);return p;}
 async open(id,workspaceId){
  const inv=await this.inventory();if(inv.truncated||inv.omittedHostIds?.length)throw Error('Каталог Orca неполный; запуск запрещён');
  const workspace=inv.workspaces.find(w=>w.id===workspaceId&&(!w.hostId||w.hostId==='local'));if(!workspace)throw Error('Локальное пространство не найдено');
  const intent=this.intents[id];if(intent&&intent.workspaceId!==workspaceId)throw Error('Session ID already used for another workspace');
  const owner=this.owners().find(s=>s.id===id);if(owner){if(!inv.terminals.some(t=>t.id===owner.terminalId&&t.workspaceId===workspaceId))throw Error('Этот Pi уже работает вне выбранного пространства');return {type:'resume',sessionId:id,terminalId:owner.terminalId,reused:true};}
  if(intent){if(!intent.terminalId)throw Error('Результат прошлого запуска неизвестен. Повторный запуск запрещён');return {type:'resume',sessionId:id,terminalId:intent.terminalId,reused:true};}
  // Persist before dispatch. Never recreate this id after timeout, disconnect or service restart.
  this.intents[id]={workspaceId,startedAt:Date.now(),terminalId:null};this.save();
  const handle=await this.launch(workspace,id);this.intents[id].terminalId=handle;this.save();
  return {type:'resume',sessionId:id,terminalId:handle,reused:false};
 }
 async close(id,force=false){
  const inv=await this.inventory();if(inv.truncated||inv.omittedHostIds?.length)throw Error('Каталог Orca неполный; закрытие запрещено');
  const owner=this.owners().find(s=>s.id===id);if(!owner)throw Error('Живой Pi не найден');
  if(!inv.terminals.some(t=>t.id===owner.terminalId))throw Error('Вкладка Pi больше не открыта');
  if(owner.status==='running'&&!force)throw Error('Pi работает. Подтверди прерывание задачи');
  await this.closeTerminal(owner.terminalId);
  return {type:'closed',sessionId:id};
 }
}
