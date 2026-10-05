import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {fileURLToPath} from 'node:url';

// Version-specific, fail-closed read-only compatibility seam. Does not spawn,
// stop, reload or consume any agent. Runtime ownership metadata prevents session
// switching from revealing another session's workflow tree.
export const insertion=`    // pi-mobile-readonly-v2: observation only, scoped to the owning session.
    mobileReadState: () => { const sessionId = currentCtx?.sessionManager?.getSessionId?.(); return { version: 1, sessionId, agents: manager.listAgents(), workflows: [...workflowTasks.values()].filter(task => mobileWorkflowOwners.get(task.id) === sessionId) }; },
`;
const previous=`    // pi-mobile-readonly-v1: observational access only, scoped to the owning session.
    mobileReadState: () => ({ version: 1, sessionId: currentCtx?.sessionManager?.getSessionId?.(), agents: manager.listAgents(), workflows: [...workflowTasks.values()] }),
`;
export const ownerDeclaration='  const mobileWorkflowOwners = new Map<string, string>(); // pi-mobile-readonly-v2\n';
export const ownerTool='      mobileWorkflowOwners.set(runId, ctx.sessionManager.getSessionId()); // pi-mobile-readonly-v2\n';
export const ownerFile='    mobileWorkflowOwners.set(task.id, ctx.sessionManager.getSessionId()); // pi-mobile-readonly-v2\n';
export function patchSource(source,remove=false){
 let text=source.replace(previous,'');
 if(remove)return text.replace(insertion,'').replace(ownerDeclaration,'').replace(ownerTool,'').replace(ownerFile,'');
 const anchor='  const registryEntry = {\n',mapAnchor='  const workflowTasks = new Map<string, WorkflowTask>();\n',toolAnchor='      workflowTasks.set(runId, task);\n',fileAnchor='    workflowTasks.set(task.id, task);\n';
 if([anchor,mapAnchor,toolAnchor,fileAnchor].some(a=>text.split(a).length!==2)||!text.includes('let currentCtx: ExtensionContext | undefined'))throw Error('Unsupported pi-subagents source; observer was not installed');
 if(!text.includes(insertion))text=text.replace(anchor,anchor+insertion);
 if(!text.includes(ownerDeclaration))text=text.replace(mapAnchor,mapAnchor+ownerDeclaration);
 if(!text.includes(ownerTool))text=text.replace(toolAnchor,toolAnchor+ownerTool);
 if(!text.includes(ownerFile))text=text.replace(fileAnchor,fileAnchor+ownerFile);
 return text;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const file=path.join(os.homedir(),'.pi/agent/npm/node_modules/@tintinweb/pi-subagents/src/index.ts'),pkg=JSON.parse(fs.readFileSync(path.join(path.dirname(file),'../package.json'),'utf8'));
 if(pkg.version!=='0.19.0')throw Error('Unsupported pi-subagents '+pkg.version+'; observer not installed');
 const source=fs.readFileSync(file,'utf8'),next=patchSource(source,process.argv.includes('--remove'));
 if(next!==source){if(!fs.existsSync(file+'.pi-mobile-original'))fs.copyFileSync(file,file+'.pi-mobile-original');fs.writeFileSync(file,next);}
 console.log('Read-only observer '+(process.argv.includes('--remove')?'removed':'installed')+'. Existing Pi processes are unchanged; reload only when idle.');
}
