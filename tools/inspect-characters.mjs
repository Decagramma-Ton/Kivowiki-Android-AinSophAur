import fs from 'node:fs/promises';
const dir = new URL('./audit/', import.meta.url);
if (process.argv[2] === 'fetch') {
  for (const [name,path] of [['character-info','info-D_ZXEBqy.js'],['declare','declare.vue_vue_type_script_setup_true_lang-DXmK-dOY.js'],['supplementary','supplementary.vue_vue_type_script_setup_true_lang-BXixyppL.js']]) {
    const r = await fetch(`https://kivo.wiki/assets/${path}`, {signal:AbortSignal.timeout(30000)});
    if (!r.ok) throw Error(`${name}: ${r.status}`);
    await fs.writeFile(new URL(`characters/${name}-public.js`,dir),await r.text());
  }
} else if (process.argv[2] === 'samples') {
  for (const id of [86,373,346,136,619,593]) {
    const {data:d} = JSON.parse(await fs.readFile(new URL(`characters/student-${id}.json`,dir)));
    console.log(id,JSON.stringify({skin_list:d.skin_list,source:d.source,contributor:d.contributor,gallery:d.gallery.map(g=>({title:g.title,count:g.images.length,sample:g.images[0]})),voice:d.voice.slice(0,2),gift_data:d.gift_data,furniture:d.furniture,character_datas:d.character_datas},null,2));
  }
} else {
  const source = await fs.readFile(new URL(process.argv[2]||'site-main-decoded.js',dir),'utf8');
  for (const term of process.argv.slice(3)) {
    let pos = source.indexOf(term, source.indexOf('));')+3), count=0;
    while(pos>=0 && count++<4) { console.log(term,source.slice(Math.max(0,pos-220),pos+1400)); pos=source.indexOf(term,pos+term.length); }
  }
}
