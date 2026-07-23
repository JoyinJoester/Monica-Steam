# Store Detail Recovery

## Goal

- 修复部分商品点击无反馈、愿望单重定向循环，并按 Monica M3 层级精简详情操作区。

## Done When

- 点击有效 appId 后立即进入详情目的地，加载失败仍可重试或打开官方页面。
- 愿望单响应重定向不会触发 OkHttp 21 次 follow-up。
- 购物车是唯一主操作，愿望单状态明确，购买选项独立且触控区不小于 48dp。
