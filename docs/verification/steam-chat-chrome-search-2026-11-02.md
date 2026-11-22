# Monica Steam 渐变模糊、会话内搜索与共享内容

日期：2026-11-02。继续引用聊天中最后的用户要求：系统导航条下方也显示模糊；顶部向下、底部向上逐渐变清晰；从聊天右上角菜单进入会话内搜索；在好友详情底部浏览媒体。保留既有长按定位、点击进入、左右滑动、群头像及 WorkBuddy 改动。

## 实现

聊天容器绘制到窗口底部。安全距离放在底部操作区内部，消息和模糊源保留在其后方；键盘显示时底部操作区跟随 IME，不重复叠加导航条距离。顶、底模糊层额外延伸至内容区 24dp，边缘透明，没有矩形硬边。

TelegramEdgeBlurView 在 Android 12/API31 以上使用 RenderNode/RenderEffect，捕获独立消息列表，混合 0、约 4.67、9.33、14dp 的 Gaussian 半径并沿高度连续插值，透明度和主题底色同向减弱。捕获边缘留出最大半径三倍的采样余量，避免裁切边缘拖色。顶部最强、向下减弱；底部最强、向上减弱。悬浮胶囊沿用原 TelegramGlassView；旧系统或软件画布使用渐变着色，不宣称执行平台模糊。

私聊与群聊右上角菜单增加“搜索消息”。原消息列表保持组成，顶部替换为搜索框和分类筛选，下方替换为匹配计数与前后定位按钮；可按发送者与时间缩小范围，通过底部“以列表显示”弹层选择结果。关闭搜索恢复输入栏，使用独立 saveable 状态保留未发送的文字、游戏分享和面板状态。分类只在搜索打开时执行；原独立搜索页已退出生产导航，旧文件只保留给既有诊断夹具。

好友／群详情底部在同一 LazyColumn 中显示共享内容分类、三列媒体／贴纸网格与文件／链接／邀请条目。点击共享内容回到原消息位置，私聊详情移除重复的成员列表。无共享内容时明确显示空状态。媒体图片继续使用现有允许来源和图片缓存，无法读取时显示占位；未增加权限或放宽远端来源策略。

## 验证记录

设备仅使用公共 API32/x86_64 AVD 与独立包 takagi.ru.monica.steam.avatartest，不覆盖真实 Steam 应用。夹具中的消息、头像与媒体缓存均为合成数据，不登录 Steam 账号。

最终主程序／测试包与相关 JVM 构建 10 分 1 秒成功，183 项 JVM、33 个 suite，0 failure、0 error、0 skipped，覆盖聊天 UI／数据／详情、Telegram、群聊和底部安全区。首次构建 10 分 44 秒成功；后续只调整设备测试定位与截图采集，测试包补建分别 56 秒、1 分 2 秒成功。生产源码在最终主程序构建后未再修改。

最终新设备方法 4/4，通过时间 runner 97.887 秒、外部驱动 106.623 秒。覆盖私聊／群聊深浅主题的原消息 RecyclerView 实例保留、关键词／分类／发送者／时间筛选、无匹配结果、前后定位和结果弹层、实际关闭搜索、原生输入栏显示及草稿恢复；实际点击媒体和链接后返回会话目标，未发送消息。底部模糊 View 在键盘关闭后确实延伸到窗口底边（误差不超过 3px）。条纹探针在高度 15%／50%／85% 的顶部对比度为 53／178／249，底部为 249／175／53，确认两侧向内容方向逐渐变清晰；这不是 ARM 真机性能或每种半径的单独测量。

同一最终主 APK 的既有回归 25/25，runner 89.917 秒、外部驱动 93.771 秒：TelegramChatUiTest 6、SteamInboxInteractionUiTest 6、SteamInboxSwipeUiTest 5、SteamGroupAvatarUiTest 4、SteamGroupAvatarRendererTest 4。合计 29 个不同设备方法通过，不重复累计深浅主题循环或此前批次。

