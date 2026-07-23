# Progress Log

## Session Start

- **Date**: 2026-07-23
- **Task name**: steam-stability-audit
- **Task dir**: `.codex-tasks/20260723-steam-stability-audit/`
- **Spec**: `EPIC.md`
- **Plan**: `SUBTASKS.csv` (5 milestones)
- **Environment**: Android/Kotlin/Compose; APK 构建明确禁止

## Context Recovery Block

- **Current milestone**: #5 — 完成度审查与最终验证
- **Current status**: DONE
- **Last completed**: #7 — 独立 Steam 通知页面
- **Current artifact**: `SUBTASKS.csv`
- **Key context**: 当前基线测试为 528 通过、1 跳过；用户报告多次随机闪退，优先审查 `requireNotNull(selectedGame)` 和生命周期竞态。
- **Known issues**: 自动化验证不包含真实 Steam 账号的设备级网络联调；Steam HTML/API 未来变更仍需现场日志复核。
- **Next action**: 用户安装验证商店详情、愿望单与礼物通知的真实账号行为。

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

---

## Milestone 3: Compose 列表 key 与数据边界

- **Status**: DONE
- **What was done**: 对第三方/缓存可重复数据的 Lazy 列表统一改用索引复合 key，覆盖购物车、愿望单、游戏库、成就、区域价格、WebDAV 备份、库存批量出售和交易报价；保留数据库/枚举天然唯一 key。
- **Validation**: `./gradlew :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.library.SteamLibraryIntegrationGuardTest' --tests 'takagi.ru.monica.steam.store.SteamStoreCollectionUiGuardTest' --tests 'takagi.ru.monica.steam.trade.SteamTradeOfferIntegrationGuardTest' --tests 'takagi.ru.monica.steam.market.SteamInventoryLazyKeyTest' --tests 'takagi.ru.monica.steam.ui.SteamDuplicateLazyKeyTest' --tests 'takagi.ru.monica.steam.backup.SteamRemoteBackupLazyKeyTest' --no-daemon --max-workers=1 --console=plain` → exit 0
- **Files changed**:
  - `app/src/main/java/takagi/ru/monica/steam/store/SteamStoreModels.kt` / `SteamNativeCartScreen.kt` — 购物车/愿望单 key。
  - `app/src/main/java/takagi/ru/monica/steam/ui/SteamLibraryScreen.kt` — 游戏、成就、区域价格 key。
  - `app/src/main/java/takagi/ru/monica/steam/store/SteamStoreScreen.kt` — 商店区域价格 key。
  - `app/src/main/java/takagi/ru/monica/steam/backup/SteamMaFileWebDavService.kt` / `SteamBackupScreen.kt` — WebDAV 备份 key。
  - `app/src/main/java/takagi/ru/monica/steam/trade/SteamTradeOfferModels.kt` / `SteamTradeOffersContent.kt` — 交易报价 key。
  - `app/src/main/java/takagi/ru/monica/steam/ui/SteamBatchSellSheet.kt` — 库存批量出售 key。
  - `app/src/test/java/...` — 重复数据回归测试。
- **Next step**: Milestone 4 — ViewModel 协程与生命周期竞态

---

## Milestone 4: ViewModel 协程与生命周期竞态

- **Status**: DONE
- **What was done**: 将安全事件 Flow collector 固定到 ViewModel `init`，避免组织排序等状态更新重复创建永久 collector；为账号、详情、区域价格、库存市场、交易报价、登录审批和 MDBX 存储源请求增加 generation/账号校验，旧响应不再覆盖当前状态；会话刷新绑定请求开始时的存储源。
- **Validation**: `./gradlew :app:testDebugUnitTest --tests 'takagi.ru.monica.steam.ui.*' --tests 'takagi.ru.monica.steam.store.*' --tests 'takagi.ru.monica.steam.trade.*' --no-daemon --max-workers=1 --console=plain` → exit 0；22 suites / 51 tests / 0 failures / 0 errors。
- **Files changed**:
  - `app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt` — 账号与存储源 generation、collector 生命周期保护。
  - `app/src/main/java/takagi/ru/monica/steam/store/SteamStoreViewModel.kt` — 详情与区域价格过期响应保护。
  - `app/src/test/java/takagi/ru/monica/steam/ui/SteamAccountRequestGuardTest.kt` — 账号/存储源 guard 回归测试。
  - `app/src/test/java/takagi/ru/monica/steam/ui/SteamViewModelCollectorGuardTest.kt` — collector 数量回归测试。
  - `app/src/test/java/takagi/ru/monica/steam/store/SteamStoreViewModelRaceTest.kt` — 商店详情竞态回归测试。
- **Commit**: `1c73dbc fix: isolate stale Steam account requests`（已推送 `origin/main`）。
- **Next step**: Milestone 5 — 完成度审查与最终验证

---

## Milestone 5: 完成度审查与最终验证

- **Status**: DONE
- **What was done**: 完成后台入口、网络/存储异常、ViewModel 竞态、Compose key、商店详情与愿望单重定向、独立通知详情与礼物动作的收尾验证。
- **Validation**: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon --max-workers=1 --console=plain` → 147 suites / 551 tests / 0 failures / 0 errors / 1 skipped，BUILD SUCCESSFUL。
- **Residual risks**:
  - Steam 礼物和愿望单仍依赖官方未公开网页形态；当前已对重定向和礼物动作缺失做安全降级。
  - 自动化环境没有真实账号，无法代替安装后的登录态、地区和礼物现场联调。
  - 项目仍继承 Monica 大量通用模块，存在编译时间与体积成本，但本轮未进行破坏性依赖裁剪。
- **APK**: 未运行 assemble/bundle/package APK 任务。
