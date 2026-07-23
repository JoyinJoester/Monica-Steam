# Progress Log

## Session Start

- **Date**: 2026-07-23
- **Task name**: steam-stability-audit
- **Task dir**: `.codex-tasks/20260723-steam-stability-audit/`
- **Spec**: `EPIC.md`
- **Plan**: `SUBTASKS.csv` (5 milestones)
- **Environment**: Android/Kotlin/Compose; APK 构建明确禁止

## Context Recovery Block

- **Current milestone**: #3 — Compose 列表 key 与数据边界
- **Current status**: IN_PROGRESS
- **Last completed**: #2 — SAF 导出取消与异常边界
- **Current artifact**: `SUBTASKS.csv`
- **Key context**: 当前基线测试为 528 通过、1 跳过；用户报告多次随机闪退，优先审查 `requireNotNull(selectedGame)` 和生命周期竞态。
- **Known issues**: 尚未建立设备级复现环；将先用最小单元回归测试锁定状态竞态。
- **Next action**: 将第三方/缓存列表的业务 key 改为包含索引的防重复 key，并补回归测试。

---

## Milestone 1: 异步游戏/价格请求过期状态保护

- **Status**: DONE
- **What was done**: 为区域价格响应增加请求 generation、账号/游戏校验；将状态合并提取为可测试的空安全函数，避免详情页关闭后 `requireNotNull` 崩溃。
- **Key decisions**:
  - Decision: 过期响应直接丢弃，不回填当前页面。
  - Reasoning: 账号切换、关闭详情和重新打开游戏都可能让原选择失效。
- **Validation**: `./gradlew :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.library.*' --no-daemon` → exit 0
- **Files changed**:
  - `app/src/main/java/takagi/ru/monica/steam/library/SteamLibraryViewModel.kt` — generation 与空安全状态合并。
  - `app/src/test/java/takagi/ru/monica/steam/library/SteamLibraryStateRaceTest.kt` — 关闭详情/切换游戏回归测试。
- **Next step**: Milestone 2 — SAF 导出取消与异常边界

---

## Milestone 2: SAF 导出取消与异常边界

- **Status**: DONE
- **What was done**: 新增统一的 `SteamSafWriter`，健康诊断、库存/市场 CSV 与 maFile ZIP 导出均通过同一空安全写入边界；空输出流、写入异常和关闭异常都返回失败，不从 UI 协程抛出。
- **Problems encountered**:
  - Problem: 首轮编译发现移除的 `IOException` 仍被导入文件其他路径使用。
  - Resolution: 恢复该导入并重新验证。
  - Retry count: 1
- **Validation**: `./gradlew :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.io.SteamSafWriterTest' --tests 'takagi.ru.monica.steam.health.*' --tests 'takagi.ru.monica.steam.market.SteamInventoryLazyKeyTest' --tests 'takagi.ru.monica.ui.SteamInventoryMarketUiGuardTest' --no-daemon --max-workers=1 --console=plain` → exit 0
- **Files changed**:
  - `app/src/main/java/takagi/ru/monica/steam/io/SteamSafWriter.kt` — 统一 SAF 写入边界。
  - `app/src/main/java/takagi/ru/monica/steam/ui/SteamHealthScreen.kt` — 诊断导出。
  - `app/src/main/java/takagi/ru/monica/steam/ui/SteamInventoryMarketContent.kt` — CSV 导出。
  - `app/src/main/java/takagi/ru/monica/steam/ui/SteamBackupScreen.kt` — ZIP 导出。
  - `app/src/test/java/takagi/ru/monica/steam/io/SteamSafWriterTest.kt` — 空目标和 provider 失败回归测试。
- **Next step**: Milestone 3 — Compose 列表 key 与数据边界
