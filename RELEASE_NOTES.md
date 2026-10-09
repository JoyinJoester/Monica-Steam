# Monica Steam 1.0.310 发行说明

## 中文

- 商店首页合并搜索与顶栏，显示圆形账号头像；特别优惠改为突出主卡片的滑动轮播，热销榜只展示游戏。
- 游戏详情采用紧凑布局，多区价格优先显示当前地区；用 SteamDB 替换 ITAD 价格查询入口。
- 桌面小组件修复账号绑定、MDBX 来源和缓存读取，显示真实头像与游戏封面，并采用外缘大圆角、相邻小圆角的自适应分组卡片。
- 新增临时登录：密码、验证码和扫码登录均可选择不保存本次凭据，退出应用或进程结束后移除临时账号；不会覆盖原有账号。临时账号不支持内置网页登录、小组件或账号导出。
- 修复商店侧边栏在点击遮罩、拖拽或中断关闭动画后可能留下黑色遮罩、阻挡主页操作的问题。
- 修正确认列表请求的签名标签，并使用 Steam 服务器时间签名，避免手机时间偏差导致确认刷新失败。
- 确认列表明确返回会话失效时，更新账号会话并最多重试读取一次；失败提示提供重新登录指引，不再将刷新失败显示为“暂无待确认项目”。

## English

- Integrated store search into the top bar with a circular account avatar, a focused offer carousel and a games-only top-sellers section.
- Made game details and regional prices more compact, placed the current region first, and replaced ITAD price lookup with SteamDB.
- Fixed widget account binding, MDBX source lookup and cached data loading. Widgets use real avatars and game covers with adaptive grouped cards and smaller adjoining corners.
- Added temporary sign-in for password, verification-code and QR flows. Credentials remain in memory and the temporary account is removed when the app exits or its process ends, without replacing saved accounts. Signed-in web pages, widgets and account export are unavailable for temporary accounts.
- Fixed the store drawer leaving a dim overlay and blocking the home screen when dismissal animations were interrupted.
- Corrected the confirmation-list signature tag and use Steam server time for confirmation signatures instead of the phone clock.
- Retry confirmation reads once with refreshed credentials after an explicit authentication failure. Expired sessions now show sign-in guidance, and failed refreshes are no longer presented as an empty confirmation list.
