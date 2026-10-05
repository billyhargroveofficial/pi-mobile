import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile);
export async function readOrca(){
  const run=async(args)=>{const {stdout}=await exec(process.env.ORCA_CLI_COMMAND||'/usr/local/bin/orca',[...args,'--json','--limit','1000'],{timeout:6000,maxBuffer:8*1024*1024}); const data=JSON.parse(stdout); if(!data.ok)throw Error('Orca query failed'); return data.result;};
  const [w,t]=await Promise.all([run(['worktree','list']),run(['terminal','list'])]);
  return {workspaces:w.worktrees.filter(w=>!w.isArchived).map(w=>({id:w.id,name:w.displayName||w.path,path:w.path,hostId:w.hostId})),
    terminals:t.terminals.filter(t=>t.agentIdentity==='pi').map(t=>({id:t.handle,title:t.title,workspaceId:t.worktreeId,path:t.worktreePath,agent:'pi',connected:false,hostId:t.executionHostId})),
    truncated:!!(w.truncated||t.truncated),omittedHostIds:[...new Set([...(w.hostScope?.omittedHostIds||[]),...(t.hostScope?.omittedHostIds||[])])]};
}
