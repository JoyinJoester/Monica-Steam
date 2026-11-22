# Monica Steam 1.0.310 — chat entry and exclusive inbox filters

Date: 2026-11-08. The reported entry was clicking a conversation to open its content. Telegram styling remains limited to chat. No commit, push, tag, release or APK delivery was performed.

## Changes

`TelegramWallpaperDrawable` previously parsed a 507,258-byte SVG in each constructor and rasterized a full-width ARGB bitmap in `onBoundsChange`, both on the UI thread. The visible wallpaper and the glass-source wallpaper repeated that work whenever a conversation opened. Eight original 1080px mount samples took 81.484–367.687 ms each, median 129.194 ms.

The host adapter now parses and rasterizes on one background worker. Drawables share a single 1440×2960 alpha mask, 4,262,400 bytes; only bundled decoration enters this cache. The screen initially draws its current theme background, then invalidates when the pattern is ready. Current bounds, theme and strength apply at drawing time, including changes made during preparation. Pending drawables are weakly referenced. Plain wallpaper schedules no pattern generation. Shared masks are never recycled by individual drawables. Warm mount samples now take 0.031–0.092 ms, median 0.040 ms; the deferred work is not included in that mount measurement.

The existing conversation navigation transition policy is unchanged. This fixes a synchronous entry stall rather than claiming a new animation or full Telegram transition parity.

All, Unread, Direct, Groups, Pinned and Archived now use one `SteamConversationFilter` state and one selectable tab group. Unread is no longer an independent checkbox. Repeating a selection keeps that tab selected; choosing another tab replaces it. Unread includes pending direct and group conversations outside the archive. Archive remains a separate view. Existing selection exit and list reset behavior are retained.

## Verification

The final main/test/JVM build completed in 584.999 seconds, with 94 selected JVM tests and zero failures, errors or skips. A later test-only build took 88.584 seconds after correcting a pending-frame observation in the new test. The main APK SHA-256 stayed `d1a8401b2a123c3580cb3e6501d80492e4869b9d91d58808db12c7b4fcf166d6` throughout final device testing. Version remains 1.0.310 / code18, Debug build 26110818-21.

The public `Monica_Issue136_API_32` AVD ran API32/x86_64 with a temporary 2 GiB memory argument and software GPU. All app fixtures used an init-script-only `.chatmotion` application id on Owner 0. The system rejected additional full and guest users because capacity was already occupied; no existing user was removed or reused for app data. Existing normal application packages remained installed.

| Device batch | Passed/total | Runner seconds |
| --- | --- | --- |
| Original behavior and timing | 2/3 | 36.538 |
| Original isolated entry sample | 1/1 | 25.635 |
| Fixed isolated entry sample | 1/1 | 16.809 |
| Fixed regression batch | 13/14 | 81.337 |
| Filter selection observation corrected | 1/1 | 6.310 |

Fifteen different device methods obtained passing results on the same final main APK. Coverage includes real list-to-thread entry and Back, 80-message history, mutually exclusive filter semantics and rows, repeated unread selection, bounded shared wallpaper and current theme/bounds/plain handling, all three wallpaper styles in private/group chats, wallpaper preference confirmation/cancellation, drafts, sending/retry callbacks, attachment panels, group channel switching, IME-facing reading anchors, delayed inbox rows on return and real PixelCopy glass-source refresh. These are synthetic production-component fixtures without a logged-in Steam account or outbound Steam messages.

The original filter test failed because All remained selected after Unread. The first fixed batch's new filter test passed the unread/group/direct sequence but inspected All immediately after the last click, before Compose committed the next frame. The existing inbox interaction test passed in that batch. Only the test was changed to wait for mounted selection before retaining the same selection and checkbox assertions. Its targeted retest passed; this is not a claim that the first batch was all green.

## Entry measurements

Both isolated sampling runs used the same real inbox, navigation and populated thread controls, eight opens/returns per run, normal frame clock and the same running emulator. Timing starts at row click and ends after a populated native message list plus a frame-commit callback. The first sample includes cold code/composition costs; subsequent samples retain process caches. Each run used a new instrumentation process. The old run preceded installation of the new APK, with build work finished before both measurements.

| Measurement | Original | Fixed |
| --- | ---: | ---: |
| First entry sample | 2590 ms | 1384 ms |
| Median of subsequent 7 entries | 572 ms | 340 ms |
| Window frame total duration p50 | 227.561 ms | 154.109 ms |
| Window frame total duration p95 | 913.826 ms | 378.971 ms |

The repeated-entry sample improved by approximately 40.6%. Frame durations cover the complete open/return loop, not just one animation. These small software-emulator Debug samples do not establish physical-device FPS, live-service latency or a universal speedup. Initial parsing now runs concurrently with first display; the first mount cost is not the time until the decorative pattern appears.

## Evidence and cleanup

`chat-entry-filters-310/results.json` records methods, build commands, source hashes, APK hashes, raw timing samples and cleanup receipts. Text copies beside it normalize line endings and trailing whitespace; original logs remain in `.codex-tasks/20261108-chat-motion-filters`. The first build's native mmap failure, initial boot-before-ready ADB failure and earlier assertion failures are retained. Baseline APK and the first corrected test APK were hashed but not separately archived.

Upstream audit passed for 132 records; regeneration matched 114 generated files and the manifest. The wallpaper is an explicitly preserved host adapter. No source assets were replaced. Reviewed native screenshots show the private patterned wallpaper, group plain wallpaper and inbox with only Unread selected. Other captured images remain available locally. All screenshots are in ignored `docs/design/chat-motion-filters-20261108`; none are committed or embedded here.

Only the two task-owned `.chatmotion` packages were uninstalled. Owner 0 and existing users 10, 11, 12 and 14, the normal Steam packages, AVD registration/configuration and all userdata disks were verified present. The self-started AVD was stopped; the unrelated connected device was untouched. Existing repository changes were preserved.
