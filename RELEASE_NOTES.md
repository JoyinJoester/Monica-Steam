# Monica Steam 1.0.311 发行说明

## 中文

- 蒸蒸空间动态独立补齐作者姓名／头像，并通过官方 Web API 批量读取成就等事件的游戏名，批量缺项时按当前语言补查 Steam 公开商店名称，并缓存已确认结果；同时复用当前账号的游戏库名称缓存，旧动态在网络失败时也可补全已有名称。名称优先于图片补全；修复有效游戏 ID 被冗余的 appid=0 遮蔽，缺名称的缓存重新核验，缺失内容仍明确保留未知状态。收起好友区域时同步缩窄头像占位，从左向右连续排列。“在 Steam 查看”交给浏览器打开原始 Steam 网页，避免被应用自己的链接入口接回社区；无可用浏览器时提示打开失败。

- 个人统计使用游戏封面与真实 Steam 头像、紧凑时长和分层连续成就列表；本人／好友平均时长共用单轴双色对比，搭配加粗波浪成就进度、好友头像、游戏徽章与卡片及当前游戏点数商品预览。点数商店与商品打开应用内原生页面，限定当前游戏并隔离缓存，返回保留统计状态。各区块独立加载，隐私或失败不显示为零，切换账号／游戏后拒绝旧请求结果。

- 聊天游戏邀请与商店链接使用大封面、无描边圆角卡片，整张点击或长按从原位展开预览，关闭再收回原位；查看商店、复制链接和消息操作放到展开后。按钮参考 Monica“版本与链接”弹窗：查看商店／打开链接使用横向胶囊，复制、消息操作与删除使用连续圆角分组和尾部图标，删除使用警示色，大字号时主操作自然纵排。类型和 App 编号合并排版，保留原始邀请链接、缺少名称时的编号回退、滚动、减少动画及切换会话时的清理。

- 修复聊天内容及搜索筛选选中后计数变成实心色块的问题，徽标数字保留独立的对比色。

- 游戏详情隐藏主 Dock，购买／心愿单浮条仅在商店页显示，个人统计与创意工坊不保留对应底部占位；已拥有游戏显示“已在你的游戏库中”并保留版本、DLC 与送礼购买选项。点击购买打开版本、包及捆绑包弹窗，复用购物车、送礼和免费领取流程。仅当 Steam 返回与当前游戏、购买包和价格匹配的有效折扣截止时间时显示倒计时；大字号下正文避让浮条实际高度。

- 聊天、联系人与游戏库顶栏共用商店的圆形 Steam 账号头像，切换账号时同步更新；操作顺序统一为头像、搜索、三点菜单。

- 聊天列表与联系人页右上角菜单直接复用游戏库菜单组件，统一圆角磨砂／液态玻璃、图标、间距及弹出动效；保留原操作与刷新中禁用重复刷新的行为。

- 游戏库的 Steam 合集入口收进右上角菜单，移除账号卡片下方的独立横条；菜单显示当前合集名称，保留原有筛选与返回流程。

- 聊天和游戏库刷新使用附近同步同款变形图形并入右上角菜单，刷新时菜单仍可操作；私聊、群聊、空间、社区、个人主页、截图、点数及其他已有刷新入口统一图形，移除对应重复浮动或横条刷新，保留下拉与减少动画。商店首页保持现有刷新效果。

- 游戏详情使用单行导航：独立圆形返回按钮，右侧按内容宽度排列商店、个人统计及受支持游戏的创意工坊，大字号可横向滚动，大圆角页签采样实际背景。商店入口默认查看介绍与价格，游戏库入口默认查看当前账号的时长、最近游玩和成就；各页保留滚动位置与筛选，截图、适用游戏的数据入口和分享继续可用，缺失数据明确提示且不估算时长。切换正文时顶栏持续挂载，M3E 选中背景连续滑动，液态玻璃复用 Dock 的移动与回弹，保留创意工坊子页返回。悬浮操作栏支持拖动、吸边和自动收起：商店页提供购买、官方商店、评测和分享，统计页提供官方商店与分享。

- 聊天、搜索、成就、商店筛选、游戏详情与表情分类共用 Dock 的液态玻璃镜片和动效：按住浮起当前选中块，点击时立即切换并连续移向目标，到位后回弹；再次点击已选中项也有放大与回弹，快速切换从当前位置转向。拖动时跟手移动和形变，松手吸附后确认，边缘自动滚动到隐藏选项。文字围绕各自标签中心放大，修复偏移、重影、深色块及外壳裁切，保留 Dock 高光边缘；取消、离开页面或布局变化恢复已确认状态，支持 RTL、大字号与减少动画。

