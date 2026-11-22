# Chat filter and call motion source review

Reviewed on 2026-11-20. Scope: the Steam conversation list and its call controls. The local Telegram reference is `参考项目/Telegram-master/Telegram-master/TMessagesProj/src/main/java/org/telegram/`; this is a source review of the local snapshot, not a claim about the latest upstream release.

## Applied

- Monica's Dock already places its shell and expanding lens as siblings. Its selection lens uses a 56-to-88 scale, depth refraction, dispersion and inner shadow. The chat filter reused that lens inside a Kyant backdrop, whose `layoutLayerBlock` sets `clip = true`. The entire expanding lens was therefore clipped by the outer capsule. The fix opts only the conversation filter into a sibling shell. The existing drag state machine, scrolling, release/cancel callbacks and other application filters are retained.
- Telegram `ui/Components/voip/VoIPToggleButton.java:122` uses a short press-scale animator; its icon/background replacement and checked state also animate over 150 ms. Steam now adapts that interaction with a modest 0.9 press scale, 150 ms icon/color transitions, a distinct hang-up color and muted-state color. This is an independent Compose implementation, not a copied Telegram view or a replacement call engine. The accessibility description updates immediately; crossfading icons are decorative. Reduced motion uses immediate state changes.

## Useful next candidates, with prerequisites

| Reference | Effect | Fit and boundary |
| --- | --- | --- |
| `ui/Components/voip/VoIPStatusTextView.java`, `replaceViews` | Short vertical fade between connecting, connected and reconnecting states | Suitable for the existing chat call banner. Keep the status tied to the actual runtime and preserve current actions. Deferred in this change. |
| `ui/Cells/GroupCallUserCell.java`, `AvatarWavesDrawable`; `ui/Components/voip/VoipBlobDrawable.java` | Avatar waves and scale driven by speaking amplitude; damp to stillness when muted | Steam's current participant model exposes mute/join state, not measured speaking amplitude. Requires real WebRTC audio-level plumbing and validation before an honest speaking visualization can be added. No synthetic speaking animation was added. |
| `ui/Components/FilterTabsView.java` | Selected-tab interpolation, scrolling to the chosen tab, counter transitions | Monica already has measured unequal-width slot interpolation, RTL handling, edge scrolling and cancellation. Preserve that implementation; the regression was the rendering hierarchy, not missing selection logic. |
| `ui/Components/ButtonBounce.java` | Small bounded press/release feedback | Useful for chat attachment/reaction controls if those controls lack feedback. Existing native Telegram-derived controls should be checked individually to avoid applying two animations. Deferred. |

The local `GlassTabsView.java` only stores/expands lens bounds; it does not contain a complete rendering implementation. It cannot by itself be used as evidence that a working liquid-glass effect has been ported. Monica's already functioning Dock pipeline is the concrete reference for this fix.

No Store/Library/Token/Settings styling, Steam protocol, audio route logic, microphone permission flow or call signaling was changed. Source hashes and native validation are recorded with the task evidence. No live Steam call was placed.

## Follow-up: voice activity motion

The two candidates above were subsequently requested on 2026-11-20. Status transitions and measured avatar waves are implemented in the follow-up documented in `voice-activity-motion-310.md`. The original filter/control results and limitations above describe the earlier task.
