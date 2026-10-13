# Third-Party Notices

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

## Miuix blur and AndroidLiquidGlass

The liquid-glass backdrop capture, RuntimeShader effects, highlight model, and
supporting lens concepts use
[`miuix-blur`](https://github.com/compose-miuix-ui/miuix) and
[`AndroidLiquidGlass`](https://github.com/Kyant0/AndroidLiquidGlass).

Copyright (C) compose-miuix-ui contributors and Kyant0 contributors

Licensed under the Apache License, Version 2.0.
