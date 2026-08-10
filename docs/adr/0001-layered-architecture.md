# ADR 0001：分层、多模块、单向依赖

- 状态：已采纳
- 日期：2026-08-10

## 决策

领域模型和 EQ 数学保持纯 Kotlin；`domain` 只声明仓储、平台边界和用例；Room、DataStore、DAP 与
Android 路由监听分别放在实现模块；各功能模块使用 Hilt 注入的 ViewModel 暴露不可变 UI 状态；
`app` 只负责组合与导航。

依赖只能从应用和功能层指向领域层，领域层不依赖 Android 实现。DAP 私有协议不会进入 UI 或数据层。

## 原因

EQ 转换和协议序列化需要在无设备环境下确定性测试。厂商系统升级风险集中在 `platform:dap`，路由
兼容性集中在 `platform:routing`，从而避免系统细节扩散到整个工程。
