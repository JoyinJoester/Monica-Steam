# Monica Steam 1.0.310 发行说明

## 中文

- 首次使用必须设置并确认主密码，未完成时无法进入主页；此前未设置密码的用户升级后也需补设。默认仅锁定令牌页面，打开应用无需启动验证，备份、导出及主密码相关安全设置仍受保护；已有密码用户保留明确保存过的锁定范围。移除无主密码时直接放行 maFile 导出的旧逻辑，设置或读取失败时不自动重置数据。
- 设置页面对齐 Monica Android 的 M3E 连续分组卡片：统一外侧大圆角、相邻小圆角、紧凑间距和主题容器配色，覆盖首页、外观、数据管理、主密码锁定、导航、Steam 功能及通知等设置。搜索过滤后自动保持首尾圆角，保留原有入口与操作。
- 完善页面缩放：复用 Monica Android 的缩放容器和滑块组件，在“设置 → 外观 → 界面缩放”中调整 80%–120% 并恢复默认；页面内容与底栏统一缩放，修复超过 100% 时正文仍被限制为原大小的问题。保留系统字体设置、已有缩放值及固定系统尺寸的调节弹窗。
- 聊天表情、贴纸和特效目录新增按账号隔离的内存与本地缓存，再次进入先显示缓存并在后台静默刷新，移除表情面板加载条；刷新失败保留已有内容，切换账号不串用目录，临时登录仅使用内存缓存。
- 新增面向 Steam／配套 Millennium 模块的单账号 maFile 授权交付：每次验证主密码或已启用的生物识别，选择账号后再次确认，再返回模块预览。需要已设置主密码；取消或离开页面即终止，交付绑定接收 UID、60 秒有效、仅能打开一次且不写出明文临时文件。模块端需支持对应协议，已有文件导入导出保持可用。
- Dock 移除 M3E 样式，默认改为“固定底栏”；旧 M3E 配置自动使用固定底栏并保留原有页面顺序，液态玻璃及已设置的固定底栏顺序保持不变。
- 修复游戏库“近期活跃”遗漏家庭共享游戏的问题：保留当前账号的最近游玩时间，将近 14 天玩过的共享游戏纳入分组及筛选，并避免重复归入“未游玩”；不估算或虚增近两周时长。
- 游戏库“近期活跃”将自购与家庭共享游戏按近两周游玩时长从多到少混合排序，筛选和搜索后的默认顺序保持一致；时长相同时再比较最近游玩时间，仅有游玩日期的共享游戏仍保留在分组中。手动选择累计时长或名称排序仍按所选规则显示。
- 家庭共享游戏新增当前账号的真实游玩时长补充查询：读取近两周时长、累计时长和最近游玩时间，完整游玩记录缺项时使用不限数量的最近游戏接口补充；不使用出借者的时长，也不重复累计。部分查询失败时保留该账号此前缓存的近期时长。
- 修复 README 赞助区的 PayPal 按钮和标志显示异常，改用与 Monica Android 一致的链接徽章。
-  [Millennium 模块](https://github.com/Good-Joe2049/Millennium)说明：可以通过这个模块来实现 Monica Pass 与 Steam 主应用令牌共存，并通过 `maFile` 导入、导出及恢复复用令牌数据。
- 补充 Millennium 互导指引：区分应用内加密存储与明文 maFile，说明 Monica Steam 导出的 ZIP 需解压后选择单账号文件；澄清普通版 Monica 的验证码 URI 跳转与 Steam 版完整 maFile 导入的区别。
- 修复重新登录后工坊仍可能提示需要登录的问题：会话解析读取原账号存储中的最新凭据，避免旧缓存遮盖新登录；旧刷新晚返回时重新核对会话。订阅认证失败时仍可加载公开作品详情，保留原有错误提示与下载入口。
- 商店菜单新增 Wallpaper Engine 专用入口，以真实壁纸预览双列浏览，并保留搜索、筛选和分页；公开作品详情无需先登录。
- 创意工坊新增原文件下载与“我的下载”：支持 Steam 内容清单及分块下载、文件校验、手机文件夹保存、后台进度、取消和重新下载。匿名与当前账号下载由用户明确选择，账号任务绑定原存储来源；权限不足、网络失败与保存失败分别提示。下载不自动订阅，也不自动设置为手机动态壁纸；场景、网页和 PKG 仍需兼容播放器。

- 商店侧边栏新增“兑换 Steam 礼品卡”和“Steam 钱包余额”，与产品代码激活组成紧凑操作组；使用当前选中账号的应用内 Steam 官方页面处理兑换、查看余额及充值。
- 游戏详情顶部将当前区服价格与 SteamDB 史低合为紧凑分组，直接对照现价、历史折扣和最近史低日期，并保留多区比价与来源入口；史低加载失败不影响现价，免费或本区不可售的游戏不显示误导性的价格对照。
- 修复 Steam 更新创意工坊网页数据格式后，正常作品列表被误报为“无法识别的数据”的问题；兼容新旧格式，保留分页、搜索和标签筛选。
- maFile 本地备份和 WebDAV 备份入口纳入令牌锁保护；设置主密码后，每次导出明文 ZIP 都需在选择保存位置后通过主密码或已启用的指纹验证，取消、失败或离开验证页面均不写出令牌数据。
- 修复“关闭启动密码验证”仍影响敏感页面登录控件的问题，令牌、备份与主密码安全设置不再继承启动免验证。
- 商店首页合并搜索与顶栏，显示圆形账号头像；特别优惠改为突出主卡片的滑动轮播，热销榜只展示游戏。
- 游戏详情采用紧凑布局，多区价格优先显示当前地区；用 SteamDB 替换 ITAD 价格查询入口。
- 桌面小组件修复账号绑定、MDBX 来源和缓存读取，显示真实头像与游戏封面，并采用外缘大圆角、相邻小圆角的自适应分组卡片。
- 新增临时登录：密码、验证码和扫码登录均可选择不保存本次凭据，退出应用或进程结束后移除临时账号；不会覆盖原有账号。临时账号不支持内置网页登录、小组件或账号导出。
- 修复商店侧边栏在点击遮罩、拖拽或中断关闭动画后可能留下黑色遮罩、阻挡主页操作的问题。
- 修正确认列表请求的签名标签，并使用 Steam 服务器时间签名，避免手机时间偏差导致确认刷新失败。
- 确认列表明确返回会话失效时，更新账号会话并最多重试读取一次；失败提示提供重新登录指引，不再将刷新失败显示为“暂无待确认项目”。

## English

- First use now requires setting and confirming a master password before the main UI opens, including upgrades from installations without one. The default locks only the Token page, allowing the app to open without startup authentication while protecting backups, exports and password/security settings. Existing password users retain their explicitly saved lock scope. maFile export no longer permits missing-password bypass; setup or storage failures never reset data automatically.
- Aligned Settings with Monica Android's M3E connected cards: large outer corners, small inner corners, compact spacing and themed container colors across the home, appearance, data, password/locking, navigation, Steam features and notification settings. Groups retain their outer corners after search filtering; existing destinations and actions are preserved.
- Aligned page scaling with Monica Android's scale provider and slider under Settings → Appearance → Interface scale (80%–120%, with a default reset). Page content now scales together with navigation, fixing the 100% cap on content when a larger scale was selected. System font preferences, saved scale values and the adjustment sheet's stable system density are preserved.
- Chat emoticon, sticker and effect catalogs now use account-scoped memory and local caches. Reopening shows cached items while refreshing silently, without a picker progress bar. Failed refreshes retain existing items, account switches keep catalogs separate, and temporary accounts use memory only.
- Added consented single-account maFile handoff to Steam / a compatible Millennium module. Each request requires fresh password or enabled biometric verification, account selection and explicit confirmation before returning to the module preview. A configured master password is required; cancellation or leaving the screen aborts the request. UID-bound, single-open grants expire after 60 seconds without writing plaintext temporary files. The module must support this protocol; existing file import and export remain available.
- Removed the M3E Dock option and made Fixed the default. Existing M3E configurations use Fixed with their previous tab order, while Liquid Glass selections and existing Fixed orders are preserved.
- Fixed family-shared games missing from the library's Recently active section. The selected account's last-played time now includes shared games played within the past 14 days in sections and filters without also classifying them as unplayed or inventing two-week playtime.
- Recently active now mixes owned and family-shared games by two-week playtime, highest first, including the default order after filtering and searching. Last-played time breaks ties; shared games with only a recent play date remain in the section. Explicit total-playtime and name sorts keep their selected order.
- Family-shared games now load the selected player's reported two-week playtime, total playtime and last-played time. Missing client activity records fall back to an unlimited recent-games query; lender playtime is not used and overlapping sources are not added together. Partial failures preserve that account's previously cached recent playtime.
- Fixed the PayPal button and oversized logo in the README sponsorship section by using the same linked badge as Monica Android.
-  [Millennium module](https://github.com/Good-Joe2049/Millennium), which lets Monica Pass and the main Steam app keep the same Steam Guard authenticator through `maFile` import, export, and restoration.
- Clarified Millennium interoperability: internal encrypted storage is separate from plaintext maFile export, Monica Steam ZIP exports must be extracted before importing individual files, and code URI links to regular Monica differ from complete maFile imports into Monica Steam.
- Fixed Workshop continuing to request sign-in after a successful re-login: session resolution reads current credentials from the original account store instead of masking them with an older cached session, and rechecks sessions when a previous refresh returns. Public item details remain readable when subscription authentication fails, with the original error and download entry preserved.
- Added a dedicated Wallpaper Engine entry in the store drawer, with a two-column gallery using actual previews, search, filters and pagination. Public item details can be viewed without signing in.
- Added original-file Workshop downloads and My downloads, including Steam manifests and chunks, integrity checks, folder selection, background progress, cancellation and retry. Anonymous or selected-account access is an explicit choice; account tasks stay bound to their original storage source. Permission, connection and storage failures have separate messages. Downloading neither subscribes nor sets an Android live wallpaper; scene, web and PKG content still requires a compatible player.

- Added Steam gift-card redemption and wallet-balance shortcuts beside product activation in the store drawer. They open Steam's official pages inside the app with the selected account's authenticated session for redemption, balance and funding.
- Game details now group the current regional price with its SteamDB historical low for direct comparison, including the historical discount and last occurrence. Regional comparison and source links remain accessible; history failures leave the current price usable, and free or locally unavailable games do not show a misleading comparison.
- Fixed Workshop browsing reporting an invalid response after Steam changed its page hydration format. Both current and older formats are supported while preserving pagination, search and tag filters.
- Token locking now also protects local maFile and WebDAV backup pages. With a master password configured, each plain ZIP export requires fresh password or enabled fingerprint verification after choosing a destination; cancelling, failing or leaving verification does not write token data.
- Fixed the startup password-verification bypass leaking into sensitive login controls. Token, backup and master-password security pages no longer inherit the startup bypass.
- Integrated store search into the top bar with a circular account avatar, a focused offer carousel and a games-only top-sellers section.
- Made game details and regional prices more compact, placed the current region first, and replaced ITAD price lookup with SteamDB.
- Fixed widget account binding, MDBX source lookup and cached data loading. Widgets use real avatars and game covers with adaptive grouped cards and smaller adjoining corners.
- Added temporary sign-in for password, verification-code and QR flows. Credentials remain in memory and the temporary account is removed when the app exits or its process ends, without replacing saved accounts. Signed-in web pages, widgets and account export are unavailable for temporary accounts.
- Fixed the store drawer leaving a dim overlay and blocking the home screen when dismissal animations were interrupted.
- Corrected the confirmation-list signature tag and use Steam server time for confirmation signatures instead of the phone clock.
- Retry confirmation reads once with refreshed credentials after an explicit authentication failure. Expired sessions now show sign-in guidance, and failed refreshes are no longer presented as an empty confirmation list.
