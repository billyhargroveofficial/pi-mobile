import net from 'node:net';
import path from 'node:path';
import {homedir} from 'node:os';
import {getSupportedThinkingLevels} from '@earendil-works/pi-ai/compat';
import type {ExtensionAPI,ExtensionContext} from '@earendil-works/pi-coding-agent';

// Session-scoped resources only. No second Pi process, no access to auth secrets.
export default function mobile(pi:ExtensionAPI){
  let ctx:ExtensionContext|undefined,socket:net.Socket|undefined,retry:ReturnType<typeof setTimeout>|undefined,timer:ReturnType<typeof setTimeout>|undefined;
  let enabled=false,registered=false,buffer='',generation=0,running=false,dirty=false;
  let transcript:any[]=[],partial:any=null,truncated=false;
  const tools=new Map<string,any>(),seen=new Map<string,any>();
  let commands=Promise.resolve();
  const socketPath=process.env.PI_MOBILE_SOCKET||path.join(process.env.PI_MOBILE_DIR||path.join(homedir(),'.pi/agent/pi-mobile'),'bridge.sock');
  function metadata(){
    const available=ctx?.modelRegistry?.getAvailable()||[];
    return {id:ctx!.sessionManager.getSessionId(),title:pi.getSessionName()||path.basename(ctx!.cwd),cwd:ctx!.cwd,pid:process.pid,terminalId:process.env.ORCA_TERMINAL_HANDLE||'',workspaceId:process.env.ORCA_WORKTREE_ID||'',model:ctx?.model?`${ctx.model.provider}/${ctx.model.id}`:'',thinkingLevel:pi.getThinkingLevel?.()||'off',models:available.slice(0,1000).map(m=>({provider:m.provider,id:m.id,name:m.name||m.id,thinkingLevels:getSupportedThinkingLevels(m)})),modelsTruncated:available.length>1000};
  }
  function load(){
    const messages=ctx!.sessionManager.getBranch().filter((e:any)=>e.type==='message').map((e:any)=>({...e.message,id:e.id}));
    truncated=messages.length>200;transcript=messages.slice(-200);partial=null;tools.clear();
  }
  function send(obj:any){
    if(!socket||socket.destroyed||!registered)return false;
    try{const line=JSON.stringify(obj)+'\n';if(Buffer.byteLength(line)>30*1024*1024){ctx?.ui.setStatus('pi-mobile','Mobile: history too large');return false;}if(socket.writableLength>1024*1024){dirty=true;return false;}socket.write(line);return true;}catch{return false;}
  }
  function boundedMessages(){
    let out=partial?[...transcript,partial]:[...transcript];
    out.push(...tools.values());
    let bytes=0,start=out.length;
    for(let i=out.length-1;i>=0;i--){const n=Buffer.byteLength(JSON.stringify(out[i]));if(bytes+n>24*1024*1024)break;bytes+=n;start=i;}
    if(start>0)truncated=true;return out.slice(start);
  }
  function flush(){timer=undefined;if(!enabled||!ctx)return;dirty=false;
    send({type:'snapshot',id:ctx.sessionManager.getSessionId(),meta:metadata(),messages:boundedMessages(),status:running?'running':'idle',truncated});
  }
  function schedule(){dirty=true;if(!timer){timer=setTimeout(flush,100);timer.unref();}}
  function disconnect(){generation++;clearTimeout(timer);timer=undefined;clearTimeout(retry);retry=undefined;registered=false;socket?.destroy();socket=undefined;buffer='';}
  async function command(c:any,gen:number){
    if(gen!==generation||!ctx)return;
    try{
      if(c.type!=='command'||c.sessionId!==ctx.sessionManager.getSessionId())throw Error('Session changed');
      if(typeof c.requestId!=='string')throw Error('Invalid request');
      if(seen.has(c.requestId)){send(seen.get(c.requestId));return;}
      if(c.command==='abort')ctx.abort();
      else if(c.command==='configure'){
        // Changes affect this session only, never global defaults or another Pi.
        if(running||!ctx.isIdle())throw Error('Дождись завершения текущей задачи перед сменой модели или effort');
        const target=c.provider&&c.modelId?ctx.modelRegistry.getAvailable().find(m=>m.provider===c.provider&&m.id===c.modelId):ctx.model;
        if(!target)throw Error('Модель недоступна в этом Pi');
        if(c.thinkingLevel!==undefined&&!getSupportedThinkingLevels(target).includes(c.thinkingLevel))throw Error('Этот effort не поддерживается моделью');
        if(c.provider&&c.modelId&&!(await pi.setModel(target)))throw Error('Для модели не настроен доступ');
        if(gen!==generation||c.sessionId!==ctx.sessionManager.getSessionId())throw Error('Session changed during configuration');
        if(c.thinkingLevel!==undefined)pi.setThinkingLevel(c.thinkingLevel);
        schedule();
      }else if(c.command==='prompt'){
        if(typeof c.text!=='string'||c.text.length>100000||!Array.isArray(c.images||[])||(c.images?.length||0)>3)throw Error('Invalid prompt');
        const images=(c.images||[]).map((i:any)=>{if(i.type!=='image'||!['image/png','image/jpeg','image/webp'].includes(i.mimeType)||typeof i.data!=='string'||i.data.length>14*1024*1024)throw Error('Invalid image');return {type:'image' as const,data:i.data,mimeType:i.mimeType};});
        const content=images.length?[{type:'text' as const,text:c.text||'Посмотри изображение'},...images]:c.text;
        pi.sendUserMessage(content,{deliverAs:c.behavior==='steer'?'steer':'followUp',expandPromptTemplates:false});
      }else throw Error('Unsupported command');
      const ack={type:'ack',requestId:c.requestId,ok:true};seen.set(c.requestId,ack);if(seen.size>1000)seen.delete(seen.keys().next().value!);send(ack);
    }catch(e){const ack={type:'ack',requestId:c?.requestId,ok:false,error:e instanceof Error?e.message:'Command failed'};if(c?.requestId)seen.set(c.requestId,ack);send(ack);}
  }
  function connect(){
    if(!enabled||!ctx||socket)return;const gen=generation;const s=net.connect(socketPath);socket=s;buffer='';s.setEncoding('utf8');
    s.on('connect',()=>{if(gen!==generation)return;registered=true;send({type:'register',meta:metadata(),messages:boundedMessages(),status:running?'running':'idle',truncated});ctx?.ui.setStatus('pi-mobile','Mobile connected');});
    s.on('error',()=>{});
    s.on('drain',()=>{if(dirty)schedule();});
    s.on('close',()=>{if(gen!==generation)return;registered=false;socket=undefined;ctx?.ui.setStatus('pi-mobile','Mobile offline');if(enabled){retry=setTimeout(connect,3000);retry.unref();}});
    s.on('data',chunk=>{if(gen!==generation)return;buffer+=chunk;if(Buffer.byteLength(buffer)>16*1024*1024){s.destroy();return;}let pos;while((pos=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,pos);buffer=buffer.slice(pos+1);try{const c=JSON.parse(line);commands=commands.then(()=>command(c,gen));}catch{s.destroy();return;}}});
  }
  function remember(m:any){
    if(m.role==='assistant')partial=null;
    if(m.toolCallId)tools.delete(m.toolCallId);
    const last=transcript[transcript.length-1];
    if(last&&last.role===m.role&&last.timestamp===m.timestamp&&last.toolCallId===m.toolCallId)transcript[transcript.length-1]=m;else transcript.push(m);
    if(transcript.length>200){transcript=transcript.slice(-200);truncated=true;}
  }
  function tool(e:any,status:string){
    if(!enabled)return;
    const result=e.partialResult||e.result;
    tools.set(e.toolCallId,{role:'toolResult',toolCallId:e.toolCallId,toolName:e.toolName,toolArgs:e.args??tools.get(e.toolCallId)?.toolArgs,toolStatus:status,content:result?.content||[],isError:e.isError===true,timestamp:Date.now()});
    if(tools.size>200)tools.delete(tools.keys().next().value!);
    schedule();
  }
  pi.on('session_start',(_e,c)=>{disconnect();ctx=c;enabled=c.mode==='tui';if(!enabled)return;running=!c.isIdle();load();connect();});
  pi.on('session_shutdown',()=>{enabled=false;disconnect();ctx=undefined;transcript=[];partial=null;tools.clear();seen.clear();});
  pi.on('message_start',(e,c)=>{ctx=c;if(!enabled)return;if(e.message.role==='assistant')partial=e.message;schedule();});
  pi.on('message_update',(e,c)=>{ctx=c;if(!enabled)return;partial=e.message;schedule();});
  pi.on('message_end',(e,c)=>{ctx=c;if(!enabled)return;remember(e.message);schedule();});
  pi.on('tool_execution_start',(e,c)=>{ctx=c;tool(e,'running');});
  pi.on('tool_execution_update',(e,c)=>{ctx=c;tool(e,'running');});
  pi.on('tool_execution_end',(e,c)=>{ctx=c;tool(e,e.isError?'error':'done');});
  pi.on('agent_start',(_e,c)=>{ctx=c;running=true;if(enabled)schedule();});
  pi.on('agent_settled',(_e,c)=>{ctx=c;running=false;for(const t of tools.values())if(t.toolStatus==='running')t.toolStatus='interrupted';if(enabled)schedule();});
  pi.on('session_tree',(_e,c)=>{ctx=c;if(enabled){load();schedule();}});
  pi.on('session_info_changed',(_e,c)=>{ctx=c;if(enabled)schedule();});
  pi.on('model_select',(_e,c)=>{ctx=c;if(enabled)schedule();});
  pi.on('thinking_level_select',(_e,c)=>{ctx=c;if(enabled)schedule();});
  pi.registerCommand('mobile',{description:'Pi Mobile bridge: status | start | stop',handler:async(args,c)=>{
    ctx=c;if(c.mode!=='tui'){c.ui.notify('Mobile bridge requires interactive Pi','warning');return;}
    if(args.trim()==='stop'){enabled=false;disconnect();c.ui.setStatus('pi-mobile',undefined);}
    else if(args.trim()==='start'){enabled=true;load();running=!c.isIdle();connect();}
    c.ui.notify(registered?'Pi Mobile connected':enabled?'Pi Mobile waiting for gateway':'Pi Mobile stopped','info');
  }});
}
