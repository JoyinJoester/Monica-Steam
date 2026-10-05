# Monica Steam 内置 SteamDB

2026-10-05。使用 SteamDB 替换原 ITAD 史低查询。无须 API Key；原有 ITAD 配置页改为内置数据说明，旧凭据仅保留兼容迁移，不再用于商品查询。

## 设计

游戏详情在购买操作之后提供单个紧凑入口，点击展开原生底部面板。各区价格展开后直接显示对应地区的 Steam 历史最低价；明确区分历史史低和近两年史低。玩家数、峰值、关注、评分、更新日期采用连续分组，缺失字段显示“—”。单项失败不阻塞其他来源。

延续应用 Material 3 动态主题。Canvas 基准配色为 primary `#0B57D0`、primaryContainer `#D3E3FD`、background `#F8F9FF`、surface `#F0F1F9`、onSurface `#1F1F1F`。使用应用字体，标题 titleLarge、价格 headlineSmall、数值 titleMedium、辅助文字 bodySmall。价格承担主要视觉层级，触摸目标至少 48dp，面板内容可滚动，不固定正文高度。

本地编辑器：`http://127.0.0.1:5186/`。可编辑源文件：[canvas.json](canvas.json)；[打开本地 Canvas](http://127.0.0.1:5186/#docz=5VndbttGGn0VYtDecWOSomSJd05zYyyyKODuxaIIDEoaR0QokjscJfYGBriO7UiNf5ptHBe20nSLZtfoNrbaGLUjyzDQR0lFSr7yKxTDGckULdGkN8223StbA37D853v7wznPsAa1iFQwE3T0AoqN4WhWuZ-PKD_3LjOeYeHXnXd22h4q7uAB3mkwRmgAG-ldlp33O823JbT2Vrs7P_bffiYPnX67Cvv2clPzoK3feKtfsVNfjRx46y17a403eqyu7bsrn_vrn_fPl5rH651Tx63D792X-_TlbPWdvvowPuy1T14frq17m4su81Nt95wV5pnrZVOq-l-_R19iVevdR592_nPo84nP3jO339yFgAPZpBaJr5YJdOAgAeWruIZE5WBAlSjiEytSBZVHWIM_wjngALyeoU8iEuQGN4HRRXdAcqMqtuQB3kTl26aRWgDBaMK5IFdUi2yPzIrRhGSzWZMA_sLeRObgAdlE2umARQAZy0EbVu7C8E8A2YD5eP7QCsCBRRVrAIeGBRukGJCffvwpbt97K40AQ9mgSLwYA4owjzPjC0VYU3Vz-1PH-y41WXGy9ZC-3DVXd_rPNmh5rIc3gDB2wRk3979dNFdaVLiyfsDcaNbZLPhLWyIsWbctgObLC91jncpiO7evvf5GrUVU1LP-BYPbiOzYgWIyKtIYM9J_lMpiQfqrGYDBcwBHtxWLaBIPNAwLF80u6MZ5Bc2rQnLuq4iwANdzUMdKOBDE2FV5yTAg7sq0lQ_TDOarvth0wp-kFSEzHvTebVwh61JQAFlE8HpuxBhwANb-xsESiqbpf9KQEln5m_1WYAGRnOD-EVBiONA35K5oGs2nsSwHPBgRAGO9uavFYjmpm2sYjvgDpy1VKM4TbwiDlUsy0QkdCRk9Z1O86TdbHobjX7Jc7QS_TyoO92Tx972vve0MZSM8QwPCiYyILJJ8WAdKCTYGNG_efY77_-eDxBnlyDEQjC9JSFW4PuGjLe8ibFZniKrAeqiaDIqut7zRSZBY75kxrMBgH5XZAAlmSJMZeMg7Fv2khPO4v8iqkG4khRAiOAMgnaJYUylafpJUiyQAWMGk7ztegVjvy_0wLrVAxr60UnHdgJ9HuXsAEjSaUI8jseE2DNlCAslzQpgo02SpOkHf_pLJESrkte1AgjAspBWCEXX71KXo-pbjq7b4ITjfnzBpSLBlTQbm2guXJqhuUj2ka9JAnHXfdjsrDW4FOd9-8_zqszI_QiQAr2kKtO0KNPBmjQNXTNCrMhiLFbOTSNoOf7Mra3SlsNxaV6WhEhi2KQI8cJa1O7n3tPX_eK5SEHmIgVpykB6NAEWVO-E3M_GSwpmONp5Sebcxrq3-YP7quE6LY7L8qIkcBzHkoWt5rK8nImkhbWDgS6-9Mp7tcO6OJeRZF7MCm-PFbtgolBWpNOxaOlbXj7jOrub3quN7t6iW13muFzmWkp8PzI7bOxP-wEivJrj1WvdvZfdHed8nHX3X7ZfN7p7i-2jA7IqCxIvCgLnPRtePVciqWIVVRxiKROvo5ybjqYpOIg5ThKkzFhuLBVdP3TfofVz4zrX3W24xxudrUW6qbf5wqs_vxofIya8WUHhHjser5ucm_aGfHguebXP3JbTO6IM0IBNw5fGjAXTgsa0Zkwb8N5Q7wam1Yxp4hDi8XQcxD3D4SOfCfNnL7oPv2FJOTYUe8ToF4OjP68ikWn7dELVLP5aVHPIgSSyWfz_ls3i4LEuiXAW35Fw7kHMJFfO4jtTzgzkeO4qyll8N8o5TGQS6Sz-ctI5BCuJdo5RvKyUQl8yEkvozs6q--na6cPV7t7GWWvFqz4dHBuc9_xFp_7o7UvoEDuJNHQ0Pb8NDR32P76Ijvb-goh-4zy5IKHfOE8S6ud-OmwtePVvuidP3O0v3jYtvhQO8ZJERccbeb8PFR2iKZGMjuZpUEZfkim_DgEdIiORghb_Nwo6DDm-hB49-9-6hKYjMu-nP_nAnUuio4PGEWI6-Fk9vqC-RDzTdyPz3iBwUUyAnFpHjJnVzXZzi5T5n6duxNEHYYHsjykWJ3at8B6XvjaeuyiwdWjbVxXJw3oI9dCXGiGGcrFKZ9D-UqXizx6v7pDbrKMD7j3hWlq-ilh5U_1HTng_8HlPTJPve2RBEqT0mDQmCkNpGqJU4nQX6qZhYsZSTmBSTohVrQPm0XKdZtEfpiamJif8y62jpd6Jyb_du7w-Boo4dcEJ0qBCTsgJZDIzf8eNsnd_1u9AYiqVpAUN2kc0ocTH4UuaUUYe4gWdNyFHxHiD-8IeEX2JXjFe7WA_KHI2vzytO6dLq53jXW7iw0mO3AbHLC_yacjvQnKE3mNOlaBuhWiRYo7wwR0iSPEPS_S-3Ks5p04teGt-he7dPlqjFHf3_uU9WPKqT4ODjN74h49OiRqSzBqSPIyxAoJFDfc4k1lBpxJx1t_jFxQTw8qAvlcKg89kkoOXRqJ3P3nefXDsri90nQfcTYLQMLRK-axVvTn50VmrlsiDW_M_Aw)；链接同时保存在 [canvas-url.txt](canvas-url.txt)。在父工作区执行 `.tools/start-m3e-canvas.ps1` 可启动或复用本地 Canvas。

## 参考与实现

- [Millennium](https://github.com/Good-Joe2049/Millennium/tree/ba9de8dea582d068774de90e1a7b32706787165e)：扩展接口、地区映射、评分公式与按需缓存；MIT 授权全文随应用打包于 `assets/licenses/millennium.txt`。
- 核对 [SteamDB Browser Extension](https://github.com/SteamDatabase/BrowserExtension/blob/master/scripts/store/app.js) 的字段语义：`l` 是近两年史低，而非活动说明或折扣。
- 游戏信息共用 ExtensionApp；史低使用 ExtensionAppPrice。当前在线优先读取 Steam 官方人数，失败可显示 SteamDB 记录。评分独立读取所有语言的 Steam 购买评价，避免使用当前页面的语言或筛选结果。
- 成功缓存：人数 30 秒、游戏信息 60 秒、价格与评分 5 分钟，各类最多 64 项。相同查询合并请求，关闭页面可取消观察，已发出的共享请求完成后缓存；无后台轮询。
- 尊重 429 / Retry-After，手动刷新也不能绕过等待。网络或服务异常时可展示带时间的上次可用数据。免费游戏、未知地区或币种跳过价格查询。
- 仅发送公开 AppID 和价格地区；不向 SteamDB 发送 Steam Cookie、令牌或账户配置。

## 验证

SteamDB 专项单元测试与关联回归共 **45 项通过**。Release 已构建成功，核验包名 `takagi.ru.monica.steamapp`、版本 `1.0.309` / `18`；日志保存在 `.codex-temp/steamdb/release-final.log`。

公共 AVD `Monica_Issue136_API_32`，Android 32 / x86_64，320dp 宽度；独立测试包 `takagi.ru.monica.steam.recoverytest`。最终设备测试 **7 项通过**：按需展开、近两年史低标注、部分失败与重试、1.5 倍字体和深色主题、切换游戏隔离旧请求、免费游戏、无需密钥的设置页，以及公开数据接口检查（部分检查合并在同一测试）。

真实接口仅使用 Dota 2（570）及 Portal 2（620）：游戏数据、中国区与南亚区价格、在线人数和所有语言评价均通过。模拟器直连路径不可用，使用电脑现有本地代理完成 HTTPS 查询；结束后已恢复原代理设置，并停止本轮启动的公共 AVD。没有修改应用证书验证或代理设置，未读取用户 maFile 或发送聊天消息。

以下截图来自原生 Compose 页面，使用固定测试数据，不代表当前实时价格或人数：

- [数据面板](native-data.png)
- [深色与大字体](native-large-dark.png)
- [部分数据失败](native-partial.png)
- [地区价格](native-region.png)
- [内置数据说明](native-settings.png)

设备日志：`.codex-temp/steamdb/device-tests.log`；测试包构建：`device-build-final.log`。真实 HTTP 检查样本与源码参考保存在同一临时目录。
