import fs from 'node:fs/promises';
const dir=new URL('./audit/characters/',import.meta.url);
const d=JSON.parse(await fs.readFile(new URL('student-86.json',dir))).data;
const tasks=[['equipment-7','data/equipments/7'],['equipment-52','data/equipments/52'],['equipments','data/equipments?page=1&page_size=100&is_favorite=false'],['relation-19','data/relations/19'],['declares',`interactive/declares/${d.info_declare_uuid}`],['supplementary',`interactive/supplementarys/${d.supplementary_uuid}?page=1&page_size=20`],...d.model.map(id=>[`model-${id}`,`data/models/${id}`]),...d.spine.slice(0,2).map(id=>[`spine-${id}`,`data/spines/${id}`])];
for(let i=0;i<tasks.length;i+=3)for(const result of await Promise.allSettled(tasks.slice(i,i+3).map(async([name,path])=>{
 const response=await fetch(`https://api.kivo.wiki/api/v1/${path}`,{signal:AbortSignal.timeout(30000)});
 const body=await response.json(); await fs.writeFile(new URL(`${name}.json`,dir),JSON.stringify(body,null,2));
 console.log(name,response.status,JSON.stringify(body.data).slice(0,1700));
}))) if(result.status==='rejected')console.error(String(result.reason));
