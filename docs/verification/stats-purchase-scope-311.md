# Store-only purchase bar — 1.0.311

2026-11-21. The game-detail purchase bar was attached outside all Store/Personal Stats/Workshop sections and ignored the selected account's known ownership. Personal Stats therefore showed a purchase and wishlist prompt even for a game in the player's library.

SteamGameDetailTabs now enables the bar only for Store. The purchase host keeps the same composition while changing visibility, removes the hidden bar from hit testing/semantics, and supplies zero purchase clearance to Stats/Workshop. Persistent tab navigation, scroll positions and saved achievement state remain mounted. Store retains measured clearance for its visible bar.

SteamStoreScreen resolves ownership from the current account/game purchase context or its existing owned-app set and supplies that same result to the bar, hints and purchase content. The bar shows the existing localized In your library / Purchase options labels for owned games, hides wishlist and discount countdown, and keeps the existing editions/gifts flow reachable. It does not infer ownership from playtime or family sharing and does not change license/purchase enforcement. No new strings or account network operations were introduced.

Debug main/test build01 succeeded in 2m3s (124.170s driver). Existing MuMu0/API35/x86_64/PID15892 was reused with separate .statspurchase packages. All eight device cases passed in one run, 26.888s runner: Store-only bar scope in M3E and glass with large type; stable navigation identity and retained stats scroll after a Store round trip; actual selection travel for both styles; owned-state arrival updating the visible bar while disabling self purchase and allowing the gift callback; unowned package selection; free-license callback; and large-type glass wishlist/purchase access.

The local editable Canvas and actual preview were inspected, followed by native dark M3E/glass stats and Chinese owned-store screenshots. Captures use synthetic fixtures and remain ignored under docs/design/20261121-stats-purchase-scope. No live ownership request, transaction, user account, physical OEM or R8 validation is claimed.

Seven scoped source/document paths passed diff checks and SHA-256 freezing. Both task packages were removed; users, original packages, current user, font/display and the pre-existing MuMu process/data remain unchanged. Release notes now describe the final Store-only behavior. Version remains 1.0.311/code18. No commit, push, release or APK delivery.
