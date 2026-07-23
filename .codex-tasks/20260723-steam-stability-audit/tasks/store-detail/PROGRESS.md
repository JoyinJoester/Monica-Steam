# Progress

## Recovery

- 状态：DONE。
- 根因：详情目的地依赖已加载实体；商店 OkHttp 客户端保留默认自动重定向。
- 修复：新增 `detailAppId` 导航状态和可恢复错误页；禁用自动重定向并把 3xx 识别为会话失效；操作区改为三个全宽 M3 按钮。
- 验证：商店 ViewModel、愿望单和 UI Guard 共 12 项测试通过，源码编译通过。
