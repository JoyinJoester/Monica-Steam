# Epic Specification

## Goal

- 全面审查 Monica Steam 的闪退风险，修复可验证的高风险路径，并补齐回归测试与完成度审查报告。

## Non-Goals

- 不构建 APK。
- 不修改 Monica Android 原项目。
- 不在本轮新增与稳定性无关的大型业务功能。

## Constraints

- 仅修改 `Monica Steam` 仓库。
- 只能运行单元测试、静态检查和 Kotlin 编译；遵守现有架构与依赖。
- 每个独立修复模块单独提交并推送 `main`。

## Risk Assessment

- Steam 网络响应和用户操作具有竞态，必须验证过期请求不会写入当前页面状态。
- Compose 列表数据可能来自重复或不完整的第三方响应。
- 文件选择器取消、生命周期销毁和账号切换是高频边界条件。

## Child Deliverables

- 异步游戏/价格请求的过期状态保护与回归测试。
- SAF 导出取消和异常边界的安全处理。
- Compose 列表 key 与页面数据完整性审查。
- ViewModel 长期 collector、协程取消与生命周期竞态审查。
- 全量验证与独立版完成度/架构审查报告。

## Dependency Notes

- 列表和生命周期审查可与前两项并行分析，但最终验证依赖所有修复完成。
- 最终审查依赖前四项完成。

## Child Task Types

- `single-full`

## Done-When

- [ ] 每个子任务均已验证并在 `SUBTASKS.csv` 标记为 `DONE`。
- [ ] 全量测试和 `:app:compileDebugKotlin` 通过。
- [ ] 无 APK 构建。
