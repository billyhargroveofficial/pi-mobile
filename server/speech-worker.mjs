import {parentPort,workerData} from 'node:worker_threads';
import {createRequire} from 'node:module';
import path from 'node:path';
// Same native sherpa-onnx engine and downloaded Parakeet model used by Orca.
try{
 const sherpa=createRequire(import.meta.url)(workerData.module),root=workerData.modelDir;
 const recognizer=sherpa.createOfflineRecognizer({featConfig:{sampleRate:16000,featureDim:80},modelConfig:{transducer:{encoder:path.join(root,'encoder.int8.onnx'),decoder:path.join(root,'decoder.int8.onnx'),joiner:path.join(root,'joiner.int8.onnx')},tokens:path.join(root,'tokens.txt'),numThreads:2,provider:'cpu',debug:0},decodingMethod:'greedy_search'});
 const stream=sherpa.createOfflineStream(recognizer),bytes=Buffer.from(workerData.audio),samples=new Float32Array(bytes.length/2);
 for(let i=0;i<samples.length;i++)samples[i]=bytes.readInt16LE(i*2)/32768;
 sherpa.acceptWaveformOffline(stream,{sampleRate:16000,samples});sherpa.decodeOfflineStream(recognizer,stream);
 parentPort.postMessage({text:JSON.parse(sherpa.getOfflineStreamResultAsJson(stream)).text?.trim()||''});
}catch(e){parentPort.postMessage({error:String(e.message||e).slice(0,500)});}
