import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
export const LIMIT=10*1024*1024;
export function decodeFile(file){
 if(!file||typeof file.name!=='string'||!file.name.trim()||file.name.length>200||/[\\/\x00-\x1f\x7f]/.test(file.name)||file.name==='.'||file.name==='..')throw Error('Invalid attachment name');
 if(typeof file.data!=='string'||file.data.length>Math.ceil(LIMIT/3)*4||file.data.length%4!==0||! /^[A-Za-z0-9+/]*={0,2}$/.test(file.data))throw Error('Invalid attachment encoding');
 const bytes=Buffer.from(file.data,'base64');if(!bytes.length||bytes.length>LIMIT||bytes.toString('base64')!==file.data)throw Error('Invalid attachment size');return bytes;
}
const marker='\n\nПрикреплённые файлы Pi Mobile (локальные пути для чтения инструментами):\n';
export function attachFiles(command,dataDir){
 if(!command.files?.length)return command;
 const root=path.join(dataDir,'uploads');fs.mkdirSync(root,{recursive:true,mode:0o700});
 if(fs.lstatSync(root).isSymbolicLink())throw Error('Invalid upload directory');
 let used=0;for(const entry of fs.readdirSync(root,{withFileTypes:true}))if(entry.isFile())used+=fs.statSync(path.join(root,entry.name)).size;
 const lines=[];
 for(const f of command.files){const bytes=decodeFile(f),digest=crypto.createHash('sha256').update(bytes).digest('hex'),target=path.join(root,digest+path.extname(f.name).replace(/[^a-zA-Z0-9.]/g,'').slice(0,16));
  if(!fs.existsSync(target)){if(used+bytes.length>256*1024*1024)throw Error('Хранилище вложений заполнено (256 MiB). Освободи uploads на компьютере');fs.writeFileSync(target,bytes,{mode:0o600,flag:'wx'});used+=bytes.length;}
  else if(!fs.lstatSync(target).isFile()||fs.lstatSync(target).isSymbolicLink())throw Error('Invalid attachment file');
  lines.push(JSON.stringify({name:f.name,path:target}));
 }
 const {files,...out}=command;return {...out,text:command.text+marker+lines.join('\n')};
}
export function attachmentDisplay(text,dataDir){
 const at=text.lastIndexOf(marker);if(at<0)return text;
 try{const records=text.slice(at+marker.length).split('\n').map(x=>JSON.parse(x));const root=path.resolve(dataDir,'uploads');if(!records.length||records.length>3||records.some(x=>typeof x.path!=='string'||path.dirname(x.path)!==root||typeof x.name!=='string'||/[\r\n]/.test(x.name)))return text;return text.slice(0,at)+(at?'\n':'')+records.map(x=>'📎 '+x.name).join('\n');}catch{return text;}
}
