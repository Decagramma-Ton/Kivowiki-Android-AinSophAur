# F0 验收证据索引

普通验收先读上一级的验收说明、验收报告和界面图册。本目录保留真实原始输出，含调试期间的旧记录；不要把早期失败日志或占位截图当作最终状态。

| 最终证据 | 内容 |
| --- | --- |
| delivery-build.log | 当前源代码的逻辑测试、Debug/Release Lint 与 R8 构建 |
| delivery-device-tests.log、tests/ | 14 项设备测试及 19 项逻辑测试 XML |
| offline-final.log | 明确断网的缓存阅读 |
| font200-final.log、tablet-final.log | 200% 系统字号、大屏布局专项 |
| release-smoke.json | 当前 APK 的摘要、5 次进程冷启动、阅读收藏、进程恢复、覆盖安装检查 |
| apk-inspection.json、apk-signature.txt、apk-alignment.txt | 当前 APK 的身份、签名、大小与本机库对齐 |
| visible-preview.log | 直接执行电脑验收.cmd，从关闭状态启动可见模拟器并安装打开 |
| release-meminfo.txt、release-gfxinfo.txt | 有明确设备条件的有限测量，不能当作真机帧率证明 |
| document-check.json | 本工程和原设计文档的本地链接、图册资源静态核对 |
| screenshots/release-*.png | 当前 Release 包实际界面 |

图册只选用已核对的截图。home-initial、早期构建/模拟器/离线失败输出等用于保留排查依据，没有进入最终成功计数。账号相关设备测试使用替身数据；没有真实账号密码或令牌记录。
