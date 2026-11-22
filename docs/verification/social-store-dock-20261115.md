# Steam Space, Store filters and repeated Dock switches — 2026-11-15

Steam Space now uses a continuous timeline with one avatar column and aligned author/time, activity text and media. Images use a two-column grid and the existing fullscreen viewer. The playing-friends strip collapses continuously with feed scrolling. New events keep the current reading anchor and show a return-to-latest pill; older pagination does not create a new-arrival prompt. Account changes reset local selections and viewer state. Sensitive previews retain their cover and original-page action.

Store filtering is divided into price, language and tag sections. Prices are mutually exclusive; languages are searchable and wrap within two columns; a short initial tag list includes already-selected tags. Section changes retain the draft, cancel does not apply it, and reset/apply stay reachable in the bottom action area. Shared search and choice components preserve the two existing search families. Liquid Glass chat filters magnify on hold and drag to select on release. M3E filters wrap and select by click, with no drag selection. Labels and selection marks remain centered.

Repeated Dock switches retain only visited enabled Dock tabs, bounded by the configured tab order. Inactive tabs have a stopped lifecycle and hidden semantics, retain their recorded layers, and lose focus. Backgrounding, focus loss and platform-view routes release inactive tabs. Token UI flows use lifecycle-aware collection; QR, message and entry actions wait until the page is active. The existing lock gate source is unchanged and checks access before cached content is shown again.

## Device verification

Tests use a distinct offline fixture package, `takagi.ru.monica.steamapp.alldock`, on an existing MuMu Android 15/API35/x86_64 instance. They mount production screen composables and the actual lock gate with synthetic data. They do not log in, send messages, or use user vaults. The public API32 AVD was already running and remains available; API35 was used for AGSL coverage.

- Final `social16`: 22/22 passed in 48.504 seconds, covering Store drafts/search/cancel, media grid/viewer, new arrivals and reading position, actual social entry/search/menu paths, content settings and reopening, continuous header collapse and targets, stale presence and account revocation, pagination/reauthentication, light/dark large fonts, and cached-token lock checks.
- `install-fixed05`: 25/25 passed in 42.491 seconds, covering hold magnification, drag/release/cancel/RTL/badge reflow, M3E no-drag and click behavior, and retained-page focus, Back, lifecycle and native/platform-view cleanup.
- `fixed-token06`: hidden actual Token collectors stop and entry actions defer until active, 1/1 passed in 7.261 seconds. `install-lock10`: actual lock gate rechecks cached token content, 1/1 passed in 2.949 seconds; also repeated successfully in final `social16`.
- Final `fixed-five17`: 30 observed Dock callbacks with no lost FrameMetrics, no page remounts after warm-up and no hidden Chat UI subscriptions. Measurement ran without concurrent Gradle work.

Canonical application-ID Debug packaging with R8/minification and resource shrinking succeeded in 599.869 seconds. All 101 related JVM tests passed with zero failures, errors or skips, across 11 suites for Space parsing/cache/settings/state/header scroll, Store metadata/filtering/UI guards, selection motion and Dock/reduced-motion guards. The final x86_64 APK remains in the ordinary build output; SHA-256 is `ec1752c3db618f66af9e0ce4707881cd7b6010fd71ee7d37f7bec8b443e55f49`. It was not installed as the production application or copied to a delivery folder. Device screenshots and runtime assertions use the isolated unminified fixture APK; no claim of R8 device-runtime verification is made.

## Timing evidence

All timing figures are synthetic emulator measurements, not vivo X100 frame-rate claims. The five-page controls cycle Store, Chat, Token, Settings and Library six times each after warm-up. Their original-retention control and full-retention variants use the same screen fixture; three-page reverse controls also restore the expensive behavior.

| Run | First committed frame median / P95 | Frame total P95 | Draw P95 | Remounts |
| --- | --- | --- | --- | --- |
| Five-page original-retention control `five-baseline07` | 116.107 / 204.551 ms | 116.029 ms | 23.854 ms | 6 per page |
| Full retention `fixed-five08` | 17.622 / 38.827 ms | 18.032 ms | 3.538 ms | 0 |
| Full retention repeat `fixed-five09` | 18.009 / 39.564 ms | 18.122 ms | 3.770 ms | 0 |
| Final source verification `fixed-five17` | 20.673 / 44.643 ms | 19.823 ms | 4.074 ms | 0 |

The three-page original controls `install-baseline02`, `baseline03` and `reverse06` remounted each page ten times; their first committed-frame medians were 151.557, 128.444 and 143.836 ms. Warm controls `warm04` and `warm05` avoided remounts and measured 17.561 and 18.628 ms. This supports page/backdrop reconstruction as a contributor to repeated-switch jank. It does not establish elimination of every long frame, first-entry cost or OEM rendering issue.

## Failure history and evidence

Original failure logs are preserved. `install-baseline01` missed the first tap and is excluded from performance comparisons. The old Token collection regression `red-token04` failed as expected with a hidden active collector. The first social batch `install-social11` had six failures; later batches had three and then two. These exposed obsolete Compose editor/menu assumptions, ambiguous author-vs-playing-strip targets, short-vs-full date expectations, a Back call on the wrong thread, and native popup root coordinates incorrectly treated as display coordinates. Tests now use the actual editor family, unique playing targets and physical popup offsets; timestamp accessibility includes a full locale-formatted date. Safety and data assertions retain their original conditions; editor, menu, date and navigation checks now follow the current UI. `install-social15` independently passed both remaining menu/entry methods before the final full batch.

Build receipts, device outputs, source hashes and timing summaries are in `social-store-dock-20261115-results.json`; original receipts and fixture APK hashes are retained locally under `.codex-tasks/20261115-all-dock-jank/raw/`. Canonical minified Debug/R8 and all 101 related JVM results are recorded there.

Local editable Canvas sources and videos/screenshots remain under ignored `docs/design/`. Projects are `monica-steam-filter-style-20261115` and `monica-steam-social-store-20261115` on the loopback editor at port 5186, with portable `canvas.json` and complete `canvas-url.txt` files. Actual light, dark/large-font, media-viewer, filter and new-arrival screenshots were inspected. No images are added to Git or embedded in this tracked report.

No commit, push, release, version bump or APK delivery was performed. No real Steam account or vivo X100 was tested; R8 packaging and emulator results do not establish ARM64/OEM animation quality. Only the MuMu instance started by this task is stopped; its disk/configuration and the already-running public AVD are preserved.