- 蒸蒸空间动态卡片去掉细线描边，改为主题圆角色块；增加圆角和内容留白，游戏内容行使用相邻层级底色，保留头像、时间、成就图片和详情操作。

- 购买选项顶部展示游戏封面；捆绑包使用完整宽度卡片，展示包含游戏图片、名称、件数和价格。内容详情同样显示组合图片，每个游戏可进入详情，保留 Steam 官方购买入口；无图时保持文字内容，大字号下自然滚动。

- 成就列表显示实际小图标，按游戏解析图片文件名并适配 Steam 当前资源目录；未完成成就保留灰色图标，缺少或加载失败时回退正常图标，未解锁隐藏成就仍遮盖，个人主页成就对比同步解析文件名。蒸蒸空间渲染缓存动态时也修正旧图片地址，以修正后的地址读取图片缓存，无需等待动态刷新；未提供或无法读取时保留奖杯占位。

- 蒸蒸空间顶栏改为独立返回圆钮与标题、筛选、刷新胶囊，M3E 使用高斯磨砂、液态玻璃使用动态折射；刷新中显示进度并禁用重复刷新，分类生效时突出筛选按钮。保留紧凑动态标题，将发布入口移为右下 FAB 并避让实际 Dock；列表按顶栏、好友区域及 FAB 高度避让，大字号和好友收拢保持可用。

- 好友、聊天内容与成就集合／完成状态筛选按标签和计数收紧宽度，左侧对齐，取消整行空白尾部；好友筛选直接复用 Dock 的玻璃采集与镜片。M3E 使用紧凑单行磨砂胶囊、高斯背景模糊及柔和半透明材质，取消非选中项的独立底块，拉开标题与分类间距，游戏详情页签同步采用该材质。窄屏和大字号可横向滚动到全部选项，M3E 保留点击选择，液态玻璃支持拖动切换。

- 聊天资料页的“共享内容”更名为“聊天内容”。已加载的图片、视频和贴纸按月显示缩略图网格，视频缺少封面时显示类型标识，剧透媒体保持遮盖；链接、文件与邀请使用图标列表并显示发送者和日期，点击回到原消息。

- 液态玻璃样式下，表情面板使用聊天背景的高斯模糊，原生表情网格保持透明和清晰；底部表情、Steam、贴纸与特效分类使用共用筛选镜片，保留搜索、最近表情、刷新和关闭。

- 游戏评论适配 Steam 当前评测接口，并同步修正同源评分读取；保留筛选和翻页，加载失败可重试，真实空结果单独提示。头部合并真实公开头像、作者名与紧凑推荐状态，时长和日期放在下方；长名字省略，大字号下元信息自然换行。点击作者名称或头像进入原生 Steam 主页，未登录时打开社区主页；资料复用迷你资料缓存，返回评论保留详情页位置。

- 移除聊天输入栏的“分享 Steam 卡片”入口和独立面板，直接粘贴链接即可发送；保留聊天卡片识别、原有卡片操作和游戏详情分享。

- 游戏库的 Steam 合集使用独立文件夹页面，复用 Monica Android 密码库的文件夹展示层：合集与未分类游戏同页、游戏小图标、路径导航、分组列表和长按文件夹管理。长按自购或家庭共享游戏进入多选，可加入合集或确认后移出；右下悬浮按钮新建合集或编辑游戏。成员编辑支持家庭共享游戏，分类保存在当前账号；复用 Monica 列表右滑选中或取消的手势；多选操作栏随按钮内容收紧并贴齐页面左侧边距，搜索保留隐藏选择，详情返回保留目录与搜索，退出未保存的成员修改先确认放弃。支持改名、删除和多合集成员关系；Steam 合集保持平级。动态或无法安全解析的合集只读；写入前重新读取目标，仅上传当前合集并回读核验，冲突或结果不明时阻止继续写入。

- 修复悬浮工具栏圆角外漏出的矩形底色，玻璃与内容统一按轮廓裁剪；吸边时先接触、压平交界再回弹为张力圆角，提起时保留弹性连接，缝隙向中央收拢后断开。展开和收起由同一表面连续变形，保留拖动、取消、自动收起和减少动画设置。

