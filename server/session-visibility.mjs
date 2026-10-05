import {openSync,readSync,closeSync} from 'node:fs';

// Mirrors the installed @vanillagreen/pi-session-manager /sessions filter.
// Parentage alone is NOT a subagent: human forks also have parentSessionPath.
// Use the first session_info before any message, so renaming a child cannot expose it.
export function isHumanSession(session){
  if(!session.parentSessionPath)return true;
  let fd;
  try{
    fd=openSync(session.path,'r');const head=Buffer.alloc(8192),length=readSync(fd,head,0,head.length,0);
    for(const line of head.toString('utf8',0,length).split('\n').slice(1)){
      if(/"type"\s*:\s*"message"/.test(line))break;
      if(!/"type"\s*:\s*"session_info"/.test(line))continue;
      const entry=JSON.parse(line);
      return !(typeof entry.name==='string'&&/#[0-9a-f]{8}$/i.test(entry.name.trim()));
    }
  }catch{
    // Unknown/unreadable sessions stay visible; do not hide a human conversation by guesswork.
  }finally{if(fd!==undefined)closeSync(fd);}
  return true;
}
