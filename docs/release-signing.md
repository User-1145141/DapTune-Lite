# 发布签名与产物验证

## 原则

公开 Release 必须使用项目专用、不可公开的 Android keystore。禁止使用默认 debug keystore，也禁止把
keystore、密码、Base64 或生成的签名属性文件提交到仓库。首个正式 Release 发布后，应在本文记录公开
证书 SHA-256；在证书摘要公布前，不应声称任何 APK 是可验证的官方长期发布证书产物。

丢失 keystore 或 key password 将无法为 `com.weich.daptune` 发布可覆盖安装的更新。至少保留两份加密
离线备份，并把证书摘要与私钥分开保存。

## 本地环境变量

`app/build.gradle.kts` 只在以下四个变量全部存在时为 `release` 配置签名：

- `DAPTUNE_KEYSTORE_PATH`
- `DAPTUNE_KEYSTORE_PASSWORD`
- `DAPTUNE_KEY_ALIAS`
- `DAPTUNE_KEY_PASSWORD`

```powershell
$env:DAPTUNE_KEYSTORE_PATH='C:\secure\daptune-release.jks'
$env:DAPTUNE_KEYSTORE_PASSWORD='...'
$env:DAPTUNE_KEY_ALIAS='daptune'
$env:DAPTUNE_KEY_PASSWORD='...'
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease :app:assembleRelease
```

缺少任一变量时，Gradle 仍可构建未签名 Release 供 CI 验证，但该 APK 不能安装或发布。

## GitHub Secrets

标签发布工作流需要：

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

PowerShell 生成 keystore 的 Base64：

```powershell
[Convert]::ToBase64String(
  [IO.File]::ReadAllBytes('C:\secure\daptune-release.jks')
) | Set-Clipboard
```

GitHub Actions 只在临时 Runner 目录恢复 keystore。构建完成后用 Android SDK `apksigner` 验证签名，
把 APK 重命名为 `DapTune-vX.Y.Z.apk`，生成对应 SHA-256，再创建 GitHub Release。

## 标签发布

1. 更新 `versionCode`、`versionName` 和 `CHANGELOG.md`；
2. 在 `main` 通过 CI；
3. 创建与 `versionName` 完全一致的标签，例如 `v0.1.0`；
4. 推送标签；
5. Release workflow 运行测试、Lint、R8 构建、`apksigner verify` 和摘要生成；
6. 从 Release 重新下载产物，在干净设备验证安装和 DAP 能力检查。

```powershell
git tag -s v0.1.0 -m "DapTune v0.1.0"
git push origin v0.1.0
```

工作流会校验标签与 `app/build.gradle.kts` 中的 `versionName` 完全一致。不要重写已发布标签或替换同名
二进制；发现错误应增加版本号并发布新版本。

## 验证 APK

```powershell
apksigner verify --verbose --print-certs .\DapTune-vX.Y.Z.apk
Get-FileHash .\DapTune-vX.Y.Z.apk -Algorithm SHA256
```

证书摘要确认发布身份，`.sha256` 确认下载内容完整性，两者不能互相替代。正式证书确定后，把
`Signer #1 certificate SHA-256 digest` 固定记录在本节和 Release 说明中。
