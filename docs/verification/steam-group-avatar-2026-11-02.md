# Monica Steam 群头像修复验证

日期：2026-11-02。接管 WorkBuddy 分享记录中最后未完成的群头像问题，保留此前聊天、表情面板、筛选、未读入口及 Dock 改动。

## 修复

群头像在同一位图中完成成员照片或首字母拼接，然后用抗锯齿圆形遮罩输出。透明照片使用群头像底色，圆外保持透明。原生 Telegram 会话行和 Compose 群详情共用渲染器，避免两个绘制入口出现不同的遮罩和文字位置。

中文按实际字形边界居中，位置使用圆形分区质心，四象限额外保留留白，避免文字贴圆边或分割线。首字母按 Unicode code point 提取，使用 Locale.ROOT 大写。最多显示四个去重成员；加载照片前立即显示当前成员首字母，成员改名或重新绑定时更新占位图。

生产文件：SteamGroupAvatarRenderer.kt、SteamGroupAvatarImage.kt、SteamGroupMemberAvatarGrid.kt。设备测试：SteamGroupAvatarRendererTest.kt、SteamGroupAvatarUiTest.kt。五个文件的 SHA-256 和空白检查结果见同名前缀 source-audit.json。

## 测试

- 128 项 JVM 测试，25 个 suite，0 failure、0 error、0 skipped。范围为 friends.chat.ui.*、friends.chat.data.*、friends.groupchat.*；这是相关回归集合，不是全项目测试。
- 公共 Monica_Issue136_API_32（Android 32、x86_64）最终一次设备执行 8/8 通过。runner 24.567 秒，外部驱动约 34.391 秒。
- 位图测试覆盖 1–4 成员中文在 40/56/88/112/176px 的圆边与分割线留白、0–6 成员的圆内不漏底与圆外透明、四张宽高不同照片的分区覆盖，以及透明照片底色。
- Compose 测试覆盖中文 40/56/88dp、深浅主题、1.5 字号、仅改成员显示名的刷新、超过四个成员的显示上限。
- 原生 Telegram 会话行检查头像自身 56dp 槽位、10dp 偏移和透明角落；实际切换归档并检查 Dock、未读及归档语义入口存在非空屏幕边界。
- 最终六张原生截图逐张查看：深浅主题收件箱、深浅主题归档列表、深浅主题大字号中文矩阵。Dock 的 alpha-zero 输入层可能被 UiAutomation 标记为不可见，所以自动断言仅确认语义入口与边界，视觉由截图复核；未宣称 TalkBack 验收。

设备包使用独立 applicationId takagi.ru.monica.steam.avatartest，未覆盖真实 Steam 应用。截图来自合成成员和生产 UI 组件的调试 fixture，未登录真实 Steam 账号。系统现有显示为 1080×2100 / 320dpi，未调整。

## 构建

测试构建使用任务本地 init 脚本，解除隔离测试包压缩以支持内部 Kotlin 方法调用；生产构建配置没有因此修改。最终设备测试沿用成功构建的主测试 APK，仅重建测试 APK。JVM 与主程序/测试包的成功构建记录为 fixed-build-01.log（7 分 48 秒），后两次测试修订构建分别为 6 分 12 秒和 54 秒。

正常构建未使用测试 init 脚本：:app:assembleDebug --no-daemon --max-workers=1，命令级 8GB 堆、Kotlin in-process、dailySeq=02。8 分 4 秒成功，88 个任务中 23 执行、65 up-to-date；包含 minifyDebugWithR8 和 shrinkDebugRes。该项目 Debug 配置本来就是 debuggable=false、启用 R8 与资源裁剪，使用 Android Debug 签名。

output-metadata.json 和两份 APK 的 aapt badging 均确认包名 takagi.ru.monica.steamapp、版本 1.0.310 / code18、minSdk26 / targetSdk34。仅生成正常 ARM64、ARMv7 两个 ABI 输出；apksigner verify 两份均成功、v2 签名有效，证书 SHA-256 相同：6e1c4c795d062b74c28c3d4fa13833a71deef19f6501e917ee6b3df60797a4b0。

| 正常输出 | APK SHA-256 |
| --- | --- |
| arm64-v8a / 26110218-02 | f17dad9377110c0b68e1a75db9b51ba56e6ddda914e5f4e847bf910c7f20cf44 |
| armeabi-v7a / 26110218-02 | 0ea781c40066de4fa881f391e76a9382ed1295c72072001a9d4b013333f7c4b1 |

包名、架构与签名工具原始输出见同名前缀 production-apk.txt；正常构建完整日志副本见 production-build.txt。正常 ARM APK 没有在 x86_64 AVD 上执行；上述 8 项设备测试运行的是解除压缩的隔离 x86_64 包。

## 失败与局限

- 首次 PowerShell 未引用含点的 -P 参数，Gradle 参数解析失败。
- 初次 3GB 堆编译 OOM；只对本次命令改用 8GB、单 worker、in-process 后成功，未改全局 Gradle 配置或结束其他 Java 进程。
- 正常构建保留了弃用/opt-in 警告及 R8 Kotlin metadata 兼容警告；构建成功不等于这些警告已解决。
- 初次压缩的隔离测试包发生四项 NoSuchMethodError，没有进入头像渲染断言；不能计为有效旧代码对照。随后只关闭隔离测试包压缩。
- 有效旧代码对照为 3/4，通过的三项保留；中文留白失败于 4 members / 40 px: ink touches circle at 31,5。
- 首轮修复设备测试 6/8：小字抗锯齿未计入黑像素，以及在 All 页面寻找已归档的群。调整测试覆盖抗锯齿并实际进入归档后，第二轮 7/8；剩余失败为 Dock 的 UiAutomation 可见标志。最终按语义入口边界加实际截图复核后 8/8。
- shell 启动 non-exported fixture 被系统拒绝，未放宽导出权限；最终由 instrumentation 内部正常启动。
- 公共 AVD 用户数已满，新建用户请求失败，没有实际创建用户。测试结束卸载独立测试主包与 .test 包，停止本轮启动的 AVD，保留用户 0/10/11/12、注册配置和数据盘。
- 未做 ARM 真机、OEM 字体、真实 Steam 网络照片加载、完整账号流程或 TalkBack 验收。纯色宽高照片断言验证分区覆盖，不能证明所有真实照片的主体裁切效果。

原始日志保留于本地 .codex-tasks/20261102-group-avatar/。本目录的 baseline-device.txt、final-device.txt 和 jvm-summary.json 副本仅整理 CR 与尾空白，未改变结果。其他失败原始日志未覆盖。

## 本地设计与交付状态

使用本地 M3E Canvas，project=steam-group-avatar-20261102；可编辑 canvas.json 与完整本地 #docz= 链接保存在忽略目录 docs/design/group-avatar-20261102/，已经实际载入查看。六张最终截图及展示裁剪也只留在该忽略目录，不入库。

未提交、推送、发布、修改版本或发行说明；未将安装包复制到交付目录。此前 WorkBuddy 及其他已有未提交改动保留。
