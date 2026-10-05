import {homedir} from 'node:os';
import path from 'node:path';
import {createGateway} from './gateway.mjs';
const gateway=await createGateway({dataDir:process.env.PI_MOBILE_DIR||path.join(homedir(),'.pi/agent/pi-mobile'),port:Number(process.env.PI_MOBILE_PORT||8788),orca:process.env.PI_MOBILE_NO_ORCA!=='1'});
console.log(`Pi Mobile gateway: http://127.0.0.1:${gateway.port}; local bridge ready. Token is in private data directory, not logged.`);
let shutting=false;for(const signal of ['SIGTERM','SIGINT'])process.on(signal,async()=>{if(shutting)return;shutting=true;await gateway.close();process.exit(0);});