- 减少首次进入聊天及打开会话时的卡顿：加密聊天缓存与发件箱按实际读写初始化，置顶、归档和隐藏标记在后台准备，准备期间不展示未经筛选的会话。壁纸 SVG 在后台生成并共享有容量限制的图案缓存，正文和玻璃背景复用图案，纯色背景跳过生成。聊天列表保留已访问页面并减少嵌套 Compose 容器，顶栏模糊跟随实际滚动，避免边界拖动造成背景漂移；全部、未读、私聊、群聊、置顶和归档采用互斥单选，选中背景连续移动并遵循减少动画设置。

- 聊天通话状态文字短距离淡入滑动，通话按钮加入轻按回弹、图标与静音状态色过渡；私聊通话条与群语音成员头像根据 WebRTC 实际音量显示柔和声波，静音、断线或音量过期时收起。减少动画时保留静态说话圈。

- 聊天图片采用 TG 风格紧凑气泡：单图按实际比例显示，同一消息内多图拼成相册，纯图片的时间与发送状态叠在右下角，说明显示在图下。兼容 Steam 图片内嵌 `[url]…[/url]` 和响应式属性，保留比例、说明及相册顺序；地址不一致或含额外内容时保留文字。图片查看器从当前缩略图展开，关闭沿原位回落；切图、放大或缩略图已卸载时淡出，保留相册翻页、长按操作、发送失败重试与减少动画。

- 修复发送个人资料链接后，Steam 返回的 `[og]` 预览被显示为原始文字的问题；识别标题、简介和预览图，已有资料、库存及直播消息复用卡片显示，长按仍可查看详情和原文。

- 收到好友聊天消息时通知；移除“正在以……连接”、等待账号及重连常驻通知。系统结束应用进程后，需重新打开应用恢复接收；升级会清理旧连接通知，保留消息频道设置。

- 消息卡片与蒸蒸空间支持长按浮起预览，背景高斯模糊，前景显示完整详情与操作；好友动态按日期分组，新增原生文字发布页面，通过 Steam 官方好友状态接口发布，保留官方网页入口。草稿仅在内存中保存，空白或超限不可发送；提交后禁止重复点击，切换账号或离开清理草稿。收到确认后才提示成功并刷新；会话准备失败可继续编辑，进入发送后因断线、超时或后台中断无法确认结果时，保留草稿并提示先在 Steam 核实，不自动重发。Android 12 以下保留遮罩回退。

- 购物车区分 M3E 实色结算栏与液态玻璃悬浮结算胶囊，打开时隐藏全局 Dock，心愿单沿同页切换。

- 正文、工具栏与浮层按界面风格使用共享材质：液态玻璃采用折射镜片和高光，M3E 使用轻毛玻璃及主题底色；保留布局、输入状态和操作，旧系统使用实色或平台模糊回退。

- Kotlin 升级至 2.4.21，Compose BOM 升级至 2026.09.00，配套更新 AGP、Gradle、KSP 和 Room，并接入 Kyant Backdrop 2.0.1。

- 聊天列表的“新建聊天”悬浮按钮跟随设计风格：M3E 使用主题容器色圆角 FAB，液态玻璃保留现有圆形玻璃按钮。

- 聊天相关页面复用 Telegram 原生会话列表、联系人、搜索、消息、菜单及玻璃组件，保留 Steam 原有业务操作，并修复联系人大字号裁切。

- 蒸蒸空间入口移至聊天页右上角菜单，未读筛选并入同一筛选栏；移除联系人和聊天历史页的重复搜索入口。

- 聊天附件统一按 Steam 官方聊天媒体范围选择和校验：支持列出的图片、视频格式及最多 30 MiB，ZIP、PDF 等在上传前明确提示不支持；保留收到的附件链接。补充五种语言的文件类型、大小、账号、会话和服务失败提示。

- 首次使用必须设置并确认主密码，未完成时无法进入主页；此前未设置密码的用户升级后也需补设。默认仅锁定令牌页面，打开应用无需启动验证，备份、导出及主密码相关安全设置仍受保护；已有密码用户保留明确保存过的锁定范围。移除无主密码时直接放行 maFile 导出的旧逻辑，设置或读取失败时不自动重置数据。令牌、备份与主密码安全设置不再继承“关闭启动密码验证”的免验证状态，修复其影响敏感页面登录控件的问题。

