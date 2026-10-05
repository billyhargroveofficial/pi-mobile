// Read metadata using Pi's installed parser, without starting an agent or loading extensions.
import {SessionManager} from '@earendil-works/pi-coding-agent';
import {isHumanSession} from '../server/session-visibility.mjs';
const sessions=await SessionManager.list(process.argv[2]);
console.log(JSON.stringify(sessions.filter(isHumanSession).map(s=>({id:s.id,path:s.path,cwd:s.cwd,title:(s.name||s.firstMessage||'Без названия').replace(/\s+/g,' ').slice(0,160),modified:s.modified.toISOString(),messageCount:s.messageCount}))));
