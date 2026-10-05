import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile);
export async function orcaCommand(args){
  let stdout;
  try { ({stdout}=await exec(process.env.ORCA_CLI_COMMAND||'/usr/local/bin/orca',[...args,'--json'],{timeout:12000,maxBuffer:8*1024*1024})); }
  catch(e) { let result;try{result=JSON.parse(e.stdout);}catch{}throw Error(result?.error?.message||String(e.stderr||'Orca command failed or timed out').slice(0,500)); }
  const data=JSON.parse(stdout);if(!data.ok)throw Error(data.error?.message||'Orca query failed');return data.result;
}
export function visibleTerminals(t){
  // Layout membership is authoritative; background/orphan handles are not open tabs.
  if(!Array.isArray(t.visualLayouts))throw Error('Orca did not return visual layouts');
  const visible=new Set();
  function visit(node){if(!node||typeof node!=='object')return;if(node.type==='terminal'&&node.handle)visible.add(node.handle);for(const value of Object.values(node)){if(Array.isArray(value))value.forEach(visit);else if(value&&typeof value==='object')visit(value);}}
  t.visualLayouts.forEach(l=>visit(l.root));
  return t.terminals.filter(t=>visible.has(t.handle)&&!t.orphaned&&t.connected===true);
}
export async function readOrca(){
  const [w,t]=await Promise.all([orcaCommand(['worktree','list','--limit','1000']),orcaCommand(['terminal','list','--include-visual-layouts','--limit','1000'])]);
  return {workspaces:w.worktrees.filter(w=>!w.isArchived).map(w=>({id:w.id,name:w.displayName||w.path,path:w.path,hostId:w.hostId})),
    terminals:visibleTerminals(t).filter(t=>t.agentIdentity==='pi').map(t=>({id:t.handle,title:t.title,workspaceId:t.worktreeId,path:t.worktreePath,agent:'pi',connected:false,hostId:t.executionHostId})),
    allTerminalIds:t.terminals.map(t=>t.handle),
    truncated:!!(w.truncated||t.truncated),omittedHostIds:[...new Set([...(w.hostScope?.omittedHostIds||[]),...(t.hostScope?.omittedHostIds||[])])]};
}
const quote=value=>"'"+String(value).replaceAll("'","'\\''")+"'";
export async function resumeInOrca(workspace,session){
  // Only server-discovered paths reach here; never execute a client-provided command.
  const result=await orcaCommand(['terminal','create','--worktree','id:'+workspace.id,'--title',session.title,'--command',`pi --session ${quote(session.path)}`]);
  const terminal=result.terminal;
  if(!terminal?.handle)throw Error('Orca launch result unknown; inspect Mac before retrying');
  return terminal.handle;
}
