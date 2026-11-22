# Third-Party Notices

## SteamNova (formerly Millennium)

The SteamDB game-data integration's regional mapping, rating calculations and
request conventions were informed by
[SteamNova](https://github.com/GoodJoe-2049/Steam-Nova), formerly Millennium.
SteamNova's README explicitly documents the rename and identifies it as an
independent Android Steam module, unrelated to SteamClientHomebrew/Millennium.
The rename and MIT license were checked at revision
`0a03e75b784fee0a5c92720c73cca0322a8b455a`; this is a documentation check, not a
claim that Monica's existing implementation was imported from that revision.

Copyright (c) 2026 GoodJoe2042. The complete upstream MIT notice is retained in
`app/src/main/assets/licenses/steam-nova.txt`. Steam and SteamDB remain the
actual data sources; their names and functional links are unchanged.

## Telegram Android chat UI

The `telegram-chat-ui` library contains adapted source, original bitmap/Lottie assets, emoji.pack,
wallpaper and an original AGSL shader from Telegram Android 13.0.3
([DrKLO/Telegram](https://github.com/DrKLO/Telegram)). Copyright Telegram contributors,
including the original file-header authors. Telegram's source headers permit
GPL-2.0-or-later; the combined Monica Steam work is distributed under GPL-3.0-or-later.
The original GPL v2 license is preserved in `telegram-chat-ui/LICENSE.Telegram`;
the combined-work GPL v3 license is in `LICENSE`. `telegram-chat-ui/UPSTREAM.json`
records source paths, original SHA-256 values and changes for each extraction.
The local source archive has no Git commit metadata; the declared version is 13.0.3.

The module adapts rendering and native controls to explicit Steam callbacks, including
source-backed dialog/contact cells, profile information/action controls, official
emoji decoding/cells and photo/document picker cells. It
does not include Telegram account, MTProto, network or database services. It does
not claim an unmodified or complete reuse of `ChatActivity` or `ChatMessageCell`.
The complete corresponding source and build files must accompany distribution
in accordance with the GPL. See `telegram-chat-ui/README.md` for boundaries.

`com.caverock:androidsvg-aar:1.4` renders Telegram's original SVG wallpaper.
AndroidSVG is copyright Paul LeBeau, licensed under Apache License 2.0:
https://github.com/BigBadaboom/androidsvg and https://www.apache.org/licenses/LICENSE-2.0.

`com.airbnb.android:lottie:6.6.2` renders the unmodified Telegram gallery/file
animation JSON. Copyright Airbnb, Inc. and contributors, licensed under the
Apache License, Version 2.0: https://github.com/airbnb/lottie-android and
https://www.apache.org/licenses/LICENSE-2.0. Telegram animation assets retain
the upstream license recorded in `telegram-chat-ui/UPSTREAM.json`.

## Workshop-Native and Steam content downloads

The Wallpaper Engine download workflow was informed by
[Workshop-Native](https://github.com/cjtestuse/Workshop-Native), revision
`f25129d62bb86d610a723a338ef25f7b134cbf9d` (GPL-3.0).
The relevant references are `SteamSessionManager.kt`, `WorkshopDownloadWorker.kt`
and the public-download storage implementation. Monica uses its own CM,
WorkManager and Storage Access Framework integration; it does not include the
upstream app, authentication store, PKG extractor or TEX converter.

Steam manifest layout, filename encryption and ZIP/LZMA/Zstandard chunk framing
were cross-checked against [JavaSteam](https://github.com/Longi94/JavaSteam)
1.8.0 (MIT, Copyright (c) 2018 Long Tran). Its license is retained at
`licenses/JavaSteam-MIT.txt`. Monica uses its existing protobuf implementation;
JavaSteam is not a runtime dependency.

Download decompression uses [XZ for Java](https://tukaani.org/xz/java.html)
1.10 (0BSD) and [zstd-jni](https://github.com/luben/zstd-jni) 1.5.7-3
(BSD-2-Clause, including Zstandard's BSD-3-Clause components).

## SteamRoute / SteamBolt

Parts of Monica Steam's network optimization target coverage, recovery strategy,
and public-address validation were informed by SteamRoute / SteamBolt source code
provided directly by its author and used with permission. The Monica Steam
implementation is adapted to its app-scoped DNS override architecture and does
not modify Android system hosts.

## Grit

The Monica Steam game distribution and play-activity analytics components are adapted from
[`Grit`](https://github.com/shub39/Grit), including `AnalyticsCard`, `WeekDayBreakdown`,
`HabitHeatMap`, and `CardArrows` interaction patterns.

Copyright (C) 2026 Shubham Gorai

Licensed under the GNU General Public License v3.0 or later. The complete license text is
included in this repository's `LICENSE` file.

The heatmap calendar is rendered with
[`Kizitonwose Calendar`](https://github.com/kizitonwose/calendar), developed by Kizito Nwose
and distributed under the MIT License.

## Essentials

The Monica Steam floating Dock interaction is adapted from
[`EssentialsFloatingToolbar`](https://github.com/sameerasw/essentials/blob/main/app/src/main/java/com/sameerasw/essentials/ui/components/EssentialsFloatingToolbar.kt).

MIT License

Copyright (c) 2025 Sameera Sandakelum

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## KernelSU FloatingBottomBar

The optional Monica Steam liquid-glass Dock adapts the motion and rendering
structure of KernelSU's `FloatingBottomBar` at commit
`778fb38bbf0c43f168b8bbd7d9e369d6fb46754b`.

Copyright (C) KernelSU contributors

Licensed under the GNU General Public License v3.0. The complete license text
is included in this repository's `LICENSE` file.

## CFluxDO interaction reference

The lifted card preview and image expansion/return interactions were studied from
[CFluxDO](https://github.com/1molchuan/CFluxDo) at commit
`e5b8ea9ee43cd64469760fa99b99ed0c4e2f772e`. Monica Steam implements these
interactions with native Android and Compose components; it does not embed the
Flutter application. CFluxDO is distributed under the GNU General Public License
v3.0; this repository includes that license in `LICENSE`.

## Miuix blur and AndroidLiquidGlass

The liquid-glass backdrop capture, RuntimeShader effects, highlight model, and
supporting lens concepts use
[`miuix-blur`](https://github.com/compose-miuix-ui/miuix) and
[`AndroidLiquidGlass`](https://github.com/Kyant0/AndroidLiquidGlass).

Copyright (C) compose-miuix-ui contributors and Kyant0 contributors

Licensed under the Apache License, Version 2.0.

Application toolbars, navigation surfaces, floating buttons, menus and sheets use
`io.github.kyant0:backdrop:2.0.1`. The native chat View bridge in
`telegram-chat-ui/src/main/java/com/monica/telegramchat/KyantGlassShader.java`
contains the rounded-rectangle refraction shader from AndroidLiquidGlass 2.0.1,
upstream commit `65ab177e90e5c1d8c62e70cf7755841982da65f6`, with its Apache-2.0
copyright notice retained. It is adapted to Android RenderNode/RuntimeShader;
foreground chat controls and their layout remain the existing Telegram adapters.
