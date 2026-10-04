# NeoManager ProGuard/R8 规则
#
# Phase 1 骨架期 release 构建关闭 minify（见 app/build.gradle.kts）；
# Phase 2 打包管线开启时在此补全规则：
# - kotlin.Coroutines / Compose 官方规则已由 AAR 内置 consumer 规则覆盖
# - sora-editor（LGPL 动态依赖）接入后按其官方文档补充 keep 规则
# - engine-core 反射面（若有）在此显式 keep，保持最小暴露面
