import {decodeFile} from './attachments.mjs';

export const MAX_IMAGE=10*1024*1024;
export const safeId=x=>typeof x==='string'&&/^[a-zA-Z0-9._:-]{1,160}$/.test(x);

/** Reject malformed and oversized image data before it reaches either Pi or the media cache. */
export function decodeImage(image){
  if(!image||!['image/png','image/jpeg','image/webp'].includes(image.mimeType)||typeof image.data!=='string'||image.data.length>Math.ceil(MAX_IMAGE/3)*4||image.data.length%4!==0||!/^[A-Za-z0-9+/]*={0,2}$/.test(image.data))throw Error('Invalid image');
  const b=Buffer.from(image.data,'base64');
  if(!b.length||b.length>MAX_IMAGE)throw Error('Image exceeds 10MB');
  if(b.toString('base64')!==image.data)throw Error('Invalid base64 encoding');
  const ok=image.mimeType==='image/png'?b.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex')):image.mimeType==='image/jpeg'?b[0]===255&&b[1]===216&&b[2]===255:b.toString('ascii',0,4)==='RIFF'&&b.toString('ascii',8,12)==='WEBP';
  if(!ok)throw Error('Image signature mismatch');
  return b;
}

/** Pure command validation; server authorization and session ownership stay in gateway. */
export function validateCommand(c){
  if(!c||!safeId(c.requestId)||!safeId(c.sessionId)||!['prompt','abort','configure','history','document','resume','mcp','name','new','close','delete'].includes(c.command))throw Error('Invalid command');
  if(c.command==='new'){
    if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(c.sessionId)||typeof c.workspaceId!=='string'||!c.workspaceId||c.workspaceId.length>4000||/[\x00-\x1f]/.test(c.workspaceId))throw Error('Invalid new session');
    return {type:'command',command:'new',sessionId:c.sessionId,requestId:c.requestId,workspaceId:c.workspaceId};
  }
  if(c.command==='close'||c.command==='delete'){
    if(c.confirm!==true)throw Error('Confirmation required');
    return {type:'command',command:c.command,sessionId:c.sessionId,requestId:c.requestId,confirm:true,force:c.force===true};
  }
  if(c.command==='name'){
    if(typeof c.name!=='string'||!c.name.trim()||c.name.length>200||/[\x00-\x1f]/.test(c.name))throw Error('Invalid session name');
    return {type:'command',command:'name',sessionId:c.sessionId,requestId:c.requestId,name:c.name.trim()};
  }
  if(c.command==='history'){
    if(typeof c.before!=='string'||!c.before||c.before.length>500||!Number.isInteger(c.limit??40)||(c.limit??40)<1||(c.limit??40)>40)throw Error('Invalid history cursor');
    return {type:'command',command:'history',sessionId:c.sessionId,requestId:c.requestId,before:c.before,limit:c.limit??40};
  }
  if(c.command==='document'){
    if(typeof c.path!=='string'||c.path.length>2000||!/\.(md|markdown)$/i.test(c.path)||/[\x00-\x1f]/.test(c.path))throw Error('Invalid Markdown path');
    return {type:'command',command:'document',sessionId:c.sessionId,requestId:c.requestId,path:c.path};
  }
  if(c.command==='abort'||c.command==='resume'||c.command==='mcp')return {type:'command',sessionId:c.sessionId,requestId:c.requestId,command:c.command};
  if(c.command==='configure'){
    const text=x=>typeof x==='string'&&x.length>0&&x.length<=200&&!/[\x00-\x1f]/.test(x);
    if((c.provider!==undefined||c.modelId!==undefined)&&(!text(c.provider)||!text(c.modelId)))throw Error('Invalid model');
    if(c.thinkingLevel!==undefined&&!['off','minimal','low','medium','high','xhigh','max'].includes(c.thinkingLevel))throw Error('Invalid effort');
    if(c.serviceTier!==undefined&&!['standard','fast'].includes(c.serviceTier))throw Error('Invalid service tier');
    if(c.provider===undefined&&c.thinkingLevel===undefined&&c.serviceTier===undefined)throw Error('Empty configuration');
    return {type:'command',sessionId:c.sessionId,requestId:c.requestId,command:'configure',...(c.provider!==undefined?{provider:c.provider,modelId:c.modelId}:{}),...(c.thinkingLevel!==undefined?{thinkingLevel:c.thinkingLevel}:{}),...(c.serviceTier!==undefined?{serviceTier:c.serviceTier}:{})};
  }
  if(typeof c.text!=='string'||c.text.length>100000||!Array.isArray(c.images??[])||(c.images?.length||0)>3)throw Error('Invalid prompt');
  const images=c.images||[]; let size=0;
  for(const image of images)size+=decodeImage(image).length;
  const files=c.files??[];if(!Array.isArray(files)||files.length+images.length>3)throw Error('Не более 3 вложений');
  for(const file of files)size+=decodeFile(file).length;
  if(size>MAX_IMAGE)throw Error('Total attachment size exceeds 10MB');
  if(!c.text.trim()&&!images.length&&!files.length)throw Error('Empty prompt');
  if(c.behavior!==undefined&&!['steer','followUp'].includes(c.behavior))throw Error('Invalid behavior');
  return {type:'command',sessionId:c.sessionId,requestId:c.requestId,command:'prompt',text:c.text,images:images.map(i=>({type:'image',mimeType:i.mimeType,data:i.data})),behavior:c.behavior||'followUp',...(files.length?{files:files.map(f=>({name:f.name,data:f.data}))}:{})};
}
