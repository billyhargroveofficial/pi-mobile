/** In-memory, per-gateway cache for already-validated image bytes, never raw bridge frames. */
export class MediaCache {
 constructor(maxBytes=128*1024*1024){
  if(!Number.isSafeInteger(maxBytes)||maxBytes<1)throw Error('Invalid media cache capacity');
  this.maxBytes=maxBytes;this.bytes=0;this.items=new Map();
 }
 get(key){return this.items.get(key);}
 put(key,data,mimeType){
  if(typeof key!=='string'||!Buffer.isBuffer(data)||!data.length||data.length>this.maxBytes)throw Error('Invalid cached media');
  if(this.items.has(key))return false;
  while(this.bytes+data.length>this.maxBytes&&this.items.size){
   const oldest=this.items.keys().next().value;this.bytes-=this.items.get(oldest).data.length;this.items.delete(oldest);
  }
  this.items.set(key,{data,mimeType});this.bytes+=data.length;return true;
 }
}
