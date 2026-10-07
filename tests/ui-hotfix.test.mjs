import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=new URL('../android/app/src/main/java/ru/billyhargrove/pimobile/',import.meta.url);
function sources(dir){dir=dir instanceof URL?fileURLToPath(dir):dir;return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?sources(path.join(dir,e.name)):e.name.endsWith('.kt')?[path.join(dir,e.name)]:[]);}
test('production UI cannot regress to lower Toast or Snackbar notifications',()=>{
 for(const file of sources(root))assert.doesNotMatch(fs.readFileSync(file,'utf8'),/\b(?:Toast|Snackbar|SnackbarHost|SnackbarHostState)\b/,file);
});
test('removed navigation drawer has no shipped implementation or callers',()=>{
 assert.equal(fs.existsSync(new URL('ui/PiNavigation.kt',root)),false);
 for(const file of sources(root))assert.doesNotMatch(fs.readFileSync(file,'utf8'),/\b(?:PiNavigation|ModalNavigationDrawer)\b/,file);
});
