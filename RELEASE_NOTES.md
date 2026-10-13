# Monica Steam 1.0.310 发行说明

## 中文

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
