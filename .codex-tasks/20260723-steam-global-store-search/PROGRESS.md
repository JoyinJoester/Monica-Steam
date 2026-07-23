# 进度

## 当前状态

现有 `/api/storesearch` 请求固定使用账号地区，因此 Steam 会在服务端过滤该地区未售卖的商品。详情接口也只请求账号地区，锁区候选即使取得 App ID 也无法显示详情。现有 `IStoreBrowseService.GetItems` 已能按地区批量判断价格与可用性，多区价格 Sheet 可以继续复用。

## 恢复信息

任务：扩展 Steam 商店搜索以发现锁区商品

形态：single-full

进度：4/4

当前：全部完成

文件：`.codex-tasks/20260723-steam-global-store-search/TODO.csv`

下一步：无；后续可根据实际搜索命中情况调整代表性地区集合。

## 验证记录

- `SteamStoreGlobalSearchTest` 红灯验证：测试编译按预期因待实现的合并函数、地区字段和详情回退函数失败。
- `:app:compileDebugKotlin`：通过。
- `:app:testDebugUnitTest --tests "takagi.ru.monica.steam.store.*"`：通过。
- `:app:testDebugUnitTest`：573 个测试，572 通过、1 跳过、0 失败。
- `git diff --check`：通过。
- 功能提交：`cb39cca`，已推送 `origin/main`。

## 完成内容

1. 搜索同时查询账号地区及美国、中国、日本、韩国、德国、俄罗斯目录，支持取消正在进行的网络请求。
2. 账号地区返回的商品与价格保持优先，其他地区结果按 App ID 去重补充，并按名称相关度排序。
3. 搜索列表显示“当前账号地区不售卖”和参考地区价格。
4. 账号地区详情缺失时，优先使用搜索发现地区加载公开详情。
5. 锁区详情保留多区比价和 Steam 官方页面，限制新增购物车及愿望单操作，已有条目仍可移除。
6. 多区价格范围增加德国、英国、巴西、俄罗斯。