- 设置页面对齐 Monica Android 的 M3E 连续分组卡片：统一外侧大圆角、相邻小圆角、紧凑间距和主题容器配色，覆盖首页、外观、数据管理、主密码锁定、导航、Steam 功能及通知等设置。搜索过滤后自动保持首尾圆角，保留原有入口与操作。

- 完善页面缩放：复用 Monica Android 的缩放容器和滑块组件，在“设置 → 外观 → 界面缩放”中调整 80%–120% 并恢复默认；页面内容与底栏统一缩放，修复超过 100% 时正文仍被限制为原大小的问题。保留系统字体设置、已有缩放值及固定系统尺寸的调节弹窗。

- 聊天表情、贴纸和特效目录新增按账号隔离的内存与本地缓存，再次进入先显示缓存并在后台静默刷新，移除表情面板加载条；刷新失败保留已有内容，切换账号不串用目录，临时登录仅使用内存缓存。

- 新增面向 Steam／配套 SteamNova 模块的单账号 maFile 授权交付：每次验证主密码或已启用的生物识别，选择账号后再次确认，再返回模块预览。需要已设置主密码；取消或离开页面即终止，交付绑定接收 UID、60 秒有效、仅能打开一次且不写出明文临时文件。模块端需支持对应协议，已有文件导入导出保持可用。

- 正文界面保留 M3E 与液态玻璃风格；Dock 提供固定底栏和液态玻璃，移除独立 M3E Dock 选项并默认使用固定底栏。旧 M3E Dock 配置自动转为固定底栏并保留页面顺序，已有液态玻璃选择与固定底栏顺序保持不变。

- 游戏库“近期活跃”纳入近 14 天玩过的家庭共享游戏，并与自购游戏按当前账号的近两周时长从多到少混合排序，筛选与搜索后的默认顺序一致；时长相同时比较最近游玩时间，仅有游玩日期的共享游戏仍保留，不重复归入“未游玩”。读取当前玩家的近两周时长、累计时长及最近游玩时间，缺项时使用不限数量的最近游戏接口补充；不使用出借者时长、不重复累计或估算，部分失败保留该账号此前缓存。手动选择累计时长或名称排序仍按所选规则显示。

- 修复 README 赞助区的 PayPal 按钮和标志显示异常，改用与 Monica Android 一致的链接徽章。

