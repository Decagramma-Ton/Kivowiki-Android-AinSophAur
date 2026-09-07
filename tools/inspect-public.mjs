// 仅分析已公开下载的前端资源；不载入浏览器会话、不运行页面业务代码。
import fs from 'node:fs';
import vm from 'node:vm';
const dir = new URL('./audit/', import.meta.url);
const source = fs.readFileSync(new URL('site-main.js', dir), 'utf8');
function functionText(name) {
  const start = source.indexOf(`function ${name}(`);
  let p = source.indexOf('{', start), depth = 0, quote = '';
  for (; p < source.length; p++) {
    const c = source[p];
    if (quote) { if (c === '\\') p++; else if (c === quote) quote = ''; continue; }
    if ('\'"`'.includes(c)) { quote = c; continue; }
    if (c === '{') depth++;
    if (c === '}' && --depth === 0) return source.slice(start, p + 1);
    // 普通字符不改变括号深度。
    if (c !== '}') { /* handled above */ }
  }
  throw Error('String helper not found');
}
// 只提取字符串表、索引函数和整数轮转。隔离上下文没有文件、网络、进程对象。
const start = source.indexOf('const _0x424b2c=');
const end = /\}\(_0x3432,0x[0-9a-f]+\)\);/.exec(source.slice(start));
if (!end) throw Error('Public bundle format changed');
const helper = source.slice(start, start + end.index + end[0].length);
const context = vm.createContext(Object.create(null), { codeGeneration: { strings: false, wasm: false } });
vm.runInContext(functionText('_0x3432') + '\n' + functionText('_0x26c0') + '\n' + helper, context, { timeout: 2000 });
const aliases = new Set(['_0x26c0', '_0x424b2c']);
for (let pass = 0; pass < 12; pass++) for (const m of source.matchAll(/(_0x[\da-f]+)\s*=\s*(_0x[\da-f]+)(?=[,;])/g)) if (aliases.has(m[2])) aliases.add(m[1]);
const constants = new Map();
for (const m of source.matchAll(/(_0x[\da-f]+)=\{([^{}]+)\}/g)) for (const n of m[2].matchAll(/(_0x[\da-f]+):(0x[\da-f]+)/g)) constants.set(`${m[1]}.${n[1]}`, n[2]);
let decoded = source.replace(/(_0x[\da-f]+)\((0x[\da-f]+|_0x[\da-f]+\._0x[\da-f]+)\)/g, (whole, fn, arg) => {
  if (!aliases.has(fn)) return whole;
  const n = arg.startsWith('0x') ? arg : constants.get(arg);
  if (!n) return whole;
  return JSON.stringify(context._0x26c0(Number(n))) ?? whole;
});
fs.writeFileSync(new URL('site-main-decoded.js', dir), decoded);
for (const term of ['const LM=', 'LM={', "'login':", "'logout':", "'updateUserInfo'", 'refresh_token', 'refreshToken', 'baseURL', "hB="]) {
  const pos = decoded.indexOf(term, 600000);
  console.log(term, pos, decoded.slice(Math.max(0, pos - 150), pos + 1900));
}
