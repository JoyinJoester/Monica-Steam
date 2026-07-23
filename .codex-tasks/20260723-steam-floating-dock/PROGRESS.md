# Progress

## Recovery

- 状态：DONE。
- 参考：`EssentialsFloatingToolbar.kt` 使用 `HorizontalFloatingToolbar`、选中项弹簧展开标签、独立 `floatingActionButton` 和导航栏 Insets。
- Monica 接入点：`MonicaSteamActivity.kt` 的 `SteamStandaloneDock` 与 `Scaffold.bottomBar`。
- 实现：四个可排序项进入浮动工具栏，当前项展开标签；扫码作为固定右侧 FAB 接通现有 `SteamQrScannerScreen`；小屏/大字体隐藏展开文字；长标题支持 marquee。
- 许可：已在 `THIRD_PARTY_NOTICES.md` 保留 Essentials 原作者与 MIT 许可文本。
- 验证：148 suites / 552 tests / 0 failures / 0 errors / 1 skipped；Kotlin 编译通过；未构建 APK。
