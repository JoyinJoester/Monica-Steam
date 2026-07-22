# Monica Steam

Monica Steam 是 Monica Steam 页面对应的独立 Android 应用。应用保留 Steam 页面、账号存储、Steam Guard 验证码、登录批准、授权设备、库存、市场和二维码登录，并从应用入口和 Android 清单中移除 Monica 的其他功能。

## 独立性

- 应用 ID：`takagi.ru.monica.steamapp`
- 工程名：`MonicaSteam`
- Android 应用沙箱会隔离 Monica Steam 与 Monica Android 的数据库和偏好设置。
- Monica Android 与 Monica Steam 可以同时安装。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

APK 文件名使用 `Monica-Steam-Android` 前缀。

可安装 Debug 包会启用 R8、代码压缩和资源收缩，并继续使用 Android debug 证书签名；为了允许完整优化，这类交付包不开放 JDWP 调试。

## 包含的 Steam 功能

- Steam Guard 验证码与多账号管理
- maFile、密钥、账号密码和二维码导入
- 登录批准与移动确认
- 授权设备管理与验证器移除
- 库存、市场价格、批量上架与撤销挂单
- 加密本地 Steam 账号存储及可选 Monica MDBX 存储
- Steam 专用设置页：外观、语言、防截屏、剪贴板、验证码进度条、迷你资料背景与减少动画

## APK 精简

- 移除密码图标包、Passkey 清单、赞助页面图片和未使用的 Koin 应用入口
- 排除与 Steam 无关的 zxcvbn 密码字典和 Bouncy Castle Picnic 数据表
- 保留二维码扫描所需的 ML Kit 模型、CameraX/Barhopper 原生库和 Steam 加密依赖
- arm64 可安装包由 64.76 MiB 降至 15.83 MiB
- armeabi-v7a 可安装包降至 14.14 MiB

本工程派生自 Monica Android，继续遵循仓库的 GPL-3.0 许可证。
