# 进度

## 当前状态

日志弹窗会在部分设备上持续加载。采集实现依次读取多个 `logcat` 进程且没有超时，任意进程不退出都会阻塞界面。分享操作依赖 `rememberCoroutineScope`，当开发者页面离开组合树时会捕获取消异常并展示截图中的失败提示。

Dock 当前使用 HorizontalFloatingToolbar，但外层采用整宽底栏容器，右侧为扫码 FAB。参考项目的工具栏使用最小外层、Material 3 vibrant 色彩、选中标签宽度弹簧动画和单独 FAB。

## 恢复信息

任务：修复日志与悬浮 Dock

形态：epic

进度：3/3

当前：全部完成

文件：`.codex-tasks/20260723-logs-and-dock/SUBTASKS.csv`

下一步：无。

## 完成记录

1. 日志采集使用 3 秒进程超时、临时文件输出和五类 logcat 并行读取；分享使用 Activity lifecycleScope 与 FileProvider 附件。提交 `c443187`。
2. Dock 使用 EssentialsFloatingToolbar 的悬浮结构、Material 3 vibrant 色彩与弹簧标签动效；右侧显示全局账号头像并复用通用账号 Sheet。提交 `a18a35e`。
3. 全量测试 576 个，575 通过、1 跳过、0 失败；`compileDebugKotlin` 和 `git diff --check` 通过。
