<!--
  NeoManager — README
  Copyright (C) 2026 The NeoManager Contributors
  SPDX-License-Identifier: GPL-3.0-or-later
-->

<div align="center">

# Neo 管理器

**开源、全本地化的 Android 文件管理与 APK 逆向工具箱**

Kotlin · Jetpack Compose · Material You · GPL-3.0-or-later

</div>

---

## 项目状态

**当前进度：Phase 1 第①步（UI 骨架）已完成** ✅ —— 双窗口界面可安装真机演示，引擎接入（Phase 1 第②步）进行中。

| 模块 | 状态 |
|---|---|
| 多模块工程骨架 + 版本目录 + CI | ✅ 已完成 |
| MD3 主题（动态取色 + 品牌色回退） | ✅ 已完成 |
| 双窗口布局（永远左右两列 + 可拖拽分隔条） | ✅ 已完成 |
| 双窗口文件浏览（临时数据源） | ✅ 可演示 |
| 引擎接入（root/Shizuku、压缩包、编辑器） | 🚧 Phase 1 ② |
| APK 工具箱（信息/签名/安装/克隆） | ⏳ Phase 2 |
| Dex++（smali 编辑回写） | ⏳ Phase 3 |
| ARSC 结构化编辑器 / Arsc 对比 / Dex 对比 | ⏳ Phase 4 |

## 特性

- **双窗口文件管理**：横竖屏均为左右两列（窄屏下每列为窄幅纵向列表），分隔条拖拽调节（20%–80%）、双击复位，占比跨旋转持久化
- **Material You**：Android 12+ 动态取色；Android 8–11 回退内置品牌色方案，深浅色双主题
- **全本地化**：不申请 INTERNET 权限、无遥测、无广告、无账号体系
- **引擎/UI 严格分层**：`engine-core` 为纯 Kotlin 模块（零 Android 依赖），可独立单元测试与开源复用

## 路线图

| 阶段 | 目标 | 验收标准 |
|---|---|---|
| **Phase 1 能日用** | UI 骨架 + 引擎接入（双窗口浏览/压缩解压/编辑器/root+Shizuku） | 当主力文件管理器用一周不回退 |
| **Phase 2 能干活** | APK 信息/提取/安装/克隆 + apksig 签名 + AXML 查看 | 「改应用名→重签→安装」全流程 |
| **Phase 3 深度编辑** | Dex++（dexlib2 + sora-editor）+ smali 编译回写 | 改 smali 逻辑并成功回包 |
| **Phase 4 结构化对比** | ARSC 结构化编辑器 → ARSC 对比 → Dex 对比 | ARSC 改动后可被主流工具正常解析；对比结果与手工 diff 一致 |

## 架构

```
NeoManager/
├── engine-core/      # 纯 Kotlin：ARSC/AXML/Dex/ZIP 解析编辑引擎（未来单独开源）
├── engine-android/   # Android 绑定：root(libsu)/Shizuku/安装器/存储
├── editor/           # sora-editor 封装 + 语法高亮配置
├── ui/               # Compose：双窗口/设置/对比视图
└── app/              # 组装壳：单 Activity + Compose Navigation
```

**架构红线**：

1. `engine-core` 纯 Kotlin、无任何 Android 依赖，JUnit 单元测试全覆盖（含 golden-file 回归）；
2. 平台能力一律经接口注入，禁止泄漏进引擎层；
3. 第三方依赖协议合规：不引入 GPL-3.0 之外的强传染协议依赖（sora-editor 以 LGPL 动态依赖方式使用，详见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)）。

## 构建

环境要求：JDK 21（首次构建缺失时由 Gradle 工具链自动下载）、Android SDK（Android Studio 自动管理）。

```bash
# 命令行构建 Debug APK
./gradlew assembleDebug

# 仅运行 engine-core 单元测试
./gradlew :engine-core:test

# 代码规范检查
./gradlew ktlintCheck
```

或直接用 Android Studio（Ladybug 及以上版本）打开工程同步运行。

## 技术栈

| 组件 | 选型 |
|---|---|
| 语言 | Kotlin 2.4（AGP 9 内置 Kotlin 支持） |
| UI | Jetpack Compose + Material Design 3 |
| 构建 | Gradle 9.8 + Version Catalog + AGP 9.4 |
| 最低支持 | Android 8.0（minSdk 26） |
| 测试 | JUnit 5 + kotlin-test（引擎层全覆盖） |
| 规范 | ktlint + EditorConfig |
| CI | GitHub Actions（push/PR 触发，产出 APK） |

## 参与贡献

欢迎 Issue 与 PR：

1. 功能讨论请先开 Issue 对齐定位（避免与路线图阶段冲突）；
2. 提交信息遵循 Conventional Commits（`feat:` / `fix:` / `docs:` / `ci:` …）；
3. 引擎层改动必须附带单元测试；行为规格沉淀于 [docs/specs/](docs/specs/)（净室流程见该目录说明）。

## 许可

- 项目代码以 **GPL-3.0-or-later** 发布，详见 [LICENSE](LICENSE)；
- 第三方组件协议清单见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)；
- 「Neo 管理器」「NeoManager」名称与标识归项目所有，禁止冒名分发（协议管代码不管品牌）；
- 本项目为工具中立软件，使用者需自行遵守当地法律法规。
