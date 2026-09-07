// 只读取本次角色模块的公开接口与前台脚本，不读取浏览器登录态，不提交业务写入。
import fs from 'node:fs/promises';
const root = new URL('./audit/characters/', import.meta.url);
await fs.mkdir(root, { recursive: true });
const jobs = [86, 373, 346, 136, 619, 593].map(id => [`student-${id}`, `https://api.kivo.wiki/api/v1/data/students/${id}`]);
jobs.push(['schools','https://api.kivo.wiki/api/v1/data/schools?page=1&page_size=100'], ['icons','https://api.kivo.wiki/api/v1/interactive/declares/icons']);
for (let i=0; i<jobs.length; i+=3) {
  const results = await Promise.allSettled(jobs.slice(i,i+3).map(async ([name,url]) => {
    const response = await fetch(url, {signal:AbortSignal.timeout(30000)});
    const body = await response.json();
    await fs.writeFile(new URL(`${name}.json`,root),JSON.stringify(body,null,2));
    const data = body.data;
    console.log(name,response.status,body.code,JSON.stringify(data && Object.fromEntries(Object.entries(data).map(([k,v])=>[k,Array.isArray(v)?`array(${v.length})`: typeof v==='string' && v.length>100 ? `text(${v.length})`:v]))));
  }));
  for (const result of results) if(result.status==='rejected') console.error(String(result.reason));
}
