/** 从本工程真实制品/测试结果生成可离线阅读的交付摘要；不会请求网站或改变设备。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
// 此脚本保留 F0 历史实现，防止误运行后用旧版证据覆盖当前报告。
const currentBuild = await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8');
if (!/versionName\s*=\s*"0\.1\.0"/.test(currentBuild)) {
  throw new Error('此脚本仅用于 0.1.0；当前版本请运行 node tools/build-iteration-report.mjs。');
}
const out = path.join(root, 'artifacts');
const evidence = path.join(out, 'foundation');
const read = name => fs.readFile(path.join(evidence, name), 'utf8');
const apk = JSON.parse(await read('apk-inspection.json'));
const smoke = JSON.parse(await read('release-smoke.json'));
const actual = createHash('sha256').update(await fs.readFile(path.join(out, apk.file))).digest('hex');
if (actual !== apk.sha256) throw new Error('APK 已改变，请先重新执行制品检查与 Release 冒烟。');
if (actual !== smoke.apkSha256) throw new Error('Release 冒烟不是当前 APK，必须重新验证。');
const suites = [];
for (const name of await fs.readdir(path.join(evidence, 'tests'))) {
    if (!name.endsWith('.xml')) continue;
    const xml = await read(`tests/${name}`);
    // AGP 的一个设备 XML 可含多个 testsuite；不能只取首个类。
    for (const [tag] of xml.matchAll(/<testsuite\b[^>]*>/g)) {
        const attrs = Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(m => [m[1], m[2]]));
        suites.push({ name: attrs.name, tests: Number(attrs.tests), failures: Number(attrs.failures || 0), errors: Number(attrs.errors || 0) });
    }
}
const total = suites.reduce((sum, suite) => sum + suite.tests, 0);
if (total !== 33 || suites.some(suite => suite.failures || suite.errors)) throw new Error('测试结果不完整或含失败，不能生成成功报告。');
for (const log of ['offline-final.log', 'font200-final.log', 'tablet-final.log']) {
    if (!(await read(log)).includes('OK (1 test)')) throw new Error(`专项未通过：${log}`);
}
const pss = Number((await read('release-meminfo.txt')).match(/TOTAL PSS:\s+(\d+)/)?.[1]);
const times = [...smoke.coldStartsMs].sort((a, b) => a - b);
const median = times[Math.floor(times.length / 2)];
const size = (apk.bytes / 1048576).toFixed(2);
const shots = [
    ['release-home-light', '首页 · 浅色', 'phone', 'Release，真实公开资讯与首页入口'],
    ['11-home-dark', '首页 · 深色', 'dark', '同一套品牌与内容层级'],
    ['02-schedules', '日服日程', 'phone', '卡池、活动与总力战按时区显示'],
    ['03-schedules-cn', '国服日程', 'phone', '真实结束状态与独立服别'],
    ['04-schedules-global', '国际服状态', 'phone', '尚未接入时清楚说明'],
    ['05-home-archive', '资料更新', 'phone', '近期文章与编辑学生'],
    ['07-birthdays-history', '生日与那年今日', 'phone', '有界选读，按需获取素材'],
    ['release-reader', '公告原生阅读', 'phone', 'Release，链接、列表、提示块与收藏'],
    ['06-profile-light', '我的', 'phone', '游客、本地收藏与阅读足迹'],
    ['08-catalog', '图鉴入口', 'phone', '完整业务模块留待下期'],
    ['09-collection', '典藏入口', 'phone', '内容模块与网站回退'],
    ['release-settings-dark', '系统设置', 'dark', 'Release，主题与阅读设置'],
    ['tablet-home', '大屏双栏首页', 'tablet', '1920 × 1200 px，200 dpi'],
    ['tablet-profile', '大屏个人空间', 'tablet', '按可读宽度组织内容'],
    ['phone-font200-home', '200% 字号 · 首页', 'font', '尊重系统字体偏好'],
    ['phone-font200-settings', '200% 字号 · 设置', 'font', '选项自然换行，保持可操作'],
];
for (const [name] of shots) await fs.access(path.join(evidence, `screenshots/${name}.png`));

const report = `# 便携kivo古书馆 0.1.0 · 实际验收报告

日期：2026-09-07。状态：基础功能已实现，本机自动检查与模拟器验收通过，站长的电脑/真实手机/真实账号验收待进行。

## 制品

| 项目 | 实际结果 |
| --- | --- |
| APK | [${apk.file}](${apk.file}) |
| 文件大小 | ${apk.bytes.toLocaleString('en-US')} 字节 / ${size} MiB |
| SHA-256 | ${apk.sha256} |
| 身份 | wiki.kivo.app.preview，versionName 0.1.0，versionCode 1 |
| 构建 | Release，R8 代码压缩与资源缩减；独立测试证书签名 |
| 签名检查 | apksigner verify 成功，APK Signature Scheme v2；证书为 Android Debug |
| 平台声明 | minSdk 26、target/compile 37 |
| ABI | arm64-v8a、armeabi-v7a、x86、x86_64 |
| 对齐 | zipalign -P 16 成功；8 个本机库的 ELF LOAD 段均为 16384 字节对齐 |
| 权限 | INTERNET、ACCESS_NETWORK_STATE；另有 AndroidX 生成的仅签名级应用内部 receiver 权限 |
| 备份 | allowBackup=false、fullBackupContent=false；Android 12+ 云备份与设备迁移均排除，会话另存 noBackup |

检查原文：[APK 信息](foundation/apk-badging.txt)、[签名](foundation/apk-signature.txt)、[ZIP 对齐](foundation/apk-alignment.txt)、[ELF/摘要](foundation/apk-inspection.json)。这些静态检查不能代替 16 KB 页真实设备运行验证。

## 实际执行的检查

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 领域模型、身份、日期、链接 | 5 / 5 通过 | ModelContractTest |
| 网络信封、重试、PoW、跳转 | 10 / 10 通过 | NetworkContractTest、ProofOfWorkTest、RedirectContractTest |
| 内容解析与不安全 HTML 降级 | 4 / 4 通过 | ContentParserTest |
| Room / DataStore / Keystore / 本地账号契约 | 7 / 7 通过 | FoundationStorageTest，真实设备存储 + 受控账号替身 |
| 应用 UI 与实际公开公告 | 7 / 7 通过 | 四根导航、主题重建、密码不恢复与 FLAG_SECURE、阅读收藏、滚动保持、视觉巡检及自适应 |
| 真正断网的缓存公告阅读 | 1 个专项重复场景通过 | offline-final.log；断网参数要求 activeNetwork=null |
| 系统 200% 字号 | 1 个专项重复场景通过 | font200-final.log，手机实际截图 |
| 1920 × 1200 大屏 | 1 个专项重复场景通过 | tablet-final.log，双栏与侧栏实际截图 |
| Release 安装与进程恢复 | 通过 | release-smoke.json；结束后台进程后恢复设置页，进程 ID 确实变化 |
| 同签名覆盖安装 | 通过 | 收藏与主题保留；本次数据库仍为 v1，未验证跨 schema 迁移 |
| Windows 一键入口 | 已实际启动专用可见模拟器并安装、打开 APK | visible-preview.log |
| Debug / Release Lint | 0 Error；保留依赖更新提示及空资源目录提示 | app/build/reports/lint-results-*.html |

合计 **${total} 项不同自动用例通过**，另有 3 个环境专项重复场景；不把重复运行次数累加成更多覆盖。XML 证据在 [tests 目录](foundation/tests/)，完整构建与设备测试输出分别在 [构建日志](foundation/delivery-build.log) 和 [设备日志](foundation/delivery-device-tests.log)。

空资源目录是 mipmap-anydpi-v26，实际图标已在 mipmap-anydpi。自动审批拒绝了删除空目录的清理操作，因此原样保留；它不参与业务和运行。没有为了消除告警关闭 Lint、降低 SDK 或删测试。

## 模拟器的有限测量

环境：专用 Kivo_Archive_Phone，Android 16 / API 36.1 x86_64 镜像，Emulator 37.1.11，1080 × 2400 / 420 dpi，4 CPU / 2 GiB，Windows WHPX 和 host GPU。它运行在桌面电脑上，不能等同真实手机。

- Release 进程冷启动 TotalTime：${smoke.coldStartsMs.join(' / ')} ms；中位数 **${median} ms**。每次 force-stop 后启动，保留数据与缓存；这不是网络首页完全加载耗时，也不是首次安装编译耗时。
- 首页一次 dumpsys meminfo 的 TOTAL PSS：**${(pss / 1024).toFixed(1)} MiB**。模拟器的图形内存统计有边界，不能据此承诺真实设备总内存上限。
- 原始 [图形统计](foundation/release-gfxinfo.txt) 是启动后短样本，不作为滚动帧率合格证据。正式性能验收仍需指定真实设备、标准化滚动、Macrobenchmark/Perfetto、温度和耗电测量。

本期证明可运行与有界数据/资源策略，没有宣称达到原方案 09 的全部真机性能目标。

## 尚未完成的验证与能力

1. **未连接真实手机。** APK 在专用模拟器安装运行，普通手机可按说明手动安装；多厂商、旧 Android、Android 17 与真实低端性能尚未实测。
2. **未使用真实账号提交生产登录。** 原生 PoW / 令牌 / 登出按公开网页协议实现；真实成功资料字段与生产业务码需由账号持有人验证。本地替身的成功不代表服务器已成功登录。
3. 完整图鉴、组织、仓库、画廊、音乐、漫画、配队、Spine / 3D / 大图专项未开展。本期提供入口与范围说明，首页资料速览不等于完整模块。
4. 原生 QQ / 注册验证码、云同步、正式签名、公开发布、更新器、完整自定义 Markdown 与 App 内完整许可展示仍属后续阶段。

本轮没有操作服务器、写入站内业务数据、发布到 GitHub、改 Windows 安全设置或读取任务无关个人文件。

## 看图和继续

[实际界面图册](界面验收.html) 可离线打开。图册注明 Release 或 Debug 验收截图来源；所有图片均为真实安装后的界面，没有用设计图替代 App。登录页不录屏。

图册文件和图片引用已作静态核对，原始截图已查看。内置浏览器策略阻止 file:// 本地 HTML 预览，因此未在该浏览器中完成整页渲染检查；没有通过其他浏览器或临时服务器绕过策略。App 本身的视觉和交互检查在 Android 模拟器完成。

[十分钟验收说明](验收说明.md) · [工程当前状态](../开发状态/PROJECT_STATE.md) · [最新交接](../开发状态/最新交接.md) · [原方案同步](../../App项目文档/18-基础版本实施同步.md) · [第三方组件与许可](../THIRD_PARTY_NOTICES.md)
`;
await fs.writeFile(path.join(out, '验收报告.md'), report);
const cards = shots.map(([name, title, category, caption]) => `<button class="shot ${category === 'tablet' ? 'wide' : ''}" data-category="${category}" data-image="foundation/screenshots/${name}.png" aria-label="放大查看${title}"><div class="photo"><img loading="lazy" src="foundation/screenshots/${name}.png" alt="${title}的实际安卓截图"></div><span class="caption"><strong>${title}</strong><small>${caption}</small></span></button>`).join('\n');
await fs.writeFile(path.join(out, '界面验收.html'), `<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>便携kivo古书馆 · 0.1.0 电脑验收</title>
<style>
:root{color-scheme:light;--ink:#193447;--muted:#617987;--blue:#087ea1;--line:#dbe6ec;--bg:#f4f8fa}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.75 "Segoe UI","Microsoft YaHei",sans-serif}a{color:var(--blue);text-underline-offset:4px}button{font:inherit}header,main,footer{max-width:1200px;margin:auto;padding:32px}header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line)}.brand{font-size:24px;font-weight:750;letter-spacing:-.5px}.badge{color:var(--blue);background:#e1f3f8;border-radius:99px;padding:6px 14px;font-size:13px}h1{font-size:clamp(32px,4.5vw,60px);line-height:1.2;letter-spacing:-2px;margin:18px 0 24px}h2{font-size:26px;line-height:1.4;margin:0 0 16px}p{margin:0 0 18px}.hero{display:grid;grid-template-columns:1.2fr .8fr;gap:56px;align-items:center;padding:48px 0}.hero img{display:block;width:265px;max-width:100%;height:auto;border:7px solid white;border-radius:36px;box-shadow:0 14px 60px #1b42581a;margin:auto}.eyebrow{color:var(--blue);font-size:12px;font-weight:700;letter-spacing:2px}.muted{color:var(--muted)}.actions{display:flex;gap:12px;flex-wrap:wrap;margin:26px 0}.actions a{padding:10px 20px;border-radius:12px;border:1px solid var(--line);background:white;text-decoration:none}.actions a:first-child{background:var(--blue);color:white;border-color:var(--blue)}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:32px}.stat{border-top:1px solid var(--line);padding-top:16px}.stat b{display:block;font-size:30px;line-height:1.3}.stat span{font-size:13px;color:var(--muted)}.note{background:#fff;border:1px solid var(--line);padding:22px 26px;border-radius:18px;margin:12px 0 40px}.note p:last-child{margin-bottom:0}.steps{display:grid;grid-template-columns:repeat(3,1fr);gap:24px;margin:22px 0 50px}.step b{color:var(--blue);font-size:13px}.step strong{display:block;margin:7px 0}.step p{font-size:14px;color:var(--muted)}code{font:13px/1.6 Consolas,monospace;overflow-wrap:anywhere;background:#eaf1f5;padding:3px 5px;border-radius:4px}.filters{display:flex;gap:8px;flex-wrap:wrap;margin:22px 0}.filters button{border:1px solid var(--line);background:white;color:var(--ink);padding:7px 17px;border-radius:99px;cursor:pointer}.filters button[aria-pressed=true]{background:var(--ink);color:white}.gallery{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px}.shot{background:white;border:1px solid var(--line);padding:0;border-radius:20px;overflow:hidden;text-align:left;color:var(--ink);cursor:zoom-in}.shot:focus-visible,button:focus-visible,a:focus-visible{outline:3px solid #33b3d5;outline-offset:4px}.shot:hover{border-color:#73bcd0}.shot[hidden]{display:none}.photo{padding:18px;background:#eaf0f4;height:510px;display:flex;align-items:center;justify-content:center}.photo img{max-width:100%;max-height:100%;object-fit:contain;border-radius:12px}.wide{grid-column:span 3}.wide .photo{height:auto;min-height:200px}.wide img{width:100%;max-height:none}.caption{display:block;padding:18px 20px}.caption strong{display:block}.caption small{color:var(--muted);font-size:12px}.detail{margin-top:42px;padding-top:30px;border-top:1px solid var(--line);font-size:14px}.hash{font-size:12px;word-break:break-all}footer{color:var(--muted);font-size:13px}dialog{padding:16px;border:0;border-radius:16px;max-width:95vw;max-height:95vh;background:#eaf0f4}dialog::backdrop{background:#102432dd}dialog img{display:block;max-width:88vw;max-height:80vh;object-fit:contain;margin:auto}dialog button{display:block;margin:0 0 12px auto;border:0;background:white;border-radius:8px;padding:7px 14px;cursor:pointer}@media(max-width:800px){header,main,footer{padding:22px}.hero{gap:28px;padding:26px 0}.hero img{width:210px}.gallery{grid-template-columns:repeat(2,minmax(0,1fr))}.wide{grid-column:span 2}.steps{gap:15px}}@media(max-width:550px){.hero{grid-template-columns:1fr}.hero img{width:225px}.steps{grid-template-columns:1fr;gap:4px}.gallery{grid-template-columns:1fr}.wide{grid-column:span 1}.photo{height:560px}.brand{font-size:20px}.badge{font-size:11px}}
</style>
<header><div class="brand">kivo 古书馆</div><span class="badge">0.1.0 · 基础验收版</span></header>
<main><section class="hero"><div><span class="eyebrow">KIVO ARCHIVE / ANDROID</span><h1>基沃托斯的故事，<br>随身收藏。</h1><p class="muted">原生 Android 工程已可运行。浏览真实首页，阅读公告与资料，收藏喜欢的内容，选择古书馆的明暗。</p><div class="actions"><a href="验收说明.md">查看十分钟验收说明</a><a href="验收报告.md">查看实际验收报告</a></div><div class="stats"><div class="stat"><b>${total}</b><span>不同自动用例通过</span></div><div class="stat"><b>${size} MiB</b><span>压缩后的通用 APK</span></div><div class="stat"><b>7</b><span>原生工程模块</span></div></div></div><img src="foundation/screenshots/release-home-light.png" alt="Release 验收包首页实际截图"></section>
<div class="note"><p><strong>这是可操作的安卓 App。</strong> 回到 App项目开发 文件夹，双击 <code>电脑验收.cmd</code>，即可打开专用模拟器。下面的图册记录实际安装后的界面。</p><p class="muted">真实手机和真实账号尚未实测；完整图鉴、媒体、配队等业务模块按本次要求留待后续。账号流程的本地测试不等于生产登录成功。</p></div>
<section><h2>从这里开始验收</h2><div class="steps"><div class="step"><b>01 / 打开</b><strong>双击电脑验收.cmd</strong><p>等待安卓窗口启动。鼠标点击与拖动，就能浏览首页和四个主入口。</p></div><div class="step"><b>02 / 操作</b><strong>阅读 → 收藏 → 再次打开</strong><p>点右上铃铛打开公告，收藏后到“我的”查看。再试服别和浅深色切换。</p></div><div class="step"><b>03 / 上手机</b><strong>复制 APK，正常安装</strong><p>安装包就在本文件旁边。真实设备步骤、检查路线和问题记录方式见验收说明。</p></div></div></section>
<section><h2>实际界面图册</h2><p class="muted">点击图片放大。标有 Release 的截图来自交付包；其他来自同一实现的独立 Debug 验收包。登录页面不录屏。</p><div class="filters" role="group" aria-label="筛选截图"><button data-filter="all" aria-pressed="true">全部</button><button data-filter="phone" aria-pressed="false">手机</button><button data-filter="dark" aria-pressed="false">深色</button><button data-filter="tablet" aria-pressed="false">大屏</button><button data-filter="font" aria-pressed="false">200% 字号</button></div><div class="gallery">${cards}</div></section>
<section class="detail"><h2>制品与验证边界</h2><p>已验证原生导航、真实公开内容、缓存阅读、主题持久化、Keystore、受控账号契约、进程恢复与同签名覆盖安装。模拟器测试环境为 Android 16 / API 36.1；最低声明 API 26，目标 API 37。</p><p><a href="${apk.file}" download>验收 APK</a> · <a href="../开发状态/最新交接.md">继续开发的交接文档</a> · <a href="../../App项目文档/18-基础版本实施同步.md">原方案实施同步</a></p><p class="hash muted">SHA-256：${apk.sha256}</p><p class="muted">测试签名 / 独立 preview 包名 / 本地内部验收。公开发行、真实手机性能和真实账号成功响应仍需后续验证。</p></section></main>
<footer>2026-09-07 · 所有图像来自实际运行界面。游戏与站点素材沿用原有权利归属。工程与测试证据均保存在本地。</footer>
<dialog id="viewer"><button id="close">关闭 ×</button><img id="full" alt="放大的实际界面截图"></dialog>
<script>const buttons=[...document.querySelectorAll('[data-filter]')];buttons.forEach(button=>button.addEventListener('click',()=>{buttons.forEach(item=>item.setAttribute('aria-pressed',String(item===button)));document.querySelectorAll('.shot').forEach(shot=>shot.hidden=button.dataset.filter!=='all'&&shot.dataset.category!==button.dataset.filter)}));const dialog=document.getElementById('viewer');document.querySelectorAll('.shot').forEach(shot=>shot.addEventListener('click',()=>{document.getElementById('full').src=shot.dataset.image;dialog.showModal()}));document.getElementById('close').onclick=()=>dialog.close();dialog.addEventListener('click',event=>{if(event.target===dialog)dialog.close()});</script></html>`);
console.log(`已根据真实制品生成中文验收报告与离线图册：${total} 项检查，${size} MiB。`);
