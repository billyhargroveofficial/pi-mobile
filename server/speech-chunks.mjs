// Keep native recognizer input bounded and prefer quiet boundaries, without overlaps/duplication.
export function audioChunks(bytes,sampleRate=16000){
 const frame=sampleRate*2,maximum=30*frame,search=4*frame,step=Math.floor(frame/10/2)*2,result=[];
 for(let start=0;start<bytes.length;){
  let end=Math.min(bytes.length,start+maximum);
  if(end<bytes.length){let best=Infinity,boundary=end;for(let at=end-search;at+step<=end;at+=step){let energy=0;for(let i=at;i<at+step;i+=2){const value=bytes.readInt16LE(i);energy+=value*value;}if(energy<best){best=energy;boundary=at+Math.floor(step/4)*2;}}end=boundary;}
  result.push([start,end]);start=end;
 }
 return result;
}
