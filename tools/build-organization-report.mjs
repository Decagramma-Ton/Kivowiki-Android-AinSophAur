/** 只从本工程实际日志、测试 XML 和交付 APK 生成报告；缺少证据时中止，不填造通过结果。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dir = path.join(root, 'artifacts/organization-0.4');
const read = name => fs.readFile(path.join(dir, name), 'utf8');
const version = (await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8')).match(/versionName = "([^"]+)"/)[1];
if (version !== '0.4.0') throw new Error('此生成器仅对应 0.4.0，请为后续版本更新验收范围');
const apkName = `kivo-archive-${version}-preview.apk`;
const apk = await fs.readFile(path.join(root, 'artifacts', apkName));
const hash = createHash('sha256').update(apk).digest('hex');
const smoke = JSON.parse(await read('release-smoke.json'));
const inspection = JSON.parse(await read('apk-inspection.json'));
if (smoke.sha256 !== hash || inspection.sha256 !== hash || smoke.checks.length < 4)
    throw new Error('Release 验收证据与当前 APK 不对应');
if (!(await read('verify.log')).includes('BUILD SUCCESSFUL')) throw new Error('完整构建未通过');
const cases = [];
for (const [file, label] of [['phone','手机'],['font200','200% 字号'],['tablet','平板'],['offline','断网缓存']]) {
    const log = await read(`${file}.log`);
    if (file === 'tablet' && /Tests run: 7,\s*Failures: 1/.test(log)) {
        const targeted = await read('tablet-targeted.log');
        if (!targeted.includes('OK (1 test)') || /FAILURES!!!|Process crashed/.test(targeted)) throw new Error('平板目标复测未通过');
        cases.push({file,label:'平板初测（另 1 项自动点击失败，保留原日志）',count:6});
        cases.push({file:'tablet-targeted',label:'平板导航动作单独复测',count:1});
        continue;
    }
    const count = log.match(/OK \((\d+) tests?\)/)?.[1];
    if (!count || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(log)) throw new Error(`${file} 未通过`);
    cases.push({file,label,count:Number(count)});
}
async function testFiles(directory) {
    const files = [];
    for (const entry of await fs.readdir(directory, {withFileTypes:true}).catch(error => {
        if (error.code === 'ENOENT') return [];
        throw error;
    })) {
        const full = path.join(directory, entry.name);
        if (entry.isDirectory()) files.push(...await testFiles(full));
        else if (/^TEST-.*\.xml$/.test(entry.name)) files.push(full);
    }
    return files;
}
const units = [];
for (const module of ['core/model','core/data','core/content','core/media']) {
    let tests = 0, failures = 0, skipped = 0;
    for (const file of await testFiles(path.join(root, module, 'build/test-results'))) {
        const xml = await fs.readFile(file, 'utf8');
        const suite = xml.match(/<testsuite\s[^>]+>/)?.[0] || '';
        const number = key => Number(suite.match(new RegExp(`${key}="(\\d+)"`))?.[1] || 0);
        tests += number('tests'); failures += number('failures') + number('errors'); skipped += number('skipped');
    }
    if (!tests || failures) throw new Error(`${module} 单元结果缺失或失败`);
    units.push({module,tests,skipped});
}
const lints = [];
for (const variant of ['debug','release']) {
    const result = await fs.readFile(path.join(root, `app/build/reports/lint-results-${variant}.txt`), 'utf8');
    const summary = result.match(/(\d+) errors?, (\d+) warnings?/);
    if (!summary || Number(summary[1])) throw new Error(`${variant} Lint 存在错误`);
    lints.push(`${variant}：${summary[1]} errors / ${summary[2]} warnings`);
}
const shots = [
    ['screenshots/release-catalog.png','组织目录 · 三列卡片'],
    ['screenshots/release-catalog-list.png','组织目录 · 紧凑列表'],
    ['phone-catalog-search.png','搜索与返回保留'],
    ['phone-organization-overview.png','阿比多斯概览与成员'],
    ['screenshots/release-organization-map.png','地图与八个地标'],
    ['screenshots/release-landmark.png','地标资料'],
    ['screenshots/release-map-zoom.png','地图放大探索'],
    ['phone-related-characters.png','角色关系与相关成员'],
    ['screenshots/release-relation.png','关系资料'],
    ['phone-character-mermaid.png','白子资料中的真实 Mermaid'],
    ['mermaid-shiroko.png','真实图表节点与连线校验'],
    ['font200-catalog-list.png','200% 字号 · 紧凑列表'],
    ['tablet-catalog.png','平板自适应目录'],
    ['offline-organization-body.png','断网后的缓存正文'],
];
for (const [file] of shots) await fs.access(path.join(dir,file));
const escape = s => s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
await fs.writeFile(path.join(dir,'界面验收.html'), `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>组织笔记 · 0.4.0 界面验收</title><style>body{margin:0;background:#eff5f8;color:#193348;font:16px/1.7 system-ui,sans-serif}main{max-width:1240px;margin:auto;padding:32px 24px}h1{font-size:32px;margin-bottom:4px}header p{color:#526a78}section{display:grid;grid-template-columns:repeat(auto-fit,minmax(270px,1fr));gap:22px;align-items:start}figure{margin:0;background:white;border:1px solid #dce7ed;border-radius:18px;overflow:hidden}figcaption{padding:16px;font-weight:650}img{display:block;width:100%;height:auto}a{color:#007da0}footer{padding:36px 0;color:#526a78}</style><main><header><h1>组织笔记与角色联动</h1><p>0.4.0 · 实际模拟器运行截图。普通字号三列卡片，独立紧凑列表偏好；点击图片查看原图。</p><p><a href="验收报告.md">验收报告</a> · <a href="../${apkName}">安装包</a></p></header><section>${shots.map(([file,title])=>`<figure><figcaption>${escape(title)}</figcaption><a href="${encodeURI(file)}"><img loading="lazy" src="${encodeURI(file)}" alt="${escape(title)}"></a></figure>`).join('')}</section><footer>地图、角色与组织图片来自站长授权的古书馆公开素材。设备矩阵不代表所有真机兼容性。</footer></main></html>`, 'utf8');
const report = `# 组织笔记与角色联动 · 0.4.0 验收

日期：2026-09-07。交付为内部验收签名的 R8 Release；未提交、推送或对外发布。

## 交付

- [安装 APK](../${apkName})，${apk.length.toLocaleString('en-US')} 字节，${(apk.length/1048576).toFixed(2)} MiB。
- SHA-256：\`${hash}\`。
- [真实界面图册](界面验收.html) · [实施与 API 语义](../../../App项目文档/21-组织笔记与角色关系实施同步.md)。
- 最低 API 26，目标 API 37；沿用 preview 包名与验收证书，可覆盖旧验收版。

## 本轮内容

组织目录默认在普通手机显示三列卡片，去掉重复徽章；紧凑列表使用小徽章和完整译名。两种视图独立保存 compact_organization，搜索及返回状态保留，不覆盖角色图鉴偏好。大字体减少网格列数并允许完整名称换行。

组织详情包含概览、全部地图与地标、组织资料和所属关系；关系详情包含主要成员、次要成员及关系正文。角色所属、主要／次要关系、相关角色与全站搜索均使用实体 ID 导航。地图的八个阿比多斯地标按原图比例定位，支持点编号、文字列表及全屏探索。

角色分类使用统一列表与公共头部锚点，头部表态状态不因懒列表回收而重新加载；坐标记录不触发逐帧正文重组。横滑使用方向锁、64dp 阈值、1.8 倍横纵比，保留子控件与多指操作；设置可关闭，按钮仍可用。页面末尾和目录新增相关角色。

共享 Markdown 使用本地 Mermaid 11.17.2，白子真实图表验证 9 个节点、13 条连线，支持嵌套子图及全屏放大。WebView 不访问网络、文件或内容提供者；错误时可查看完整源码。

## 实际验证

完整命令：\`tools/build.ps1 -Target Verify\`。[构建日志](verify.log)通过；Lint ${lints.join('；')}。警告未被当作零警告报告，新增模块未出现阻断错误。

| 单元模块 | 用例 | 跳过 |
| --- | ---: | ---: |
${units.map(u=>`| ${u.module} | ${u.tests} | ${u.skipped} |`).join('\n')}

共 ${units.reduce((n,u)=>n+u.tests,0)} 个单元用例，失败 0。feature:character / feature:organization 的单元任务为 NO-SOURCE，业务交互由 app 设备测试覆盖，不把空任务计为用例。

| 设备场景 | 通过次数 | 证据 |
| --- | ---: | --- |
${cases.map(c=>`| ${c.label} | ${c.count} | [${c.file}.log](${c.file}.log) |`).join('\n')}

设备为项目专用 Android 36.1 模拟器 emulator-5558：手机 1080×2400；平板临时 1920×1200、density 200；字体测试 200%。临时字号、屏幕和网络均在 finally 恢复。断网用例检查 activeNetwork 为 null，复用已通过手机矩阵加载的缓存（SkipPrime），未伪称首次断网能取得新资料。

设备路径覆盖分类位置（含公共头部部分露出和短 NPC 619）、斜向纵滑不切页、横滑一次切页、开关重建持久化、组织列表偏好重建、搜索返回、地图地标、关系／成员互跳、真实 Markdown 及目录、Mermaid 节点与连线。测试只读公开资料，没有生产表态或补充写入。

最终签名 APK 单独执行 [Release 冒烟](release-smoke.json)：

${smoke.checks.map(check=>`- ${check}`).join('\n')}

[APK 检查](apk-inspection.json)记录全部本机库 ELF LOAD 对齐，${inspection.nativeLibraries.length} 个库均满足检查器的 16 KiB 段对齐条件；这不等同于全机型安装与 GPU 兼容认证。

## 回归中修正的问题

分类栏负偏移在 stickyHeader 测量中被归一化，改用尚未收起的普通头部作为锚点；头部表态提示被回收后重新加载的高度变化也已修正。目录测试改用实际标题条目，不依赖站点缓存的历史标题；相关角色测试从真实目录进入，避免测试框架逐屏扫描数百个异步块时漏过目标。输入法和吸顶栏会干扰自动注入坐标，关闭测试键盘后手机触摸路径通过；平板剩余导航项改用可访问点击单独复测，相同关系组件的触摸另在地图／成员用例通过，原失败日志保留。

R8 冒烟范围为组织、关系、目录和角色冷启动。Mermaid 实际渲染、全屏和节点检查在 Debug 设备用例中完成；另从最终 APK 提取运行时核对了 [文件大小与 SHA-256](mermaid-apk-integrity.json)，与官方制品一致。未把 Release 中未完成的长目录自动定位步骤记为图表验证通过。

## 边界

本轮为组织／关系公开内容模块；组织表态和老师补充的读写未扩展，既有角色互动保留。Mermaid 真实白子用例不代表所有扩展图类型、畸形输入或所有 WebView 版本。真机厂商系统、GPU、极端长图与全站全部组织仍需扩展样本。旧 0.3.0 的 Spine 专项报告保留为历史证据，不冒充本轮重新执行。
`;
await fs.writeFile(path.join(dir,'验收报告.md'), report, 'utf8');
console.log(`已生成 0.4.0 报告与 ${shots.length} 张真实截图图册；SHA-256 ${hash}`);
