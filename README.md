# DapTune

DapTune 是一个本地优先的 Xiaomi Dolby DAP 20 段均衡器控制器。应用只管理曲线并向厂商
DSP 写入参数 110（`GraphicEqualizerBandGains`），不处理 PCM、不录音、不切换杜比开关，也不修改
任何其他 Dolby 参数。

## 功能

- 20 个原生 Dolby 频点，Q4 精度，范围严格限制为 ±10 dB
- 内置平直、暖厚、澎湃、响度、人声、明亮和柔和预设
- 保存、复制、删除命名配置
- 导入 DapTune JSON、GraphicEQ、Equalizer APO/AutoEq 参数均衡器、CSV/TSV
- 超限曲线等比压缩或裁切；提供峰值归零、均值归零、平滑、反相等处理
- 按手机扬声器、有线耳机、具体蓝牙/LE Audio、USB 和 HDMI 输出自动切换
- 自动识别 DAP 协议能力：代理型 DAP 写后逐一回读并可回滚，直连 DAP_offload 按厂商 setter-only 语义写入

## 权限与兼容性

已验证的 DAP implementation UUID 为 `9d4921da-8225-4f29-aefa-39537a04bcaa`。应用启动时会检测
descriptor、运行状态和曲线回读能力，不匹配时拒绝写入；兼容分支依据 effect type，而非设备型号白名单。

在已验证的系统上不需要 root、LSPosed 或常驻 shell。应用通过 Android `AudioEffect` 的全局混音
session 调用厂商 DAP；HiddenApiBypass 仅用于访问系统未公开但设备已提供的构造和参数接口。

自动切换使用低优先级前台服务，只监听播放路由与杜比状态事件，没有音频线程和周期轮询。

## 工程结构

```text
app                 Compose 导航、应用入口
core:model          不可变领域模型和 20 段曲线
core:eq             文件解析、频率映射和曲线处理
core:designsystem   Material 3 主题与均衡器控件
domain              仓储边界和用例
data                Room、DataStore 与仓储实现
platform:dap        厂商 DAP 协议与事务写入
platform:routing    播放设备识别与隐私安全的稳定键
feature:*           调音、配置、自动切换功能
```

架构决策记录位于 [`docs/adr`](docs/adr)。

## 构建与检查

需要 JDK 17+ 和 Android SDK 36。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug
.\gradlew.bat :app:lintOptimized :app:assembleOptimized
```

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

`optimized` 使用与 `release` 相同的 R8、资源收缩和不可调试设置，但保留 `.debug` 应用 ID，并由
本机 Android 调试证书签名，仅用于在测试机上原位覆盖调试版、保留应用数据并评估真实运行性能。
它不是应用商店发布签名。正式发布仍应使用独立的受保护签名配置构建 `release`。
