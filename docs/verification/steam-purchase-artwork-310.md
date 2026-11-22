# Purchase cover and bundle artwork · 1.0.310

Verified on 2026-11-21. Unreleased code18; no commit, push, release, or APK delivery.

## Result

Purchase options show the game's existing Steam header image above the heading and name. The cover retains the 460/215 aspect ratio and 24dp rounded corners, uses the existing image cache, and keeps text when no image URL is returned.

Bundles use full-width vertical cards with up to three distinct returned included-game images, item-name previews, count, discount, price and a forward action. The bundle sheet repeats those images, separates count from price and provides the existing official Steam purchase action. Each included game has a rounded row, its returned image when available and its existing detail navigation. The list scrolls independently; missing images retain names. Existing M3E and Liquid Glass sheet material is reused.

The existing purchase-page service already supplies included-game images; parsers, models and metadata requests are unchanged. Package selection, gift, free-license, wishlist and official purchase callbacks retain their existing behavior. Native tests record these callbacks on synthetic fixtures; no actual cart, wishlist, account or payment operation was performed.

## Validation

- Build01 produced main/test APKs and passed 25 selected JVM tests across 10 suites in 179.442s. Final build04 succeeded in 143.352s and the same selected 25 JVM tests had zero failures, errors or skips. The selection includes purchase-page parsing, ownership/gift/bundle behavior and existing source guards.
- Final device05: 28/28 native methods, runner 42.139s. SteamStoreDetailPricesUiTest and SteamGameDetailTabsUiTest cover package selection and exact package callbacks, owned-game gifts, free claims, unavailable purchase, official bundle URL, wishlist interaction, regional prices, tabs and toolbar navigation.
- The purchase-cover assertion checks a decoded synthetic yellow bitmap pixel, so an empty image container does not satisfy it. Normal purchase and bundle artwork screenshots were inspected.
- The bundle fixture supplies two distinct synthetic covers plus ten additional items. Large-font Liquid Glass opens the full-width card, shows the contents artwork, scrolls to the twelfth item and verifies navigation to App1010 without invoking official purchase.
- Both purchase and nested bundle popup headings use actual 1.5-font Activity contexts. GetTextLayoutResult verifies line height above 40dp; final headings measured 41.142857dp and 41.142857dp. These screenshots were inspected. The test-only host resets its font override after each method; system font settings were not changed.
- Editable local Canvas was previewed before native edits. Its JSON, full docz link, normal and bundle previews plus native screenshots remain ignored under docs/design/20261121-purchase-artwork. Images are not embedded in tracked evidence.
- Existing MuMu1/API35/x86_64 was used; MuMu0 was preserved. Own .purchaseart packages were removed, baseline users/packages/current user/display/font were checked and task-started MuMu1 was stopped. Configuration and data disk retained; no AVD was used.

## Earlier attempts retained

Build01/device01 passed all28, but the parent CompositionLocal font override did not certify the nested popup's actual font size. Activity resource scaling and a measured-heading assertion were added. Device02 installation found ADB offline immediately after emulator resume; no instrumentation ran. After readiness, device03 passed27/28; the new heading query matched both the background bundle card and the foreground sheet. Scoping it to bundle_detail_99 produced device04's genuine measurement failure: the nested popup still rendered a 35.42857dp heading. The synthetic Activity now applies font scale while attaching its base context and is recreated before large-font scenarios. Device05 passes all28 with both measured-heading assertions retained. Production UI files were unchanged throughout these fixture repairs. Original logs and frozen APKs remain in the local task directory.

No real-account writes, live store bundle verification, physical device/OEM, older Android, release/R8 or payment validation. Synthetic native fixtures and local design previews establish UI behavior within that scope.

Final main APK SHA256: 99600bb82fb75d04537d3af60d596e9e729c62a75fba198d6948cf1a7f2a47e8

Final test APK SHA256: a39a3b260f2da022f410340a3e076db2d74fc4b77e8e07595f4914a5b8fb56b0
