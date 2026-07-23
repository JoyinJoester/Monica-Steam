# Monica Steam Floating Dock

## Goal

- 复用 Essentials `EssentialsFloatingToolbar` 的 M3 Expressive 浮动 Dock 结构。
- 左侧保持 Monica Steam 四个可排序顶级页面，当前项展开文字。
- 最右侧使用独立、固定、不参与排序的扫码 FAB，进入现有扫码页面。

## Constraints

- 仅修改 Monica Steam。
- 保留 Dock 第一项作为主页、现有页面状态和返回栈语义。
- 扫码按钮最小 48dp，并始终位于四个 Dock 项右侧。
- 不构建 APK；只运行单元测试和 Kotlin 编译。

## Done When

- 浮动工具栏复用 Essentials 的 `HorizontalFloatingToolbar`、弹簧展开标签和独立 FAB 结构。
- 四个可排序项仍由 `SteamDockTab.sanitizeOrder(order)` 驱动。
- 扫码 FAB 固定在右侧且调用 `navigateTo(MonicaSteamPage.SCANNER)`。
- Dock 专项测试和 Kotlin 编译通过，改动提交并推送。
