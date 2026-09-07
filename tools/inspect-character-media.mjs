import fs from 'node:fs/promises';
for(const name of ['spine-467','spine-1562']) {
 const d=JSON.parse(await fs.readFile(`tools/audit/characters/${name}.json`)).data;
 const response=await fetch('https:'+d.skel_file,{headers:{Range:'bytes=0-255'},signal:AbortSignal.timeout(30000)});
 const b=Buffer.from(await response.arrayBuffer());console.log(name,response.status,b.toString('latin1').match(/[34]\.[0-9]+\.[0-9]+/g));
}
const s=await fs.readFile('tools/audit/characters/character-info-decoded.js','utf8');
for(const term of ['setMouthOffset','loadBodyModelMouthTexture','loadBodyModelMouth','bindBodyModelHalo']) {
 const matches=Array.from(s.matchAll(new RegExp(term,'g')));
 for(const match of matches.filter(x=>x.index>1000000).slice(0,2))console.log(term,s.slice(match.index-80,match.index+1900));
}
for(const name of ['Vc','Hc','Gc','Wc']){
 const re=new RegExp(name+'=([^,;]+)');console.log(name,re.exec(s)?.[0]);
}
