# Monica Steam 模块化整理

## Goal

将 Monica Steam 的各项 Steam 功能按功能目录独立存放，建立统一的领域、数据、状态和 UI 分层，减少 `steam/ui` 与超大页面文件带来的维护耦合。

## Non-Goals

不修改 Monica Android 主工程；不改变 Steam 功能行为、数据库结构、网络协议和现有视觉设计；不构建 APK。

## Constraints

1. 只修改 `Monica Steam`。
2. 复用现有 Material 3、Google Sans Flex、主题与页面动效。
3. 每个功能模块单独提交并推送 `main`。
4. 只运行单元测试、静态架构检查和 Kotlin 编译。
5. 共享账号、网络、图片缓存和主题基础设施不得复制到功能模块内部。

## Risk Assessment

1. 页面移动涉及大量 Kotlin import 和测试路径，采用小步迁移并逐模块编译。
2. `SteamScreen.kt` 与 `SteamViewModel.kt` 同时承担令牌、确认、通知、账户管理和多个数据面板，最后处理并保留兼容入口。
3. UI 文件拆分只改变文件与包位置，不改变公开 Composable 的行为。

## Child Deliverables

1. 建立 Steam 功能目录规范、依赖规则与架构守卫。
2. 整理资料、账号组织和扫码模块。
3. 整理游戏库模块。
4. 整理库存与市场模块。
5. 整理交易模块。
6. 整理备份与健康检查模块。
7. 整理商店与购物车模块。
8. 整理通知、礼物和提醒模块。
9. 拆分令牌页与令牌状态模块。
10. 整理共享 Steam 基础设施并执行最终审查。

## Dependency Notes

子任务 1 先完成；子任务 2-8 依赖子任务 1；子任务 9 依赖子任务 2-8 的目录约定；子任务 10 依赖全部前置任务。

## Done-When

- [ ] `SUBTASKS.csv` 中所有子任务为 `DONE`
- [ ] Steam 根包无跨功能 UI 实现堆放
- [ ] 架构守卫、全量测试和 Kotlin 编译通过
- [ ] 所有模块提交已推送 `origin/main`
