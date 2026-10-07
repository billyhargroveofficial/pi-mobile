import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm} from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import fs from 'node:fs';
import {checkArchitecture,checkAndroidImports,checkOwnerImports,checkLaneImport,checkSourceContract,topLevelSymbols} from '../tools/check-architecture.mjs';

const ownership=JSON.parse(fs.readFileSync(new URL('../architecture/owners.json',import.meta.url),'utf8'));
const androidRoot=ownership.androidSourceRoot;

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
 const feature='android/app/src/main/java/ru/billyhargrove/pimobile/features/chat/ChatScreen.kt';
 assert.match(checkAndroidImports(feature,'package ru.billyhargrove.pimobile.features.chat\nimport ru.billyhargrove.pimobile.features.catalog.CatalogScreen')[0],/chat must not depend on catalog implementation/);
 assert.deepEqual(checkAndroidImports(feature,'package ru.billyhargrove.pimobile.features.chat\nimport ru.billyhargrove.pimobile.features.chat.ChatState'),[]);
});
test('gateway and Pi extension share only contracts, never each other',()=>{
 assert.match(checkLaneImport('server/orchestration.mjs','../extension/mobile-orchestration.ts'),/must not import extension/);
 assert.match(checkLaneImport('extension/mobile.ts','../server/gateway.mjs'),/must not import server/);
 assert.match(checkLaneImport('contracts/agent-transcript.mjs','../server/gateway.mjs'),/must not import server/);
 assert.match(checkLaneImport('contracts/agent-transcript.mjs','node:fs'),/pure contracts must not import external runtime packages/);
 assert.equal(checkLaneImport('server/orchestration.mjs','../contracts/agent-transcript.mjs'),null);
});
test('mixed UI namespace cannot hide a foreign capsule implementation',()=>{
 const chat=`${androidRoot}/features/chat/ChatScreen.kt`,header='package ru.billyhargrove.pimobile.features.chat\n';
 for(const source of [
  'import ru.billyhargrove.pimobile.ui.ArchiveSheet as History\n',
  'import ru.billyhargrove.pimobile.ui.*\nfun render() { ArchiveSheet() }',
  'fun render() { ru.billyhargrove.pimobile.ui.ArchiveSheet() }',
  'import ru.billyhargrove.pimobile.ui.*\nval url = "https://example.invalid"; val sheet = ArchiveSheet()',
  'import ru.billyhargrove.pimobile.ui.*\nval url = "${"https://example.invalid"}"; val sheet = ArchiveSheet()',
  'import ru.billyhargrove.pimobile.ui.*\nval label = "${ArchiveSheet()}"'
 ])assert.match(checkOwnerImports(chat,header+source,ownership).join('\n'),/chat must not depend on catalog implementation ui\/ArchiveSheet/);
 assert.match(checkOwnerImports(chat.replace('.kt','.java'),'package ru.billyhargrove.pimobile.features.chat; import ru.billyhargrove.pimobile.ui.*; class ChatScreen { ArchiveSheet sheet; }',ownership).join('\n'),/catalog implementation ui\/ArchiveSheet/);
 assert.deepEqual(checkOwnerImports(chat,header+'import ru.billyhargrove.pimobile.ui.*\n// ArchiveSheet is private to catalog\n/* nested /* ArchiveSheet */ comment */\nval label = "ArchiveSheet"\nval literal = """ArchiveSheet // literal"""',ownership),[]);
});
test('composition and shared rendering use declared public APIs only',()=>{
 const orchestration=`${androidRoot}/OrchestrationActivity.kt`,header='package ru.billyhargrove.pimobile\nimport ru.billyhargrove.pimobile.ui.*\n';
 assert.deepEqual(checkOwnerImports(orchestration,header+'fun show() { PiTranscript(); PiTheme(); PiApp.get(this); ChatActivity.intent(this) }',ownership),[]);
 assert.match(checkOwnerImports(orchestration,header+'fun show() { ModelSettingsSheet() }',ownership).join('\n'),/chat implementation ui\/ModelSettingsSheet/);
 const foundation=`${androidRoot}/ui/StatusUi.kt`;
 assert.match(checkOwnerImports(foundation,'package ru.billyhargrove.pimobile.ui\nfun show() { DictationRecorder() }',ownership).join('\n'),/ui-foundation must not depend on voice implementation/);
 const chat=`${androidRoot}/features/chat/ChatScreen.kt`;
 assert.match(checkOwnerImports(chat,'package ru.billyhargrove.pimobile.features.chat\nimport ru.billyhargrove.pimobile.MainActivity',ownership).join('\n'),/catalog implementation MainActivity/);
 const exported=topLevelSymbols('class VoiceWaveform(val memberProperty: String) { fun member() {} }\nprivate fun hidden() {}\n@Composable\nfun Waveform() {}\nval label = "${"https://example.invalid"}"\nfun <T : Comparable<T>> extension() {}');
 assert.deepEqual(exported,['VoiceWaveform','Waveform','label','extension']);
 assert.match(checkOwnerImports(orchestration,header+'fun show() { Waveform() }',ownership,{'ui/VoiceWaveform':exported}).join('\n'),/voice implementation ui\/VoiceWaveform/);
});
test('inspection state and projection remain platform/transport free; screen only gets its media leaf',()=>{
 const state=`${androidRoot}/features/orchestration/OrchestrationSession.kt`,projection=state.replace('Session','Projection'),screen=state.replace('Session','Screen');
 for(const source of [
  'import android.os.Handler as Poller\n',
  'import androidx.activity.ComponentActivity\n',
  'import android.os.*\nfun poll() { Handler() }',
  'fun poll() { android.os.Handler() }',
  'import ru.billyhargrove.pimobile.net.HttpApi as Wire\n',
  'import ru.billyhargrove.pimobile.net.*\nfun poll() { HttpApi() }',
  'val label = "${android.os.Handler()}"'
 ])assert.match(checkSourceContract(state,source,ownership).join('\n'),/platform-free-state must not reference/);
 assert.deepEqual(checkSourceContract(state,'import androidx.compose.runtime.*\nimport org.json.JSONObject\n// android.os.Handler\nval label = "android.os.Handler"',ownership),[]);
 assert.match(checkSourceContract(projection,'import androidx.compose.runtime.mutableStateOf',ownership).join('\n'),/pure-projection must not reference/);
 assert.deepEqual(checkSourceContract(projection,'import org.json.JSONObject\nimport ru.billyhargrove.pimobile.core.OrchestrationData',ownership),[]);
 assert.deepEqual(checkSourceContract(screen,'import androidx.compose.foundation.*\nimport ru.billyhargrove.pimobile.net.MediaLoader',ownership),[]);
 assert.match(checkSourceContract(screen,'import ru.billyhargrove.pimobile.net.*',ownership).join('\n'),/presentation-only must not reference/);
 assert.match(checkSourceContract(screen,'import ru.billyhargrove.pimobile.net.HttpApi as Api',ownership).join('\n'),/presentation-only must not reference/);
 assert.match(checkSourceContract(screen,'fun load() { okhttp3.OkHttpClient() }',ownership).join('\n'),/presentation-only must not reference/);
});
test('extracted chat presentation accepts only the bounded anchor/media leaves',()=>{
 for(const name of ['ChatScreen','ChatHeader','ChatComposer','ChatWorkDock']){
  const file=`${androidRoot}/features/chat/${name}.kt`;
  assert.deepEqual(checkSourceContract(file,'import android.graphics.Rect\nimport ru.billyhargrove.pimobile.net.MediaLoader',ownership),[]);
  for(const source of ['import android.app.Activity','import android.content.Context','import android.graphics.*','import ru.billyhargrove.pimobile.net.PiClient','import ru.billyhargrove.pimobile.store.SettingsStore'])
   assert.match(checkSourceContract(file,source,ownership).join('\n'),/presentation-only must not reference/);
 }
});
test('catalog data owners cannot reach HTTP, platform, or their screens',()=>{
 const prefix=`${androidRoot}/features/catalog/`;
 for(const name of ['Usage','Archive']){
  const state=prefix+name+'Session.kt',projection=prefix+name+'Projection.kt',screen=prefix+name+'Screen.kt';
  const symbols={['features/catalog/'+name+'Screen']:[name+'Screen']};
  for(const source of ['import android.os.Handler','import ru.billyhargrove.pimobile.net.HttpApi','import ru.billyhargrove.pimobile.ui.ArchiveSheet'])
   assert.match(checkSourceContract(state,source,ownership).join('\n'),/platform-free-state must not reference/);
  assert.match(checkSourceContract(state,`fun render() { ${name}Screen() }`,ownership,symbols).join('\n'),/platform-free-state must not depend on presentation-only/);
  assert.match(checkSourceContract(projection,'import androidx.compose.runtime.mutableStateOf',ownership).join('\n'),/pure-projection must not reference/);
  assert.match(checkSourceContract(projection,`fun render() { ${name}Session() }`,ownership).join('\n'),/pure-projection must not depend on platform-free-state/);
  assert.match(checkSourceContract(screen,'import ru.billyhargrove.pimobile.net.HttpApi',ownership).join('\n'),/presentation-only must not reference/);
 }
});
test('configuration owners and shared capability projection preserve bounded source direction',()=>{
 const prefix=`${androidRoot}/features/chat/`;
 for(const name of ['ModelSettings','QuickEffort']){
  const state=prefix+name+'Session.kt',screen=prefix+name+'Screen.kt';
  const symbols={['features/chat/'+name+'Screen']:[name+'Screen']};
  for(const source of ['import android.content.Context','import ru.billyhargrove.pimobile.ui.EffortSlider','import ru.billyhargrove.pimobile.net.PiClient'])
   assert.match(checkSourceContract(state,source,ownership).join('\n'),/platform-free-state must not reference/);
  assert.match(checkSourceContract(state,`fun render() { ${name}Screen() }`,ownership,symbols).join('\n'),/platform-free-state must not depend on presentation-only/);
  assert.deepEqual(checkSourceContract(screen,'import ru.billyhargrove.pimobile.ui.EffortSlider',ownership),[]);
  assert.match(checkSourceContract(screen,'import ru.billyhargrove.pimobile.net.PiClient',ownership).join('\n'),/presentation-only must not reference/);
 }
 const projection=prefix+'ModelCapabilities.kt';
 assert.match(checkSourceContract(projection,'import androidx.compose.runtime.mutableStateOf',ownership).join('\n'),/pure-projection must not reference/);
 assert.match(checkSourceContract(projection,'fun change() { QuickEffortSession() }',ownership).join('\n'),/pure-projection must not depend on platform-free-state/);
});
test('profiled feature source direction also rejects same-package/wildcard/aliased reverse dependencies',()=>{
 const prefix=`${androidRoot}/features/orchestration/`,symbols={'features/orchestration/OrchestrationScreen':['OrchestrationScreen']};
 for(const source of [
  'fun render() { OrchestrationScreen() }',
  'import ru.billyhargrove.pimobile.features.orchestration.OrchestrationScreen as Draw',
  'import ru.billyhargrove.pimobile.features.orchestration.*\nfun render() { OrchestrationScreen() }',
  'fun render() { ru.billyhargrove.pimobile.features.orchestration.OrchestrationScreen() }'
 ])assert.match(checkSourceContract(prefix+'OrchestrationSession.kt',source,ownership,symbols).join('\n'),/platform-free-state must not depend on presentation-only/);
 assert.match(checkSourceContract(prefix+'OrchestrationProjection.kt','fun project() { OrchestrationSession() }',ownership).join('\n'),/pure-projection must not depend on platform-free-state/);
 assert.deepEqual(checkSourceContract(prefix+'OrchestrationScreen.kt','fun render() { OrchestrationSession(); OrchestrationProjection.project() }',ownership,symbols),[]);
});
test('source contracts reject unknown or unowned metadata rather than becoming a silent exception',async t=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'pi-contract-'));t.after(()=>rm(root,{recursive:true,force:true}));
 await mkdir(path.join(root,'architecture'),{recursive:true});
 await writeFile(path.join(root,'architecture/owners.json'),JSON.stringify({androidSourceRoot:androidRoot,owners:{},platformClasses:{},sourceContracts:{Unowned:'made-up'}}));
 assert.match(checkArchitecture(root).join('\n'),/invalid source contract made-up/);
});
test('missing owners and dual-language copies fail closed',async t=>{
 const root=await mkdtemp(path.join(os.tmpdir(),'pi-architecture-'));
 t.after(()=>rm(root,{recursive:true,force:true}));
 const base='android/app/src/main/java/ru/billyhargrove/pimobile',absolute=path.join(root,base);
 await mkdir(path.join(root,'architecture'),{recursive:true});await mkdir(path.join(absolute,'ui'),{recursive:true});await mkdir(path.join(absolute,'core'),{recursive:true});
 const manifest={androidSourceRoot:base,platformClasses:{core:[]},owners:{chat:['ui/MessageAdapter']}};
 const manifestFile=path.join(root,'architecture/owners.json');
 await writeFile(manifestFile,JSON.stringify(manifest));
 await writeFile(path.join(absolute,'ui/MessageAdapter.java'),'package ru.billyhargrove.pimobile.ui; class MessageAdapter {}');
 assert.deepEqual(checkArchitecture(root),[]);
 await writeFile(manifestFile,JSON.stringify({...manifest,publicFeatureApis:{catalog:['ui/MessageAdapter']},compositionRoots:['ui/MessageAdapter'],platformInteropDependencies:{'ui/MessageAdapter':['ui/MessageAdapter']}}));
 const badMetadata=checkArchitecture(root).join('\n');
 assert.match(badMetadata,/public API owner catalog does not match/);
 assert.match(badMetadata,/composition root must be an owned app entry point/);
 assert.match(badMetadata,/invalid bounded platform interop dependency/);
 await writeFile(manifestFile,JSON.stringify(manifest));
 await writeFile(path.join(absolute,'ui/MessageAdapter.kt'),'package ru.billyhargrove.pimobile.ui\nclass MessageAdapter');
 assert.match(checkArchitecture(root).join('\n'),/dual Java\/Kotlin implementation/);
 await writeFile(path.join(absolute,'ui/Unowned.kt'),'package ru.billyhargrove.pimobile.ui\nclass Unowned');
 assert.match(checkArchitecture(root).join('\n'),/no source ownership registered/);
 await writeFile(path.join(absolute,'core/Unknown.java'),'package ru.billyhargrove.pimobile.core; class Unknown {}');
 assert.match(checkArchitecture(root).join('\n'),/core\/Unknown.java: no source ownership registered/);
 await mkdir(path.join(absolute,'features/chat'),{recursive:true});
 await writeFile(path.join(absolute,'features/chat/New.kt'),'package ru.billyhargrove.pimobile.features.chat\nclass New');
 assert.match(checkArchitecture(root).join('\n'),/features\/chat\/New.kt: no source ownership registered/);
});
