# Monica Steam 会话列表滑动验证

日期：2026-11-02。继续 WorkBuddy 接管任务，按用户指定参考 Monica Pass 列表的左右滑动效果。保留此前 Dock、会话菜单、群头像和其他未提交改动。

## 行为与实现

会话行右滑切换选择，支持选择多项和取消已选项；左滑打开现有删除确认框，默认保留会话内容，只有用户在确认框中明确选择才沿原有删除逻辑执行。多选期间禁用左滑删除，横向拖动仍取消原生行的点击，避免意外改变选择。

参考 Monica Pass 的 SwipeActions.kt，新增 SteamConversationSwipeActions.kt：头像、选中标记和文字作为一整行跟手移动，选择使用 primaryContainer，删除使用 errorContainer，操作图标与文字渐显并带视差。触发阈值为行宽 20%，最大位移 65%，超过 50% 后增加阻力；松手使用 MediumBouncy / StiffnessMedium 弹簧复位。短滑、系统取消和多指中断不执行操作，关闭系统动画时直接复位。

横向手势在 Initial pointer pass 锁定后取消 AndroidView 的原生点击；纵向滚动不被随后横向偏移抢占。SteamConversationRow.kt 将整个原生行放入新容器；SteamConversationList.kt 支持右滑取消已选项；TelegramNativeSocial.kt 直接创建 DialogCell，移除会话行对旧 SwipeSelectLayout 的包裹。旧 Java 类保留，未扩大修改范围。操作文案复用现有五种语言资源。

## 测试

- 最终相关 JVM 集合 128 项、25 个 suite，0 failure、0 error、0 skipped。范围为 friends.chat.ui.*、friends.chat.data.*、friends.groupchat.*，不是全项目测试。最终测试主包、测试 APK 和 JVM 构建 8 分 6 秒成功。
- 公共 Monica_Issue136_API_32（Android 32、x86_64）最终同一次设备执行 13/13 通过，runner 45.501 秒，外部驱动 52.715 秒。其中滑动 5 个不同方法，既有群头像 8 个不同方法。
- 滑动覆盖左滑删除确认及取消保留、多项选择和右滑取消、多选期间左滑不删除也不改变选择、短滑与 ACTION_CANCEL 无动作、深浅主题两方向实际行位移与可见标签，以及 320dp / 1.5 字号下的纵向滚动、点击和长按。
- 群头像回归覆盖渲染器圆形遮罩与中文留白、1–4 成员、多种尺寸和照片分区、透明底、Compose 刷新，以及原生收件箱／归档入口。既有测试读取窗口的修正不会改变生产头像实现。
- 另将 animator_duration_scale 临时设为 0，重复短滑／取消方法，1/1 通过，runner 7.571 秒。原值为 null，finally 删除该 setting 并确认恢复 null；这是一个已执行方法的补验，不增加不同方法数量。

设备包使用独立 applicationId takagi.ru.monica.steam.avatartest，未覆盖真实 Steam 应用。测试由 instrumentation 内部启动合成 fixture，渲染生产会话组件，未登录真实 Steam 账号。测试 init 脚本仅对隔离包设 x86_64、debuggable=true、关闭 R8／资源裁剪，未修改正常生产配置。

四张最终方向截图、删除确认和窄屏滚动截图均已查看。选择截图记录松手后的弹簧回弹帧，不作为完全静止布局证据；窄屏图记录限定宽高的列表滚动夹具，不代表整个应用小屏布局验收。最终滑动展示从四张方向截图裁剪，没有重绘界面。

## 正常构建

正常 ARM 构建不使用测试 init 脚本，命令为 :app:assembleDebug --no-daemon --max-workers=1，命令级 8GB 堆、Kotlin in-process、dailySeq=03。本项目正常 Debug 本来启用 R8／资源裁剪、debuggable=false，并使用 Android Debug 签名。

正常构建 9 分 40 秒成功，88 个任务中 23 执行、65 up-to-date，包含 minifyDebugWithR8 和资源裁剪。output-metadata.json 与两份 APK 的 aapt badging 均确认包名 takagi.ru.monica.steamapp、版本 1.0.310 / code18、minSdk26 / targetSdk34、无 application-debuggable，输出仅为 ARM64 和 ARMv7。apksigner verify 两份均成功、v2 签名有效，证书 SHA-256 均为 6e1c4c795d062b74c28c3d4fa13833a71deef19f6501e917ee6b3df60797a4b0，与上一轮正常 Debug 签名一致。

正常构建、APK 包名／ABI／版本／签名原始工具输出及 SHA-256 记录见同名前缀 production-build.txt、production-apk.txt 和 production-apk.json。正常 ARM APK 没有在 x86_64 AVD 上执行，上述设备结果来自隔离 x86_64 包。

## 失败记录与局限

- 有效修前负向对照 0/1：左滑后等不到删除确认。baseline-build.log 构建成功，8 分 14 秒。
- 首轮修复构建及 JVM 成功，11 分 55 秒；设备 8/13。随后诊断截图显示删除确认实际上已打开，UiAutomation 却仍读取下方列表窗口。补齐 FLAG_RETRIEVE_INTERACTIVE_WINDOWS 并遍历全部窗口，结束时恢复原 flags；测试手势的断言部分 finally 发送 UP／CANCEL。诊断与窗口修订构建分别 1 分 13 秒、1 分 10 秒。
- 窗口修订后的设备批次 11/13，runner 38.354 秒。剩余真实生产问题为多选时禁用左滑将横拖交还给原生行，被识别为点击并改变选择；已修为横向锁定后消费事件、位移限制到允许方向。另一个群头像归档节点读取失败，补齐相同的多窗口读取与 flags 恢复。最终重新构建并完整 13/13 通过。
- 所有原始失败日志与诊断图保留，没有覆盖或计为通过。最终截图目录中继承的 failure-1558073.png 是前序失败产物，不算最终通过截图。
- 未做 ARM 真机、OEM、真实 Steam 网络账号流程、TalkBack、所有语言布局或完整应用小屏验收。构建成功不等于解决全部既有弃用／opt-in／R8 metadata 警告。

原始日志位于本地 .codex-tasks/20261102-inbox-swipe/。本目录同名前缀的修前／最终／关闭动画设备结果、JVM、构建和源码 SHA-256 副本仅规范 CR 与尾空白，未改变结果。source-audit.json 检查本轮四个生产文件及两个设备测试的空白和冲突标记，包含未跟踪文件。

## 本地设计与清理

本地 Canvas project=steam-inbox-swipe-20261102，已导入并实际查看。canvas.json、完整 #docz= 链接、合成截图和展示图只留在忽略目录 docs/design/inbox-swipe-20261102/，不入库。

结束时卸载本轮独立测试主包与 .test 包，停止本轮启动的公共 AVD；用户 0/10/11/12、注册文件及 userdata 数据盘保留，未清理真实 Steam 应用数据。未创建新 AVD、修改全局 Gradle 配置或结束其他 Java 进程。

未提交、推送、发布、修改版本或发行说明，也未复制安装包到交付目录。此前 WorkBuddy 及其他已有改动保留。