保留前两批失败：first-device.txt 为 3/4，群资料分类在屏幕外；第二批 final-device.txt 仍为 3/4，performScrollTo 选择了内层横向分类条，未滚动外层详情。最终测试改为在外层 LazyColumn 按 shared-history-header 键定位，仍断言内容实际可见并点击真实条目；未放宽可见性或返回消息断言。第二批失败在同名前缀 second-device.txt 保存，最终成功来自 complete-device.txt，两者没有混用。截图采集等待空闲及 180ms，关闭搜索后先断言搜索输入消失、原生输入栏可见。初期搜索分类条透明导致底层消息干扰文字，最终生产实现已为分类条补充主题玻璃底色。

已查看最终私聊搜索、原生输入栏恢复以及深浅主题好友详情媒体截图。群详情语义交互通过，但本轮 profile-group-dark-media.png／profile-group-light-media.png 捕获了私聊会话帧，不计群详情视觉证据；较早同名无主题截图也不混入最终展示。全部截图仅使用合成数据，精选副本位于忽略目录 docs/design/chat-chrome-search-20261102/。

前序点击／长按修复在本轮修改前补验：15/15 设备方法通过，runner 81.737 秒，外部驱动 93.765 秒；另关闭系统动画补验 2/2，runner 9.195 秒，setting 从 null 恢复 null。15 项为交互 6、滑动 5、群头像界面 4；群头像 renderer 的另 4 项不在该批名单中，不能把本批称为 19 项。计时夹具 firstComposition=26ms、settled=119ms；原生点击 8 次 18/10/4/9/11/16/36/64ms，mainReads=0，迟到偏好读取未恢复旧会话。原始记录位于 .codex-tasks/20261102-inbox-interaction/，前序失败全部保留。

## 范围

搜索和详情共享内容均基于当前已加载／缓存的消息，未增加服务端全历史检索。分类和草稿验证使用合成记录，不代表真实账号所有富内容或异常网络情况。

正常构建 8 分 31 秒成功，仍为 1.0.310/code18，dailySeq=05。不使用测试 init 脚本，执行 :app:assembleDebug --no-daemon --max-workers=1，命令级 8GB 堆和 Kotlin in-process；正常 Debug 沿用 R8、资源裁剪、debuggable=false、ARM64／ARMv7 和 Android Debug 签名。实际两个 APK 的包名均为 takagi.ru.monica.steamapp、最低 API26／目标 API34，无 application-debuggable 标记，各自 ABI 正确，apksigner 验证通过，证书 SHA-256 均为 6e1c4c795d062b74c28c3d4fa13833a71deef19f6501e917ee6b3df60797a4b0。包 SHA-256、大小与完整签名输出见同名前缀 production-apk.json／production-apk.txt。x86_64 设备通过结果不冒充 ARM 包实测；既有 R8 metadata／弃用警告保留。

新增 21 条中英文资源名与格式占位符一致；两个最终 ARM APK 各 42 个编译值逐项与源码一致，结果见 apk-resource-audit.json。初次资源核对脚本严格 UTF-8 解码因 aapt 输出其他资源的非法字节失败，随后仅修解码方式为替代未知字节，仍严格比较全部 42 个目标值，没有更改资源或放宽目标值匹配。两组改动合计 26 个独立源码／测试／资源路径，在正常构建期间固定 SHA-256 并在构建后重核一致；各组 source-audit.json 同时检查未跟踪文件、尾空白和冲突标记。

本轮只卸载独立测试主包与 .test 包，确认用户 0/10/11/12 保留且当前用户为 0 后，停止本次启动的公共模拟器；注册、config.ini 和原 userdata／qcow2 保留，未清真实应用数据。原始日志保存在 .codex-tasks/20261102-chat-chrome-search/，证据文本副本只规范 CR 和尾空白。

最终构建不复制至交付目录，不提交、推送或发布。截图和合成设计展示只留在忽略的 docs/design/；文本证据保存于 docs/verification/。未做 ARM 真机、OEM、真实 Steam 网络账号、所有语言／TalkBack 或 Android13+ AGSL 硬件验证。
