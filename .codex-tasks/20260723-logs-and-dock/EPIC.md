# 日志与悬浮 Dock

## Goal

修复开发者设置中的日志读取、复制和文件分享，并以 EssentialsFloatingToolbar 的布局与动效重制 Monica Steam 的底部 Dock。Dock 固定四个可排序页面，右侧显示当前 Steam 账号头像，点击后全局切换账号。

## Constraints

1. 日志分享必须使用 FileProvider 附件与系统分享器。
2. 日志采集必须存在进程超时，界面始终结束加载状态。
3. Dock 使用 Material 3 HorizontalFloatingToolbar，保持悬浮、48dp 触控尺寸、动态标签宽度和安全区留白。
4. 账号切换写入共享 SteamAccountRepository，所有页面观察同一选中账号。
5. 不构建 APK；每个子模块单独提交并推送 `main`。

## Child Deliverables

1. 修复日志采集与文件分享。
2. 重制悬浮 Dock 并接入全局账号切换。
3. 执行整体验证与任务归档。

## Done-When

- [x] 日志采集具备超时和可见失败状态，复制与附件分享可用。
- [x] Dock 无整宽背景，左侧四页加右侧账号头像，账号切换在全局生效。
- [x] 单元测试、Kotlin 编译和静态检查通过。
- [x] 每个子模块提交并推送 `origin/main`。
