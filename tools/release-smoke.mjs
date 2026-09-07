/** 已打包 Release 的设备冒烟：仅操作本工程 AVD 与 preview 包，不连接真实账号。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sdk = process.env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA, 'Android/Sdk');
const adb = path.join(sdk, 'platform-tools/adb.exe');
const serial = 'emulator-5558';
const app = 'wiki.kivo.app.preview';
const component = `${app}/wiki.kivo.app.MainActivity`;
const artifacts = path.join(root, 'artifacts/character-0.3');
const version = (await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8')).match(/versionName = "([^"]+)"/)[1];
const apkPath = path.join(root, 'artifacts', 'kivo-archive-' + version + '-preview.apk');
await fs.mkdir(path.join(artifacts, 'screenshots'), { recursive: true });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function run(...args) {
    const result = spawnSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout: 45000, windowsHide: true });
    if (result.status !== 0) throw new Error(result.stderr || result.error || result.stdout);
    return result.stdout.trim();
}
function currentPid() {
    const result = spawnSync(adb, ['-s', serial, 'shell', 'pidof', app], { encoding: 'utf8', timeout: 10000, windowsHide: true });
    if (result.status === 1 && !result.stdout.trim()) return '';
    if (result.status !== 0) throw new Error(result.stderr || result.error || '无法确认进程');
    return result.stdout.trim();
}
if (!run('emu', 'avd', 'name').split('\n').map(line => line.trim()).includes('Kivo_Archive_Phone')) throw new Error('设备身份不符');

const decode = value => value.replaceAll('&amp;', '&').replaceAll('&quot;', '"').replaceAll('&lt;', '<').replaceAll('&gt;', '>');
function parse(xml) {
    const nodes = [], parents = [];
    for (const token of xml.matchAll(/<\/?node\b[^>]*>/g)) {
        if (token[0].startsWith('</')) { parents.pop(); continue; }
        const attrs = Object.fromEntries([...token[0].matchAll(/([\w-]+)="([^"]*)"/g)].map(match => [match[1], decode(match[2])]));
        nodes.push({ ...attrs, parent: parents.at(-1) });
        if (!token[0].endsWith('/>')) parents.push(nodes.length - 1);
    }
    return nodes;
}
async function dump(name = 'release-current-ui') {
    // 只读桥运行于独立测试进程，不启动 Debug 界面；避免系统 dump 的空闲等待和旧 XML。
    let exported = false;
    for (let attempt = 0; attempt < 7; attempt++) {
        const output = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'wiki.kivo.app.ReleaseUiSnapshotTest', '-e', 'releaseUiDump', 'true', 'wiki.kivo.app.preview.debug.test/androidx.test.runner.AndroidJUnitRunner');
        if (output.includes('OK (1 test)') && /^INSTRUMENTATION_STATUS_CODE: 0\s*$/m.test(output)) { exported = true; break; }
        if (!output.includes('Release 窗口尚未就绪')) throw new Error('当前窗口读取失败：' + output);
        await pause(500);
    }
    if (!exported) throw new Error('等待 Release 活动窗口超时；没有读取旧快照');
    const xml = run('shell', 'cat', '/sdcard/Android/data/wiki.kivo.app.preview.debug/files/release-driver/current.xml');
    await fs.writeFile(path.join(artifacts, `${name}.xml`), xml);
    return parse(xml);
}
async function waitFor(value, key = 'text', name) {
    return waitForCondition(nodes => nodes.some(node => node[key] === value), value, name);
}
async function waitForCondition(predicate, label, name) {
    for (let attempt = 0; attempt < 8; attempt++) {
        const nodes = await dump(name);
        if (predicate(nodes)) return nodes;
        await pause(600);
    }
    throw new Error(`界面未出现：${label}`);
}
async function find(value, key = 'text', direction = 'down') {
    let nodes = await dump();
    let previous = '';
    const visible = node => {
        if (node[key] !== value) return false;
        const b = node.bounds.match(/\d+/g).map(Number);
        return b[3] - b[1] >= 32;
    };
    // 仅在本工程模拟器中，依据实际 UI 树的滚动区域寻找控件；不使用固定屏幕坐标。
    for (let attempt = 0; !nodes.some(visible) && attempt < 24; attempt++) {
        const region = nodes.find(node => node.scrollable === 'true');
        if (!region) { await pause(500); nodes = await dump(); continue; }
        const bounds = region.bounds.match(/\d+/g).map(Number);
        const x = Math.round((bounds[0] + bounds[2]) / 2), h = bounds[3] - bounds[1];
        const candidate = nodes.find(node => node[key] === value);
        if (candidate) direction = Number(candidate.bounds.match(/\d+/g)[1]) <= bounds[1] + 32 ? 'up' : 'down';
        const signature = nodes.map(node => node.text).filter(Boolean).join('|');
        if (signature === previous) direction = direction === 'down' ? 'up' : 'down';
        previous = signature;
        // 慢速短滑，避免 fling 越过刚进入视口的短选项行。
        const from = direction === 'down' ? .65 : .35, to = direction === 'down' ? .45 : .55;
        run('shell', 'input', 'swipe', String(x), String(Math.round(bounds[1] + h * from)), String(x), String(Math.round(bounds[1] + h * to)), '650');
        await pause(400); nodes = await dump();
    }
    if (!nodes.some(visible)) throw new Error('无法定位控件：' + value);
    return nodes;
}
async function tap(value, key = 'text', direction = 'down') {
    const nodes = await find(value, key, direction);
    const node = nodes.find(item => item[key] === value);
    const bounds = node.bounds.match(/\d+/g).map(Number);
    run('shell', 'input', 'tap', String(Math.floor((bounds[0] + bounds[2]) / 2)), String(Math.floor((bounds[1] + bounds[3]) / 2)));
    await pause(500);
}
async function capture(name) {
    run('shell', 'screencap', '-p', '/sdcard/kivo-release-capture.png');
    run('pull', '/sdcard/kivo-release-capture.png', path.join(artifacts, `screenshots/${name}.png`));
}
function selected(nodes, text) {
    let node = nodes.find(item => item.text === text);
    while (node) {
        if (node.selected === 'true' || node.checked === 'true') return true;
        node = nodes[node.parent];
    }
    return false;
}
function launch(fresh = false) {
    // force-stop 结束进程，但不保证丢弃系统保存的任务状态。测“全新启动”时显式新建任务；
    // 测进程恢复则保留原任务。两者都不清除 App 的收藏、设置、数据库或登录存储。
    return run('shell', 'am', 'start', '-W', ...(fresh ? ['-f', '0x10008000'] : []), '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', '-n', component);
}
function detail(fresh = false) { return run('shell', 'am', 'start', '-W', ...(fresh ? ['-f', '0x10008000'] : []), '-a', 'android.intent.action.VIEW', '-d', 'kivoarchive://content/BULLETIN/39', '-n', component); }

const apkSha256 = createHash('sha256').update(await fs.readFile(apkPath)).digest('hex');
const report = { package: app, device: serial, apkSha256, coldStartsMs: [], checks: [] };
for (const relative of ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']) {
    const driverApk = path.join(root, relative);
    try { await fs.access(driverApk); } catch { throw new Error('请先构建只读验收桥：:app:assembleDebug :app:assembleDebugAndroidTest'); }
    run('install', '-r', driverApk);
}
console.log(run('install', '-r', apkPath));
// 上一次中断可能留下任意启动偏好；通过正常设置入口准备首页，保留用户数据。
detail(true);
await waitFor('26年7月站内动态');
await tap('阅读设置', 'content-desc');
await tap('首页', 'text', 'up');
for (let i = 0; i < 5; i++) {
    run('shell', 'am', 'force-stop', app);
    const output = launch(true);
    const time = output.match(/TotalTime: (\d+)/);
    if (!time || !output.includes('Status: ok')) throw new Error(output);
    report.coldStartsMs.push(Number(time[1]));
    await pause(1000);
}
await waitFor('馆内公告', 'content-desc', 'release-home-ui');
await capture('release-home-light');
detail();
await waitFor('26年7月站内动态');
let nodes = await dump();
if (!nodes.some(node => node['content-desc'] === '取消收藏')) await tap('收藏', 'content-desc');
await waitFor('取消收藏', 'content-desc', 'release-reader-ui');
await capture('release-reader');
report.checks.push('R8 Release 安装、5 次进程冷启动、真实公告阅读及收藏');

await tap('阅读设置', 'content-desc');
await tap('深色');
nodes = await waitFor('深色', 'text', 'release-settings-dark-ui');
if (!selected(nodes, '深色')) throw new Error('深色未选中');
await capture('release-settings-dark');

// 真实结束后台进程后从启动器回到原任务，检查 Navigation 的 SavedState 恢复。
run('shell', 'input', 'keyevent', 'KEYCODE_HOME');
await pause(1200);
const previousPid = run('shell', 'pidof', app);
// am kill 只结束已进入后台的进程，而且执行是异步的。等待系统完成后台转换和终止，
// 不能固定睡 800ms 后就声称已发生进程重建，也不能靠清空应用数据制造通过。
for (let attempt = 0; attempt < 15 && currentPid() === previousPid; attempt++) {
    run('shell', 'am', 'kill', app);
    await pause(1000);
}
if (currentPid() === previousPid) throw new Error('系统尚未终止后台进程，无法执行恢复验收');
launch();
nodes = await waitFor('深色', 'text', 'release-process-restored-ui');
const nextPid = run('shell', 'pidof', app);
if (!selected(nodes, '深色') || previousPid === nextPid) throw new Error(`进程或主题恢复检查失败：${previousPid} → ${nextPid}`);
report.checks.push('后台进程结束后恢复设置页与深色选择');
report.processIds = { previous: previousPid, restored: nextPid };

// 覆盖安装同一已签名 APK，不清数据；随后从资料页验证持久收藏。
run('install', '-r', apkPath);
launch();
detail();
await waitFor('取消收藏', 'content-desc', 'release-upgrade-ui');
report.checks.push('同包同签名覆盖安装后保留收藏');
await tap('阅读设置', 'content-desc');
nodes = await find('深色');
if (!selected(nodes, '深色')) throw new Error('覆盖安装后主题丢失');
report.checks.push('覆盖安装后保留主题');
await tap('浅色', 'text', 'up');
run('shell', 'am', 'force-stop', app);
launch(true);
await waitFor('馆内公告', 'content-desc');
await capture('release-home-light');
await tap('系统设置', 'content-desc');
await tap('角色图鉴');
await pause(500);
run('shell', 'am', 'force-stop', app);
launch(true);
nodes = await waitForCondition(nodes => selected(nodes, '图鉴') && nodes.some(node => node.text === '角色图鉴'), '图鉴页面与选中的底栏', 'release-start-catalog-ui');
await pause(4000);
await capture('release-start-catalog');
report.checks.push('全新进程按启动偏好进入原生角色图鉴，并选中图鉴底栏');
// 保持图鉴启动偏好，再用外链冷启动；设置异步到达不能抢走链接指定的角色。
run('shell', 'am', 'force-stop', app);
run('shell', 'am', 'start', '-W', '-f', '0x10008000', '-a', 'android.intent.action.VIEW', '-d', 'kivoarchive://content/STUDENT/86', '-n', component);
await waitFor('CHARACTER · 086');
await pause(1200);
await waitFor('CHARACTER · 086', 'text', 'release-cold-link-ui');
report.checks.push('图鉴启动偏好下的冷启动深链接仍进入指定角色');
run('shell', 'am', 'force-stop', app);
launch(true);
await waitFor('角色图鉴');
await tap('系统设置', 'content-desc');
await tap('首页');
run('shell', 'am', 'force-stop', app);
launch(true);
await waitFor('馆内公告', 'content-desc');
// 在正式交付的 R8 产物上检查新增原生运行时，Debug 通过不能替代 JNI/压缩后的验证。
run('shell','am','start','-W','-a','android.intent.action.VIEW','-d','kivoarchive://content/STUDENT/86','-n',component);
await waitFor('CHARACTER · 086');
await capture('release-character-86');
await tap('鉴赏');
for (const [section,label,readyText,name] of [
    ['立绘与回忆大厅','初始立绘','动作 · Idle_01','spine'],
    ['立绘与回忆大厅','Shiroko_home','动作 · Idle_01','home'],
    ['人物与光环模型','Shiroko_Original_Body','动作 · Shiroko_Original_Normal_Idle','model'],
    ['人物与光环模型','Shiroko_Original_Halo','自动旋转','halo'],
]) {
    await tap('本页目录'); await tap(section); await tap(label);
    await waitFor(readyText);
    await pause(2500);
    await capture('release-native-'+name);
    const previewNodes=await dump('release-native-'+name+'-ui');
    if(previewNodes.some(node=>node.text?.includes('重新下载并重试')))throw new Error('R8 原生预览失败：'+name);
    await tap('关闭预览','content-desc');
    report.checks.push('R8 原生资源预览：'+label);
}
run('shell','am','force-stop',app);launch(true);await waitFor('馆内公告','content-desc');
await fs.writeFile(path.join(artifacts, 'release-meminfo.txt'), run('shell', 'dumpsys', 'meminfo', app));
await fs.writeFile(path.join(artifacts, 'release-gfxinfo.txt'), run('shell', 'dumpsys', 'gfxinfo', app));
await fs.writeFile(path.join(artifacts, 'release-smoke.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
