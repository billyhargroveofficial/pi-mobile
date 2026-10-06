import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const project=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const namespace='ru.billyhargrove.pimobile';
const layerRules={core:new Set(['core']),store:new Set(['store','core']),media:new Set(['media','core']),net:new Set(['net','core','store','media'])};

// Skip comments/literals, but retain Kotlin interpolation expressions as code.
// Nested quoted URLs inside ${...} must not hide subsequent wildcard references.
function referenceCode(source){
 let result='',depth=0;const stack=[];
 for(let i=0;i<source.length;i++){
  const c=source[i],pair=source.slice(i,i+2),frame=stack.at(-1);
  if(depth){
   if(pair==='/*'){depth++;i++;result+='  ';}
   else if(pair==='*/'){depth--;i++;result+='  ';}
   else result+=c==='\n'?'\n':' ';
  }else if(frame?.quote){
   if(frame.quote!=="'"&&pair==='${'){stack.push({braces:1});result+=' {';i++;}
   else if(frame.quote!=="'"&&c==='$'&&/[a-zA-Z_]/.test(source[i+1]||'')){
    const name=source.slice(i+1).match(/^\w+/)[0];result+=' '+name;i+=name.length;
   }else if(source.slice(i,i+frame.quote.length)===frame.quote){result+=' '.repeat(frame.quote.length);i+=frame.quote.length-1;stack.pop();}
   else if(frame.quote!=='"""'&&c==='\\'){result+='  ';i++;}
   else result+=c==='\n'?'\n':' ';
  }else if(pair==='//'){
   while(i<source.length&&source[i]!=='\n'){result+=' ';i++;}if(i<source.length)result+='\n';
  }else if(pair==='/*'){depth=1;i++;result+='  ';}
  else if(source.slice(i,i+3)==='"""'){stack.push({quote:'"""'});result+='   ';i+=2;}
  else if(c==='"'||c==="'"){stack.push({quote:c});result+=' ';}
  else {
   result+=c;
   if(frame?.braces&&c==='{')frame.braces++;
   else if(frame?.braces&&c==='}'&&--frame.braces===0)stack.pop();
  }
 }
 return result;
}

// Kotlin files can export functions/types whose names differ from the filename.
export function topLevelSymbols(source){
 const code=referenceCode(source),symbols=[];let cursor=0,depth=0,parens=0;
 for(const match of code.matchAll(/\b(?:class|object|interface|typealias|fun|val|var)\s+(?:<(?:[^<>]|<[^<>]*>)*>\s*)?([a-zA-Z_][\w.]*)/g)){
  for(;cursor<match.index;cursor++){
   if(code[cursor]==='{')depth++;else if(code[cursor]==='}')depth--;
   else if(code[cursor]==='(')parens++;else if(code[cursor]===')')parens--;
  }
  const prefix=code.slice(Math.max(code.lastIndexOf('\n',match.index),code.lastIndexOf(';',match.index))+1,match.index);
  if(depth===0&&parens===0&&!/\bprivate\b/.test(prefix))symbols.push(match[1].split('.').at(-1));
 }
 return [...new Set(symbols)];
}

/** Ownership applies to shared ui/ too; only named public APIs cross features. */
export function checkOwnerImports(relative,source,manifest,symbols={}){
 const prefix=manifest.androidSourceRoot+'/',base=relative.startsWith(prefix)?relative.slice(prefix.length).replace(/\.(java|kt)$/,''):null;
 const assigned=new Map(Object.entries(manifest.owners).flatMap(([owner,bases])=>bases.map(name=>[name,owner]))),owner=assigned.get(base);
 if(!owner)return [];
 const clean=referenceCode(source),imports=[...clean.matchAll(/(?:^|[;\n])\s*import\s+(?:static\s+)?(ru\.billyhargrove\.pimobile\.[\w.*]+)/g)].map(m=>m[1]);
 const body=clean.replace(/\b(?:package|import(?:\s+static)?)\s+[\w.*]+(?:\s+as\s+\w+)?\s*;?/g,''),publicApis=new Set(Object.values(manifest.publicFeatureApis||{}).flat());
 const roots=new Set(manifest.compositionRoots||[]),interop=new Set(manifest.platformInteropDependencies?.[base]||[]),errors=[];
 for(const [target,targetOwner] of assigned){
  if(targetOwner===owner||targetOwner==='ui-foundation')continue;
  const pkg=namespace+(path.posix.dirname(target)==='.'?'':'.'+path.posix.dirname(target).replaceAll('/','.'));
  const referenced=[path.posix.basename(target),...(symbols[target]||[])].some(name=>{
   const fq=pkg+'.'+name,imported=imports.some(value=>value===fq||value.startsWith(fq+'.'));
   const qualified=new RegExp('\\b'+fq.replaceAll('.','\\.')+'\\b').test(body),simple=new RegExp('\\b'+name+'\\b').test(body);
   const visible=imports.includes(pkg+'.*')||path.posix.dirname(base)===path.posix.dirname(target);
   return imported||qualified||visible&&simple;
  });
  if(!referenced)continue;
  if(interop.has(target)||targetOwner==='app-shell'&&roots.has(base))continue;
  // Shared presentation cannot start depending on another feature's facade.
  if(owner!=='ui-foundation'&&publicApis.has(target)&&(!target.endsWith('Activity')||roots.has(base)))continue;
  errors.push(`${relative}: ${owner} must not depend on ${targetOwner} implementation ${target}; use its public API`);
 }
 return errors;
}

