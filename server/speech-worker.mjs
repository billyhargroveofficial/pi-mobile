import {parentPort,workerData} from 'node:worker_threads';
import {createRequire} from 'node:module';
import path from 'node:path';
import {audioChunks} from './speech-chunks.mjs';
// Same native sherpa-onnx engine and downloaded Parakeet model used by Orca.
try{
 const sherpa=createRequire(import.meta.url)(workerData.module),root=workerData.modelDir;
 const recognizer=sherpa.createOfflineRecognizer({featConfig:{sampleRate:16000,featureDim:80},modelConfig:{transducer:{encoder:path.join(root,'encoder.int8.onnx'),decoder:path.join(root,'decoder.int8.onnx'),joiner:path.join(root,'joiner.int8.onnx')},tokens:path.join(root,'tokens.txt'),numThreads:2,provider:'cpu',debug:0},decodingMethod:'greedy_search'});
 const bytes=Buffer.from(workerData.audio),parts=[];
 for(const [start,end] of audioChunks(bytes)){
  const stream=sherpa.createOfflineStream(recognizer),samples=new Float32Array((end-start)/2);
  for(let i=0;i<samples.length;i++)samples[i]=bytes.readInt16LE(start+i*2)/32768;
  sherpa.acceptWaveformOffline(stream,{sampleRate:16000,samples});sherpa.decodeOfflineStream(recognizer,stream);
  const text=JSON.parse(sherpa.getOfflineStreamResultAsJson(stream)).text?.trim();if(text)parts.push(text);
 }
 parentPort.postMessage({text:parts.join(' ')});
}catch(e){parentPort.postMessage({error:String(e.message||e).slice(0,500)});}
