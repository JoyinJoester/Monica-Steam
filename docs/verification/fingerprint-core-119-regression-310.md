# 指纹可用性回归与认证弹窗兼容修复（2026-11-10）

最新结果：收到恢复构建授权后，完成 Debug 主/测试包、17 项定向 JVM 回归和压缩 Release；已有 API 35 模拟器的隔离用户中，实际系统指纹认证、拒绝/取消、设置页返回刷新和 Release 主密码备用路径通过。另发现并修正新版 Biometric 激活原有弹窗图标定制后触发的系统权限异常。没有用户手机、物理传感器或 ARM64 实测；下面先保留前期记录，最后列出本次验证及失败。

## 前期记录（当时尚未允许构建）

用户反馈升级后“主密码与锁定”中的指纹开关仍显示开启，但因“未录入生物信息”而禁用。本轮按用户要求不运行 Gradle、Kotlin 编译、单元/设备测试或虚拟机。仅检查源码、已解析依赖的字节码及 Google Maven 发布物，并准备修正。没有新的 APK，不宣称手机已恢复。

## 已确认原因

Steam 实际缓存的 `androidx.core:core:1.19.1` AAR SHA-256 为 `08108ea780205bd54603088a62a6dd5378f63bd25542674be24cd4cdb8191a09`。使用 `javap -c -p` 读取其 classes.jar，确认 `androidx.core.hardware.fingerprint.FingerprintManagerCompat`：

- `hasEnrolledFingerprints()` 只含 `iconst_0; ireturn`。
- `isHardwareDetected()` 只含 `iconst_0; ireturn`。
- 两个 `authenticate()` 重载直接返回。

早前 compileSdk 37 迁移将平台 FingerprintManager 调用换为这个 AndroidX 包装类，未验证新 Core 中的具体实现。两份应用 helper 在调用现代 BiometricManager 之前先检查上述两个恒假方法，必然将指纹设备拒绝并映射成 NONE_ENROLLED。这是升级引入的确定性缺陷；截图与此路径一致，但截图不证明手机录入信息丢失。本次没有读取或删除手机指纹、密钥或应用数据。

旧 `androidx.biometric:biometric:1.2.0-alpha05` 自身也在旧系统的检测及认证分支引用 Core 的这个类，所以仅移除应用预检查仍不能覆盖 Android 8–10。

## 源码处理

1. 两份 helper 共用 `fingerprintBiometricStatus(context)`。保留 `FEATURE_FINGERPRINT` 硬件门控，使用 `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` 读取系统的录入和就绪状态；只有 SUCCESS 开放入口，错误/未知仍拒绝。实际 BiometricPrompt 仍请求 BIOMETRIC_STRONG，原密码备用路径与认证成功回调保持原样。
2. 公共现代 API 判断的是符合要求的强生物认证，不能逐项查询“仅指纹是否录入”。系统可能选择已录入且合格的强认证方式；硬件标志不等于指定认证方式。这一语义明确记录，不声称仍使用已失效的指纹专用录入查询，也不调用新库的 internal API。
3. 将 Biometric 更新到 Google Maven 当前公布的 `1.4.0-beta01`（仍是预发布版本）。其官方源码/字节码使用自有 `androidx.biometric.internal.FingerprintManagerCompat` 处理旧 Android，不再借用 Core 的空实现。API 30 以上由平台 BiometricManager 处理。
4. 设置页保留状态码，在 LifecycleResumeEffect 中重新读取，避免离开系统设置再返回后继续缓存旧结果。没有改页面布局、权限、数据库、加密或已保存的指纹开关。

## 静态证据与限制

