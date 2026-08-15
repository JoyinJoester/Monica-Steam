<h1 align="center">Monica Steam</h1>

<div align="center">

[中文](README.md) | **English**

<img src="image/monica_launcher.webp" alt="Monica Steam App Icon" width="220" />

<p><strong>A Steam-focused standalone Android client</strong></p>
<p>Steam Guard · Library · Store · Friends & Chat · Mobile Confirmations</p>

<p>
	Links:
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
[![QQ Group](https://img.shields.io/badge/QQ-1087865010-12B7F5?style=flat-square&logo=tencentqq&logoColor=white)](https://qm.qq.com/q/2vTdTkHV3u)

[![Afdian](https://img.shields.io/badge/Afdian-JoyinJoester-ea4aaa?style=flat-square)](https://afdian.com/a/JoyinJoester)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-joyinjoester-29ABE0?style=flat-square&logo=kofi&logoColor=white)](https://ko-fi.com/joyinjoester)
[![PayPal](https://img.shields.io/badge/PayPal-Support%20Monica-FFD140?style=flat-square&logo=paypal&logoColor=003087)](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW)

</div>

<br>

<div align="center">

# ⚠️ IMPORTANT RISK WARNING ⚠️

### Monica Steam may trigger Steam risk control and cause a “red letter” / account restriction

### If that concerns you, do not use it for now

### When this notice is removed from the README, the issue has been resolved

</div>

<br>

Monica Steam is a Steam-focused Android client derived from the Steam surfaces in [Monica Android](https://github.com/Monica-Pass/Monica-for-Android).
It brings Steam Guard, account management, mobile confirmations, library, store, friends, chat, notifications, and Steam account backup into a standalone app.

> **Status: public test build.** Monica Steam is under active development. It is not a stable release and not an official Steam client.
>
> This project is not affiliated with, endorsed by, or sponsored by Valve Corporation. Steam, Steam Guard, and related marks belong to their respective owners.

---

## Read this first

### Who Monica Steam is for
- People who manage multiple Steam accounts and Steam Guard codes on Android.
- Users who want login approvals, trade confirmations, friends chat, and store browsing on mobile.
- Monica users who want Steam features as a separate app, not the full password vault.

### What you get
- Steam Guard codes, multi-account switching, `maFile` import, and mobile confirmations.
- Library, playtime stats, achievements, family sharing, and offline cache.
- Store browsing, wishlist, and cart entry points (final checkout still uses Steam’s official flow).
- Friends, DM/group chat, notifications, and experimental voice calls.
- Main-password / biometric lock, plus WebDAV / ZIP Steam account backups.

### Quick install

1. Download the latest APK from [Releases](https://github.com/JoyinJoester/Monica-Steam/releases).
2. Install on Android 8.0+.
3. Import a `maFile` or sign in to Steam to initialize tokens and sessions.
4. **Back up existing `maFile` / authenticator data first.** Never treat a test build as your only copy.

### Known limitations
- Still a public test build; APIs and UI may change.
- Some features depend on Steam web pages or non-public mobile endpoints and may break when Steam changes them.
- Store prices, gifts, notifications, chat, and voice may depend on region, session state, and risk-control policy.
- Experimental voice call reliability varies by device and network.
- **Steam risk control may lead to red-letter / account restrictions. Skip this app if that is unacceptable.**

---

## Relationship with Monica Pass

[Monica Pass](https://github.com/Monica-Pass/Monica-for-Android) is the main Monica ecosystem and local-first password-vault project. Monica Android originally included both password management and Steam surfaces.

Monica Steam was extracted from that Steam experience and is maintained as a separate product:

| Project | Role | Link |
| --- | --- | --- |
| Monica Pass | Local-first password vault and Monica ecosystem | [GitHub](https://github.com/Monica-Pass/Monica-for-Android) · [Website](https://monica-pass.github.io/Monica/) |
| Monica Android | Full Monica Android client and source of the Steam module | [Android project](https://github.com/Monica-Pass/Monica-for-Android) |
| Monica Steam | Standalone Steam-focused Android client | [This repository](https://github.com/JoyinJoester/Monica-Steam) |

- Own application ID, sandbox, release cycle, and repository: `takagi.ru.monica.steamapp`.
- May reuse Monica Material 3 design, navigation, security, storage, and Steam components, but does **not** modify Monica Android.
- Does **not** include the Monica Pass vault, Bitwarden, KeePass, autofill, or password-management workflows.
- Cannot open or manage Monica Pass vault records.
- `maFile`, Steam account ZIP, MDBX, and WebDAV here are for Steam account data only.

See [`SOURCE.md`](./SOURCE.md) for the extraction baseline.

---

## Features

### Steam accounts and Steam Guard
- Steam Guard TOTP codes and multi-account management.
- `maFile`, key-only, credential, and QR-code imports.
- Login approvals, mobile confirmations, and authorized-device management.
- Authenticator removal and account switching.
- Local encrypted account storage with optional MDBX backing.

### Library and game data
- Library, family sharing, playtime, achievements, and ownership details.
- Account-level game count, playtime, and estimated-value summaries.
- Recent-play filters, completion filters, distribution charts, and play-activity heatmaps.
- Cached library data for offline viewing.

### Steam Store
- Browsing, search, regional prices, currency conversion, and account-region filtering.
- Purchase options, editions, DLC, bundles, system requirements, screenshots, and reviews.
- Native cart and wishlist views; final checkout stays on Steam’s official flow.
- Events and points-store content when Steam exposes compatible data.

### Friends, chat, and notifications
- Friends list, profiles, unified DM/group conversations.
- Text, emoji, stickers, images, reactions, reports, and chat search where supported.
- Group channels, roles/permissions, invite links, and related management.
- Notifications, unread state, gift/confirmation-related actions.
- Experimental private/group voice calls.

### Appearance and backup
- Monica color schemes, including Monica Plus palettes.
- Material 3 Expressive layouts, floating Dock, liquid-glass Dock, and UI scaling.
- Steam-only `maFile` WebDAV backup/restore, plus ZIP export/import.
- Main-password and biometric protection; log viewing/cleanup/sharing.
- Account and recently-played widgets.
- Steam network optimization (hosts / diagnostics).

---

## Data and security boundaries

- App ID: `takagi.ru.monica.steamapp`. Databases and preferences are isolated by Android’s application sandbox.
- Can be installed side by side with Monica Android; data is **not** shared automatically.
- Back up existing `maFile` files before import, migration, or remote backup.
- Steam pages and mobile APIs can change without notice. For purchases, gifts, account security, or final confirmation, **Steam’s official result is authoritative**.
- Never treat a test build as the only copy of your Steam authenticator or account data.

### Security model (current implementation)
- UI: Jetpack Compose + Material 3 / Material 3 Expressive.
- Local protection: main password, biometrics (BiometricPrompt), encrypted local storage.
- Async / background: Kotlin Coroutines + Flow + WorkManager.
- Networking: OkHttp and related stacks against Steam web/mobile endpoints.
- Backup: WebDAV, ZIP, and `maFile` import/export.

---

## Sponsorship

If Monica Steam / the Monica projects help you, support is welcome.

<div align="center">
<img src="image/support_author.jpg" alt="Support Monica Steam" width="320"/>
<br/>
<sub>WeChat / Alipay QR</sub>
<br/><br/>

<form action="https://www.paypal.com/ncp/payment/BHSYWK73CA8FW" method="post" target="_blank" style="display:inline-grid;justify-items:center;align-content:start;gap:0.5rem;">
  <input style="text-align:center;border:none;border-radius:0.25rem;min-width:11.625rem;padding:0 2rem;height:2.625rem;font-weight:bold;background-color:#FFD140;color:#000000;font-family:&quot;Helvetica Neue&quot;,Arial,sans-serif;font-size:1rem;line-height:1.25rem;cursor:pointer;" type="submit" value="Support Monica" />
  <img src="https://www.paypalobjects.com/images/Debit_Credit_APM.svg" alt="cards" />
  <section style="font-size: 0.75rem;"> Powered by <img src="https://www.paypalobjects.com/paypal-ui/logos/svg/paypal-wordmark-color.svg" alt="paypal" style="height:0.875rem;vertical-align:middle;"/></section>
</form>

<br/>
<p>
  <a href="https://www.paypal.com/ncp/payment/BHSYWK73CA8FW">
    <img src="https://img.shields.io/badge/PayPal-Support%20Monica-FFD140?style=for-the-badge&logo=paypal&logoColor=003087" alt="PayPal Support Monica" />
  </a>
</p>
</div>

Support is prioritized for:
- Steam protocol adaptation and risk-control mitigation.
- Android experience and stability work.
- Chat, notification, and backup maintenance.

You can also support via [Afdian](https://afdian.com/a/JoyinJoester), [Ko-fi](https://ko-fi.com/joyinjoester), or [PayPal](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW).

---

## Development

### Prerequisites
- Latest stable Android Studio.
- JDK 17+.
- `compileSdk 35`, `targetSdk 34`, `minSdk 26` (Android 8.0+).
- Build baseline: AGP `8.7.3`, Kotlin `2.0.21`, Compose BOM `2026.03.00` (see `gradle/libs.versions.toml`).

### Useful commands

JVM tests only:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Build packages:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Release signing is supplied externally through `keystore.properties` or `MONICA_STEAM_RELEASE_*` environment variables. Never commit signing files or credentials.

### Code layout (current)
- `takagi/ru/monica/steam` — Steam domain (accounts, tokens, confirmations, library, store, friends, chat, notifications, …).
- `takagi/ru/monica/ui` — Compose screens and shared settings shell.
- `takagi/ru/monica/data` / `repository` / `security` — local data, repositories, and security.
- `takagi/ru/monica/webdav` / `workers` — backup and background work.

### Repository guide
- [`README.md`](./README.md) — Chinese overview (main)
- [`RELEASE_NOTES.md`](./RELEASE_NOTES.md) — public test release notes
- [`SOURCE.md`](./SOURCE.md) — extraction baseline from Monica Android
- [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) — third-party notices
- [`docs/`](./docs) — architecture and release-signing notes

---

## Acknowledgments

Monica Steam’s design, compatibility work, and feature direction draw inspiration from:

- [Monica Pass](https://github.com/Monica-Pass/Monica-for-Android) — design language, security lock, and shared foundations.
- [Steam Desktop Authenticator](https://github.com/Jessecar96/SteamDesktopAuthenticator) — maFile / Steam Guard / confirmation reference.
- [steamguard-cli](https://github.com/dyc3/steamguard-cli) — login, token migration, and confirmation protocol reference.
- [AnotherVaporAuth](https://github.com/freefrank/AnotherVaporAuth) — mobile authenticator and approval UX reference.
- [Grit](https://github.com/shub39/Grit) — library analytics and heatmap interaction reference.
- [Essentials](https://github.com/sameerasw/essentials) — floating Dock interaction reference.
- [KernelSU](https://github.com/tiann/KernelSU) — liquid-glass Dock motion structure reference.

See [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md) for full third-party license text.

---

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=JoyinJoester/Monica-Steam&type=Date)](https://star-history.com/#JoyinJoester/Monica-Steam&Date)

---

## Feedback and support

- Issues: [Monica Steam Issues](https://github.com/JoyinJoester/Monica-Steam/issues)
- QQ group: `1087865010`
- Telegram group: [Join the Monica community](https://t.me/+IZUDLL-vWOA1Y2U1)
- Sponsor: [Afdian](https://afdian.com/a/JoyinJoester) · [Ko-fi](https://ko-fi.com/joyinjoester) · [PayPal](https://www.paypal.com/ncp/payment/BHSYWK73CA8FW)

---

## License

Copyright (c) 2025–2026 JoyinJoester

Monica Steam is released under the [GNU General Public License v3.0](LICENSE).

Additional third-party copyright and license information is in [`THIRD_PARTY_NOTICES.md`](./THIRD_PARTY_NOTICES.md).

Steam and related trademarks belong to Valve Corporation and their respective owners. This project is an unofficial third-party client.
