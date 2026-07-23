# 进度

## 当前状态

Monica Steam 全部 Steam 功能模块整理已完成。legacy `steam/ui`、Steam 根包和旧 `steam/service` 均不再包含 Kotlin 实现；所有仍含 Kotlin 的 Steam 根模块均由架构守卫完整登记。

## 模块目录约定

每个功能使用独立的根目录，并按 `domain`、`data`、`presentation`、`ui` 分层。共享账号数据库、网络客户端、图片缓存、主题和导航基础设施保留在公共目录。Activity 只依赖模块公开入口，不能引用模块内部的数据适配器或状态管理。

## 恢复信息

任务：整理 Monica Steam 全部 Steam 功能模块

形态：epic

进度：10/10

当前：全部完成

文件：`.codex-tasks/20260723-steam-modularization/SUBTASKS.csv`

下一步：无；后续功能继续遵循现有模块边界。

## 已完成

### 子任务 1：功能目录规范与架构守卫

- 新增 `docs/architecture/STEAM_MODULES.md`，定义 feature Module、Interface、Implementation、Seam、Adapter、Depth、Leverage、Locality 及依赖规则。
- 新增 `SteamModuleArchitectureGuardTest`，阻止根包和 legacy `steam/ui` 继续堆积实现，并限制 Activity 依赖 feature public UI entry。
- 验证：`:app:testDebugUnitTest --tests "takagi.ru.monica.steam.architecture.*"`，包含 `compileDebugKotlin`，通过。
- 独立提交：`4c27b08`。

### 子任务 2：资料、账号组织与扫码模块

- 资料 UI 位于 `steam/profile/ui`，包括动态背景与裁剪；通用账号头像在后续库存/市场迁移中提升至 `steam/foundation/ui`。
- 账号组织 UI 位于 `steam/organization/ui`；组织规则仍由 `steam/organization` 提供。
- 扫码页面位于 `steam/scanner/ui`，扫码偏好和 MDBX/本地存储选择位于 `steam/scanner/data`。
- 更新 `MainActivity`、`MonicaSteamActivity`、令牌页、游戏库页和 ViewModel 的公开入口引用。
- 验证：`:app:compileDebugKotlin` 通过；聚焦 58 个测试通过；架构守卫再次通过。
- 独立提交：`adde11b`。

### 子任务 3：游戏库模块

- 游戏库页面从 legacy `steam/ui` 迁移至 `steam/library/ui`，保留游戏库 ViewModel、缓存和详情/成就/多区价格行为。
- 更新 `MonicaSteamActivity`、库测试和稳定性/动效守卫的路径与包引用。
- 验证：`:app:compileDebugKotlin` 通过；库、动效、稳定性和架构聚焦测试通过。
- 独立提交：`f0f436b`。

### 子任务 4：库存与市场模块

- 库存/市场组合页面从 legacy `steam/ui` 迁移至 `steam/inventory/ui`；批量出售 Sheet 迁移至 `steam/market/ui`。
- 抽离 `steam/foundation/ui`：共享账号卡、账号切换 Sheet、通用头像与远程图片缓存，消除库存页面反向依赖令牌页实现。
- `SteamScreen`、库存和交易页面通过共享 Interface 使用同一实现，没有复制扫码、账号切换或图片缓存逻辑。
- 验证：`:app:compileDebugKotlin` 通过；库存、市场、批量定价、解析、分析、交易引用和架构聚焦测试通过。
- 独立提交：`769ad62`。

### 子任务 5：交易模块

- 交易报价页面从 legacy `steam/ui` 迁移至 `steam/trade/ui`，交易模型与网络实现继续由 `steam/trade` 持有。
- 账号切换、账号卡和报价图片复用 `steam/foundation/ui`，没有复制 UI 或缓存实现。
- 验证：`:app:compileDebugKotlin`、交易模块测试和架构守卫通过。
- 独立提交：`bc76af7`。

### 子任务 6：备份与健康检查模块

- 备份页面迁移至 `steam/backup/ui`，保留 WebDAV、MDBX、maFile ZIP、SAF 导入导出和身份验证。
- 健康检查页面迁移至 `steam/health/ui`，保留账号报告、服务器时间和诊断文本导出。
- 验证：`:app:compileDebugKotlin`、备份/健康模块测试和架构守卫通过。
- 独立提交：`45e7d58`。

### 子任务 7：商店与购物车模块

- `steam/store` 按 `domain`、`data`、`presentation`、`ui` 分层；根目录不再直接放 Kotlin 实现。
- 商店首页/详情、原生购物车、愿望单和官方结算 WebView 位于 `store/ui`；缓存、解析、账号地区与会话策略位于 `store/data`。
- ViewModel 位于 `store/presentation`，模型与价格/购物车规则位于 `store/domain`。
- 验证：`:app:compileDebugKotlin`、全部 store 测试、礼物入口、稳定性/动效和架构守卫通过。
- 独立提交：`18fa988`。

### 子任务 8：通知、礼物与提醒模块

- notifications 按 `domain/data/ui` 分层；通知缓存、解析和页面分别归属明确。
- gift 模型、解析、响应操作与礼物入口迁移到 `gifts/domain`、`gifts/data`，不再藏在 notifications。
- alerts 按 `domain/data` 分层，登录请求通知也归入 alerts/data；Manifest 使用新 Receiver 路径。
- 验证：`:app:compileDebugKotlin`、通知/礼物/提醒、总边界和架构测试通过。
- 独立提交：`3ebbbe5`。

### 子任务 9：令牌页与令牌状态模块

- 令牌 Screen、搜索规则和协调状态迁移至 `steam/token/{domain,presentation,ui}`，公开函数名保持兼容。
- 全局 UI 缩放偏好和 CompositionLocal Provider 迁移至 `steam/foundation/ui`。
- 所有生产代码和回归测试切换到新路径；legacy `steam/ui` 不再包含 Kotlin 实现。
- 验证：`:app:compileDebugKotlin` 通过；令牌、导入、搜索、缩放、市场保护、动效及架构共 90 个相关测试通过。
- 独立提交：`7145a1d`。

### 子任务 10：共享基础设施与最终审查

- 将模糊的 `steam/service/SteamLoginImportService.kt` 原样迁入 `steam/token/data`，仅调整包名和引用，不改变登录、扫码、授权设备或验证器迁移行为。
- 架构守卫改为精确匹配所有含 Kotlin 的 Steam 根模块，并补充 `analytics`、`confirmations`、`gifts`、`importer` 的登记与文档说明。
- 最终结构检查：legacy `steam/ui`、Steam 根包和旧 `steam/service` 的 Kotlin 文件数量均为 0；27 个含 Kotlin 的根模块全部被守卫覆盖。
- 验证：`:app:testDebugUnitTest` 共 569 个测试，568 通过、1 跳过、0 失败；`:app:compileDebugKotlin` 通过；`git diff --check` 通过。
- 独立提交并推送：`9ee226d`。