- Google Maven 元数据、AAR、sources.jar、POM/module 元数据及字节码摘录保存在 `.codex-tasks/20261109-fingerprint-regression/raw/`。只下载到本轮证据目录，没有通过 Gradle 修改依赖缓存或执行构建。
- 新 Biometric AAR 的 minCompileSdk 为 36、minor 为 1，minAGP 为 8.9.1；项目当前 compileSdk 37 / AGP 9.5.1 满足这些元数据要求。这不替代实际依赖解析与编译。
- 对 BiometricManager、BiometricPrompt、PromptInfo.Builder、AuthenticationCallback、AuthenticationResult 的 public 签名做旧/新比对，没有删除的原有公开方法。未断言整库二进制和行为完全兼容。
- 新库的 AAR SHA-256：`b83737061ed6dcb1f0f89fd5c6c5d6e098f68bd6c0af88a9bf26185e6ea3ff35`；sources.jar：`65cc57f5b9395378567e4ca0c04ed0fb603fcee709e37c979f845cbb13d24de7`。
- 先前升级试验未覆盖已录入指纹的真实设备，此遗漏已同步给正在进行 Monica Android 升级的会话，避免复制旧包装类替换。

后续构建资源允许时，需要验证：已录入强指纹、未录入、仅弱认证、临时不可用与未知结果；系统录入后返回设置页；真实提示框成功/取消/失败及密码备用路径；至少 API 26–29 旧系统回退和用户 API 36 手机，最后核对压缩 Release。当前全部为待执行，不计作通过。

## 2026-11-10 接管核对与待运行回归

接管时逐项核对上次 `result.json` 中六个源码/配置/说明文件的 SHA-256，全部一致。聊天筛选已使用包含 UNREAD 的单一枚举状态，动效修复及既有验证记录仍在；本次没有重新运行聊天设备测试，也没有以旧测试结果替代当前指纹验证。

新增 `app/src/test/java/takagi/ru/monica/utils/FingerprintBiometricAvailabilityTest.java`，测试依赖增加 Robolectric 4.16.1，仅作用于 JVM 测试。复用 Monica Android 的框架 shadow 夹具模式，调用 Steam 当前的共享检测函数和两份实际 helper，不模拟 AndroidX BiometricManager 的返回值。七个测试方法在 API 26/28 上展开为八个场景：

- 两个系统的已录入指纹场景，同时断言平台硬件/录入为真、旧 Core 包装类仍为假，防止夹具自己未录入却误计修复成功。
- API 26 有硬件未录入返回 NONE_ENROLLED，API 28 安全设备未录入返回 UNKNOWN；两份 helper 均须拒绝开放入口。
- 两个系统无硬件，以及有模拟传感器但未声明指纹 feature，均须拒绝开放入口。

本次仅准备测试源码、核对现有依赖字节码和检查补丁，未运行 Gradle、Kotlin/Java 编译、JVM 测试或设备测试，未启动虚拟机。八个场景均标记为 **待执行**。框架 shadow 也不能替代真实提示框成功/取消、设置返回刷新、物理传感器、API 36 手机或 R8 验证。

本地接管记录：`.codex-tasks/20261110-fingerprint-takeover/`。原 2026-11-09 结果和证据未覆盖。

## 2026-11-10 构建与设备验证

用户通过会话 `01a1c2ee-b98f-7210-9017-a93a4ad76f27` 明确解除构建等待；本轮读取原消息核验。Steam 使用独立 Gradle 缓存、单 worker、5 GiB 堆，未操作其他项目构建。用户随后要求继续避让主会话时，所有本轮构建和测试已经结束，仅继续文档与清理。

实际依赖图包含 Core 1.19.1 与 Biometric 1.4.0-beta01。最初 JVM 构建 283.337 秒、Debug/测试包 164.151 秒；权限修正后的 Debug/测试包及定向 JVM 联合构建 80.144 秒，8 项 API26/28 框架 shadow 指纹回归、9 项聊天列表回归全部通过，无跳过。最终 Release 构建 216.719 秒，实际执行 `minifyReleaseWithR8` 和资源裁剪；较早的 Release 473.943 秒成功但尚无权限修正，不能作为最终修复包。

所有本轮安装均为隔离包 `takagi.ru.monica.steamapp.fingerprinttest`，测试专用 Debug 证书、x86_64、禁止联网的 manifest 覆盖；Release 保留正常压缩且不可调试，没有添加用于测试的生产保留规则。此包不是用户正式签名发行包；未复制到交付目录或发布。

### 第二个实际缺陷

首个真实系统提示框测试 `auth-success` 抛出 `SecurityException: Must have SET_BIOMETRIC_DIALOG_ADVANCED permission`。已有 `AppLauncherIconManager.applyBiometricPromptBranding` 用反射设置 logo；新 Biometric 开始支持这些 setter，而 Android 在后续 `authenticate()` 才检查特权权限，原 setter 外的 `runCatching` 无法捕获。

