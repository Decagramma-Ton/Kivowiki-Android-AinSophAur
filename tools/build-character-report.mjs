/** 只汇总本地真实验收证据；版本、摘要或测试状态不符时拒绝生成通过报告。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const artifacts = path.join(root, 'artifacts');
const out = path.join(artifacts, 'character-0.3');
const read = name => fs.readFile(path.join(out, name), 'utf8');
const buildSource = await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8');
if (!/versionName\s*=\s*"0\.3\.0"/.test(buildSource)) throw Error('此生成器仅用于 0.3.0。');
const inspection = JSON.parse(await read('apk-inspection.json'));
const smoke = JSON.parse(await read('release-smoke.json'));
const apk = await fs.readFile(path.join(artifacts, inspection.file));
const hash = createHash('sha256').update(apk).digest('hex');
if (hash !== inspection.sha256 || hash !== smoke.apkSha256) throw Error('验收证据与交付 APK 不一致。');
if (!(await read('release-build.log')).includes('BUILD SUCCESSFUL')) throw Error('最终构建没有通过。');
if (!(await read('signature.log')).includes('Verified using v2 scheme (APK Signature Scheme v2): true')) throw Error('签名证据缺失。');
if (!(await read('zipalign.log')).includes('Verification successful')) throw Error('ZIP 对齐检查未通过。');
if (!inspection.nativeLibraries.length || inspection.nativeLibraries.some(item => !item.supports16KiBAlignment)) throw Error('ELF 对齐检查未通过。');

const unitSuites = [];
for (const directory of ['core/model/build/test-results/test', 'core/data/build/test-results/testDebugUnitTest', 'core/content/build/test-results/testDebugUnitTest', 'core/media/build/test-results/testDebugUnitTest']) {
    for (const file of (await fs.readdir(path.join(root, directory))).filter(name => name.endsWith('.xml'))) {
        const xml = await fs.readFile(path.join(root, directory, file), 'utf8');
        const tag = xml.match(/<testsuite\b[^>]+>/)?.[0] ?? '';
        const attrs = Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
        if (attrs.failures !== '0' || attrs.errors !== '0' || !Number(attrs.tests)) throw Error('单元结果无效：' + file);
        unitSuites.push({ name: attrs.name, tests: Number(attrs.tests), skipped: Number(attrs.skipped || 0) });
    }
}
const unitTotal = unitSuites.reduce((sum, suite) => sum + suite.tests - suite.skipped, 0);
if (unitTotal !== 47) throw Error('单元用例数量发生变化，请复核报告定义：' + unitTotal);

// am instrument 返回 -1 是运行器正常结束；真正失败与跳过以每个测试的状态码为准。
const device = {};
for (const [name, expected] of [['baseline', 10], ['phone', 8], ['font200', 2], ['tablet', 6], ['offline', 2], ['storage-device', 9]]) {
    const log = await read(name + '.log');
    const reported = Number(log.match(/OK \((\d+) tests?\)/)?.[1]);
    const passed = [...log.matchAll(/^INSTRUMENTATION_STATUS_CODE: 0\s*$/gm)].length;
    const skipped = [...log.matchAll(/^INSTRUMENTATION_STATUS_CODE: -4\s*$/gm)].length;
    if (reported !== expected || passed + skipped !== expected || /FAILURES!!!|INSTRUMENTATION_FAILED/.test(log)) throw Error('设备证据没有全部完成：' + name);
    device[name] = { passed, skipped };
}
const lint = {};
for (const variant of ['debug', 'release']) {
    const xml = await fs.readFile(path.join(root, `app/build/reports/lint-results-${variant}.xml`), 'utf8');
    lint[variant] = { errors: [...xml.matchAll(/severity="(?:Error|Fatal)"/g)].length, warnings: [...xml.matchAll(/severity="Warning"/g)].length };
    if (lint[variant].errors) throw Error('Lint 存在错误：' + variant);
}

const shots = [
    ['phone-catalog', '搜索与卡片', 'phone', '真实“白子”查询：原皮、换装和衍生角色分别展示'],
    ['phone-catalog-list', '紧凑列表', 'phone', '同一查询切换视图，返回后保留查询与偏好'],
    ['phone-filters', '分组筛选面板', 'phone', '24 个维度分为四组，取消、重置和应用各有明确作用'],
    ['phone-character-86', '白子 · 档案首页', 'phone', '翻译、基本信息、组织关系、换装与四分类入口'],
    ['phone-info-86', '正文对齐修复', 'phone', '实际资料中的图片、图注和查看入口正确居中'],
    ['phone-hoshino-attack', '星野（临战）· 类型切换', 'phone', '攻击／防御切换整套战斗信息'],
    ['phone-character-346', '日奈（礼服）', 'phone', '保留多个衍生技能及实际收录等级'],
    ['phone-character-136', '伊吹', 'phone', '多个 EX 和关联角色技能使用独立数据'],
    ['phone-character-619', '剧情登场角色', 'phone', '尚未实装时仍保留资料，不把零值模板当作战斗数值'],
    ['phone-character-593', 'NPC 档案', 'phone', '独立身份与未知信息显示'],
    ['phone-voice-cn', '国语语音', 'phone', '独立语言数组、分类、搜索和逐条播放／保存'],
    ['phone-gallery-gif', '动态图鉴赏', 'media', '实际 GIF 播放、暂停与缩放；系统栏保持清晰对比'],
    ['phone-preview-1562', 'Spine 立绘', 'media', '实际骨架、动作与原生画布'],
    ['phone-preview-467', 'Spine 回忆大厅', 'media', '双页 Atlas 加载，独立镜头和播放控制'],
    ['release-native-model', '人物模型 · Release', 'media', '交付 R8 包中的 Filament、蒙皮动画与嘴型'],
    ['release-native-halo', '光环模型 · Release', 'media', '旧 OBJ 转换后的实际原生渲染'],
    ['tablet-preview-440', '宽屏媒体工作区', 'tablet', '画布与侧边控制区，1920 × 1200 / 200 dpi'],
    ['tablet-character-86', '宽屏角色档案', 'tablet', '侧栏导航与限制阅读宽度的档案正文'],
    ['font200-character-373', '200% 字号 · 档案', 'font', '组织与社团独占整行，避免名称被挤成逐字换行'],
    ['font200-filters', '200% 字号 · 筛选', 'font', '标签自然换行、可滚动，操作按钮保持可达'],
    ['offline-cached-info', '断网阅读', 'offline', 'activeNetwork 为 null，已缓存的角色资料继续可读'],
    ['offline-preview-467', '断网回忆大厅', 'offline', '已完整缓存的骨架与纹理无需重新下载'],
];
for (const [name] of shots) await fs.access(path.join(out, `screenshots/${name}.png`));
const times = [...smoke.coldStartsMs].sort((a, b) => a - b);
const median = times[Math.floor(times.length / 2)];
const pss = Number((await read('release-meminfo.txt')).match(/TOTAL PSS:\s+(\d+)/)?.[1]);
const size = (apk.length / 1048576).toFixed(2);
const verification = { version: '0.3.0', apk: { file: inspection.file, bytes: apk.length, sha256: hash }, unitTotal, unitSuites, device, lint, nativeLibraries: inspection.nativeLibraries.length, smoke, screenshots: shots.map(([name, title]) => ({ file: `screenshots/${name}.png`, title })) };
await fs.writeFile(path.join(out, 'verification.json'), JSON.stringify(verification, null, 2) + '\n');

const report = `# 便携古书馆 0.3.0 · 角色模块验收报告

日期：2026-09-07。本报告对应本机实际构建与专用 Android 模拟器中的运行结果。没有用效果图代替安装后的画面。

## 交付制品

| 项目 | 结果 |
| --- | --- |
| APK | [${inspection.file}](../${inspection.file}) |
| 大小 | ${apk.length.toLocaleString('en-US')} 字节 / ${size} MiB；包含四种 ABI 的通用包 |
| SHA-256 | ${hash} |
| 身份 | wiki.kivo.app.preview；0.3.0 / code 3；与旧验收版同一测试签名 |
| 构建 | R8、资源缩减开启；minSdk 26，target/compile 37 |
| 签名与对齐 | v2 签名检查、ZIP 16 KiB 对齐、${inspection.nativeLibraries.length} 个本机库的 ELF LOAD 段检查通过 |
| 权限 | INTERNET、ACCESS_NETWORK_STATE、媒体库合并的普通 WAKE_LOCK，以及 AndroidX 签名级内部 receiver 权限；无存储运行时权限、录屏授权或后台媒体服务 |

原文证据：[制品结构](apk-inspection.json)、[签名](signature.log)、[对齐](zipalign.log)、[Release 构建](release-build.log)、[机器摘要](verification.json)。静态 16 KiB 对齐不等于已经在 16 KiB 页真机上运行。

## 实际验证

| 检查 | 完成情况 |
| --- | --- |
| 单元测试 | ${unitTotal} 项通过：查询枚举／false／生日、六角色字段、协议／认证／重试、Markdown 容器与富表格、媒体资源与 GLB／OBJ |
| 原有 App 回归 | ${device.baseline.passed} 项通过：导航、主题、密码保护、公告收藏、搜索返回、频道／社区、首页排序、滚动恢复与设置 |
| 新角色手机套件 | ${device.phone.passed} 项通过，${device.phone.skipped} 项离线专用用例按条件跳过；离线项由下列独立断网场景补验 |
| 200% 字号 | ${device.font200.passed} 项通过：六个角色、图鉴搜索／筛选／视图；另行人工查看真实截图 |
| 宽屏 | ${device.tablet.passed} 项通过，${device.tablet.skipped} 项离线专用用例跳过；包括 GIF、语音和四类原生预览 |
| 真实断网 | ${device.offline.passed} 项通过：断言 activeNetwork == null，读取已缓存资料与四类媒体；测试结束恢复网络 |
| 数据设备测试 | ${device['storage-device'].passed} 项通过；${device['storage-device'].skipped} 项真实账号用例因本轮未提供凭据而跳过 |
| Lint | Debug：${lint.debug.errors} 错误 / ${lint.debug.warnings} 提示；Release：${lint.release.errors} 错误 / ${lint.release.warnings} 提示，主要为依赖新版与兼容 API 提醒 |
| 最终压缩包 | ${smoke.checks.join('；')} |

不把同一用例在不同窗口或多次运行的次数累加为更多独立覆盖。设备测试原文：[通用回归](baseline.log)、[角色手机](phone.log)、[大字号](font200.log)、[宽屏](tablet.log)、[断网](offline.log)、[数据存储](storage-device.log)、[Release](release-smoke.json)。feature:character 暂无单独 JVM 用例，行为由 App 设备套件验证，不把 NO-SOURCE 任务记成测试通过。

写入检查使用 MockWebServer 与受控服务，覆盖显式认证、表态切换、成功 data=null、未登录拒绝、令牌过期、不确定写入不自动重发。**本轮没有向生产环境提交测试表态或补充资料**，不把替身响应记为生产账号验收。

## 关键功能与实际修正

- 图鉴保留网站全部 24 个筛选维度与 8 种排序，卡片／紧凑列表共用查询和分页。真实姓名搜索需同时传 name 与 character_data_search，仅后者在本次线上返回全部列表，已修正代码与接口文档。
- 六类档案覆盖右侧基本信息、翻译、换装、类型、表态和数据／资料／鉴赏／语音。日奈的衍生等级、伊吹的多个 EX／联动分别保留；NPC／未实装角色不展示模板数值。
- ::: center / left / right 及更多冒号、嵌套、无空格写法、代码围栏已有解析回归；原生排版测试检查真实文字布局坐标，图片和图注继承对齐方向。
- Spine 立绘 1562、大厅 467、人物 GLB 440、光环 OBJ 87 均检查了实际像素、播放暂停、镜头复位和后台停止。修复画布越界覆盖工具栏、后台重组暂停时无法及时停帧，以及人物预览色彩转换问题。
- 实际 GIF 支持播放／暂停／缩放；200% 字号下组织关系改为整行。视频导出通过设备真实编码及读回校验，保持纵横比，取消时清理临时文件。
- 修复冷启动深链接与异步启动偏好的竞争：外部角色链接先选定目标后，稍后加载的图鉴偏好不会再次覆盖页面。Release 脚本区分全新任务与恢复已有任务，并等待后台进程实际结束后才检查恢复。

详细覆盖矩阵及设计调整原因见 [20 实施同步](../../../App项目文档/20-角色图鉴与角色档案实施同步.md) 和 [F2 模块文档](../../开发状态/模块/F2-角色图鉴与档案.md)。

## 设备与测量范围

设备：Kivo_Archive_Phone / emulator-5558，Android 36.1 Google Play x86_64，1080 × 2400 / 420 dpi；平板覆盖 1920 × 1200 / 200 dpi；字体最高 200%。临时字号、尺寸、密度和网络设置均由 finally 恢复。

Release 五次进程冷启动 TotalTime：${smoke.coldStartsMs.join(' / ')} ms，中位数 ${median} ms。首页一次 TOTAL PSS 为 ${pss} KiB（约 ${(pss / 1024).toFixed(1)} MiB）。这些是保留缓存、进程重启的模拟器样本，不能代表网络完成耗时、真机 60fps、温控、续航或低端设备内存表现。

原生资源采取按需下载、有界解码、单个活动预览、512 MiB 私有磁盘缓存和后台停帧。60／30 帧为调度目标；没有宣称已经达到原方案全部真机性能门槛。

## 尚需站方验收的边界

1. 未连接真实手机，Android 8.x GIF 后备路径、多厂商 GPU、16 KiB 页运行及全站每个资源变体尚未逐一实测。
2. 未进行本轮生产账号写入；表态与补充实际业务成功状态仍需账号持有人验收。
3. 组织／社团完整页面按要求预留。原有 Mermaid／LaTeX 可读降级仍存在；没有丢弃原文，也未宣称与网站所有插件视觉完全等价。
4. 大厅触摸事件与台词缺乏准确映射契约，不猜字幕时序。录像提供 5 秒、720 长边、无声 MP4；超预算或非 4.2 骨架明确报错，不伪装为空白成功。
5. 包内保留 Spine 运行时许可原文，站方公开分发前需核对适用的 Spine Editor／运行时授权；素材使用授权与运行时授权分别处理。当前仍是内部测试签名，未公开发行。

源码、接口补充、当前状态、最新交接、模块／ADR 和设计方案已同步。本轮未执行 git commit、push 或部署。

[真实界面图册](界面验收.html) · [操作验收说明](../验收说明.md) · [最新交接](../../开发状态/最新交接.md)
`;
await fs.writeFile(path.join(out, '验收报告.md'), report);

const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
const cards = shots.map(([name, title, group, caption]) => `<button class="shot ${group === 'tablet' ? 'wide' : ''}" data-group="${group}" data-image="screenshots/${name}.png" aria-label="放大：${escape(title)}"><span class="photo"><img loading="lazy" src="screenshots/${name}.png" alt="${escape(title)}实际截图"></span><span class="caption"><strong>${escape(title)}</strong><small>${escape(caption)}</small></span></button>`).join('\n');
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>kivo 古书馆 · 角色模块实际验收</title>
<style>
:root{color-scheme:light;--ink:#183449;--muted:#587082;--blue:#087fa3;--line:#d8e5ec;--bg:#f3f7fa}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.75 "Segoe UI","Microsoft YaHei",sans-serif}header,main,footer{max-width:1280px;margin:auto;padding:28px 32px}header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line)}.brand{font-weight:750;font-size:24px}.tag{border:1px solid var(--line);border-radius:30px;padding:5px 14px;font-size:13px}a{color:var(--blue);text-underline-offset:4px}button{font:inherit}h1{font-size:clamp(32px,4.5vw,58px);line-height:1.25;margin:20px 0}h2{margin:0 0 14px;font-size:26px}p{margin:0 0 18px}.muted,small{color:var(--muted)}.hero{display:grid;grid-template-columns:1.3fr .7fr;gap:65px;align-items:center;padding:40px 0 56px}.hero>img{width:245px;max-width:100%;border:7px solid white;border-radius:32px;box-shadow:0 18px 55px #18344920;margin:auto}.eyebrow{font-size:12px;letter-spacing:2px;color:var(--blue);font-weight:700}.actions{display:flex;flex-wrap:wrap;gap:12px;margin:26px 0}.actions a{padding:10px 18px;border:1px solid var(--line);border-radius:12px;background:#fff;text-decoration:none}.actions a:first-child{background:var(--blue);color:white}.stats{display:flex;gap:40px;border-top:1px solid var(--line);padding-top:22px}.stats b{display:block;font-size:28px}.stats small{font-size:13px}.note{border:1px solid var(--line);background:#fff;border-radius:18px;padding:22px 26px;margin-bottom:40px}.note p:last-child{margin:0}.filters{display:flex;flex-wrap:wrap;gap:8px;margin:24px 0}.filters button{border:1px solid var(--line);border-radius:30px;background:white;padding:7px 16px;color:var(--ink);cursor:pointer}.filters button[aria-pressed=true]{background:var(--ink);color:white}.gallery{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px}.shot{border:1px solid var(--line);padding:0;border-radius:20px;overflow:hidden;background:white;text-align:left;color:var(--ink);cursor:zoom-in}.shot[hidden]{display:none}.shot:hover{border-color:var(--blue)}.photo{display:flex;height:530px;padding:18px;align-items:center;justify-content:center;background:#e8eff4}.photo img{display:block;max-height:100%;max-width:100%;object-fit:contain;border-radius:12px}.caption{display:block;padding:17px 20px}.caption strong,.caption small{display:block}.caption small{font-size:13px;margin-top:5px}.wide{grid-column:1/-1}.wide .photo{height:auto}.wide img{width:100%}.detail{margin-top:42px;border-top:1px solid var(--line);padding-top:30px}.hash{word-break:break-all;font-size:12px}button:focus-visible,a:focus-visible{outline:3px solid #35b4d3;outline-offset:4px}footer{font-size:13px;color:var(--muted)}dialog{padding:16px;border:0;border-radius:16px;max-width:95vw;max-height:95vh;background:var(--bg)}dialog::backdrop{background:#102536df}dialog img{display:block;max-width:88vw;max-height:80vh;object-fit:contain;margin:auto}dialog button{display:block;margin:0 0 12px auto;padding:8px 16px;border:0;border-radius:8px;background:white;cursor:pointer}@media(max-width:800px){header,main,footer{padding:22px}.hero{gap:28px}.gallery{grid-template-columns:repeat(2,minmax(0,1fr))}.stats{gap:22px}.photo{height:480px}}@media(max-width:560px){.hero{grid-template-columns:1fr;padding-top:16px}.hero>img{width:220px}.gallery{grid-template-columns:1fr}.photo{height:550px}.stats{justify-content:space-between;gap:8px}.stats b{font-size:24px}.brand{font-size:20px}}
</style>
<header><span class="brand">kivo 古书馆</span><span class="tag">Android · 0.3.0</span></header><main>
<section class="hero"><div><span class="eyebrow">CHARACTER ARCHIVE / 实际运行记录</span><h1>角色的每一面，<br>随时翻阅。</h1><p class="muted">从搜索筛选到完整档案，从文字资料到动态立绘、模型与语音。本页记录真实安装后的界面，点击图片即可放大。</p><div class="actions"><a href="../${inspection.file}" download>下载本地验收 APK</a><a href="验收报告.md">查看验收报告</a><a href="../验收说明.md">操作说明</a></div><div class="stats"><div><b>24</b><small>完整筛选维度</small></div><div><b>6</b><small>代表角色逐项核对</small></div><div><b>${size} MiB</b><small>四 ABI 通用包</small></div></div></div><img src="screenshots/phone-character-86.png" alt="白子原生档案实际界面"></section>
<div class="note"><p><strong>可以直接操作的 App。</strong> 回到 App项目开发 文件夹，双击“电脑验收.cmd”打开专用 Android 模拟器。也可将 APK 复制到自己的安卓手机正常安装。</p><p class="muted">本次完成本机自动检查与模拟器验收；尚未覆盖真实手机和生产账号写入。图册包含 Debug 验收画面与明确标记的 Release 交付包画面，原始截图保留在本地。</p></div>
<section><h2>真实界面图册</h2><p class="muted">Markdown 居中已经修复；大字号、宽屏与断网场景均有对应运行记录。</p><div class="filters" role="group" aria-label="筛选截图">${[['all','全部'],['phone','手机'],['media','原生媒体'],['tablet','宽屏'],['font','200% 字号'],['offline','断网']].map(([key,label])=>`<button data-filter="${key}" aria-pressed="${key==='all'}">${label}</button>`).join('')}</div><div class="gallery">${cards}</div></section>
<section class="detail"><h2>验证与继续开发</h2><p>${unitTotal} 项单元测试通过；角色、原有页面、存储与环境专项分开统计，详见<a href="验收报告.md">验收报告</a>。资源下载、解析和渲染均有预算，后台停止动画。真实设备的性能、温控和兼容性仍需继续验收。</p><p><a href="../../../App项目文档/20-角色图鉴与角色档案实施同步.md">完整覆盖与方案调整</a> · <a href="../../开发状态/最新交接.md">开发交接</a> · <a href="verification.json">机器可读摘要</a></p><p class="hash muted">SHA-256：${hash}</p><p class="muted">内部测试签名，未公开发行。Spine 运行时许可已随包保留，公开分发前需核对站方适用授权。</p></section></main><footer>2026-09-07 · 所有图片来自实际运行；站点及游戏素材保留原有权利归属。</footer>
<dialog id="viewer"><button id="close">关闭 ×</button><img id="full" alt="放大的实际界面截图"></dialog><script>
const filters=[...document.querySelectorAll('[data-filter]')];filters.forEach(button=>button.addEventListener('click',()=>{filters.forEach(item=>item.setAttribute('aria-pressed',String(item===button)));document.querySelectorAll('.shot').forEach(shot=>shot.hidden=button.dataset.filter!=='all'&&shot.dataset.group!==button.dataset.filter)}));const viewer=document.getElementById('viewer');document.querySelectorAll('.shot').forEach(shot=>shot.addEventListener('click',()=>{const full=document.getElementById('full');full.src=shot.dataset.image;full.alt=shot.getAttribute('aria-label');viewer.showModal()}));document.getElementById('close').onclick=()=>viewer.close();viewer.addEventListener('click',event=>{if(event.target===viewer)viewer.close()});
</script></html>`;
await fs.writeFile(path.join(out, '界面验收.html'), html);

// 归档旧的根报告，重算本地相对链接；不删除任何历史证据。
for (const filename of ['验收报告.md', '界面验收.html']) {
    const previous = await fs.readFile(path.join(artifacts, filename), 'utf8');
    if (!previous.includes('0.2.0') || previous.includes('0.3.0')) continue;
    const archive = path.join(artifacts, 'iteration-0.2', filename);
    const relocate = target => {
        if (/^(?:[a-z]+:|#|\/)/i.test(target)) return target;
        return path.relative(path.dirname(archive), path.resolve(artifacts, target)).split(path.sep).join('/');
    };
    const archived = filename.endsWith('.md')
        ? previous.replace(/\]\(([^)]+)\)/g, (_, target) => `](${relocate(target)})`)
        : previous.replace(/((?:href|src|data-image)=")([^"#]+)(")/g, (_, start, target, end) => start + relocate(target) + end);
    try { await fs.writeFile(archive, archived, { flag: 'wx' }); } catch (error) { if (error.code !== 'EEXIST') throw error; }
}
await fs.writeFile(path.join(artifacts, '验收报告.md'), `# 当前验收报告 · 0.3.0\n\n角色图鉴、角色档案及 Markdown 对齐已纳入本期交付。\n\n[本期完整验收报告](character-0.3/验收报告.md) · [实际界面图册](character-0.3/界面验收.html) · [验收 APK](${inspection.file}) · [操作说明](验收说明.md)\n\n历史结果：[0.2.0](iteration-0.2/验收报告.md)。本轮未提交或推送源码，旧报告中的发布记录不代表本轮操作。\n`);
await fs.writeFile(path.join(artifacts, '界面验收.html'), '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="refresh" content="0;url=character-0.3/界面验收.html"><title>kivo 0.3.0 界面验收</title><p><a href="character-0.3/界面验收.html">打开 0.3.0 角色模块实际图册</a></p></html>');
console.log(`已核对当前 APK 与全部证据并生成报告：${unitTotal} 项单元检查，${shots.length} 张实际界面，${size} MiB。`);
