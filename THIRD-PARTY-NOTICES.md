# 第三方组件与协议声明（THIRD-PARTY NOTICES）

本文档列出 NeoManager 及其构建产物中直接或间接引入的第三方组件与协议。
项目主协议为 **GPL-3.0-or-later**；引入规则：**不使用 GPL-3.0 之外的强传染协议依赖**。

## 一、当前已引入（0.2.0-alpha01 · Phase 1 ②）

| 组件 | 协议 | 用途 | 引入方式 |
|---|---|---|---|
| Kotlin 标准库与 kotlin-test | Apache-2.0（带例外条款） | 语言运行时与测试断言 | Maven 依赖 |
| kotlinx-coroutines | Apache-2.0 | UI 侧后台扫描与操作调度 | Maven 依赖 |
| androidx.core-ktx | Apache-2.0 | AndroidX 基础 | Maven 依赖 |
| androidx.activity-compose | Apache-2.0 | Activity 与 Compose 桥接 | Maven 依赖 |
| androidx.navigation-compose | Apache-2.0 | 单 Activity 导航 | Maven 依赖 |
| androidx.lifecycle-runtime-* | Apache-2.0 | 生命周期感知 | Maven 依赖 |
| androidx.compose-*（BOM 管理） | Apache-2.0 | UI 框架 / Material 3 / 图标 | Maven 依赖 |
| **Apache Commons Compress 1.28.0** | Apache-2.0 | zip/tar/7z 归档与 gzip/xz/zst/bzip2 压缩流 | Maven 依赖（engine-core） |
| **zstd-jni** | BSD-2-Clause | zstd 编解码本地实现（commons-compress 依赖） | Maven 依赖（engine-core） |
| **XZ for Java 1.10** | Public Domain | xz 编解码（commons-compress 依赖） | Maven 依赖（engine-core） |
| **junrar 7.5.9** | unRar 许可（免费分发，仅限配合 RAR 解压使用） | RAR4 归档只读解包 | Maven 依赖（engine-core） |
| **libsu 6.0.0** | Apache-2.0 | root shell 执行框架 | Maven 依赖（engine-android） |
| **Shizuku api / provider 13.1.5** | Apache-2.0 | 免 root 特权操作（用户服务） | Maven 依赖（engine-android） |
| **sora-editor 0.23.4** | LGPL-2.1 | 代码编辑器组件（大文件/行号/换行） | **动态依赖**：作为独立组件经 Maven 引入运行时使用，不复制、不修改其源码，与主项目 GPL-3.0-or-later 兼容（editor 模块） |
| JUnit 5（junit-jupiter / platform） | EPL-2.0 | engine-core 单元测试 | 仅测试期依赖 |
| ktlint（ktlint-gradle 封装） | MIT | 代码规范检查（仅构建期，不分发） | 构建工具 |

## 二、计划引入（按路线图阶段）

| 组件 | 协议 | 用途 | 阶段 | 引入方式说明 |
|---|---|---|---|---|
| apktool（brutex 等模块） | Apache-2.0 | AXML 反编译/回编译参考与复用 | Phase 2 | Maven 依赖或经合规改造吸收 |
| apksig | Apache-2.0 | APK v1/v2/v3 签名 | Phase 2 | Maven 依赖 |
| dexlib2 / smali / baksmali | BSD-3 | Dex 读写与 smali 汇编 | Phase 3 | Maven 依赖 |

## 三、协议合规说明

1. **unRar 许可**（junrar）：允许免费分发用于解压 RAR 归档；禁止用 RAR 压缩算法
   重建 RAR 归档器（本项目仅用其只读解包能力，符合条款）。
2. **LGPL-2.1**（sora-editor）：以「动态依赖 + 不修改源码」方式使用，保持组件可替换性，
   满足 LGPL 对「组合作品」的宽松要求；若未来需要修改其源码，将改为独立进程/插件方式。
3. **Apache-2.0 / BSD 系**：与 GPL-3.0-or-later 单向兼容，保留版权与 NOTICE 声明即合规。
4. 本项目 **不引入** GPL 之外的强传染协议（如 AGPL 强网络条款场景）；EPL-2.0（JUnit）
   仅测试期使用，不进入分发产物。
