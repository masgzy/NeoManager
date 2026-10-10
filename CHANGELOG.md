# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式；
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)（alpha 阶段允许破坏性变更）。

## [0.2.0-alpha02] - 2026-10-11 · 日用补齐

对照官方新版行为走查后补齐的「要紧的基本功能」，发布给催更用户的第一个可用版本。

### 新增 — 文件管理

- **多选批量操作**：长按条目 → 操作菜单「多选」进入多选模式（或直接从菜单发起）；
  顶栏提供全选 / 已选计数 / 复制 / 剪切 / 删除，返回键优先退出多选
- **递归搜索**：路径栏搜索按钮展开搜索框（自动聚焦），输入防抖 350ms 后在当前目录
  及子目录中按名称包含匹配（大小写不敏感）；上限 500 结果 / 深度 12 层 / 2000 目录，
  超限提示截断；搜索结果列表支持同样的多选操作
- **打开方式分发**：目录与压缩包（zip/apk/jar/apks/xapk/apkm/tar/gz/xz/zst/bz2/7z/rar）
  点击直接进入内部浏览；图片（jpg/jpeg/png/gif/webp/bmp）进内置查看器；
  音视频/文档/APK 等调用系统应用打开（FileProvider 安全共享，未关联应用时给出提示）；
  长按菜单新增「多选」「用系统应用打开」
- **内置图片查看器**：任意 VFS 位置（含压缩包内部）查看图片，大图降采样解码（最长边
  2048px）防 OOM，支持双指缩放与拖动
- **编辑器深色模式**：跟随系统深浅色切换 sora-editor 配色方案

### 修复

- CI 构建链修复：补充 JitPack 仓库（libsu 坐标）、Shizuku API 包名（`rikka.shizuku`）、
  sora-editor 编译期可见性（api 方式，LGPL 动态打包不变）等 6 处问题，
  `assembleDebug + :engine-core:test + ktlintCheck` 三链路全绿

## [0.2.0-alpha01] - 2026-10-10 · Phase 1 ② 引擎接入

统一 IO 引擎上线：双窗口从「演示骨架」升级为「能日用」。

### 新增 — 引擎（engine-core，纯 Kotlin，49 项单元测试全绿）

- **统一 VFS 抽象**：`VfsUri`（`file://` 与可递归嵌套的 `zip:` 方案，`!` 分隔 +
  百分号编码）、`Vfs` 接口（列表/读取/写入/建目录/删除/重命名 + 能力位）、
  `LocalVfs`（NIO 实现，含符号链接识别）
- **ZIP 家族内部浏览**：`ZipVfs` 随机访问浏览 zip/jar/apk 内部（支持 zip 套 zip 递归、
  GBK 回退解码中文条目名）；改动回写采用「原条目保持压缩方式与时间戳」的整包重建
  （STORED 条目预置 CRC/大小，为 APK 的 resources.arsc/so 保持未压缩存储）
- **归档引擎** `ArchiveEngine`：魔数探测（zip/gz/xz/zst/bz2/7z/rar/ustar-tar）；
  列表与解压支持 zip/tar/tar.gz/tar.xz/tar.zst/tar.bz2/7z（只读）/rar4（只读）；
  打包支持 zip/tar 系与 gz/xz/zst/bz2 单文件压缩；zip-slip 路径穿越防护
- **跨后端文件操作** `FileOps`：本地 ↔ zip 之间流式复制/移动（同卷 rename 快路径）
- **提权回退** `VfsRegistry.CompositeVfs`：本地不可读时自动切换 Root/Shizuku 后端

### 新增 — 平台（engine-android）

- **Root 后端**（libsu，Apache-2.0）：`ls -Apl` 解析列表、`/data/local/tmp` 中转站读写
  （避免 shell 文本流损坏二进制）、root 删除/建目录/重命名
- **Shizuku 后端**（Apache-2.0）：用户服务 `NeoFileService`（AIDL，ParcelFileDescriptor
  传输二进制）、授权流程、服务绑定；Shizuku 优先、Root 兜底的自动选择
- 设置页新增「引擎」分区：Root/Shizuku 状态探测、授权请求、服务绑定

### 新增 — 界面（ui / editor / app）

- **文件操作**：长按条目底部操作菜单（复制/移动/跨窗复制移动/重命名/删除/压缩为
  ZIP/解压/属性/编辑器打开）、剪贴板粘贴、新建文件夹、删除二次确认
- **排序与过滤**：名称/大小/修改时间/扩展名 + 逆序 + 显示隐藏文件（目录恒优先）
- **属性对话框**：类型/大小/时间/路径 + **校验和**（MD5/SHA-1/SHA-256/CRC32，后台计算）
- **压缩包浏览**：点击 zip/apk/jar 直接进入内部浏览（返回栈无缝上溯到容器目录）
- **文本编辑器**（sora-editor 0.23.4，LGPL-2.1 动态依赖）：行号/自动换行、多字符集
  （UTF-8/UTF-16LE/UTF-16BE/GBK/ISO-8859-1，BOM 嗅探）、大文件提示（>2MB）、
  压缩包内只读预览
- 编辑器独立路由与顶栏；Material You 动态取色延续

### 变更

- `FilePaneState` 升级为 VfsUri 导航（zip 根上溯即容器目录）、排序/隐藏开关并入加载管线
- 底部工具栏三枚占位按钮全部启用（新建/排序/更多菜单）

### 依赖

- 新增：commons-compress 1.28.0（Apache-2.0）、zstd-jni（BSD-2）、xz 1.10（Public
  Domain）、junrar 7.5.9（unRar 许可）、libsu 6.0.0（Apache-2.0）、Shizuku api/provider
  13.1.5（Apache-2.0）、sora-editor 0.23.4（LGPL-2.1，动态依赖）
- 全部符合协议红线：未引入 GPL-3.0 之外的强传染协议依赖；THIRD-PARTY-NOTICES.md 已同步

### 已知限制

- zip 内编辑为整包重建（未保留 ZIP64 扩展与 extra fields）；APK 深度编辑（对齐/签名）
  属 Phase 2 重打包管线
- 7z 写入需 p7zip JNI（暂缓）；rar 仅支持 RAR4 格式读取
- 文件操作进度回调未接入（大目录复制/移动暂无进度条）
- 编辑器语法高亮 grammar、查找替换 UI 在后续版本补齐

## [0.1.0-alpha02] - 2026-10-09 · Phase 1 ① UI 骨架

- 多模块工程骨架（engine-core / engine-android / editor / ui / app）+ 版本目录 + CI
- 单 Activity + Compose Navigation + MD3 主题（Android 12+ 动态取色、8-11 品牌色回退）
- 双窗口布局：恒定左右两列 + 可拖拽分隔条（20%–80%、双击复位、跨旋转持久化）
- 双窗口文件浏览（java.io 临时数据源）
- 净室规范文档、第三方声明、GPL-3.0-or-later 许可
