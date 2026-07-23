# 进度

## 当前状态

好友模块已经完成独立化，当前扩展到 Monica Steam 全部 Steam 功能。审查发现 `steam/ui` 中仍有大型跨功能页面：`SteamScreen.kt` 6488 行、`SteamViewModel.kt` 2936 行、`SteamLibraryScreen.kt` 2206 行、`SteamInventoryMarketContent.kt` 1441 行。

## 模块目录约定

每个功能使用独立的根目录，并按 `domain`、`data`、`presentation`、`ui` 分层。共享账号数据库、网络客户端、图片缓存、主题和导航基础设施保留在公共目录。Activity 只依赖模块公开入口，不能引用模块内部的数据适配器或状态管理。

## 恢复信息

任务：整理 Monica Steam 全部 Steam 功能模块

形态：epic

进度：1/10

当前：整理游戏库模块

文件：`.codex-tasks/20260723-steam-modularization/SUBTASKS.csv`

下一步：将 `SteamLibraryScreen.kt` 及其相关 UI/状态实现迁移到 `steam/library`，保持游戏库行为和缓存不变。

## 已完成

### 子任务 1：功能目录规范与架构守卫

- 新增 `docs/architecture/STEAM_MODULES.md`，定义 feature Module、Interface、Implementation、Seam、Adapter、Depth、Leverage、Locality 及依赖规则。
- 新增 `SteamModuleArchitectureGuardTest`，阻止根包和 legacy `steam/ui` 继续堆积实现，并限制 Activity 依赖 feature public UI entry。
- 验证：`:app:testDebugUnitTest --tests "takagi.ru.monica.steam.architecture.*"`，包含 `compileDebugKotlin`，通过。
- 独立提交：`4c27b08`。

### 子任务 2：资料、账号组织与扫码模块

- 资料 UI 位于 `steam/profile/ui`，包括动态背景、裁剪、头像渲染；头像网络加载与缓存不再由令牌页持有。
- 账号组织 UI 位于 `steam/organization/ui`；组织规则仍由 `steam/organization` 提供。
- 扫码页面位于 `steam/scanner/ui`，扫码偏好和 MDBX/本地存储选择位于 `steam/scanner/data`。
- 更新 `MainActivity`、`MonicaSteamActivity`、令牌页、游戏库页和 ViewModel 的公开入口引用。
- 验证：`:app:compileDebugKotlin` 通过；聚焦 58 个测试通过；架构守卫再次通过。
- 独立提交：`adde11b`。
