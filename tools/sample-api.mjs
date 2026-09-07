// 少量公开 GET 样本，用于核对契约。最多四个请求并行，不读取账号数据。
import fs from 'node:fs/promises';
const base = 'https://api.kivo.wiki/api/v1/';
const folder = new URL('./audit/fixtures/', import.meta.url);
await fs.mkdir(folder, { recursive: true });
const queries = {
  news: 'news?page=1&page_size=6',
  articles: 'articles?page=1&page_size=4&summary_size=100',
  bulletins: 'bulletins?page=1&page_size=6',
  recent: 'data/students?page=1&page_size=6&updated_at_sort=desc',
  birthday: 'data/students/birthday/week',
  lucky: 'data/lucky_item',
  statistics: 'statistics/index',
};
for (const server of ['jp', 'cn']) for (const [key, path] of Object.entries({ pickup: 'pick_up', event: 'event/now', raid: 'raid/now' })) queries[`${key}-${server}`] = `data/${path}?server=${server}`;
async function get(name, path) {
  const r = await fetch(base + path, { signal: AbortSignal.timeout(25000) });
  const body = await r.json();
  await fs.writeFile(new URL(`${name}.json`, folder), JSON.stringify(body, null, 2));
  const d = body.data;
  console.log(name, r.status, JSON.stringify(d).slice(0, name === 'recent' ? 2700 : 900));
  return d;
}
const entries = Object.entries(queries);
const results = {};
for (let i = 0; i < entries.length; i += 4) await Promise.all(entries.slice(i, i + 4).map(async ([name, path]) => { results[name] = await get(name, path); }));
if (results.lucky?.type === 'item') await get('lucky-detail', `data/items/${results.lucky.id}`);
for (const id of results.birthday?.students ?? []) await get(`birthday-${id}`, `data/students/${id}`);
const firstBulletin = results.bulletins?.bulletin?.[0]?.id;
if (firstBulletin) await get(`bulletin-${firstBulletin}`, `bulletins/${firstBulletin}`);
await fs.writeFile(new URL('manifest.json', folder), JSON.stringify({ capturedAt: new Date().toISOString(), base, queries }, null, 2));
