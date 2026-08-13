# Changelog

本项目的重要变化记录在此。版本遵循 [Semantic Versioning](https://semver.org/)。

## [Unreleased]

## [0.3.1] - 2026-08-13

### Fixed

- 自动切换服务会在每次启动或恢复时重建唯一的路由监听会话，避免通知仍在但系统回调已经丢失的假活状态；
- 路由回调先于数据库和 Dolby 写入注册，事件在慢操作期间合并保留最新值，监听源意外结束后由同一会话退避重建；
- 调音、配置、自动页与服务改为共享同一个无占位初值的路由事件源，不再重复注册四套路由回调或重复查询当前输出；
- 删除并行维护的监听就绪标记、世代计数、通知文本缓存以及重复的 Dolby 路由事件入口，诊断日志不再阻塞监听恢复；
- 任务移除恢复只保留已验证的一次性系统请求，移除与其竞争的粘性服务重启路径；
- 后台协程取消会正确向上传播，不再被非关键操作日志写入吞掉。

## [0.3.0] - 2026-08-13

### Added

- 新增“关于 DapTune”页面，集中展示版本、项目、许可证与隐私入口；
- 支持手动检查 GitHub 正式版本，以及默认开启、24 小时限频的自动检查更新。

### Fixed

- Android 12+ 首次启动必须先授予“附近的设备”权限，授权后立即刷新播放路由；
- 蓝牙设备只有在完整地址能由系统已配对设备清单唯一验证时才会创建历史和专属规则；匿名地址、设备名称和 MediaRouter 包装 ID 不再被误当成设备身份；
- 已由旧版产生的匿名地址、AudioDevice 回退值和 `DEFAULT_ROUTE` 历史键，会在配置无冲突时事务式迁移到验证后的设备键并删除重复项；
- 经典蓝牙与 LE Audio 对同一物理设备使用同一隐私哈希键，原始地址不写入数据库；
- 路由解析、回调注册和单次处理超时均被隔离，监听异常会记录并以有上限的退避自动恢复；
- 已运行服务收到启动或任务恢复请求时会重新核对当前输出，不再只凭服务存活状态跳过刷新；
- 前台服务进程被系统回收后会通过同一监听入口恢复，补齐标准蓝牙与 MediaRouter 路由事件；
- 身份无法验证时自动切换保持现状，不再按名称猜测或写入瞬态设备。

### Release identity

- Android signer certificate SHA-256:
  `79:62:1F:C9:4C:0C:56:C8:10:8C:BE:C8:32:28:81:7C:D0:6A:E6:1B:05:26:92:53:C3:2D:8D:D2:60:89:4E:F0`.

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

[Unreleased]: https://github.com/silverpoetry/DapTune/compare/v0.3.1...HEAD
[0.3.1]: https://github.com/silverpoetry/DapTune/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/silverpoetry/DapTune/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/silverpoetry/DapTune/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/silverpoetry/DapTune/releases/tag/v0.1.0
