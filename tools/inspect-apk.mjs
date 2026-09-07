/** 只读取本工程 APK：记录 ZIP 内本机库的 ELF LOAD 段对齐，不执行任何包内代码。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { inflateRawSync } from 'node:zlib';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const version = (await fs.readFile(path.join(root, 'app/build.gradle.kts'), 'utf8')).match(/versionName = "([^"]+)"/)[1];
const apkPath = path.join(root, 'artifacts', 'kivo-archive-' + version + '-preview.apk');
const apk = await fs.readFile(apkPath);
let end = apk.length - 22;
while (end >= Math.max(0, apk.length - 65557) && apk.readUInt32LE(end) !== 0x06054b50) end--;
if (end < 0) throw new Error('不是有效 ZIP/APK');
const count = apk.readUInt16LE(end + 10);
let cursor = apk.readUInt32LE(end + 16);
const nativeLibraries = [];
for (let i = 0; i < count; i++) {
    if (apk.readUInt32LE(cursor) !== 0x02014b50) throw new Error('ZIP 中央目录损坏');
    const method = apk.readUInt16LE(cursor + 10);
    const compressedSize = apk.readUInt32LE(cursor + 20);
    const nameLength = apk.readUInt16LE(cursor + 28);
    const extraLength = apk.readUInt16LE(cursor + 30);
    const commentLength = apk.readUInt16LE(cursor + 32);
    const local = apk.readUInt32LE(cursor + 42);
    const name = apk.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8');
    cursor += 46 + nameLength + extraLength + commentLength;
    if (!/^lib\/[^/]+\/[^/]+\.so$/.test(name)) continue;
    const offset = local + 30 + apk.readUInt16LE(local + 26) + apk.readUInt16LE(local + 28);
    const compressed = apk.subarray(offset, offset + compressedSize);
    const elf = method === 8 ? inflateRawSync(compressed) : compressed;
    if (elf.readUInt32BE(0) !== 0x7f454c46 || elf[5] !== 1) throw new Error(`未知 ELF 格式：${name}`);
    const wide = elf[4] === 2;
    const headers = wide ? Number(elf.readBigUInt64LE(32)) : elf.readUInt32LE(28);
    const stride = elf.readUInt16LE(wide ? 54 : 42);
    const number = elf.readUInt16LE(wide ? 56 : 44);
    const alignments = [];
    for (let p = 0; p < number; p++) {
        const start = headers + p * stride;
        if (elf.readUInt32LE(start) === 1) {
            alignments.push(wide ? Number(elf.readBigUInt64LE(start + 48)) : elf.readUInt32LE(start + 28));
        }
    }
    nativeLibraries.push({ name, bytes: elf.length, loadSegmentAlignments: alignments,
        supports16KiBAlignment: alignments.length > 0 && alignments.every(value => value >= 16384) });
}
const report = { file: path.basename(apkPath), bytes: apk.length,
    sha256: createHash('sha256').update(apk).digest('hex'), nativeLibraries };
const reportDir = path.join(root, process.env.KIVO_REPORT_DIR || 'artifacts/character-0.3');
await fs.mkdir(reportDir, { recursive: true });
await fs.writeFile(path.join(reportDir, 'apk-inspection.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
if (nativeLibraries.some(library => !library.supports16KiBAlignment)) process.exitCode = 1;
