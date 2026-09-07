// 仅隔离还原公开 JS 字符串表用于协议审计；不执行 Vue、认证或网络业务。
import fs from 'node:fs';
import vm from 'node:vm';
for (const file of process.argv.slice(2)) {
  const source=fs.readFileSync(file,'utf8');
  const initial=/const (_0x[\da-f]+)=(_0x[\da-f]+);\(function/.exec(source);
  if(!initial) throw Error('Unknown public bundle prefix');
  const end=/\}\((_0x[\da-f]+),0x[\da-f]+\)\);/.exec(source.slice(initial.index));
  if(!end) throw Error('Unknown string rotation');
  const helper=source.slice(initial.index,initial.index+end.index+end[0].length);
  function extract(name) {
    const start=source.indexOf(`function ${name}(`);
    if(start<0) throw Error(`Missing helper ${name}`);
    let depth=0,quote='';
    for(let p=source.indexOf('{',start);p<source.length;p++) {
      const c=source[p];
      if(quote) { if(c==='\\')p++;else if(c===quote)quote=''; continue; }
      if('\'"`'.includes(c)){quote=c;continue;}
      if(c==='{')depth++;
      if(c==='}'&&--depth===0)return source.slice(start,p+1);
    }
    throw Error('Unbalanced helper');
  }
  const context=vm.createContext(Object.create(null),{codeGeneration:{strings:false,wasm:false}});
  vm.runInContext(extract(end[1])+'\n'+extract(initial[2])+'\n'+helper,context,{timeout:2000});
  const aliases=new Set([initial[1],initial[2]]);
  for(let pass=0;pass<15;pass++)for(const m of source.matchAll(/(_0x[\da-f]+)\s*=\s*(_0x[\da-f]+)(?=[,;])/g))if(aliases.has(m[2]))aliases.add(m[1]);
  const constants=new Map();
  for(const m of source.matchAll(/(_0x[\da-f]+)=\{([^{}]+)\}/g))for(const n of m[2].matchAll(/(_0x[\da-f]+):(0x[\da-f]+)/g))constants.set(`${m[1]}.${n[1]}`,n[2]);
  const output=source.replace(/(_0x[\da-f]+)\((0x[\da-f]+|_0x[\da-f]+\._0x[\da-f]+)\)/g,(whole,fn,arg)=>{
    if(!aliases.has(fn))return whole;
    const n=arg.startsWith('0x')?arg:constants.get(arg);
    return n?JSON.stringify(context[initial[2]](Number(n)))??whole:whole;
  });
  fs.writeFileSync(file.replace('-public.js','-decoded.js'),output);
  console.log(file,output.length);
}
