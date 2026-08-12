# 安全策略

## 支持范围

| 版本 | 安全更新 |
|---|---|
| 最新公开 Release | 支持 |
| 当前 `main` | 尽力支持 |
| debug、optimized 和私人测试包 | 不提供发布保证 |
| 旧版或第三方重新签名包 | 不支持 |

## 私密报告漏洞

使用本仓库的
[GitHub Private Vulnerability Reporting](https://github.com/silverpoetry/DapTune/security/advisories/new)。
可能造成任意代码执行、签名材料泄露、未经授权的系统音效修改、持续音频服务故障或隐私数据暴露的问题，
不要先开公开 Issue。

报告应包含受影响版本、Android 和固件版本、最短复现步骤、实际影响和最小脱敏日志。不要上传厂商 APK、
完整 framework、设备分区、账户数据、设备序列号、蓝牙地址、IP、私钥、keystore 或真实音频内容。请在
公开细节前预留合理修复时间。

## 安全边界

- DapTune 只写已识别 DAP 的参数 110，不开启 Dolby，不写其他参数，不注入音频 PCM；
- effect 状态无效、没有控制权或 profile 数量异常时拒绝写入；
- 可回读实现逐 profile 验证，失败时恢复原曲线；setter-only 结果明确标记为未回读；
- 文件导入限制大小，原生 JSON 拒绝未知字段和未知版本；
- 网络仅访问代码中固定的 AutoEq 官方 GitHub Raw HTTPS 地址；不接受用户 URL，不执行下载内容；
- 没有麦克风、媒体读取、辅助功能或设备管理权限；
- 蓝牙等原始地址不写数据库。

私有 Android 和厂商接口仍可能随系统更新改变。任何“写入成功”都不能代替在目标输出路径上验证实际
音频行为。

## 安装与发布安全

- 只安装本仓库 Releases 的 APK，并同时检查发布证书和 SHA-256；
- debug 或 `optimized` 构建由 Android debug 证书签名，不能冒充官方 Release；
- keystore、密码和 Base64 只能保存在受保护的离线备份及 GitHub Actions Secrets；
- 不要关闭 Android 签名验证来覆盖安装来源不明的 APK；
- 系统更新后先关闭自动切换并重新验证兼容性。

## Root 实验工具

`tools/magisk` 下的内容不是安装 DapTune 的前提。`daptune-usb-dsp-offload` 只针对已验证的 Xiaomi
`turner` 属性路线；刷入错误产品可能导致音频不可用。使用前必须确认代号、保留 recovery/ADB 回滚路径，
并理解属性值变化不等于 DSP 路由已经工作。
