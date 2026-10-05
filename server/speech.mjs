import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {Worker} from 'node:worker_threads';
export const MAX_AUDIO_BYTES=16000*2*60;
export function audioBytes(value){
 if(typeof value!=='string'||value.length>Math.ceil(MAX_AUDIO_BYTES/3)*4||value.length%4!==0||! /^[A-Za-z0-9+/]*={0,2}$/.test(value))throw Error('Некорректное аудио');
 const data=Buffer.from(value,'base64');if(data.length<3200||data.length>MAX_AUDIO_BYTES||data.length%2||data.toString('base64')!==value)throw Error('Запись должна длиться от 0,1 до 60 секунд');return data;
}
/** Reuse installed Orca's native engine and model files; never changes Orca settings or downloads models. */
export function speechConfig(){
 const resources=process.env.PI_MOBILE_ORCA_RESOURCES||(process.platform==='darwin'?'/Applications/Orca.app/Contents/Resources':'');
 const data=process.platform==='darwin'?path.join(os.homedir(),'Library/Application Support/Orca'):path.join(process.env.XDG_CONFIG_HOME||path.join(os.homedir(),'.config'),'orca');
 const module=process.env.PI_MOBILE_SPEECH_MODULE||path.join(resources,'node_modules',`sherpa-onnx-${process.platform}-${process.arch}`,'sherpa-onnx.node');
 const modelDir=process.env.PI_MOBILE_SPEECH_MODEL_DIR||path.join(data,'speech-models/parakeet-tdt-0.6b-v3-int8');
 if(!resources&&!process.env.PI_MOBILE_SPEECH_MODULE)throw Error('Задай PI_MOBILE_SPEECH_MODULE и PI_MOBILE_SPEECH_MODEL_DIR на Linux');
 if(!fs.existsSync(module)||!['encoder.int8.onnx','decoder.int8.onnx','joiner.int8.onnx','tokens.txt'].every(f=>fs.existsSync(path.join(modelDir,f))))throw Error('Локальная модель Parakeet v3 Orca не установлена. Настрой диктовку на компьютере');
 return {module,modelDir};
}
export async function transcribe(audio,{signal,config=speechConfig()}={}){
 if(signal?.aborted)throw Error('Распознавание отменено');
 const worker=new Worker(new URL('./speech-worker.mjs',import.meta.url),{execArgv:[],workerData:{...config,audio}});
 try{return await new Promise((resolve,reject)=>{
  const timer=setTimeout(()=>reject(Error('Распознавание превысило 90 секунд')),90000);
  const abort=()=>reject(Error('Распознавание отменено'));signal?.addEventListener('abort',abort,{once:true});
  const finish=(error,value)=>{clearTimeout(timer);signal?.removeEventListener('abort',abort);error?reject(error):resolve(value);};
  worker.once('error',e=>finish(e));worker.once('exit',code=>{if(code)finish(Error('Движок диктовки завершился с ошибкой'));});
  worker.once('message',m=>finish(m.error?Error(m.error):null,{text:String(m.text||'').slice(0,100000)}));
 });}finally{await worker.terminate();}
}
