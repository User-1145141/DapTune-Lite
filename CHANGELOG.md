# Changelog

本项目的重要变化记录在此。版本遵循 [Semantic Versioning](https://semver.org/)。

## [Unreleased]

## [0.2.0] - 2026-08-12

### Added

- 配置页可检索 AutoEq 官方推荐结果，并把标准 GraphicEQ 自动转换为 DapTune 20 段自定义配置；
- AutoEq 索引本地搜索、七天缓存、离线回退和明确的网络错误状态；
- 搜索防抖、测量来源与耳机类型展示，以及下载、转换、保存和当前路由应用状态。

### Security

- 只访问代码中固定的 AutoEq 官方 GitHub Raw HTTPS 路径，不发送搜索词，不接受用户提供的 URL；
- 对索引、配置大小、编码路径、目录穿越、条目名称和 GraphicEQ 内容执行严格校验。

### Release identity

- Android signer certificate SHA-256:
  `79:62:1F:C9:4C:0C:56:C8:10:8C:BE:C8:32:28:81:7C:D0:6A:E6:1B:05:26:92:53:C3:2D:8D:D2:60:89:4E:F0`.

## [0.1.0] - 2026-08-12

### Added

- Xiaomi Dolby DAP 参数 110 的 20 段 Q4 编辑、预览、保存和应用；
- 内置平直、暖厚、澎湃、响度、人声、明亮和柔和配置；
- 按扬声器、有线、蓝牙、LE Audio、USB 和 HDMI 路由自动切换；
- 前台服务生命周期恢复、设备历史删除和可清空操作日志；
- DapTune JSON、GraphicEQ、ParametricEQ、CSV、TSV 自动或显式导入；
- 峰值/均值归零、平滑、整体移动、缩放和硬阈值限制；
- 代理型 DAP 回读验证与回滚、`DAP_offload` setter-only 写入策略；
- Material 3 动态配色、折叠标题、交互曲线和 20 段触感滑杆；
- DapTune JSON v1 规范、Schema、示例和契约一致性校验；
- 中英文仓库首页、品牌横幅、完整文档入口和本地链接/标题锚点校验。

### Release identity

- Android signer certificate SHA-256:
  `79:62:1F:C9:4C:0C:56:C8:10:8C:BE:C8:32:28:81:7C:D0:6A:E6:1B:05:26:92:53:C3:2D:8D:D2:60:89:4E:F0`.

[Unreleased]: https://github.com/silverpoetry/DapTune/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/silverpoetry/DapTune/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/silverpoetry/DapTune/releases/tag/v0.1.0
