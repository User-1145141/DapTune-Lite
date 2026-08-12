# DapTune

[![CI](https://github.com/silverpoetry/DapTune/actions/workflows/ci.yml/badge.svg)](https://github.com/silverpoetry/DapTune/actions/workflows/ci.yml)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](docs/compatibility.md)
[![License](https://img.shields.io/github/license/silverpoetry/DapTune)](LICENSE)

[English](README_EN.md) | 简体中文

DapTune 是面向部分小米系固件的本地 Dolby DAP 20 段均衡器控制器。它只管理曲线，并通过系统已经
提供的全局 `AudioEffect` 把参数 110（`GraphicEqualizerBandGains`）写入厂商 DSP；不处理 PCM，
不录音，不开启或破解 Dolby，也不修改空间音频等其他参数。

> [!WARNING]
> 这是独立的实验性项目，不是 Dolby Laboratories 或 Xiaomi 的官方软件。私有音效接口可能随系统更新
> 改变。首次使用、系统升级或更换输出路径后，请用容易辨认的曲线低音量验证；不兼容时立即停止自动切换。

## 功能

- 固定 20 个 Dolby DAP 频点，使用原生 Q4（1/16 dB）精度；最高 `+10 dB`，不人为设置最大衰减；
- 大尺寸曲线预览和 20 段滑杆，支持点选频点、0.5 dB 操作步进、触感反馈与动态纵轴；
- 保存、复制、覆盖和删除自定义配置，自定义配置优先排列；
- 导入 DapTune JSON、Wavelet/AutoEq `GraphicEQ`、Equalizer APO/AutoEq 参数均衡器和频率增益表；
- 峰值归零、均值归零、平滑、整体位移、缩放和可配置的硬阈值限制；
- 按扬声器、有线耳机、具体蓝牙或 LE Audio 设备、USB、HDMI 自动选择配置；
- 前台服务事件驱动切换，记录路由、配置、写入结果和验证方式，日志可随时清空；
- 代理型 DAP 写后逐一回读并在失败时回滚；setter-only `DAP_offload` 按厂商真实语义写入。

## 使用条件

- Android 11（API 30）或更高版本；
- 系统必须暴露 DapTune 已识别的 Xiaomi Dolby DAP implementation；
- 系统 Dolby 全景声必须已开启，且当前播放路径确实经过该 DAP；
- 已验证的系统不需要 root、LSPosed、Shizuku 或常驻 ADB。

DapTune 不按手机型号假定兼容。启动时会检查 implementation UUID、effect 控制权、DAP 状态、profile
数量及回读能力，不满足条件就拒绝写入。完整边界见[兼容性说明](docs/compatibility.md)。

## 快速开始

1. 从 [Releases](https://github.com/silverpoetry/DapTune/releases) 下载 APK，或按下文自行构建；
2. 保持系统 Dolby 全景声开启，进入“调音”，先手动应用一条容易识别的测试曲线；
3. 在“配置”导入或保存曲线；
4. 在“自动”中为每个播放设备选择配置，再开启自动切换；
5. 允许后台运行并保留前台服务。Android 13+ 即使拒绝通知权限，AOSP 仍允许前台服务运行，
   但通知抽屉不会显示其通知；部分厂商后台策略仍可能终止服务。

详细步骤、升级和卸载方式见[安装指南](docs/installation.md)。

## DapTune JSON

DapTune 的原生交换格式是封闭、可版本化、无损保存 Q4 值的 JSON 对象。v1 的核心约束如下：

- `format` 必须是 `com.weich.daptune.profile`，`version` 必须是 `1`；
- `band_plan` 必须是 `dolby-dap-20-v1`；
- `frequencies_hz` 必须与规定的 20 个频点按顺序逐项一致；
- `gains_q4` 必须是 20 个 32 位有符号整数，`dB = gains_q4 / 16`；
- 每段最高为 `160`（`+10 dB`），负值没有产品层人为下限；
- 六个字段全部必填，未知字段会被拒绝。

准确字段定义、频点表、换算规则、验证行为和兼容策略见
[DapTune JSON v1 格式规范](docs/daptune-json-format.md)。仓库同时提供机器可读的
[JSON Schema](docs/schema/daptune-profile-v1.schema.json) 以及三个可直接导入的例子：

- [平直](examples/profiles/flat.daptune.json)；
- [暖厚](examples/profiles/warm.daptune.json)，与应用内置曲线逐 Q4 一致；
- [深衰减](examples/profiles/deep-attenuation.daptune.json)，演示低于 `-10 dB` 的合法值。

其他常见 EQ 文件的实际支持范围见[导入格式说明](docs/import-formats.md)。

## 工程结构

```text
app                 Compose 导航与应用入口
core:model          不可变领域模型和 20 段曲线
core:eq             文件解析、频率映射和曲线处理
core:designsystem   Material 3 主题与均衡器控件
domain              仓储边界和用例
data                Room、DataStore 与仓储实现
platform:dap        厂商 DAP 协议与事务写入
platform:routing    播放设备识别与隐私安全的稳定键
feature:*           调音、配置、自动切换与日志界面
```

设计说明见[架构文档](docs/architecture.md)和[架构决策记录](docs/adr)。

## 构建与验证

需要 JDK 17 和 Android SDK 36。项目提交 Gradle Wrapper：

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintRelease :app:assembleRelease
node .\tools\validate-profile-contract.mjs
```

`release` 启用 R8 和资源收缩；没有完整签名环境变量时会生成未签名 APK，仅用于源码验证。
`optimized` 与 `release` 使用相同优化，但采用 `.debug` 应用 ID 和本机 debug 证书，只用于测试机性能
验证，不能作为正式发布包。签名流程见[发布签名](docs/release-signing.md)。

## 文档

- [安装与更新](docs/installation.md)
- [兼容性与已知限制](docs/compatibility.md)
- [DapTune JSON v1](docs/daptune-json-format.md)
- [其他 EQ 导入格式](docs/import-formats.md)
- [架构](docs/architecture.md)
- [故障排查](docs/troubleshooting.md)
- [隐私](PRIVACY.md)、[安全策略](SECURITY.md)、[贡献指南](CONTRIBUTING.md)

## 许可与商标

源代码按 [Apache License 2.0](LICENSE) 发布，第三方组件见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。Dolby、Dolby Atmos、Xiaomi 及其他名称和商标
归各自权利人所有；这里的引用只用于描述兼容接口和测试环境，不表示认可、授权或隶属关系。
