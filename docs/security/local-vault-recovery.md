# Steam 本地密钥恢复

2026-10-05：将 Monica Android 的主密码恢复能力接入 Monica Steam，保留 Steam 可不设置主密码和可选启动锁定的使用方式。

## 升级后的使用方式

已设置主密码的用户，升级后需在原系统密钥仍可用时用主密码解锁一次，并让应用保持解锁以完成本地旧数据转换。仅使用指纹或跳过启动验证不能代替首次主密码解锁。

此后如果 Android 系统密钥不可用，启动页会提供“使用主密码恢复”。恢复在本机完成，不需要云端；验证成功后重建可用的加密配置，原配置文件保留。再次打开应用仍遵循原来的锁定设置。

没有设置主密码时，Steam 仍可使用，但没有基于主密码的独立恢复能力。恢复材料不是数据库备份：数据库、本地文件和恢复材料均需保留；登记恢复材料前已经丢失的密钥、尚未转换的旧密文，不能凭主密码补救。不要清除数据或卸载后尝试恢复。

## 实现范围

- `LocalVaultRecovery` 使用 PBKDF2-HMAC-SHA256（600,000 次、随机盐）和 AES-GCM 保存独立的主密码恢复封装及加密配置快照。主密码变更同步轮换恢复凭据，旧主密码失效。
- `SecurePreferencesStore` 在打开存储前检查原配置和系统密钥；读取失败不会重建原密钥、清空文件或回退到明文。恢复写入独立新存储，校验后原子切换。
- `SteamSecureStorageGate` 在仓库、ViewModel 和账号服务初始化前处理安全存储失败；后台聊天服务等初始化等待存储可用。
- `SteamRecoveryMaintenance` 在认证后将 Steam 账号、令牌、备注、标签、分组、安全事件、游戏库及成就缓存、待发送任务、聊天缓存、会话配置和游戏活动文件转换为由主数据密钥保护的密文，并迁移 ITAD 凭据及共用数据库中的旧密文。
- 迁移读取回验后才写回，数据库使用原值比较更新，文件和偏好写入与正常写入共享锁；保留时间戳、无法解密的字段和未识别的 JSON 成员，允许中断后继续。
- 已设置主密码时，新写入数据使用主数据密钥，不再降级成仅依赖系统密钥的密文。已有主数据密钥不可用时不会生成替代密钥。
- 主密码保存失败会反馈失败；“跳过启动验证”不再绕过令牌及主密码设置页要求的认证。

## 验证记录

公共模拟器：`Monica_Issue136_API_32`，Android 32 / x86_64。密钥删除仅针对独立包 `takagi.ru.monica.steam.recoverytest`，使用无真实登录凭据的合成账号。

| 检查 | 结果 |
| --- | --- |
| 加密、恢复与迁移设备测试 | 16 项通过：错误密码、损坏封装、密码轮换、中断提交、重复丢失密钥、并发写入、旧版写入、缺失 MDK、Steam 字段及 outbox、无主密码、迁移续跑等 |
| 恢复页 Compose 交互 | 2 项通过：错误反馈、密码清理、大字体下操作可达、操作不自动执行 |
| 定向单元测试 | 62 项通过 |
| 完整单元测试 | 1775 项，20 失败、6 跳过；20 项失败均在修改前源码快照中复现，属于现有断言/模块布局/商店测试问题 |
| 真实启动恢复 | 建立旧格式账号→设置主密码及迁移→删除系统密钥→启动恢复页→输入主密码恢复→解锁后显示测试令牌，通过 |
| 数据及进程重启 | 恢复和强制结束进程重启后，账号密钥、备注、标签、分组、时间戳一致；原加密配置 XML 逐字节不变 |
| 构建 | Debug、设备测试包和 R8 Release 构建通过；保留 Steam 自身的版本配置 |

测试日志保存在本地 `.codex-temp/settings-recovery/`。完整套件失败列表为 `unit-failures.json`，修改前复现日志为 `baseline-tests.log`。Steam 在线商店网络不在本次验证范围内，模拟器原代理不可连接。

## 复现设备测试

在仓库根目录使用 Android Studio JBR 和项目 Gradle 缓存：

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --init-script scripts/recovery-test.gradle
```

`scripts/recovery-test.gradle` 将测试安装隔离为独立包和 UID，使用 x86_64、可调试且不混淆的 Debug 及测试专用 Compose Activity。不要用这个初始化脚本构建普通发行包。设备测试类为 `LocalVaultRecoveryInstrumentedTest` 和 `SecureStorageRecoveryUiTest`。

`SteamStartupRecoveryInstrumentedTest` 默认不执行密钥操作，必须显式传入 `recoveryStage`；按 `seed`、`lose`、`verify` 顺序执行。`seed` 要求独立测试安装没有主密码和账号，`lose` 仅允许 `.recoverytest` 包，两个阶段之间通过真实 Activity 输入测试主密码。`verify` 可在重启后重复运行。不应对真实安装执行这些破坏密钥的测试。

界面草图及原生截图见 [设置与恢复设计](../design/settings-recovery/README.md)。
