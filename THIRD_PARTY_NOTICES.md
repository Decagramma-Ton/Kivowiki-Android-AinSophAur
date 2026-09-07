# 第三方组件与素材说明

本文件随 0.4.0 内部验收工程和 APK 一起交付。精确解析版本见 app/gradle.lockfile 的 releaseRuntimeClasspath；其中还包含 BOM、多平台元数据和被 R8 移除的内容，不能把整个依赖图直接当作 APK 中实际保留的代码清单。

| 组件族 | 许可 | 上游与本地许可正文 |
| --- | --- | --- |
| AndroidX：Compose、Activity、Lifecycle、Navigation、Room、DataStore、Core、Window、Graphics 及相关支持库 | Apache-2.0 | [AndroidX 源码](https://android.googlesource.com/platform/frameworks/support/)、[许可正文](licenses/APACHE-2.0.txt) |
| Kotlin、kotlinx.coroutines、kotlinx.serialization、JetBrains Compose/AndroidX 适配、annotations | Apache-2.0 | [Kotlin](https://github.com/JetBrains/kotlin)、[kotlinx](https://github.com/Kotlin)、[许可正文](licenses/APACHE-2.0.txt) |
| Dagger / Hilt、Accompanist、Guava listenablefuture、JSR-305、JSpecify | Apache-2.0 | [Dagger](https://github.com/google/dagger)、[Accompanist](https://github.com/google/accompanist)、[JSpecify](https://github.com/jspecify/jspecify)、[许可正文](licenses/APACHE-2.0.txt) |
| javax.inject、jakarta.inject-api | Apache-2.0 | [Injection API](https://github.com/jakartaee/inject)、[许可正文](licenses/APACHE-2.0.txt) |
| OkHttp、Okio、Retrofit 与序列化转换器 | Apache-2.0 | [Square 开源项目](https://square.github.io/)、[许可正文](licenses/APACHE-2.0.txt) |
| Media3 1.11.0、Filament / gltfio / utils 1.75.1 | Apache-2.0 | [Media3](https://github.com/androidx/media)、[Filament](https://github.com/google/filament/tree/v1.75.1)、[许可正文](licenses/APACHE-2.0.txt) |
| Spine Android 4.2.12 与 Spine Runtimes | Spine Runtimes License Agreement | [对应分支](https://github.com/EsotericSoftware/spine-runtimes/tree/4.2)、[完整许可](licenses/Spine-Runtimes.txt)；不将其标为 MIT / Apache |
| libGDX 1.14.0 / jnigen-loader 2.5.2（Spine 的传递依赖） | Apache-2.0 | [libGDX](https://github.com/libgdx/libgdx)、[许可正文](licenses/APACHE-2.0.txt) |
| Coil 3 | Apache-2.0 | [Coil](https://github.com/coil-kt/coil)、[许可正文](licenses/APACHE-2.0.txt) |
| CommonMark 与 GFM 表格 / 自动链接扩展 0.24.0 | BSD-2-Clause | [对应版本源码](https://github.com/commonmark/commonmark-java/tree/commonmark-parent-0.24.0)、[含作者声明的许可正文](licenses/commonmark-BSD-2-Clause.txt) |
| Mermaid 11.17.2（本地浏览器构建） | MIT | [上游](https://github.com/mermaid-js/mermaid)、[许可正文](licenses/mermaid-LICENSE.txt)；打包文件保留传递组件的内嵌许可声明，来源及摘要见 core/content/src/main/assets/mermaid/README.md |
| jsoup 1.21.2 | MIT | [对应版本源码](https://github.com/jhy/jsoup/tree/jsoup-1.21.2)、[含作者声明的许可正文](licenses/jsoup-MIT.txt) |
| autolink-java 0.11.0 | MIT | [对应版本源码](https://github.com/robinst/autolink-java/tree/autolink-0.11.0)、[含作者声明的许可正文](licenses/autolink-MIT.txt) |

许可核对来自上述上游与 Maven 发布 POM。许可证文本保持原文，不将中文概述替代其条款。开发工具（Gradle、AGP、KSP、ktfmt、JUnit、Android SDK/Emulator 等）不作为 App 功能打包；它们由各自上游分发并适用各自许可。

0.3.0 增加 Filament、gltfio、utils 原生库。实际 ABI、大小与 16 KiB 对齐检查见 artifacts/character-0.3/apk-inspection.json；不能沿用 F0 只有 graphics-path / shared-counter 的包体结论。

网站正文与游戏图片按站长本次授权从公开 API/素材域名按需获取，未批量打包。仅内置四张嘴型贴图，来源和摘要见 [嘴型素材记录](core/media/src/main/assets/character/mouth/README.md)。游戏与站点的原有权利归各权利人；开源组件的许可证不会自动覆盖这些内容。应用轻量标记由矢量代码构成；未下载或内置网络字体。

关于页已提供本地许可全文入口，工程 licenses 与 APK 共用许可正文。Spine Runtimes 的许可条件独立于站长对游戏素材的授权，公开分发前需站方核对有效 Spine Editor 授权或其他适用安排。本内部验收包未对外发布。
