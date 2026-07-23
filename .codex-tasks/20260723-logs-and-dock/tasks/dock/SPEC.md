# 悬浮 Dock 与账号切换

## Goal

复用 EssentialsFloatingToolbar 的 Material 3 悬浮结构和弹簧宽度动效，左侧固定四个 Dock 页面，右侧显示当前 Steam 账号头像并打开全局账号切换 Sheet。

## Acceptance Criteria

1. Dock 没有整宽背景容器，安全区内保留 16dp 外边距。
2. 四个页面由设置排序，选中页显示标签并使用弹簧宽度动效。
3. 右侧头像使用共享 SteamAvatarImage，点击显示账号列表并写入 SteamAccountRepository.select。
4. 无账号时显示默认账号图标并仍可打开账号管理入口。
