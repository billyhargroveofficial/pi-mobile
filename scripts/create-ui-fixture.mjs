// Local synthetic history for UI/paging verification; never modifies a user's Pi session.
import {SessionManager} from '@earendil-works/pi-coding-agent';
import fs from 'node:fs';import path from 'node:path';
const cwd=process.cwd(),dir=path.join(cwd,'artifacts/ui-history');fs.mkdirSync(dir,{recursive:true});
const session=SessionManager.create(cwd,dir);
const usage={input:0,output:0,cacheRead:0,cacheWrite:0,totalTokens:0,cost:{input:0,output:0,cacheRead:0,cacheWrite:0,total:0}};
let timestamp=Date.now()-1000000;
const assistant=content=>({role:'assistant',api:'openai-codex-responses',provider:'openai-codex',model:'gpt-6-sol',content,usage,stopReason:'stop',timestamp:timestamp++});
for(let n=0;n<65;n++){session.appendMessage({role:'user',content:`HISTORY_HEAD_${String(n).padStart(3,'0')}: тест истории`,timestamp:timestamp++});session.appendMessage(assistant([{type:'text',text:`Ответ ${n}. Это синтетическая история для проверки загрузки порциями.`}]));}
const md='# Markdown preview\n\n**Жирный**, *курсив*, `код`.\n\n- Первый пункт\n- Второй пункт\n\n| Колонка | Значение |\n| --- | --- |\n| Pi | Mobile |\n\n$$\n\\frac{a}{b}=\\sqrt{x^2+y^2}\n$$\n';
fs.writeFileSync(path.join(cwd,'artifacts/preview-example.md'),md);
session.appendMessage({role:'user',content:'Покажи пример блока работы и формулу.',timestamp:timestamp++});
let a=assistant([{type:'text',text:'Сначала проверю файлы проекта.'},{type:'toolCall',id:'fixture-tool-1',name:'ls',arguments:{path:cwd}}]);a.stopReason='toolUse';session.appendMessage(a);
session.appendMessage({role:'toolResult',toolCallId:'fixture-tool-1',toolName:'ls',content:[{type:'text',text:'android/\nserver/\nextension/'}],isError:false,timestamp:timestamp++});
a=assistant([{type:'text',text:'Теперь открою Markdown и проверю формулу.'},{type:'toolCall',id:'fixture-tool-2',name:'read',arguments:{path:'artifacts/preview-example.md'}}]);a.stopReason='toolUse';session.appendMessage(a);
session.appendMessage({role:'toolResult',toolCallId:'fixture-tool-2',toolName:'read',content:[{type:'text',text:md}],isError:false,timestamp:timestamp++});
session.appendMessage(assistant([{type:'text',text:'## Готово\n\n**Markdown** и формулы на своих местах. [Открыть файл](artifacts/preview-example.md)\n\n$$\nE=mc^2\n$$\n\nПосле формулы продолжается обычный текст.'}]));
console.log(JSON.stringify({id:session.getSessionId(),file:session.getSessionFile(),sourceMessages:136}));
