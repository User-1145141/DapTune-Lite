# 架构

DapTune 采用多模块、单向依赖和本地优先设计。EQ 数学、路由选择策略和 DAP 协议序列化可以在没有
Android 设备的 JVM 测试中验证；厂商接口与 UI 不进入领域模型。

## 模块边界

```text
app
├── feature:editor ───────┐
├── feature:profiles ─────┼──> domain ───> core:model
├── feature:automation ───┘       │
├── data ─────────────────────────┤
├── platform:dap ─────────────────┤
├── platform:routing ─────────────┤
├── core:eq ──────────────────────┘
└── core:designsystem
```

- `core:model`：不可变曲线、配置、路由、DAP 结果和日志模型；
- `core:eq`：严格文件解析、对数频率插值、biquad 采样、Q4 量化和曲线变换；
- `domain`：仓储及平台接口、应用和选择配置的用例、日志语义；
- `data`：Room 配置、设备规则、应用状态和日志，DataStore 负责轻量设置；
- `platform:dap`：descriptor 分类、私有 `AudioEffect` 桥、参数打包、写入事务和回滚；
- `platform:routing`：Android 音频设备事件、路由稳定和隐私安全的设备键；
- `feature:*`：Material 3 UI、ViewModel 和前台服务；
- `app`：Hilt 应用入口、Compose 导航和顶层状态组合。

依赖只朝领域和纯模型方向流动。`domain` 不依赖 Room、Compose、`AudioEffect` 或 Android 路由实现。

## 曲线数据流

```text
文件、AutoEq 或滑杆
    │
    v
20 个 Double dB ──曲线处理/超限策略──> 20 个 signed Q4 Int
    │                                      │
    │                                      ├──> Room 配置
    │                                      ├──> DapTune JSON
    │                                      └──> DAP 参数 110 payload
    v
预览图使用同一不可变 EqCurve
```

领域里的 `EqCurve` 正好包含 20 个 Q4 整数，并只施加 `+160 Q4` 正增益上限。负值不设产品下限。
显示、持久化和 DAP 写入共享这一对象，避免 UI 曲线与实际 payload 使用不同舍入结果。

原生 JSON 直接保存 Q4；其他格式先转换为 dB 响应，再统一量化。详细契约见
[DapTune JSON v1](daptune-json-format.md)和[导入格式](import-formats.md)。

## DAP 写入事务

一次应用曲线在进程互斥锁内串行执行：

1. 查找 implementation descriptor，建立全局 session `AudioEffect`；
2. 检查控制权、effect/DAP 开关及 profile 数量；
3. 根据 descriptor type 判定真实回读或 setter-only；
4. 向所有 profile 写参数 110；
5. 可回读实现逐数组比较，失败时写回全部原数组；
6. 发送保存命令；setter-only 实现复核关键状态；
7. 在 `finally` 释放 effect，并把验证等级写入应用状态和日志。

应用不接管 PCM，不创建音频处理线程，不修改 DAP 开关，也不向其他 Dolby 参数写值。完整理由见
[ADR 0002](adr/0002-dap-write-transaction.md)。

## 路由身份与自动切换

内置扬声器和通用有线耳机使用固定键。蓝牙、LE Audio、USB、HDMI 等设备使用
`SHA-256(routeType + rawIdentity)` 的前 12 字节十六进制摘要作为稳定键；原始蓝牙地址不写数据库。
设备显示名会保存在本地，便于用户识别历史设备和日志。

前台服务同步解析一次当前路由，再收集事件流。路由变化后按以下优先级选择配置：

1. 精确设备绑定；
2. 设备类型或默认规则；
3. 内置平直配置。

同一服务生命周期内，相同路由和相同曲线不会重复写入。系统 Dolby 状态恢复时强制重新应用。恢复机制、
超时和生命周期见 [ADR 0003](adr/0003-event-driven-automation.md)。

## 持久化与隐私

Room 保存配置、哈希设备键、规则、最近应用快照和操作日志；DataStore 保存自动切换设置。AutoEq 仓储
只访问固定的官方 GitHub Raw HTTPS 路径，缓存推荐索引并在本地完成搜索；下载文本进入现有严格解析器，
不执行远程内容。应用没有麦克风或媒体读取权限。Android 系统备份可按用户设置备份数据库与 DataStore，详见
[隐私说明](../PRIVACY.md)。

## 架构决策记录

- [ADR 0001：分层、多模块、单向依赖](adr/0001-layered-architecture.md)
- [ADR 0002：按 DAP 回读能力选择写入事务](adr/0002-dap-write-transaction.md)
- [ADR 0003：自动切换采用事件驱动前台服务](adr/0003-event-driven-automation.md)
