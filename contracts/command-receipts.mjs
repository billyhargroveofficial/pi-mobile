const mutations=new Set(['prompt','abort','configure','name','resume','new','close','delete']);

/** Bounded mutation deduplication shared by host runtimes. Read payloads are never retained. */
export class CommandReceipts {
  constructor(limit=1000){
    if(!Number.isSafeInteger(limit)||limit<1)throw Error('Invalid receipt capacity');
    this.limit=limit;this.entries=new Map();
  }
  get(id){return this.entries.get(id);}
  remember(command,id,value){
    if(!mutations.has(command)||typeof id!=='string'||!id)return;
    this.entries.set(id,value);
    while(this.entries.size>this.limit)this.entries.delete(this.entries.keys().next().value);
  }
  clear(){this.entries.clear();}
  get size(){return this.entries.size;}
}
