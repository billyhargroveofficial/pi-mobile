import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const project=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const namespace='ru.billyhargrove.pimobile';
const layerRules={core:new Set(['core']),store:new Set(['store','core']),media:new Set(['media','core']),net:new Set(['net','core','store','media'])};

export function checkAndroidImports(relative,source){
 const errors=[],match=relative.match(/^android\/app\/src\/main\/java\/ru\/billyhargrove\/pimobile\/(.+)\.(java|kt)$/);
 if(!match)return errors;
 const declared=source.match(/^\s*package\s+([\w.]+)/m)?.[1],directory=path.posix.dirname(match[1]),expected=directory==='.'?namespace:namespace+'.'+directory.replaceAll('/','.');
 if(declared!==expected)errors.push(`${relative}: expected package ${expected}, got ${declared||'(missing)'}`);
 const owner=match[1].split('/')[0],allowed=layerRules[owner];
 if(owner==='ui'&&/\bru\.billyhargrove\.pimobile\.PiApp\b/.test(source)&&!['ui/ImageViewer','ui/ArchiveSheet'].includes(match[1]))errors.push(`${relative}: UI must not add a new app-shell dependency`);
 if(!allowed)return errors;
 // Detect explicit imports and qualified references; skip the package declaration.
 const body=source.replace(/^\s*package\s+[\w.]+\s*;?/m,'');
 const refs=body.matchAll(/\bru\.billyhargrove\.pimobile(?:\.([\w]+))?\b/g);
 for(const ref of refs){const dependency=ref[1]||'(shell)';if(!allowed.has(dependency)&&!['BuildConfig','R'].includes(dependency))errors.push(`${relative}: ${owner} must not depend on ${dependency}`);}
 return [...new Set(errors)];
}

export function checkLaneImport(relative,specifier){
 if(!specifier.startsWith('.'))return null;
 const origin=relative.split('/')[0];if(!['server','extension','contracts'].includes(origin))return null;
 const target=path.posix.normalize(path.posix.join(path.posix.dirname(relative),specifier)).split('/')[0];
 if(target==='server'&&origin!=='server'||target==='extension'&&origin!=='extension'||origin==='contracts'&&target!=='contracts')return `${relative}: ${origin} must not import ${target}`;
 return null;
}
function walk(dir){return fs.existsSync(dir)?fs.readdirSync(dir,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?walk(path.join(dir,entry.name)):[path.join(dir,entry.name)]):[];}
export function checkArchitecture(root=project){
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'architecture/owners.json'),'utf8'));
 const sources=walk(path.join(root,manifest.androidSourceRoot)).filter(f=>/\.(java|kt)$/.test(f));
 const errors=[],seen=new Map(),assigned=new Map();
 const declarations=[...Object.entries(manifest.owners).flatMap(([owner,classes])=>classes.map(base=>[owner,base])),...Object.entries(manifest.platformClasses).flatMap(([owner,classes])=>classes.map(name=>[owner,`${owner}/${name}`]))];
 for(const [owner,base] of declarations){
  if(assigned.has(base))errors.push(`Duplicate owner for ${base}: ${assigned.get(base)}, ${owner}`);
  assigned.set(base,owner);
  const variants=['.java','.kt'].filter(ext=>fs.existsSync(path.join(root,manifest.androidSourceRoot,base+ext)));
  if(variants.length!==1)errors.push(`${owner}: ${base} must have exactly one Java or Kotlin implementation (found ${variants.length})`);
 }
 for(const file of sources){const relative=path.relative(root,file).split(path.sep).join('/'),base=relative.slice((manifest.androidSourceRoot+'/').length).replace(/\.(java|kt)$/,'');
  const first=base.split('/')[0];if(!assigned.has(base)&&first!=='features')errors.push(`${relative}: no source ownership registered`);
  if(first==='features'&&!Object.hasOwn(manifest.owners,base.split('/')[1]))errors.push(`${relative}: unknown feature capsule`);
  seen.set(base,(seen.get(base)||0)+1);errors.push(...checkAndroidImports(relative,fs.readFileSync(file,'utf8')));
 }
 for(const [base,count] of seen)if(count>1)errors.push(`${base}: dual Java/Kotlin implementation`);
 for(const folder of ['server','extension','contracts'])for(const file of walk(path.join(root,folder)).filter(f=>/\.(mjs|js|ts)$/.test(f))){
  const relative=path.relative(root,file).split(path.sep).join('/'),source=fs.readFileSync(file,'utf8');
  for(const m of source.matchAll(/\b(?:from\s*|import\s*\(|require\s*\()\s*['"]([^'"]+)['"]/g)){
   const error=checkLaneImport(relative,m[1]);if(error)errors.push(error);
  }
 }
 return [...new Set(errors)].sort();
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const errors=checkArchitecture();if(errors.length){console.error(errors.join('\n'));process.exitCode=1;}
 else console.log('Architecture: owner inventory, Java/Kotlin uniqueness and dependency boundaries verified');
}
