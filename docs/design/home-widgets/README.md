# Monica Steam 桌面小组件

2026-10-08。设计采用本地 M3E Canvas。

[打开本地可编辑 Canvas](http://127.0.0.1:5186/#docz=3ZhNb9tGEIb_ijBnwuJ-cEnxmF5SpDm16KUwipW4klhTpECubLmGABftoYfChhH0A6jTomkKJIcEPgSuYAcwkN9iyfG_KChSlpYxtaqitE0vgihwxXdmn5l3lnsgfRkIcOF-FPoNXvlYCt6pvPqzMv7tu-uHj0Ynh1fn31yen4IBzZh30ju77SgUYEA34LIZxR1wgYdeHPle-iMPhJTinthN7-zF3SC9VbZFunQPPB5vgdvkQSIMqEeyfT_yRAKujHvCgE4k_SgEF0S_G4sk8bcFDPIHJ-B-tge-NycgzPRcHf86ev7L6PGL0deHl8OD8XA4_vZwdPLV9cNHYEAfXNOA3cnnDrgUYQPa4DLCBpsGtOKo153768ZOC2WLEJuswtQA3vcTcKEPBvhSdJTb_fT2LT9Mr6ToSzAg4HURFDKaLm2koYW9IDBgm8c-DyW40PSDQKSJS_wvRfa4ehR4WUYGmwNjJgwrwpijEYZnwupRf07XslqIY2ZfMbiIUgNi7vm95JOoCy52ppd3IimjTvZL-gfgQtKLm7whPohCyf1QxB9FO6CGQrJQ0jW74KbPWRgKmYXid3hL_L1g4ga40Jaym7jVKt_mksfJBt_iHe5vJOnmJJJLv7HRiDrVhuVZDNOaEJaHHW7aXp3yRq1pN5nJmzWGmrxuUcw-b_aCYOOLbmuWLXaTrPTrXK6QowZPs-BtOgne1u0jLQUsq9Wrg6ej56fLbmpKTxlgliIMmVijzCpVltfg2YNJJ_n5xfiHk8uzgwqiLsFLK0WqOpapIxZeDho2B00jCu_0pIzC28gB2Zu0k5mSPJp8Y7Gqw1bYRUSXJftt6xChGVpMLUPEimWY_pKXYTf2OzzevSlDMKARxaGIk7QTywBcbBogY3DTppNf1tPLgRqxk0VMsz6KKNFE7JRygTbwVqW9dDNE5azWVE0202iqlbO6fz7-8fT6-4tVwURm3pjpskhMVvwrTBRb812_1X6TC6pgQW-nAt0Y5bJcoHKvRNhZBxUIFzRpuUC4VNPo7MHo2U8rUzH1OLY0FeS9oGLSJWZYYLPIBZ02yKW5KLe4V38QAzvmWtiwCrr0bOgM7vLlyWj_5cqEMHXS1APC1jnS4XQSXt9Ih1RfxFSbXfs9mOrSaKb5ctSCooX4c5dk03ODrYu_3CbHx_uvL44yxpamy1qAfu6W2JqKq-nElfvlSlNnAX1sqsMcprpjFjbf0TiHkcqtM1Oye7uSORcL_ER-KEVnlVK0FkxqcwJ9bK39ebf2-0JaciclOTE1XTljvL5ybnhhWSlPrqu8202qhNkWNqttwT0RK1Vbm9kgtZSqLRzFMJmObjQPU3uqJqWFcTcKgminci_0W225dGUsOvBTVR3RvozA5U46fvb76PjJeDi8Oni6ctkyBQpiI50e9k9DQRFB1ltCYRfSbusGGGwv6JY89sRO5VMeBGJ3LVQ4BXk1XTPH5U7z-uLocvh4dPSk4ix_LnsDi5oyyFCmxbT2toMMZnPvpshaBxliKoZAtS9oiPm_GmQIUgYZqn87V36sW2mQWcA-yV1pMitPNkdnSwS_o7mBqO8wLaRVQtbi45gsmhtUiVRp1xbWkkz_Ox7uzICleFG7Jvm5Dk3DJFpgy891q3k4WUAsK6iztJvA1u_hm4O_AA)

- `canvas.json`：带内嵌图片的可移植设计文档；`canvas-dark.json`、`canvas-empty.json` 与 `canvas-configure.json` 保留对应状态。
- `canvas-preview.png`：Canvas 的真实预览。`native-preview.png`：Android RemoteViews 渲染的深浅主题对照，示例数据。
- `native/`：1.0 / 1.5 字体倍率、380 / 260 / 180dp 游戏组件和 380 / 250dp 统计组件的真实渲染矩阵。
- `assets/sources.json`：演示封面和公开 Steam 头像的来源。图片只作为设计、Android 测试素材；生产组件显示用户所选账号及其游戏的实际图片。

## 原因与修复

1. 应用内的 requestPinAppWidget 原先未执行账号配置，也未提供绑定回调。现在先选择账号，再通过系统返回的 widget ID 保存账号来源；已有未绑定组件可以直接点按补选。
2. MDBX 账号使用负 runtime ID，旧偏好读取将负 ID 过滤为 null；配置页和数据读取也只支持 Room。现保留非零 ID，配置支持本地/MDBX，并按已保存来源读取同一份加密库缓存，不切换应用全局账号。
3. 原更新广播等待资料和图片网络请求，之后只读缓存的初版又不能补齐缺失图片。现在先发布缓存；WorkManager 独立补图、逐张发布、有限重试，并复用应用的 Steam 头像缓存。
4. 1.5 倍字体下，原固定统计布局越过内容下边界（293px > 280px）。现为紧凑控件提供有界自适应字号、两行游戏名和窄尺寸布局。外缘 20dp / 接缝 4dp，横纵一致；单卡片四角 20dp。

上述问题在代码与测试中确认；无法仅凭用户截图判断其设备具体命中了哪条路径。离线时展示已有缓存，资料时间明确标注，不将未知库存和估值显示为零。

## 验证

- 修复前设备回归：MDBX ID 读取期望 -91234，实际 null；1.5 倍字体的统计容器越界。均已在修复后通过。
- JVM：59 条相关测试，0 failures / errors，包括图片逐张发布不被慢资料请求阻塞。
- 公共 API 32 AVD：8 条设备测试通过，涵盖系统 pin 回调、正负 ID、加密缓存、应用头像缓存、过期状态、图片和文本渲染、横纵分组圆角、窄尺寸及 1.5 倍字体。
- 单独启用联网验证并在最终设备测试中再次确认：真实 Steam 头像与 Hollow Knight 封面下载成功。
- 普通版 `:app:assembleDebug` 成功，已生成 arm64-v8a / armeabi-v7a 两个 APK；构建保留既有 R8 Kotlin metadata 兼容警告。未交付安装包。
- 设备渲染使用示例统计和公开图片，不代表用户真实账号数值。