- [SteamNova（原 Millennium）模块](https://github.com/GoodJoe-2049/Steam-Nova)互导指引说明 Monica Pass 与 Steam 主应用如何通过 maFile 导入、导出及恢复复用令牌数据；区分应用内加密存储与明文 maFile，Monica Steam 导出的 ZIP 需解压后选择单账号文件，并澄清普通版 Monica 的验证码 URI 跳转与 Steam 版完整 maFile 导入的区别。

- 修复重新登录后工坊仍可能提示需要登录的问题：会话解析读取原账号存储中的最新凭据，避免旧缓存遮盖新登录；旧刷新晚返回时重新核对会话。订阅认证失败时仍可加载公开作品详情，保留原有错误提示与下载入口。

- 商店菜单新增 Wallpaper Engine 专用入口，以真实壁纸预览双列浏览，并保留搜索、筛选和分页；公开作品详情无需先登录。

- 创意工坊新增原文件下载与“我的下载”：支持 Steam 内容清单及分块下载、文件校验、手机文件夹保存、后台进度、取消和重新下载。匿名与当前账号下载由用户明确选择，账号任务绑定原存储来源；权限不足、网络失败与保存失败分别提示。下载不自动订阅，也不自动设置为手机动态壁纸；场景、网页和 PKG 仍需兼容播放器。

- 商店侧边栏新增“兑换 Steam 礼品卡”和“Steam 钱包余额”，与产品代码激活组成紧凑操作组；使用当前选中账号的应用内 Steam 官方页面处理兑换、查看余额及充值。

- 游戏详情与多区价格采用紧凑布局，当前地区优先显示，并用 SteamDB 替换 ITAD 价格查询入口。当前区服价格与 SteamDB 史低合为分组，对照现价、历史折扣及最近史低日期，保留多区比价和来源入口；史低失败不影响现价，免费或本区不可售的游戏不显示误导性对照。

- 修复 Steam 更新创意工坊网页数据格式后，正常作品列表被误报为“无法识别的数据”的问题；兼容新旧格式，保留分页、搜索和标签筛选。

- maFile 本地备份和 WebDAV 备份入口纳入令牌锁保护；设置主密码后，每次导出明文 ZIP 都需在选择保存位置后通过主密码或已启用的指纹验证，取消、失败或离开验证页面均不写出令牌数据。

- 商店首页合并搜索与顶栏，显示圆形账号头像；特别优惠改为突出主卡片的滑动轮播，热销榜只展示游戏。

- 桌面小组件修复账号绑定、MDBX 来源和缓存读取，显示真实头像与游戏封面，并采用外缘大圆角、相邻小圆角的自适应分组卡片。

- 新增临时登录：密码、验证码和扫码登录均可选择不保存本次凭据，退出应用或进程结束后移除临时账号；不会覆盖原有账号。临时账号不支持内置网页登录、小组件或账号导出。

- 修复商店侧边栏在点击遮罩、拖拽或中断关闭动画后可能留下黑色遮罩、阻挡主页操作的问题。

- 修正确认列表请求的签名标签，并使用 Steam 服务器时间签名，避免手机时间偏差导致确认刷新失败。

- 确认列表明确返回会话失效时，更新账号会话并最多重试读取一次；失败提示提供重新登录指引，不再将刷新失败显示为“暂无待确认项目”。

## English

- Steam Space loads author names and avatars independently and resolves game names for achievements and other activity through an official Web API batch, with bounded public-store lookups for missing names and a cache of confirmed results per language. The current account’s library name cache also fills missing labels in retained activity, including when news refresh fails. Names load before optional media enrichment. A valid game ID is retained when redundant appid is zero; cached entries with missing names are revalidated and unavailable data stays unknown. Collapsed friend avatars use narrower consecutive slots from the start edge. View on Steam selects a browser for the original Steam web URL, avoiding the app’s own community handler; unavailable browsers report an open failure.

- Personal statistics uses game artwork, the real Steam avatar, compact playtime metrics and layered continuous achievement rows, with a shared axis comparing your time and friends’ average, a thicker wavy achievement indicator, friend avatars, badges, trading cards and game-specific Points Shop previews. Shop and reward actions open native pages with game-scoped caches and retain stats on return. Sections load independently; unavailable data stays unknown and cancelled account/game requests cannot publish late results.

- Chat game invitations and store links use wide artwork on rounded, borderless cards. Tap or long-press to expand a preview from the card’s original bounds and close it back into place, with store, copy and message actions inside. Actions follow Monica’s version-and-links dialog: Store/Open pills above connected Copy, Message and Delete rows with trailing icons and a warning-colored Delete; main destinations stack for large text. Type and App ID share a row. Exact invitation links, App ID title fallback, scrolling, reduced motion and conversation-scope cleanup are preserved.

- Fixed selected count badges turning into solid shapes in Chat content and search filters by retaining contrasting numeral colors.

- Game details hide the main Dock and show the purchase/wishlist bar only on Store, without reserving its space in Personal Stats or Workshop. Owned games display In your library and retain edition, DLC and gift options. Buying opens edition, package and bundle options through existing cart, gift and free-license flows. A countdown appears only when Steam supplies a valid expiry matching the current game, package and price. Larger text uses measured clearance for the bar.

- Chat, contacts and library headers share the store’s circular Steam account avatar and update it when switching accounts. Header actions consistently appear as avatar, search and overflow menu.

- Chat and contacts headers now reuse the library overflow component, matching its rounded frosted/glass surface, icons, spacing and popup motion. Duplicate refresh is disabled while refreshing; other menu actions remain available.

- Move Steam Collections into the library overflow menu and remove its standalone row below the account header. The menu keeps the active collection name, existing filtering and back navigation.

- Chat and library refresh now morphs the top-right menu icon using the same expressive shape as Nearby Sync, with menu actions still available. Thread, Space, Community, profile, screenshot, Points Shop and existing refresh actions share this indicator; duplicate floating/linear refresh indicators are removed while pull gestures and reduced motion remain supported. Store-home refresh is unchanged.

- Game details use one compact navigation row with a round Back button and content-sized Store, Personal Stats and supported Workshop tabs, with horizontal scrolling for large text and rounded tabs sampling the actual background. Store entries open the overview and prices; library entries open the current account’s playtime, last played date and achievements. Each page retains scroll and filters, with screenshots, supported game-data links and sharing available; unavailable data is labelled without inventing playtime. Navigation stays mounted during content transitions so M3E selection slides continuously and Liquid Glass retains Dock travel and rebound, including nested Workshop back navigation. The draggable, edge-attaching, auto-collapsing toolbar offers purchase options, the official store, reviews and sharing on Store, and the official store and sharing on Personal Stats.

- Chat, search, achievements, Store filters, game-detail tabs and emoji categories share the Dock Liquid Glass lens and motion. Holding lifts the current selection; taps select immediately, move continuously to the target and rebound after arrival, including enlargement on the current item and continuous retargeting. Dragging follows and deforms, commits after snapping on release and scrolls at the edges to reach hidden options. Labels enlarge around their own centers, fixing drift, ghosting, dark blobs and shell clipping while retaining Dock highlights. Cancellation, leaving the page or reflow restores the committed state; RTL, large text and reduced motion remain supported.

- Steam Space activity cards use rounded theme surfaces without outlines, with more inner spacing and a separate tonal level for game rows; avatars, times, achievement images and detail actions remain available.

- Add game artwork above purchase options. Bundles use full-width cards with included-game artwork, names, item counts and prices; the contents sheet repeats the artwork and keeps individual game links and the official Steam purchase action. Missing artwork retains the text, with scrolling for large fonts.

- Achievement lists display actual icons, resolve filenames per game and support Steam’s current asset directory. Locked achievements retain gray icons with a normal-image fallback when missing or unreadable; locked hidden achievements stay concealed and profile comparisons resolve filenames too. Cached Space activity also normalizes retired image routes and uses the corrected cache key without waiting for a feed refresh, retaining a trophy fallback when no image can be read.

- Give Steam Space a floating back button and a compact title/filter/refresh capsule with Gaussian frost in M3E and live refraction in Liquid Glass. Show refresh progress, disable duplicate refresh and highlight active filters, keep the feed heading compact and move publishing to a bottom-right FAB above the measured Dock. Feed padding clears the header and FAB, including large text and the collapsing playing-friends header.

- Friend, Chat content and achievement set/completion filters fit their labels and counts at the start edge, removing empty full-row tails; friend filters reuse the Dock glass source and lens. M3E uses compact frosted capsules with Gaussian backdrop blur and softer translucent material, removes separate inactive chips and adds clearer title/category spacing; game-detail tabs share the material. Narrow screens and large text scroll to every option. M3E retains click selection and Liquid Glass supports drag selection.

- Renamed Shared content to Chat content in chat profiles. Loaded images, videos and stickers use monthly thumbnail grids, missing video posters retain a type marker and spoilers stay covered. Links, files and invitations use typed rows with sender/date metadata and return to the original message.

- In Liquid Glass mode, the emoji panel blurs the chat background while the native grid stays transparent and crisp. Emoji, Steam, Stickers and Effects categories use the shared filter lens, with search, recent emoji, refresh and close available.

- Game reviews and ratings use Steam’s current endpoint, preserving filters and pagination with explicit retry and a distinct empty state. Headers combine the public avatar, author name and compact recommendation status, with playtime/date below; long names ellipsize and metadata wraps at larger font sizes. Tapping the author opens the native Steam profile when signed in or the community profile when signed out. Mini-profile caching and the detail page position are retained on return.

- Removed the manual Steam-card sharing button and sheet from the chat composer. Paste links directly to send them; chat card recognition, card actions and game-detail sharing remain available.

- Steam collections use a standalone folder page with the folder presentation extracted from Monica Android’s password library: collections and unfiled games share one list with small game icons, path navigation, grouped rows, and long-press folder actions. Long-press owned or family-shared games to select and add them to a collection or confirm removal; floating controls create collections and edit membership. Membership editing includes family-shared games, with collections saved for the current account. Reuse the Monica list right-swipe gesture to select or unselect games. Selection controls fit their buttons and align with the left page margin. Search retains hidden choices, returning from details preserves the directory and query, and leaving pending membership edits asks to discard them. Rename, deletion, and multi-collection membership remain available; Steam collections stay flat. Dynamic or unreadable collections are read-only. Writes recheck the reviewed target, upload only that entry, and verify the result; conflicts or unknown outcomes block further writes.

- Fix rectangular glass-base fragments outside floating-toolbar corners. Glass and content share one contour; edge contact precedes flattening and a tension rebound. Lifting stretches a retained tether, pinches the gap towards the middle, then breaks it. One surface morphs during expand/collapse while drag, cancellation, auto-collapse and reduced motion remain available.

- Reduce first chat-entry and conversation-opening stalls by initializing encrypted caches and the outbox on actual I/O and preparing pin, archive and hidden flags in the background; pending flags keep unfiltered conversations hidden. Wallpaper SVG patterns are rasterized in a shared bounded background cache reused by content and glass, while plain backgrounds skip generation. Visited chat pages are retained with fewer nested Compose containers, and header blur follows actual scrolling without boundary drift. All, Unread, Direct, Groups, Pinned and Archived filters use exclusive selection with continuous indicator motion and reduced-motion support.

- Chat call labels fade and slide between states, with short press, icon and mute-color transitions on call controls. Direct-call and group-member avatars show soft waves driven by actual WebRTC audio levels, clearing on mute, disconnection or stale samples. Reduced motion keeps a static speaking ring.

- Chat photos use compact Telegram-style bubbles: single images retain their aspect ratio, images in one message form an album, photo-only timestamps and delivery status overlay the lower-right corner, and captions appear below. Nested Steam `[url]…[/url]` wrappers and responsive attributes preserve images, captions and album order; mismatched URLs or extra content stay visible as text. The viewer expands from the thumbnail and returns on close, using a fade after switching, zooming or source removal. Album paging, message actions, failed-send retry and reduced motion remain available.

- Fixed Steam-enriched `[og]` links appearing as raw text after sharing a profile. Existing profile, inventory and broadcast messages now use card titles, descriptions and preview images, with details and original text available from the long-press preview.

- Friend chat messages can still notify you. Removed persistent connecting, waiting-for-account and reconnecting notifications. If Android ends the app process, reopen the app to resume receiving messages. Upgrades retire old connection notifications and preserve the message channel settings.

- Message cards and Steam Space support lifted previews above a Gaussian-blurred background, with full details and actions. Friends' activity is grouped by date, with a native text composer using Steam’s official friends-status service and an optional official webpage entry. Drafts stay in memory; blank or oversized text cannot be sent, pending submissions block repeat taps, and account changes or leaving clear the draft. Success and refresh require acknowledgement. Session-preparation failures allow editing. After dispatch begins, a lost response, timeout or background interruption retains the draft and asks the user to check Steam before posting again, without automatic resubmission. Android versions below 12 use a dimmed fallback.

- The cart uses a solid M3E checkout bar or floating glass capsule and hides the global Dock. The wishlist remains a tab on the same page.

- Content, toolbars and overlays share style-aware materials: refraction lenses and highlights for Liquid Glass, restrained frost and tonal fills for M3E. Layouts, input state and actions are preserved, with solid or platform-blur fallbacks on older Android versions.

- Upgraded Kotlin to 2.4.21 and Compose BOM to 2026.09.00, along with AGP, Gradle, KSP and Room; added Kyant Backdrop 2.0.1.

- The inbox New chat FAB follows the selected design style: a rounded Material 3 button with themed container colors for M3E, or the existing circular glass button for Liquid Glass.

- Chat surfaces reuse Telegram conversation/contact rows, search, messages, menus and glass components while preserving Steam actions and fixing large-text contact clipping.

- Moved Steam Space into the chat overflow menu and merged unread filtering into the same filter row. Removed duplicate search actions in contacts and chat history.

- Chat attachment selection and upload checks now follow the official Steam chat media path: listed image/video formats up to 30 MiB. ZIP, PDF and other unsupported files show a local error before upload, while received attachment links remain readable. Added five-language messages for file type/size, account, session and service failures.

- First use now requires setting and confirming a master password before the main UI opens, including upgrades from installations without one. The default locks only the Token page, allowing the app to open without startup authentication while protecting backups, exports and password/security settings. Existing password users retain their explicitly saved lock scope. maFile export no longer permits missing-password bypass; setup or storage failures never reset data automatically. Token, backup and master-password security controls no longer inherit the startup-verification bypass.

- Aligned Settings with Monica Android's M3E connected cards: large outer corners, small inner corners, compact spacing and themed container colors across the home, appearance, data, password/locking, navigation, Steam features and notification settings. Groups retain their outer corners after search filtering; existing destinations and actions are preserved.

- Aligned page scaling with Monica Android's scale provider and slider under Settings → Appearance → Interface scale (80%–120%, with a default reset). Page content now scales together with navigation, fixing the 100% cap on content when a larger scale was selected. System font preferences, saved scale values and the adjustment sheet's stable system density are preserved.

- Chat emoticon, sticker and effect catalogs now use account-scoped memory and local caches. Reopening shows cached items while refreshing silently, without a picker progress bar. Failed refreshes retain existing items, account switches keep catalogs separate, and temporary accounts use memory only.

- Added consented single-account maFile handoff to Steam / a compatible SteamNova module. Each request requires fresh password or enabled biometric verification, account selection and explicit confirmation before returning to the module preview. A configured master password is required; cancellation or leaving the screen aborts the request. UID-bound, single-open grants expire after 60 seconds without writing plaintext temporary files. The module must support this protocol; existing file import and export remain available.

- Content retains M3E and Liquid Glass appearance. Dock offers Fixed and Liquid Glass, removes the separate M3E Dock option and defaults to Fixed. Legacy M3E Dock configurations migrate to Fixed with their tab order; existing Liquid Glass selections and Fixed orders are preserved.

- Recently active includes family-shared games played within 14 days and mixes them with owned games by the selected player’s two-week playtime, highest first, including default filtered/search order. Last-played time breaks ties; shared games with only a play date remain included without also appearing as unplayed. Missing reported two-week, total and last-played data falls back to an unlimited recent-games query. Lender time is never used, sources are not added together or estimated, and partial failures retain that account’s cached recent time. Explicit total-playtime and name sorts keep their chosen rules.

- Fixed the PayPal button and oversized logo in the README sponsorship section by using the same linked badge as Monica Android.

- [SteamNova (formerly Millennium) module](https://github.com/GoodJoe-2049/Steam-Nova) interoperability guidance explains reusing Steam Guard data between Monica Pass and Steam through maFile import, export and restoration. It distinguishes internal encrypted storage from plaintext maFile, requires extracting Monica Steam ZIP exports before selecting individual account files, and separates regular Monica code URI links from complete maFile imports into Monica Steam.

- Fixed Workshop continuing to request sign-in after a successful re-login: session resolution reads current credentials from the original account store instead of masking them with an older cached session, and rechecks sessions when a previous refresh returns. Public item details remain readable when subscription authentication fails, with the original error and download entry preserved.

- Added a dedicated Wallpaper Engine entry in the store drawer, with a two-column gallery using actual previews, search, filters and pagination. Public item details can be viewed without signing in.

- Added original-file Workshop downloads and My downloads, including Steam manifests and chunks, integrity checks, folder selection, background progress, cancellation and retry. Anonymous or selected-account access is an explicit choice; account tasks stay bound to their original storage source. Permission, connection and storage failures have separate messages. Downloading neither subscribes nor sets an Android live wallpaper; scene, web and PKG content still requires a compatible player.

- Added Steam gift-card redemption and wallet-balance shortcuts beside product activation in the store drawer. They open Steam's official pages inside the app with the selected account's authenticated session for redemption, balance and funding.

- Game details and regional prices use a compact layout with the current region first, replacing ITAD price lookup with SteamDB. Current regional prices and historical lows form one comparison group with historical discounts and the last occurrence, preserving regional comparison and source links. History failures leave current prices usable; free or locally unavailable games do not show a misleading comparison.

- Fixed Workshop browsing reporting an invalid response after Steam changed its page hydration format. Both current and older formats are supported while preserving pagination, search and tag filters.

- Token locking now also protects local maFile and WebDAV backup pages. With a master password configured, each plain ZIP export requires fresh password or enabled fingerprint verification after choosing a destination; cancelling, failing or leaving verification does not write token data.

- Integrated store search into the top bar with a circular account avatar, a focused offer carousel and a games-only top-sellers section.

- Fixed widget account binding, MDBX source lookup and cached data loading. Widgets use real avatars and game covers with adaptive grouped cards and smaller adjoining corners.

- Added temporary sign-in for password, verification-code and QR flows. Credentials remain in memory and the temporary account is removed when the app exits or its process ends, without replacing saved accounts. Signed-in web pages, widgets and account export are unavailable for temporary accounts.

- Fixed the store drawer leaving a dim overlay and blocking the home screen when dismissal animations were interrupted.

- Corrected the confirmation-list signature tag and use Steam server time for confirmation signatures instead of the phone clock.

- Retry confirmation reads once with refreshed credentials after an explicit authentication failure. Expired sessions now show sign-in guidance, and failed refreshes are no longer presented as an empty confirmation list.
