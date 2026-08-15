<h1 align="center">Monica Steam</h1>

<div align="center">

**中文** | [English](README_EN.md)

<img src="image/monica_launcher.webp" alt="Monica Steam App Icon" width="220" />

<p><strong>专注 Steam 的独立 Android 客户端</strong></p>
<p>Steam Guard · 游戏库 · 商城 · 好友聊天 · 移动确认</p>

<p>
	友情链接：
	<a href="https://linux.do" title="Linux.do">
		<img src="https://www.google.com/s2/favicons?domain=linux.do&sz=64" alt="Linux.do" width="22" />
		Linux.do
	</a>
	·
	<a href="https://github.com/Monica-Pass/Monica-for-Android" title="Monica Pass">
		Monica Pass
	</a>
</p>

[![Release](https://img.shields.io/github/v/release/JoyinJoester/Monica-Steam?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/releases)
[![Downloads](https://img.shields.io/github/downloads/JoyinJoester/Monica-Steam/total?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/releases)
[![Last Commit](https://img.shields.io/github/last-commit/JoyinJoester/Monica-Steam?style=flat-square)](https://github.com/JoyinJoester/Monica-Steam/commits/main)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square)](LICENSE)
[![QQ群](https://img.shields.io/badge/QQ群-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

[![爱发电](https://img.shields.io/badge/爱发电-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)
[![PayPal](https://img.shields.io/badge/PayPal-Support%20Monica-FFD140?style=flat-square&logo=paypal&logoColor=003087)](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW)

</div>

<br>

<div align="center">

# ⚠️ 重要风险提示 ⚠️

### Monica Steam 可能会因为 Steam 的风控问题而红信

### 如果介意的话请先不要使用

### 后面什么时候删了这句话，就是解决了

</div>

<br>

Monica Steam 是一款专注 Steam 的独立 Android 客户端，源自 [Monica Android](https://github.com/Monica-Pass/Monica-for-Android) 中的 Steam 功能。
它把 Steam 令牌、账号管理、移动确认、游戏库、商城、好友、聊天、通知和 Steam 账号备份集中到一个应用中。

> **当前状态：公开测试版。** Monica Steam 仍在持续开发中，不是正式版，也不是 Steam 官方客户端。
>
> 本应用与 Valve Corporation 没有隶属、授权或赞助关系。Steam、Steam Guard 及相关商标归其各自权利人所有。

---

## 用户先看

### Monica Steam 适合谁
- 需要在 Android 上集中管理多个 Steam 账号与 Steam Guard。
- 希望在手机上完成登录批准、交易确认、好友聊天与商城浏览。
- 已经使用 Monica 系列产品，想单独安装 Steam 能力，而不是完整密码库。

### 你能得到什么
- Steam Guard 动态令牌、多账号切换、`maFile` 导入与移动确认。
- 游戏库、游玩统计、成就、家庭共享与离线缓存。
- Steam 商城浏览、愿望单、购物车入口（最终结算仍走官方流程）。
- 好友、私聊/群聊、通知、实验性语音通话等社区能力。
- 主密码 / 生物识别保护，以及 WebDAV / ZIP 形式的 Steam 账号备份。

### 快速安装

1. 从 [Releases](https://github.com/JoyinJoester/Monica-Steam/releases) 下载最新 APK。
2. 在 Android 8.0+ 设备安装。
3. 导入 `maFile` 或登录 Steam 账号，完成令牌与会话初始化。
4. **使用前务必备份现有 `maFile` / 令牌数据。** 测试版不应作为唯一备份。

### 已知限制
- 仍为公开测试版，接口与布局可能随时调整。
- 部分能力依赖 Steam 网页或非公开移动接口，Steam 变更后可能暂时失效。
- 商城价格、礼物、通知、聊天与语音可能受账号地区、登录状态、风控策略影响。
- 实验性语音通话接通率与后台持续能力因设备/网络而异。
- **存在因 Steam 风控导致账号收到红信 / 限制的风险；介意请先不要使用。**

---

## 与 Monica Pass 的关系

[Monica Pass](https://github.com/Monica-Pass/Monica-for-Android) 是 Monica 生态及本地优先密码库项目；Monica Android 原本同时包含密码管理功能与 Steam 页面。

Monica Steam 从 Monica Android 的 Steam 功能中独立出来，作为单独产品维护：

| 项目 | 定位 | 链接 |
| --- | --- | --- |
| Monica Pass | 本地优先密码库与 Monica 生态 | [GitHub](https://github.com/Monica-Pass/Monica-for-Android) · [官网](https://monica-pass.github.io/Monica/) |
| Monica Android | 完整 Android 客户端，也是 Steam 模块来源 | [Android 目录](https://github.com/Monica-Pass/Monica-for-Android) |
| Monica Steam | 独立的 Steam 专用 Android 客户端 | [本仓库](https://github.com/JoyinJoester/Monica-Steam) |

- 独立包名、沙箱、发布周期与仓库：`takagi.ru.monica.steamapp`。
- 可复用 Monica 的 Material 3 设计、导航、安全、存储与 Steam 组件，但**不修改** Monica Android。
- **不包含** Monica Pass 密码库、Bitwarden、KeePass、自动填充与密码管理流程。
- 不能打开或管理 Monica Pass 的密码库记录。
- 本应用中的 `maFile`、Steam 账号 ZIP、MDBX、WebDAV 仅服务 Steam 账号数据。

源码提取基线见 [`SOURCE.md`](./SOURCE.md)。

---

## 功能概览

### Steam 账号与令牌
- Steam Guard 动态令牌与多账号管理。
- `maFile`、仅密钥、账号凭据与二维码导入。
- 登录批准、移动确认与授权设备管理。
- 移除验证器与账号切换。
- 本地加密账号存储，可选 MDBX 存储。

### 游戏库与游戏数据
- 游戏库、家庭共享、游玩时间、成就与拥有状态。
- 账号游戏数、总时长与估算价值统计。
- 最近游玩、完成度、价格分布与游玩热力图筛选。
- 游戏库缓存：断网可看上次同步数据。

### Steam 商城
- 商城浏览、搜索、多区价格、汇率换算与账号地区筛选。
- 购买选项、版本、DLC、捆绑包、配置要求、截图与评价。
- 原生购物车与愿望单；最终结算由 Steam 官方流程完成。
- 在 Steam 提供兼容数据时显示活动内容与点数商城。

### 好友、聊天与通知
- 好友列表、好友详情、私聊与群聊统一会话。
- 文字、表情、贴纸、图片、回应、举报与聊天搜索（支持范围内）。
- 群组频道、角色权限、邀请链接等管理能力。
- 独立通知页、未读状态、礼物/确认相关操作。
- 实验性私聊/群组语音通话。

### 外观与备份
- Monica 全套配色（含 Monica Plus）。
- Material 3 Expressive 布局、悬浮 Dock、液态玻璃 Dock、界面缩放。
- Steam 专用 `maFile` WebDAV 备份/恢复，以及 ZIP 导入导出。
- 主密码与生物识别；日志查看/清除/分享。
- 账号与最近游玩桌面小组件。
- Steam 网络优化（hosts / 诊断相关能力）。

---

## 数据与安全边界

- 应用 ID：`takagi.ru.monica.steamapp`，数据库与偏好设置由 Android 应用沙箱隔离。
- 可与 Monica Android 并排安装，**不会自动共享数据**。
- 导入、迁移或开启远程备份前，请先备份现有 `maFile`。
- Steam 网页与移动接口可能随时变化；涉及购买、礼物、账号安全或最终确认时，**以 Steam 官方结果为准**。
- 测试版不要当作 Steam 令牌或账号数据的唯一备份。

### 安全模型（实现现状）
- UI：Jetpack Compose + Material 3 / Material 3 Expressive。
- 本地保护：主密码、生物识别（BiometricPrompt）、加密本地存储。
- 异步与后台：Kotlin Coroutines + Flow + WorkManager。
- 网络：OkHttp 等，访问 Steam 网页 / 移动接口。
- 备份：WebDAV、ZIP、`maFile` 导入导出。

---

## 赞助支持

如果 Monica Steam / Monica 系列对你有帮助，欢迎支持持续开发。

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica Steam" width="320"/>
<br/>
<sub>微信 / 支付宝扫码支持</sub>
<br/><br/>

<form action="https://www.paypal.com/ncp/payment/BHSYWK73CA8FW" method="post" target="_blank" style="display:inline-grid;justify-items:center;align-content:start;gap:0.5rem;">
  <input style="text-align:center;border:none;border-radius:0.25rem;min-width:11.625rem;padding:0 2rem;height:2.625rem;font-weight:bold;background-color:#FFD140;color:#000000;font-family:&quot;Helvetica Neue&quot;,Arial,sans-serif;font-size:1rem;line-height:1.25rem;cursor:pointer;" type="submit" value="Support Monica" />
  <img src="https://www.paypalobjects.com/images/Debit_Credit_APM.svg" alt="cards" />
  <section style="font-size: 0.75rem;"> 技术支持提供方： <img src="https://www.paypalobjects.com/paypal-ui/logos/svg/paypal-wordmark-color.svg" alt="paypal" style="height:0.875rem;vertical-align:middle;"/></section>
</form>

<br/>
<p>
  <a href="https://www.paypal.com/ncp/payment/BHSYWK73CA8FW">
    <img src="https://img.shields.io/badge/PayPal-Support%20Monica-FFD140?style=for-the-badge&logo=paypal&logoColor=003087" alt="PayPal Support Monica" />
  </a>
</p>
</div>

你的支持将优先用于：
- Steam 协议适配与风控风险降低。
- Android 体验优化与稳定性改进。
- 聊天、通知、备份等核心能力维护。

也可通过 [爱发电](https://afdian.com/a/JoyinJoester)、[Ko-fi](https://ko-fi.com/joyinjoester) 或 [PayPal](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW) 支持。

---

## 开发与构建

### 环境要求
- Android Studio 最新稳定版。
- JDK 17+。
- `compileSdk 35`，`targetSdk 34`，`minSdk 26`（Android 8.0+）。
- 构建基线：AGP `8.7.3`，Kotlin `2.0.21`，Compose BOM `2026.03.00`（以 `gradle/libs.versions.toml` 为准）。

### 常用命令

只跑 JVM 测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

构建安装包：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release 签名通过 `keystore.properties` 或 `MONICA_STEAM_RELEASE_*` 环境变量外部提供，请勿提交签名文件。

### 项目分层（代码现状）
- `takagi/ru/monica/steam`：Steam 业务（账号、令牌、确认、库、商城、好友、聊天、通知等）。
- `takagi/ru/monica/ui`：Compose 页面与共享设置壳。
- `takagi/ru/monica/data` / `repository` / `security`：本地数据、仓库与安全能力。
- `takagi/ru/monica/webdav` / `workers`：备份与后台任务。

### 仓库导航
- [`README_EN.md`](./README_EN.md) — English overview
- [`RELEASE_NOTES.md`](./RELEASE_NOTES.md) — 公开测试版更新说明
- [`SOURCE.md`](./SOURCE.md) — 从 Monica Android 的提取基线
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) — 第三方组件声明
- [`docs/`](./docs) — 架构与发布签名说明

---

## 致谢

Monica Steam 的设计、兼容性适配与部分功能方向，受到以下优秀开源项目与软件的启发：

- [Monica Pass](https://github.com/Monica-Pass/Monica-for-Android) — 设计语言、安全锁定与基础组件来源。
- [Steam Desktop Authenticator](https://github.com/Jessecar96/SteamDesktopAuthenticator) — Steam maFile、Steam Guard 与交易确认参考。
- [steamguard-cli](https://github.com/dyc3/steamguard-cli) — Steam Guard 登录、令牌迁移与确认协议参考。
- [AnotherVaporAuth](https://github.com/freefrank/AnotherVaporAuth) — Steam 移动验证器与登录批准体验参考。
- [Grit](https://github.com/shub39/Grit) — 游戏库统计与热力图交互参考。
- [Essentials](https://github.com/sameerasw/essentials) — 悬浮 Dock 交互参考。
- [KernelSU](https://github.com/tiann/KernelSU) — 液态玻璃 Dock 动效结构参考。

更多第三方许可证见 [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md)。

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Monica-Steam&type=Date)](https://star-history.com/#JoyinJoester/Monica-Steam&Date)

---

## 反馈与支持

- Issue：[Monica Steam Issues](https://github.com/JoyinJoester/Monica-Steam/issues)
- QQ 群：`1087865010`
- Telegram 群组：[加入 Monica 社区](https://t.me/+IZUDLL-vWOA1Y2U1)
- 赞助：[爱发电](https://afdian.com/a/JoyinJoester) · [Ko-fi](https://ko-fi.com/joyinjoester) · [PayPal](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW)

---

## 许可证

Copyright (c) 2025–2026 JoyinJoester

Monica Steam 基于 [GNU General Public License v3.0](LICENSE) 开源发布。

其他第三方组件的版权与许可证见 [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md)。

Steam 及相关商标归 Valve Corporation 及其权利人所有。本项目为非官方第三方客户端。
