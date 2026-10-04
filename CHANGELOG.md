# 更新日志（Changelog）

本项目的全部显著变更将记录在本文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本（SemVer）](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 新增

- 多模块 Gradle 工程骨架（`engine-core` / `engine-android` / `editor` / `ui` / `app`）与版本目录（Version Catalog）
- Material Design 3 主题：Android 12+ 动态取色；Android 8–11 品牌色方案回退；深浅色双主题
- 双窗口布局：对齐参考形态，横竖屏均为左右两列（竖屏下每列为窄幅纵向文件列表）；分隔条拖拽调节（20%–80%）与双击复位，占比旋转持久化
- 文件面板组件：路径栏、类型图标文件列表、底部工具栏占位、错误与空态视图
- 临时演示文件浏览数据源（`java.io` 直读，Phase 1 ② 由引擎层替换；`FileBrowser` 接口已就位）
- 存储权限引导：Android 11+ 「所有文件访问」横幅（返回前台自动复查）与 Android 8–10 运行时权限首启请求
- 设置页骨架：版本 / 开源许可 / 项目地址 / 本地化承诺说明
- `engine-core`：`PathNormalizer` 纯 Kotlin 路径规范化工具与 22 项 JUnit 5 行为规格测试
- GitHub Actions CI：push/PR 触发 `assembleDebug` + `engine-core` 单测 + `ktlint` 检查，产出 Debug APK
- 工程配套：`.gitignore`、`.editorconfig`、ktlint、源文件 SPDX 头、THIRD-PARTY-NOTICES、本更新日志
- 自适应启动图标（API 26+ 全覆盖，含 Android 13+ 主题图标）

### 技术决策

- 锁定 2026-10 最新稳定栈：AGP 9.4.1（内置 Kotlin 支持，模块不再单独 apply kotlin-android）+ Kotlin 2.4.20 + Compose BOM 2026.09.00 + Gradle 9.8
- minSdk 26 / targetSdk+compileSdk 37
- 应用 ID：`io.github.masgzy.neomanager`
- 全本地化承诺落地：清单永不声明 `android.permission.INTERNET`，不嵌入任何遥测 SDK
