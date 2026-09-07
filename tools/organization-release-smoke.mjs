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
const artifacts = path.join(root, 'artifacts/organization-0.4');
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
    for (let attempt = 0; !nodes.some(visible) && attempt < 80; attempt++) {
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
// 仅复用既有只读窗口桥与基于控件树的点击，不读取其他应用或清理资料。
const checks = [];
run('install','-r',apkPath);
async function openEntity(type, id, expected) {
    run('shell','am','force-stop',app);
    run('shell','am','start','-W','-f','0x10008000','-a','android.intent.action.VIEW','-d',`kivoarchive://content/${type}/${id}`,'-n',component);
    await waitFor(expected);
}
await openEntity('SCHOOL',1,'阿比多斯高中');
await tap('地图地标');
await waitFor('放大探索地图');
await pause(10000);
await capture('release-organization-map');
await tap('地标 1：阿比多斯主楼','content-desc');
await waitFor('关闭地标资料','content-desc');
await capture('release-landmark');
await tap('关闭地标资料','content-desc');
await tap('放大探索地图');
await waitFor('关闭地图','content-desc');
await tap('放大');
await capture('release-map-zoom');
await tap('复位');
await tap('关闭地图','content-desc');
checks.push('R8 组织冷启动、地图地标描述、全屏放大与复位');
await openEntity('RELATION',19,'废校对策委员会');
await tap('关系资料');
await waitFor('资料目录');
await capture('release-relation');
checks.push('R8 关系深链接及 Markdown 正文');
await openEntity('STUDENT',86,'CHARACTER · 086');
await capture('release-character-86');
checks.push('R8 角色冷启动深链接');
run('shell','am','force-stop',app);
run('shell','am','start','-W','-f','0x10008000','-n',component);
await tap('资料');
await tap('组织笔记');
await waitFor('30 个组织');
await pause(5000);
await capture('release-catalog');
await tap('切换紧凑列表视图','content-desc');
await pause(10000);
await capture('release-catalog-list');
await tap('切换卡片视图','content-desc');
checks.push('R8 组织目录三列与紧凑列表');
const report = { version, bytes:(await fs.stat(apkPath)).size, sha256:createHash('sha256').update(await fs.readFile(apkPath)).digest('hex'), checks };
await fs.writeFile(path.join(artifacts,'release-smoke.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify(report,null,2));