export function checkAndroidImports(relative,source){
 const errors=[],match=relative.match(/^android\/app\/src\/main\/java\/ru\/billyhargrove\/pimobile\/(.+)\.(java|kt)$/);
 if(!match)return errors;
 const declared=source.match(/^\s*package\s+([\w.]+)/m)?.[1],directory=path.posix.dirname(match[1]),expected=directory==='.'?namespace:namespace+'.'+directory.replaceAll('/','.');
 if(declared!==expected)errors.push(`${relative}: expected package ${expected}, got ${declared||'(missing)'}`);
 const owner=match[1].split('/')[0],allowed=layerRules[owner];
 if(owner==='ui'&&/\bru\.billyhargrove\.pimobile\.PiApp\b/.test(source)&&!['ui/ImageViewer','ui/ArchiveSheet'].includes(match[1]))errors.push(`${relative}: UI must not add a new app-shell dependency`);
 // Detect explicit imports and qualified references; skip the package declaration.
 const body=source.replace(/^\s*package\s+[\w.]+\s*;?/m,'');
 if(owner==='features'){
  const capsule=match[1].split('/')[1];
  for(const ref of body.matchAll(/\bru\.billyhargrove\.pimobile\.features\.([\w]+)\b/g))if(ref[1]!==capsule)errors.push(`${relative}: ${capsule} must not depend on ${ref[1]} implementation`);
 }
 if(!allowed)return [...new Set(errors)];
 const refs=body.matchAll(/\bru\.billyhargrove\.pimobile(?:\.([\w]+))?\b/g);
 for(const ref of refs){const dependency=ref[1]||'(shell)';if(!allowed.has(dependency)&&!['BuildConfig','R'].includes(dependency))errors.push(`${relative}: ${owner} must not depend on ${dependency}`);}
 return [...new Set(errors)];
}

export function checkLaneImport(relative,specifier){
 const origin=relative.split('/')[0];if(!['server','extension','contracts'].includes(origin))return null;
 if(!specifier.startsWith('.'))return origin==='contracts'?`${relative}: pure contracts must not import external runtime packages`:null;
 const target=path.posix.normalize(path.posix.join(path.posix.dirname(relative),specifier)).split('/')[0];
 if(target==='server'&&origin!=='server'||target==='extension'&&origin!=='extension'||origin==='contracts'&&target!=='contracts')return `${relative}: ${origin} must not import ${target}`;
 return null;
}
function walk(dir){return fs.existsSync(dir)?fs.readdirSync(dir,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?walk(path.join(dir,entry.name)):[path.join(dir,entry.name)]):[];}
export function checkArchitecture(root=project){
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'architecture/owners.json'),'utf8'));
 const sources=walk(path.join(root,manifest.androidSourceRoot)).filter(f=>/\.(java|kt)$/.test(f));
 const text=new Map(sources.map(file=>[file,fs.readFileSync(file,'utf8')]));
 const symbols=Object.fromEntries(sources.map(file=>[path.relative(path.join(root,manifest.androidSourceRoot),file).split(path.sep).join('/').replace(/\.(java|kt)$/,''),topLevelSymbols(text.get(file))]));
 const errors=[],seen=new Map(),assigned=new Map();
 const declarations=[...Object.entries(manifest.owners).flatMap(([owner,classes])=>classes.map(base=>[owner,base])),...Object.entries(manifest.platformClasses).flatMap(([owner,classes])=>classes.map(name=>[owner,`${owner}/${name}`]))];
 for(const [owner,base] of declarations){
  if(assigned.has(base))errors.push(`Duplicate owner for ${base}: ${assigned.get(base)}, ${owner}`);
  assigned.set(base,owner);
  const variants=['.java','.kt'].filter(ext=>fs.existsSync(path.join(root,manifest.androidSourceRoot,base+ext)));
  if(variants.length!==1)errors.push(`${owner}: ${base} must have exactly one Java or Kotlin implementation (found ${variants.length})`);
 }
 for(const [owner,apis] of Object.entries(manifest.publicFeatureApis||{}))for(const api of apis){
  if(assigned.get(api)!==owner)errors.push(`${api}: public API owner ${owner} does not match registered source owner`);
 }
 for(const root of manifest.compositionRoots||[])if(!assigned.has(root)||root.includes('/'))errors.push(`${root}: composition root must be an owned app entry point`);
 for(const [source,targets] of Object.entries(manifest.platformInteropDependencies||{})){
  if(!assigned.has(source)||targets.some(target=>assigned.get(target)!=='app-shell'))errors.push(`${source}: invalid bounded platform interop dependency`);
 }
 for(const file of sources){const relative=path.relative(root,file).split(path.sep).join('/'),base=relative.slice((manifest.androidSourceRoot+'/').length).replace(/\.(java|kt)$/,'');
  const first=base.split('/')[0];if(!assigned.has(base))errors.push(`${relative}: no source ownership registered`);
  if(first==='features'&&!Object.hasOwn(manifest.owners,base.split('/')[1]))errors.push(`${relative}: unknown feature capsule`);
  const source=text.get(file);
  seen.set(base,(seen.get(base)||0)+1);errors.push(...checkAndroidImports(relative,source),...checkOwnerImports(relative,source,manifest,symbols));
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
