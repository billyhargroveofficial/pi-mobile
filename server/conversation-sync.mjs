import crypto from 'node:crypto';
const digest=m=>crypto.createHash('sha256').update(JSON.stringify(m)).digest('hex');
/** A checkpoint contains hashes, never message bodies. Its stream changes on gateway restart. */
export function checkpoint(stream,epoch,messages){return {stream,epoch,rows:messages.map(m=>[m.id,digest(m)])};}
export function resumeDelta(current,resume){
 const point=current.checkpoint;
 if(!resume||resume.stream!==point.stream||resume.epoch!==point.epoch||!Array.isArray(resume.rows)||resume.rows.length>400)return null;
 if(resume.rows.some(r=>!Array.isArray(r)||r.length!==2||typeof r[0]!=='string'||r[0].length>500||typeof r[1]!=='string'||! /^[a-f0-9]{64}$/.test(r[1])))return null;
 const old=new Map(resume.rows),ids=new Set(current.messages.map(m=>m.id));if(old.size!==resume.rows.length)return null;
 const hashes=new Map(point.rows),oldIds=[...old.keys()];
 // Prefix eviction from a bounded live window is not deletion from the conversation.
 const overlap=oldIds.indexOf(current.messages[0]?.id),trimmed=current.history?.hasMore===true;
 const removable=trimmed?(overlap>=0?oldIds.slice(overlap):[]):oldIds;
 return {...current,type:'messages',resumed:true,messages:current.messages.filter(m=>old.get(m.id)!==hashes.get(m.id)),removedIds:removable.filter(id=>!ids.has(id)),order:current.messages.map(m=>m.id)};
}
