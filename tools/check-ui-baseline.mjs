import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../docs/ui-baseline/v0.6.003');
const manifest=JSON.parse(fs.readFileSync(path.join(root,'manifest.json'),'utf8'));
const actual=fs.readdirSync(root).filter(name=>name.endsWith('.png')).sort();
const expected=Object.keys(manifest.files).sort();
if(JSON.stringify(actual)!==JSON.stringify(expected))throw Error('UI baseline file set differs from manifest');
for(const name of expected){
 const bytes=fs.readFileSync(path.join(root,name));
 if(bytes.length<24||!bytes.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex')))throw Error('Invalid PNG: '+name);
 const {sha256,width,height}=manifest.files[name];
 if(crypto.createHash('sha256').update(bytes).digest('hex')!==sha256||bytes.readUInt32BE(16)!==width||bytes.readUInt32BE(20)!==height)throw Error('Changed UI baseline: '+name);
}
console.log(`UI baseline: ${expected.length} pinned synthetic screenshots verified`);