新增权限门控：没有该权限时不设置自定义 logo，交由系统显示应用身份；不申请特权权限，不弱化 `BIOMETRIC_STRONG`。同一 API35 系统上的两份实际 helper，修正后均成功完成提示框认证。

### 设备范围与结果

公共 API32 启动后发现既有用户 0/10/11/12/14，guest 和普通用户都达到上限；临时修改上限被系统拒绝，全部原用户保留。停止本任务启动的 API32，复用既有 `Pixel_Fold_API_35`（x86_64，16 KiB 页），没有新建 AVD。仅创建 `Monica_Steam_Fingerprint_20261110`/user10，原 user0/11 保留。

Debug 分次共 11 次成功调用、6 个不同测试方法，分布于权限修正前后的两个构建，并非一次整批全绿：

- 系统未录入/已录入状态各一次；两份 helper 与真实系统状态一致。
- 未录入/已录入设置页各一次；原开启偏好保留，可用性正确控制开关。
- 两份 helper 各完成一次真实系统提示框成功、取消和未知指纹不放行，共六次。
- `enrollment-resume04` 完成真实系统录入后返回，确认仍为原 Activity、状态恢复 RESUMED、开关恢复可用且原偏好保留，runner 127.081 秒。

压缩 Release 覆盖安装后，通过实际 MonicaSteamActivity 操作创建合成主密码，进入主密码与锁定设置：关闭指纹再开启，未知指纹显示“无法识别”，取消后仍为禁用；正确指纹后变为启用。关闭“仅锁定令牌页面”并结束进程，下一 PID17293 出现系统指纹提示框，错误指纹及取消仍留在密码锁定页，输入合成主密码后进入主界面。再结束进程，PID17570 通过正确指纹进入主界面。未使用反射或测试回调替代 Release 的实际解锁路径；结果由 UI 层级、系统提示框状态与进程号记录。

### 保留的失败与限制

- API35 在应用安装前因 AudioService 空指针导致 system_server 重启，首次安装中断；随后系统恢复、隔离安装成功。另有系统 Google/Chrome 进程崩溃，不能称整台模拟器无 crash。
- `auth-success` 是真实生产权限异常，修后同路径通过。
- `enrollment-resume` 在指纹完成后继续停在人脸设置流程，150 秒超时；`enrollment-resume02` 在凭据重置后用户未解锁时启动 instrumentation，CE 存储拒绝读取；主机驱动补了当前用户及 RUNNING_UNLOCKED 前置检查。`enrollment-resume03` 在前一运行尚未结束时被后续 instrumentation 中断。修正驱动操作后 `enrollment-resume04` 通过，未改生产逻辑或放宽断言，前三次原日志保留。
- Release 首次输入较长合成密码时主机输入出现字符顺序变化，确认匹配失败；改用明确核对的六位合成输入完成设置。未计作密码设置首轮成功，也未改密码实现。
- 原生截屏输出含多显示器警告前缀，原文件保留、另生成去前缀预览；预览为黑屏，**不算视觉验收**。功能结论来自 runner 和实际 Release UI 层级。
- API26/28 仅 Robolectric 框架 shadow，未执行旧系统物理提示框；未测 API29 设备、仅弱传感器、真实临时硬件故障、用户 API36 手机、OEM/TEE/ARM64。没有对真实用户指纹、密钥、应用数据进行操作，也不声称所有机型已恢复。

### 清理与证据

恢复 API35 user0，停止并删除本任务 user10（第一次立即删除失败，确认停止后重试成功），卸载两个隔离包并停止本任务启动的 API35。原 user0/11、公共 API32 原用户、两台 AVD 配置/注册/数据盘均保留；未触碰 MuMu、其他 Java 进程或公共 ADB 服务。后续无新构建/设备测试、提交、推送或发布。

本目录同名子目录保留构建回执、JVM XML、成功与失败设备日志、Release UI 断言摘要及最终哈希清单；完整原始记录在上述本地任务目录。截图只在忽略的 `docs/design/fingerprint-20261110/`。
