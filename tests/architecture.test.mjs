import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {checkArchitecture,checkAndroidImports,checkLaneImport} from '../tools/check-architecture.mjs';

test('shipped source follows declared class ownership and transport dependency direction',()=>{
 assert.deepEqual(checkArchitecture(),[]);
});
test('core and storage cannot reach back into UI or transport, including qualified references',()=>{
 const core='android/app/src/main/java/ru/billyhargrove/pimobile/core/Example.java';
 assert.match(checkAndroidImports(core,'package ru.billyhargrove.pimobile.core;\nimport ru.billyhargrove.pimobile.net.PiClient;')[0],/must not depend on net/);
 assert.match(checkAndroidImports(core,'package ru.billyhargrove.pimobile.core;\nclass Example { ru.billyhargrove.pimobile.ui.ImageViewer view; }')[0],/must not depend on ui/);
 assert.deepEqual(checkAndroidImports(core,'package ru.billyhargrove.pimobile.core;\nimport ru.billyhargrove.pimobile.core.ChatMessage;'),[]);
 const ui='android/app/src/main/java/ru/billyhargrove/pimobile/ui/NewScreen.java';
 assert.match(checkAndroidImports(ui,'package ru.billyhargrove.pimobile.ui;\nimport ru.billyhargrove.pimobile.PiApp;')[0],/must not add a new app-shell dependency/);
});
test('gateway and Pi extension share only contracts, never each other',()=>{
 assert.match(checkLaneImport('server/orchestration.mjs','../extension/mobile-orchestration.ts'),/must not import extension/);
 assert.match(checkLaneImport('extension/mobile.ts','../server/gateway.mjs'),/must not import server/);
 assert.match(checkLaneImport('contracts/agent-transcript.mjs','../server/gateway.mjs'),/must not import server/);
 assert.equal(checkLaneImport('server/orchestration.mjs','../contracts/agent-transcript.mjs'),null);
});
test('missing owners and dual-language copies fail closed',async t=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'pi-architecture-'));
 t.after(()=>rm(root,{recursive:true,force:true}));
 const base='android/app/src/main/java/ru/billyhargrove/pimobile',absolute=path.join(root,base);
 await mkdir(path.join(root,'architecture'),{recursive:true});await mkdir(path.join(absolute,'ui'),{recursive:true});await mkdir(path.join(absolute,'core'),{recursive:true});
 await writeFile(path.join(root,'architecture/owners.json'),JSON.stringify({androidSourceRoot:base,platformClasses:{core:[]},owners:{chat:['ui/MessageAdapter']}}));
 await writeFile(path.join(absolute,'ui/MessageAdapter.java'),'package ru.billyhargrove.pimobile.ui; class MessageAdapter {}');
 assert.deepEqual(checkArchitecture(root),[]);
 await writeFile(path.join(absolute,'ui/MessageAdapter.kt'),'package ru.billyhargrove.pimobile.ui\nclass MessageAdapter');
 assert.match(checkArchitecture(root).join('\n'),/dual Java\/Kotlin implementation/);
 await writeFile(path.join(absolute,'ui/Unowned.kt'),'package ru.billyhargrove.pimobile.ui\nclass Unowned');
 assert.match(checkArchitecture(root).join('\n'),/no source ownership registered/);
 await writeFile(path.join(absolute,'core/Unknown.java'),'package ru.billyhargrove.pimobile.core; class Unknown {}');
 assert.match(checkArchitecture(root).join('\n'),/core\/Unknown.java: no source ownership registered/);
});
