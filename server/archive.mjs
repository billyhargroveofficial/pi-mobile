import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {resumeInOrca} from './orca.mjs';
const exec=promisify(execFile),root=fileURLToPath(new URL('../',import.meta.url));
export async function listSavedSessions(cwd){
  const {stdout}=await exec(process.execPath,['--import',path.join(root,'scripts/host-runtime.mjs'),path.join(root,'scripts/list-sessions.mjs'),cwd],{timeout:12000,maxBuffer:8*1024*1024});
  return JSON.parse(stdout);
}
export class Archive {
  constructor({dataDir,inventory,owners,list=listSavedSessions,launch=resumeInOrca}){
    Object.assign(this,{inventory,owners,list,launch});this.cache=null;this.loading=null;this.locks=new Map();
    this.file=path.join(dataDir,'resume-intents.json');
    this.intents=fs.existsSync(this.file)?JSON.parse(fs.readFileSync(this.file,'utf8')):{};
  }
  save(){const temp=this.file+'.tmp';fs.writeFileSync(temp,JSON.stringify(this.intents),{mode:0o600});fs.renameSync(temp,this.file);}
  async entries(){
    if(this.cache&&Date.now()-this.cache.at<15000)return this.cache.rows;
    if(this.loading)return this.loading;
    this.loading=(async()=>{
      const inv=await this.inventory();if(inv.truncated||inv.omittedHostIds?.length)throw Error('Каталог Orca неполный; возобновление недоступно');
      const rows=[];
      for(const w of inv.workspaces.filter(w=>!w.hostId||w.hostId==='local')){
        for(const s of await this.list(w.path))if(s.cwd===w.path&&s.messageCount>0)rows.push({...s,workspaceId:w.id,workspaceName:w.name});
      }
      rows.sort((a,b)=>b.modified.localeCompare(a.modified));this.cache={at:Date.now(),rows};return rows;
    })().finally(()=>this.loading=null);return this.loading;
  }
  async page(offset=0,query=''){
    const open=new Set(this.owners().map(s=>s.id));
    const rows=(await this.entries()).filter(s=>!open.has(s.id)&&(!query||`${s.title} ${s.workspaceName}`.toLowerCase().includes(query.toLowerCase())));
    return {type:'archive',sessions:rows.slice(offset,offset+40).map(({path,...s})=>s),hasMore:offset+40<rows.length,nextOffset:offset+40};
  }
  resume(id){
    if(this.locks.has(id))return this.locks.get(id);
    const promise=this.open(id).finally(()=>this.locks.delete(id));this.locks.set(id,promise);return promise;
  }
  async open(id){
    let inv=await this.inventory();
    if(inv.truncated||inv.omittedHostIds?.length)throw Error('Каталог Orca неполный; повторный запуск запрещён');
    let owners=this.owners();const owner=owners.find(s=>s.id===id);
    if(owner){
      if(!inv.terminals.some(t=>t.id===owner.terminalId))throw Error('Pi уже работает вне открытой вкладки. Открой его на Mac; второй процесс не запущен');
      return {type:'resume',sessionId:id,terminalId:owner.terminalId,reused:true};
    }
    const saved=this.intents[id];
    if(saved){
      if(!saved.terminalId)throw Error('Результат прошлого запуска неизвестен. Проверь Orca на Mac; повторный запуск запрещён');
      const switched=owners.some(o=>o.terminalId===saved.terminalId&&o.id!==id);
      if(!switched&&(inv.allTerminalIds.includes(saved.terminalId)||Date.now()-saved.startedAt<30000))return {type:'resume',sessionId:id,terminalId:saved.terminalId,reused:true};
      // Complete inventory proves closure, or a live owner proves /new switched this terminal away.
      delete this.intents[id];this.save();
    }
    const s=(await this.entries()).find(s=>s.id===id);if(!s)throw Error('Сохранённый диалог не найден. Обнови историю');
    // Listing can take time: recheck live ownership immediately before dispatch.
    inv=await this.inventory();owners=this.owners();
    if(inv.truncated||inv.omittedHostIds?.length)throw Error('Каталог Orca неполный; повторный запуск запрещён');
    const arrived=owners.find(o=>o.id===id);
    if(arrived){if(!inv.terminals.some(t=>t.id===arrived.terminalId))throw Error('Pi уже работает вне открытой вкладки');return {type:'resume',sessionId:id,terminalId:arrived.terminalId,reused:true};}
    const w=inv.workspaces.find(w=>w.id===s.workspaceId&&(!w.hostId||w.hostId==='local'));if(!w)throw Error('Рабочее пространство недоступно на Mac');
    if(inv.terminals.some(t=>t.workspaceId===w.id&&!owners.some(o=>o.terminalId===t.id)))throw Error('В этом пространстве есть Pi без моста. Выполни /reload в свободном Pi на Mac, чтобы исключить двойной запуск');
    const fd=fs.openSync(s.path,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);
    try{if(!fs.fstatSync(fd).isFile())throw Error('Not a file');const b=Buffer.alloc(8192),n=fs.readSync(fd,b,0,b.length,0),header=JSON.parse(b.subarray(0,n).toString().split('\n')[0]);if(header.type!=='session'||header.id!==id||header.cwd!==w.path)throw Error('Session identity changed');}finally{fs.closeSync(fd);}
    // Persist BEFORE dispatch: a timeout/crash must not blindly create a second Pi.
    this.intents[id]={startedAt:Date.now(),terminalId:null};this.save();
    let terminalId;
    try { terminalId=await this.launch(w,s); }
    catch(e) { this.intents[id].error=String(e.message).slice(0,1000);this.save();throw e; }
    this.intents[id].terminalId=terminalId;this.save();this.cache=null;
    return {type:'resume',sessionId:id,terminalId,reused:false};
  }
}
