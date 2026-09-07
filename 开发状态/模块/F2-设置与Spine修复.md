# 角色与系统设置修复 · 2026-09-07

本轮保持 0.3.0 的包名、版本及签名身份，可覆盖安装。当前 APK 与 SHA-256 位于 `artifacts`；本轮证据以 [修复验收](../../artifacts/settings-spine-fixes/验收说明.md) 为准，早先角色验收报告作为历史记录保留。

## 使用变化

1. **显示图片、加载外部来源图片默认开启。** 模型默认值和 DataStore 缺省值保持一致。升级保留已经明确保存的开关值；此前关闭过的用户可在系统设置自行打开，避免每次升级覆盖偏好。
2. **技能图标直接使用红／蓝背景。** 透明白色图标下面不再绘制通用图片组件的灰底。对照 [日奈礼服页面](https://kivo.wiki/data/character/346)，EX 及其衍生、固有武器强化使用 `#E54933`，其他技能使用 `#6172F4`。明暗主题保持图标原色，标签文字继续使用主题色。通用图片仍保留默认灰底与加载／失败占位。
3. **Spine 切换动作或皮肤保留镜头。** 不只保留缩放和平移，也保留初始取景边界，防止动作附件尺寸变化造成隐式跳动。加载新素材时才取景；“镜头复位”恢复该素材的初始镜头。
4. **缓存上限默认 1 GB。** 可选 512 MB、1 GB、2 GB、4 GB、无上限。降低上限立即整理，启动时应用保存的值，每次媒体下载结束继续整理。
5. **Spine 三项设置。** GPU 渲染和自动修复光照默认开启，实验性渲染默认关闭。
6. **重新构建修复。** 单任务参数保持为字符串数组；双击 CMD 与直接调用 Release 采用同一路径。原因及逐行日志解释见 [构建与环境](../构建与环境.md)。

## 缓存实现与维护边界

这是可重建资料的**磁盘缓存预算**，不限制收藏、账号、阅读足迹或用户主动保存到相册／文件夹的内容，也不等于 Android 设置显示的 App 总占用。界面使用 MB／GB 的常见写法，代码按 1024 进制计算。

JSON 预留 64 MiB，Coil 图片预留 256 MiB，其余分给 Spine／模型／下载暂存。例如默认 1 GiB 中重媒体为 704 MiB，512 MiB 档中重媒体为 192 MiB。分项固定预算避免多个缓存各自占满总上限，也避免重建正在使用的 Coil 缓存实例。无上限取消重媒体累计容量约束；普通图片和资料仍保留各自的有界工作缓存。

媒体按最近使用时间淘汰。骨架／模型整包删除，保证 atlas、骨架和纹理的一致性；普通下载暂存逐文件删除。下载、手动清理、预算改变和释放共用互斥锁，磁盘扫描在 IO 线程执行。资源使用结束按实例身份解除保护，同路径重试产生的新实例不会被旧弹窗释放误伤。失败／取消的未就绪包也进入整理，删除失败不虚报占用已下降。

进行中的下载和正在使用的包可能短暂使占用超过上限，关闭预览后会再次整理。数据库文件、Coil 索引和文件系统开销不计入有效内容预算，系统也可提前回收缓存。每文件、纹理解码和模型解包的内存安全限制与缓存档位无关，选择无上限不会解除它们。

## Spine 设置的具体含义

| 开关 | 原生实现 |
| --- | --- |
| 启用 GPU 渲染 | Android 10 及以上使用宿主硬件 Canvas；关闭仅对 Spine View 使用软件层，不影响 App 其余 UI。Android 9 及以下因运行时网格限制自动保留软件回退。 |
| 自动修复光照 | 与网页 `fixBlendMode` 相同，将素材的 Additive 改为 Screen；不修改其他混合模式，也不重写下载文件。保存原始模式，关闭后恢复。预览、截图、录制共用同一骨架和渲染路径。 |
| 启用实验性渲染 | 对齐网页立绘实验选项的原始比例语义，在固定原生视口中使用 1:1 骨架单位比例，允许缩放／平移，默认关闭。只影响立绘，大厅仍使用适应／填充。它不是另装一套浏览器渲染器。 |

礼服佳代子页面是 [角色 351](https://kivo.wiki/data/character/351?mode=appreciation)。本轮定向验证使用原始大厅 **Spine 682 / CH0239_home**，不是已经预修复的 684。原始素材的加法光效会明显过曝，打开修复后使用滤色混合。

## 回归入口

```powershell
# 不启动 Gradle 的脚本参数回归。
powershell -NoProfile -File tools/test-build-arguments.ps1

# 单元测试、Debug/Release Lint 与 Release 交付构建。
powershell -NoProfile -File tools/build.ps1 -Target Verify

# 仅对已启动的 Kivo_Archive_Phone 专用模拟器执行。
. ./tools/environment.ps1
Set-KivoBuildEnvironment
$env:ANDROID_SERIAL = 'emulator-5558'
Assert-KivoEmulator (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe') $env:ANDROID_SERIAL
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=wiki.kivo.app.SpineSettingsRegressionTest,wiki.kivo.app.SettingsBudgetUiTest' --no-parallel --max-workers=2 --console=plain
```

缓存淘汰用例覆盖整包顺序、逐文件下载淘汰、活动资源保护、释放后收敛、无上限和各档预算；Android 定向用例检查偏好重新读取、原始大厅真实渲染帧、缩放／拖动后切动作的像素一致性，以及光效开关的可逆性。真机 GPU 和 Android 9 及以下回退未在本轮设备矩阵覆盖，不将模拟器结论扩展到所有设备。
