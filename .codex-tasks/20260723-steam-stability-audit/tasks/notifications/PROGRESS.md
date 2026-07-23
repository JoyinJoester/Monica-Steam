# Progress

## Recovery

- 状态：DONE。
- 根因：Steam 当前礼物 HTML 可能只在礼物块中暴露拒绝回调，旧解析器因此丢失两个接受动作；通知行没有点击目的地。
- 修复：普通游戏礼物保留 `acceptunpack` / `accept` 能力并兼容 1/0 标记；新增独立通知页、筛选、可滚动详情 Sheet 和礼物动作卡；返回只回确认页。
- 验证：`*SteamNotification*` 与 `*SteamGift*` 测试集合通过，Kotlin 编译通过。
