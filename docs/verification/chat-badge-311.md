# Chat filter count rendering — 1.0.311

## Change

Selected chat-content badges were rendered as solid circles because the shared glass selector applied `ColorFilter.tint(primary)` to the entire captured row. Both the badge background and its numeral therefore became the same color. Capture now preserves the colors supplied by its content. The list filter, game-detail navigation and rich-media picker apply the primary selected color to their labels explicitly; badges retain `primary` / `onPrimary` contrast. Dock lens, motion, scale, input handling and selection timing are unchanged. Chat search reuses the same list-filter component.

Version remains 1.0.311 / code 18. Both unreleased Chinese and English notes include this fix.

## Validation

- Reused the already-running MuMu0, Android 35 / x86_64. Only isolated `.chatbadge` application and test packages were installed, using synthetic chat history and cached synthetic images.
- Debug application and test build passed in 1m15s. Final test-only build passed in 31s. Application APK hash stayed identical across all post-fix device runs.
- The final test APK run against the preserved pre-fix application (`baseline-device04`) failed both dark/light count checks as expected: zero numeral pixels at rest. Running the same test APK against the fixed application (`device03`) passed all 4 count tests in 13.490s.
- Count tests inspect actual screenshot pixels for 4, 1 and 12 in dark/light Liquid Glass at rest, held and released, plus dark M3E and light M3E with 1.5× font size. They require both foreground numeral pixels and badge-fill pixels.
- 20 distinct device methods have passing results: the final four badge methods plus 16 unchanged chat-content, movement, RTL, reduced-motion, cancellation and shared Dock/selector methods from `device02`. This is a combined result, not a claim that an earlier 20-test batch passed in full.
- Native screenshots were inspected for selected links, held two-digit invitations, light released state and M3E large text. A native before/after comparison is stored locally. The editable local M3E Canvas was updated and its actual preview inspected before implementation; it records the category/count design, not the Android shader rendering.

## Retained failures and fixture corrections

The initial baseline fixture saw duplicate numeral nodes from the hidden glass capture; the query now excludes descendants of cleared semantics. The first fixed-app batch passed 18/20 but the two pixel checks rejected the lens's real 10% surface overlay: observed dark ink was RGB(76,53,128), light ink RGB(229,229,229). A relative-color attempt still sampled the wrong held position and could count badge-corner pixels as ink. Final checks exclude vertical line padding, use the actual 1.2× content transform (the larger lens samples in screen coordinates), allow the known overlay while requiring disjoint ink/fill color neighborhoods, and retain the original minimum pixel-count requirements. The final test was rerun against the preserved old application to verify it still detects missing digits. All earlier failure logs and local APK snapshots remain available.

## Evidence and scope

Receipts, method-level outcomes, cleanup and source hashes are in `chat-badge-311-evidence/`. Private task inputs and build logs remain under `.codex-tasks/20261121-chat-badge` at the workspace root. Editable Canvas JSON, local URL, screenshots and comparisons remain only in ignored `docs/design/20261121-chat-badge/`.

Only the two task packages were removed. Original user identities, packages, display size, density and font settings match baseline. The pre-existing MuMu process and its data/configuration remain intact. Validation covers this synthetic API35 debug fixture; it does not claim the user's physical device, every custom font/theme, or release/R8 verification. No commit, push, release or APK delivery was performed.
