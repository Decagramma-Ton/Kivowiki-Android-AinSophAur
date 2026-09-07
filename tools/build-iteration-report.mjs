/** 汇总已经完成的验证，不运行测试，不把历史失败或模拟数据改写成通过。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const currentBuild = await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8');
if (!/versionName\s*=\s*"0\.2\.0"/.test(currentBuild))
    throw new Error('此脚本仅用于 0.2.0；当前版本请运行 node tools/build-character-report.mjs。');
const output = path.join(root, 'artifacts/iteration-0.2');
const bootstrap = path.join(root, 'tools/.bootstrap');
const smoke = JSON.parse(await fs.readFile(path.join(output, 'release-smoke.json'), 'utf8'));
const inspection = JSON.parse(await fs.readFile(path.join(output, 'apk-inspection.json'), 'utf8'));
const apk = await fs.readFile(path.join(root, 'artifacts', inspection.file));
const hash = createHash('sha256').update(apk).digest('hex');
if (smoke.apkSha256 !== hash || inspection.sha256 !== hash) throw Error('验收结果与当前 APK 不一致');
const appTests = await fs.readFile(path.join(bootstrap, 'iteration-app-final.log'), 'utf8');
if (!appTests.includes('OK (10 tests)') || appTests.includes('FAILURES!!!')) throw Error('当前界面套件没有全部通过');
const homeOrderTest = await fs.readFile(path.join(output, 'home-order-final.txt'), 'utf8');
if (!homeOrderTest.includes('OK (1 test)') || homeOrderTest.includes('FAILURES!!!'))
    throw Error('首页最终排序回归没有通过');
const build = await fs.readFile(path.join(bootstrap, 'iteration-delivery-build.log'), 'utf8');
if (!build.includes('BUILD SUCCESSFUL')) throw Error('当前交付构建未通过');
await fs.writeFile(path.join(output, 'app-device-results.txt'), appTests);
await fs.writeFile(path.join(output, 'delivery-build.txt'), build);
await fs.copyFile(path.join(bootstrap, 'iteration-signature.log'), path.join(output, 'apk-signature.txt'));
let unitTests = 0;
for (const directory of ['core/model/build/test-results/test', 'core/data/build/test-results/testDebugUnitTest', 'core/content/build/test-results/testDebugUnitTest']) {
    for (const file of (await fs.readdir(path.join(root, directory))).filter(file => file.endsWith('.xml'))) {
        const xml = await fs.readFile(path.join(root, directory, file), 'utf8');
        const suite = xml.match(/<testsuite\b[^>]+>/)?.[0] ?? '';
        if (!/failures="0"/.test(suite) || !/errors="0"/.test(suite)) throw Error('单元测试失败：' + file);
        unitTests += Number(suite.match(/tests="(\d+)"/)?.[1] ?? 0);
    }
}
for (const name of ['font200-final', 'tablet-final', 'offline-final', 'offline-warmup']) {
    const source = await fs.readFile(path.join(output, name + '.log'), 'utf8');
    if (!source.includes('OK (1 test)') || source.includes('FAILURES!!!')) throw Error('设备场景未通过：' + name);
    await fs.writeFile(path.join(output, name + '.txt'), source);
}
const storageDir = path.join(root, 'core/data/build/outputs/androidTest-results/connected/debug');
const storageFile = (await fs.readdir(storageDir)).find(file => file.startsWith('TEST-') && file.endsWith('.xml'));
const storage = await fs.readFile(path.join(storageDir, storageFile), 'utf8');
if (!storage.includes('failures="0"') || !storage.includes('tests="8"')) throw Error('存储设备套件结果不完整');
await fs.writeFile(path.join(output, 'storage-device-results.xml'), storage);
const median = [...smoke.coldStartsMs].sort((a,b) => a-b)[2];
const mem = await fs.readFile(path.join(output, 'release-meminfo.txt'), 'utf8');
const pss = Number(mem.match(/TOTAL PSS:\s*(\d+)/)?.[1] ?? 0);
const summary = { version: '0.2.0', generatedAt: new Date().toISOString(), apkBytes: apk.length, sha256: hash, unitTests, appDeviceTests: 10, homeOrderRegressionTests: 1, localDataDeviceTests: 7, liveAccountTests: 1, liveAccountEvidence: '专用账号运行时参数测试于 2026-09-07 返回 OK (1 test)，耗时 1.399 秒。该记录根据实际工具输出整理，凭据和正文不保存。', coldStartsMs: smoke.coldStartsMs, medianColdStartMs: median, totalPssKiB: pss, verifiedReleaseChecks: smoke.checks, limitations: ['真实手机未连接', 'QQ 客户端唤起待手机测试', 'Mermaid / LaTeX 及部分行内方言可读降级', '社区内容为带日期的公开快照'] };
await fs.writeFile(path.join(output, 'verification.json'), JSON.stringify(summary, null, 2) + '\n');
const report = `# 便携kivo古书馆 0.2.0 验收报告

日期：2026-09-07。本报告对应下列最终 APK，旧版证据不计入本版结果。

## 安装包

- 文件：[${inspection.file}](${inspection.file})
- 大小：${apk.length.toLocaleString('en-US')} 字节（约 ${(apk.length / 1048576).toFixed(2)} MiB）。
- SHA-256：${hash}
- 包名 wiki.kivo.app.preview，版本 0.2.0 / code 2；R8 和资源压缩已开启。
- v2 测试签名验证通过；与 0.1.0 使用相同验收证书。ZIP 与全部 8 个本机库的 16 KiB 对齐检查通过。

## 实际通过的检查

| 检查 | 结果 |
| --- | --- |
| 单元测试 | ${unitTests} 项通过，含提示块、嵌套、代码示例、图片尺寸、精确匹配与译名回退 |
| App 界面测试 | 10 项完整套件通过；最终首页排序另有 1 项回归通过，校验资讯在近期编辑之前，并确认“本周生日”可见 |
| 数据设备测试 | 7 项通过；普通套件中真实账号用例按设计跳过 |
| 单独真实账号测试 | 1 项通过：原生登录、权限文章读取、加密会话恢复、登出；不保存测试口令、令牌、内部正文 |
| 200% 字号 | 导航、设置与登录入口可见可操作，通过 |
| 平板 | 1920×1200 / 200 dpi 专用 AVD，侧栏导航与双栏首页通过 |
| 实际断网 | 专用 AVD 的 activeNetwork 为 null 时，已缓存公告仍可打开、刷新失败不丢正文，通过 |
| 静态检查 | Debug / Release Lint 均无错误；剩余提示主要为依赖新版提醒及原有兼容写法 |
| 最终 Release | ${smoke.checks.join('；')} |

设备为 Kivo_Archive_Phone，Android 36.1 Google Play x86_64，1080×2400 / 420 dpi。测试调整过的字号、网络和窗口配置均已恢复。

5 次进程冷启动：${smoke.coldStartsMs.join(' / ')} ms，中位数 **${median} ms**。首页采样 TOTAL PSS 为 ${pss || '未取得'} KiB。这是当前电脑模拟器的有限样本，不是低端真机帧率或性能承诺。

## 已处理的回归问题

新增设置组后，旧测试会在控件尚未进入懒列表组合区域时定位它；已改为按父列表滚动定位，并通过普通、200% 和平板复测。一次外部搜索等待超时在后续独立与完整界面套件复测通过；保留错误/超时反馈，不把生产网络偶发失败伪装为空列表。最终通过记录见 [界面测试原始输出](iteration-0.2/app-device-results.txt)。

## 尚需手动检查的边界

- 未连接真实手机，不宣称厂商 ROM、Android 8/17 或低端设备已经全覆盖。
- QQ 频道页面已真实加载、App 内返回正常；加入后的 QQ/TIM 客户端唤起未实测。
- 社区页面是 2026-09-07 核对快照，带网站最新内容入口。
- 新增常用 Markdown 容器已验证，但 Mermaid、LaTeX 和部分行内特殊样式仍为可读降级。
- 完整业务模块、正式签名和公开发行不属于本轮完成范围。

## 文档与仓库

README、当前状态、最新交接、F1 规格、ADR-023、API 差异、首页设计、用户说明，以及原方案 01–18 的版本说明均已同步；[19 实施同步](../../App项目文档/19-系统体验迭代实施同步.md) 记录所有主要调整和原因。

仓库：[Decagramma-Ton/Kivowiki-Android-AinSophAur](https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur)。工作区不提交 APK、签名私钥、设备数据、原始审计、口令或内部正文；实际提交以仓库历史为准。

[电脑与手机验收说明](验收说明.md) · [真实截图图册](界面验收.html) · [机器可读验证摘要](iteration-0.2/verification.json)
`;
await fs.writeFile(path.join(root, 'artifacts/验收报告.md'), report);
const shots = [
 ['01-home-light','首页 · 资讯、近期编辑与本周生日','phone'],['02-schedules','基沃托斯日程','phone'],['09-students','独立角色图鉴','phone'],['08-catalog','资料入口','phone'],['09-collection','典藏入口','phone'],
 ['12-search-empty','搜索 · 站娘空状态','phone'],['13-search-results','全站搜索','phone'],['14-preferences','启动目标与译名设置','phone'],['15-contributors','贡献者名单','phone'],['16-contact','帮助我们','phone'],['17-qq-channel','QQ 频道内置页面','phone'],
 ['06-profile-light','我的古书馆','phone'],['10-settings-dark','深色设置','dark'],['11-home-dark','深色首页','dark'],['phone-font200-home','200% 字号 · 首页','large'],['phone-font200-settings','200% 字号 · 设置','large'],
 ['tablet-home','平板 · 双栏首页','tablet'],['tablet-settings','平板 · 设置','tablet'],['release-start-catalog','最终 APK · 从图鉴启动','phone'],['release-reader','最终 APK · 阅读器','phone']
];
const escape = s => s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
for (const [name] of shots) await fs.access(path.join(output, 'screenshots', name + '.png'));
const figures = shots.map(([name,title,group]) => `<figure data-group="${group}"><button class="shot" aria-label="放大：${escape(title)}"><img loading="lazy" src="iteration-0.2/screenshots/${name}.png" alt="${escape(title)}"></button><figcaption>${escape(title)}</figcaption></figure>`).join('\n');
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>便携kivo古书馆 0.2.0 · 真实界面验收</title>
<style>*{box-sizing:border-box}body{margin:0;background:#f3f7fa;color:#18374a;font-family:system-ui,'Microsoft YaHei',sans-serif}header,main{max-width:1360px;margin:auto;padding:32px}header{padding-top:58px}h1{font-size:clamp(28px,4vw,48px);margin:12px 0}p{line-height:1.8;color:#526d7e}small{letter-spacing:.12em;color:#0084a3}nav{display:flex;gap:10px;flex-wrap:wrap;margin:26px 0}button,a{font:inherit}nav button{border:1px solid #c9dde6;background:white;color:#276178;border-radius:24px;padding:10px 22px;cursor:pointer}nav button[aria-pressed=true]{background:#087fa2;color:white;border-color:#087fa2}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:28px}figure{margin:0;min-width:0;background:white;padding:12px;border-radius:24px;border:1px solid #e4edf2;align-self:start}figure[data-group=tablet]{grid-column:1/-1}.shot{background:transparent;border:0;padding:0;width:100%;cursor:zoom-in}.shot img{width:100%;height:auto;display:block;border-radius:16px}figcaption{padding:16px 8px 8px;font-weight:600}figure[hidden]{display:none}dialog{border:0;border-radius:16px;padding:14px;max-width:96vw;max-height:96vh;background:#f3f7fa}dialog::backdrop{background:#0a2130d9}dialog img{max-width:90vw;max-height:82vh;object-fit:contain;display:block;margin:auto}dialog button{display:block;margin:0 0 10px auto;padding:9px 18px;border:0;border-radius:16px;cursor:pointer}a{color:#087fa2}footer{padding:40px;text-align:center;color:#607987}</style>
<header><small>KIVO ARCHIVE / VERSION 0.2.0</small><h1>把古书馆，放进口袋。</h1><p>本版真实 Android 运行截图<br>26 项单元测试 · 10 项界面测试 · 200% 字号与平板检查通过。截图不能代替交互验收，请双击工程中的「电脑验收.cmd」。</p><nav aria-label="截图分类"><button data-filter="all" aria-pressed="true">全部</button><button data-filter="phone" aria-pressed="false">手机</button><button data-filter="dark" aria-pressed="false">深色</button><button data-filter="large" aria-pressed="false">大字号</button><button data-filter="tablet" aria-pressed="false">平板</button></nav><p><a href="验收说明.md">操作说明</a> · <a href="验收报告.md">完整验收报告</a></p></header><main class="grid">${figures}</main><footer>真实手机及 QQ 客户端唤起待手动验收 · 公开页面快照核对于 2026.09.07</footer><dialog><button aria-label="关闭大图">关闭 ×</button><img alt="放大的验收截图"></dialog>
<script>const dialog=document.querySelector('dialog');document.querySelectorAll('[data-filter]').forEach(button=>button.onclick=()=>{document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));document.querySelectorAll('figure').forEach(f=>f.hidden=button.dataset.filter!=='all'&&f.dataset.group!==button.dataset.filter)});document.querySelectorAll('.shot').forEach(button=>button.onclick=()=>{const src=button.querySelector('img');dialog.querySelector('img').src=src.src;dialog.querySelector('img').alt=src.alt;dialog.showModal()});dialog.querySelector('button').onclick=()=>dialog.close();dialog.onclick=event=>{if(event.target===dialog)dialog.close()};</script></html>`;
await fs.writeFile(path.join(root, 'artifacts/界面验收.html'), html);
console.log(JSON.stringify({version:summary.version,unitTests,appTests:10,sha256:hash,median,shots:shots.length},null,2));
