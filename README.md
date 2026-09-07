# 便携古书馆 · Android

**0.4.0 组织笔记验收版**。这是由社区独立维护的非官方第三方 Android 客户端，不代表 kivo古书馆官方立场，也不经官方渠道发布。

电脑体验请双击本目录的 **电脑验收.cmd**；手机安装本地构建生成的 `artifacts/kivo-archive-0.4.0-preview.apk`。继续沿用 Kotlin / Jetpack Compose 原生架构和独立验收签名，可以覆盖旧验收版并保留本地资料。

| 入口 | 内容 |
| --- | --- |
| [验收说明](artifacts/验收说明.md) | 安装和本轮检查路径 |
| [本期验收报告](artifacts/organization-0.4/验收报告.md) | 实际结果、包体、校验及验证边界 |
| 真实界面图册 | 本地验收产物不纳入 Git；运行 `tools/build.ps1 -Target Verify` 后查看生成结果 |
| [当前状态](开发状态/PROJECT_STATE.md) / [最新交接](开发状态/最新交接.md) | 后续开发入口 |
| 组织模块设计同步 | 设计文档位于项目外部，不纳入本仓库 |
| 角色模块设计同步 | 设计文档位于项目外部，不纳入本仓库 |
| [模块与维护](开发状态/模块与维护.md) / [构建与环境](开发状态/构建与环境.md) | 分层、存储、环境及扩展规范 |
| [API 差异](开发状态/API差异.md) / [第三方组件](THIRD_PARTY_NOTICES.md) | 协议、依赖与许可证 |
| [新账号提交流程](使用Decagramma-Ton提交.md) | 使用 `Decagramma-Ton` 身份提交与推送，含首次授权说明 |

继续保留 24 维图鉴筛选、8 种排序、姓名搜索、卡片／紧凑列表；角色页包含类型与换装、翻译、完整基本信息、数据／资料／鉴赏／语音、原生 Spine 和 3D、图片与短视频导出。共享 Markdown 支持居左／居中／居右及嵌套容器，保留表格图片与链接。

本版新增组织笔记、地图地标、关系资料和角色联动；组织目录提供三列卡片／紧凑列表偏好，角色分类支持稳定切换与可关闭的横滑，共享 Markdown 支持离线 Mermaid。漫画、配队、全站音乐等仍按后续模块推进。生产表态和补充提交未用于自动测试，写入通过本地替身验证；真机兼容范围见验收报告。

重新打包双击「重新构建.cmd」；完整检查运行 tools/build.ps1 -Target Verify。正式签名、公开发行与 Spine 运行时授权核对另行处理，当前未对外发布。

项目仓库：[Decagramma-Ton/Kivowiki-Android-AinSophAur](https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur)。口令、会话、私有正文、APK、签名密钥、模拟器镜像、本地日志和缓存不进入 Git。
