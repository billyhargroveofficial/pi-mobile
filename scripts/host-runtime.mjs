// Tests run outside Pi's extension loader. Resolve the same host-provided runtime
// without installing a private copy into this extension's node_modules.
import {registerHooks} from 'node:module';
import {execFileSync} from 'node:child_process';
import {realpathSync,existsSync,readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {pathToFileURL} from 'node:url';
let root=process.env.PI_HOST_ROOT;
if(!root){
  const executable=execFileSync('which',['pi'],{encoding:'utf8'}).trim();
  root=dirname(realpathSync(executable));
  while(true){
    const file=join(root,'package.json');
    if(existsSync(file)&&JSON.parse(readFileSync(file,'utf8')).name==='@earendil-works/pi-coding-agent')break;
    const parent=dirname(root);if(parent===root)throw Error('Install Pi or set PI_HOST_ROOT to its package directory before running extension tests');root=parent;
  }
}
const hostParent=pathToFileURL(join(root,'dist/index.js')).href;
registerHooks({resolve(specifier,context,nextResolve){
  if(specifier==='@earendil-works/pi-coding-agent')return nextResolve(hostParent,context);
  if(specifier.startsWith('@earendil-works/pi-ai'))return nextResolve(specifier,{...context,parentURL:hostParent});
  return nextResolve(specifier,context);
}});
