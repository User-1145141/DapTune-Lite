# 兼容性与已知限制

DapTune 控制的是厂商私有 Dolby DAP effect，不是 Android 标准均衡器。Android 版本、手机品牌或“支持
Dolby 全景声”都不能单独证明兼容；必须同时满足 effect descriptor、协议和实际播放路径条件。

## 运行条件

| 项目 | 要求 |
|---|---|
| Android | API 30（Android 11）或更高 |
| DAP implementation UUID | `9d4921da-8225-4f29-aefa-39537a04bcaa` |
| DAP type UUID | 构造时使用 `ec7178ec-e5e1-4432-a3f4-4657e6795210` |
| Audio session | 全局混音 session `0` |
| Dolby 状态 | effect 与 DAP 均已由系统开启 |
| Profile 数量 | 1–32 |
| Android 权限 | 系统必须允许应用获得 effect 控制权并调用现有私有接口 |

应用通过 `AudioEffect.queryEffects()` 查找 implementation UUID，不维护手机型号白名单。找到 descriptor
后再读取控制权、effect 开关、DAP 开关、profile 数量和当前 profile；任一关键状态无效都不会写曲线。

## 已识别的两类 DAP

| 类型 | Descriptor type UUID | 写入后验证 |
|---|---|---|
| Xiaomi proxy `DAP` | `fa81dbde-588b-11ed-9b6a-0242ac120002` | 逐 profile 回读完整 20 段；不一致即回滚 |
| Direct `DAP_offload` | `46d279d9-9be7-453d-9d7c-ef937f675587` | setter-only；复核控制权、开关和 profile 状态 |

所有 profile 都写同一条参数 110 曲线，因为系统可在媒体播放过程中切换内部 profile。代理型 DAP
提供真实参数回读；直连 offload 实现对同一读取请求可能只返回调用方的零填充缓冲区，所以绝不能把该
缓冲区当作真实曲线或回滚来源。未识别的 descriptor type 保守地按 setter-only 处理。

setter-only 的“写入已接受”不等于对最终每个 Q4 值做过回读验证。界面和日志会区分这两种结果。

## 不需要与可能需要的权限

在已经验证的固件上，DapTune 本体不需要 root、LSPosed、Shizuku、辅助功能或 ADB 常驻。它使用
HiddenApiBypass 访问系统已存在但未公开的 `AudioEffect` 构造及参数方法；这不会在缺失厂商 DAP 的
设备上创造 DSP，也不会绕过 Dolby 授权。

仓库内的 [`daptune-usb-dsp-offload`](../tools/magisk/daptune-usb-dsp-offload) 是只面向 Xiaomi
`turner` 的独立实验工具，用来验证一个被固件属性关闭的 MediaTek USB DSP-offload 路径。它需要
Magisk/KernelSU，与主应用兼容性无关，也不应刷入其他产品。

## 播放路径限制

找到并成功写入 DAP 不保证所有声音都经过它。以下路径可能绕开全局 Dolby DSP：

- 播放器选择 direct/offload、独占 USB、特定 AAudio/MMAP 或厂商直通输出；
- USB DAC 走独立 ALSA handler，而不是平台 DSP playback handler；
- 通话、语音通信、低延迟游戏或受保护内容使用不同 audio session；
- 应用内部解码器、AudioTrack 参数或“音频输出顺序”选择了绕行路径；
- 蓝牙绝对音量、耳机内部 EQ 或系统其他音效叠加，掩盖了曲线差异。

验证方式不是只看 DapTune 显示“已应用”，而是使用一条非常明显但安全的测试曲线，在同一播放器、同一
音量和同一输出上 A/B 对照。某播放器无变化而系统 Bilibili 等其他播放器有变化，通常是播放器输出路径
问题；先切换该播放器的解码器、AudioTrack/offload 或输出优先级，再判断 DAP 写入失败。

## 自动切换与通知

自动切换使用 Android 前台服务和事件监听，不轮询、不持有唤醒锁。Android 13+ 拒绝通知权限时，AOSP
仍允许应用启动前台服务，但通知不会出现在通知抽屉；系统会在“活动应用”任务管理界面显示相应条目。
某些厂商系统仍会因省电、自启动或后台管理策略杀死服务，DapTune 无法保证绕过这些策略。

Android 12+ 的“附近的设备”权限是进入应用的前置条件。DapTune 只接受能由系统已配对设备清单唯一验证的
完整蓝牙地址作为持久身份；匿名地址、显示名和 MediaRouter 包装 ID 均不会写入设备历史。权限恢复后会
立即重新解析当前路由。

“设备重启后恢复自动切换”只在自动切换已经开启时响应 `BOOT_COMPLETED`。它不会替用户开启自动切换，
也不会在用户明确停止服务后偷偷恢复。

## 系统更新

系统更新可能改变 UUID、profile 命令、回读语义、权限策略或路由实现。更新后应：

1. 暂时关闭自动切换；
2. 确认系统 Dolby 页面本身工作；
3. 用平直和明显测试曲线手动验证；
4. 查看 DapTune 日志中的 descriptor、写入和验证结果；
5. 确认后再恢复各设备规则。

不要把私有厂商 APK、完整 framework 文件、设备标识或未脱敏日志提交到公开 Issue。
