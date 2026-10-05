// Read metadata using Pi's installed parser, without starting an agent or loading extensions.
import {SessionManager} from '@earendil-works/pi-coding-agent';
const sessions=await SessionManager.list(process.argv[2]);
console.log(JSON.stringify(sessions.map(s=>({id:s.id,path:s.path,cwd:s.cwd,title:(s.name||s.firstMessage||'Без названия').replace(/\s+/g,' ').slice(0,160),modified:s.modified.toISOString(),messageCount:s.messageCount}))));
