# 日志采集与分享

## Goal

通过限时进程读取避免日志弹窗永久加载，并把分享操作绑定到 Activity 生命周期，使日志附件写入和系统分享器调用不会因 Composable 退出而取消。

## Acceptance Criteria

1. 每个 logcat 进程拥有固定超时，超时后销毁并返回可诊断结果。
2. 刷新与初始加载对取消异常保持协程取消语义。
3. 分享文件写入 `cache/temp_share` 并使用 FileProvider URI。
4. 分享协程不使用 Dialog 的 rememberCoroutineScope。
