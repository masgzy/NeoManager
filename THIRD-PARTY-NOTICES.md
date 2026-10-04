# 第三方组件与协议声明（THIRD-PARTY NOTICES）

本文档列出 NeoManager 及其构建产物中直接或间接引入的第三方组件与协议。
项目主协议为 **GPL-3.0-or-later**；引入规则：**不使用 GPL-3.0 之外的强传染协议依赖**。

## 一、当前已引入（Phase 1）

| 组件 | 协议 | 用途 | 引入方式 |
|---|---|---|---|
| Kotlin 标准库与 kotlin-test | Apache-2.0（带例外条款） | 语言运行时与测试断言 | Maven 依赖 |
| kotlinx-coroutines | Apache-2.0 | UI 侧后台目录扫描 | Maven 依赖 |
| androidx.core-ktx | Apache-2.0 | AndroidX 基础 | Maven 依赖 |
| androidx.activity-compose | Apache-2.0 | Activity 与 Compose 桥接 | Maven 依赖 |
| androidx.navigation-compose | Apache-2.0 | 单 Activity 导航 | Maven 依赖 |
| androidx.lifecycle-runtime-* | Apache-2.0 | 生命周期感知 | Maven 依赖 |
| androidx.compose-*（BOM 管理） | Apache-2.0 | UI 框架 / Material 3 / 图标 | Maven 依赖 |
| JUnit 5（junit-jupiter / platform） | EPL-2.0 | engine-core 单元测试 | 仅测试期依赖 |
| ktlint（ktlint-gradle 封装） | MIT | 代码规范检查（仅构建期，不分发） | 构建工具 |

## 二、计划引入（按路线图阶段）

| 组件 | 协议 | 用途 | 阶段 | 引入方式说明 |
|---|---|---|---|---|
| sora-editor | LGPL-2.1 | 代码编辑器组件（大文件/高亮） | Phase 1 ② | **动态依赖**：作为独立组件经 Maven 引入运行时使用，不复制、不修改其源码，与主项目 GPL-3.0-or-later 兼容 |
| apktool（brutex 等模块） | Apache-2.0 | AXML 反编译/回编译参考与复用 | Phase 2 | Maven 依赖或经合规改造吸收 |
| apksig | Apache-2.0 | APK v1/v2/v3 签名 | Phase 2 | Maven 依赖 |
| dexlib2 / smali / baksmali | BSD-3 | Dex 读写与 smali 汇编 | Phase 3 | Maven 依赖 |
| libsu（topjohnwu） | Apache-2.0 | root shell 框架 | Phase 1 ② | Maven 依赖 |
| commons-compress | Apache-2.0 | zip/tar/xz 压缩解压 | Phase 1 ② | Maven 依赖 |
| Shizuku API | Apache-2.0 | 免 root 特权操作 | Phase 1 ② | Maven 依赖 |

> 吸收 GPL-3.0 同协议项目代码（如 AEE、AppManager 等）时，保留原版权头并在本清单登记。

## 三、协议合规要点

1. **LGPL 动态依赖原则**：sora-editor 以独立组件形态运行，本项目不静态合并其源码；如需修改其行为，通过继承/包装或向上游提 PR 完成。
2. **测试与构建工具豁免**：JUnit、ktlint 等仅在开发/测试期使用，不进入分发产物，其协议不约束应用本体。
3. **登记纪律**：每次 `gradle/libs.versions.toml` 新增依赖，须同步更新本清单（CI 后续将加自动化校验）。
