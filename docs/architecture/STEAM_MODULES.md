# Monica Steam Module Architecture

## Scope

This document applies to Steam-specific code under:

```text
app/src/main/java/takagi/ru/monica/steam/
```

The copied Monica Android infrastructure outside this directory remains shared application infrastructure and is not duplicated into Steam features.

## Feature Modules

Each user-facing Steam capability owns one feature root:

| Module | Responsibility |
|---|---|
| `token` | Steam Guard codes, login approvals, account detail and authenticator management |
| `library` | Owned games, playtime, achievements, account library summary and game details |
| `store` | Store discovery, search, product details, wishlist, cart and official checkout |
| `inventory` | Inventory loading, grouping, selection and item details |
| `market` | Market listings, quotes, pricing, selling and batch actions |
| `trade` | Incoming and outgoing trade offers and protected actions |
| `notifications` | Steam account notifications and notification details |
| `gifts` | Pending gifts and gift response operations |
| `friends` | Friends, presence, current games, requests and friend details |
| `backup` | Steam account export, import and WebDAV backup |
| `health` | Account session, authenticator and server-time health checks |
| `scanner` | Steam QR scanning and account selection for login approval |
| `profile` | Steam profile decoration, backgrounds and remote profile media |
| `organization` | Account groups, tags, pinning and organization UI |
| `quickaccess` | Widgets and widget configuration |
| `alerts` | Background synchronization and user alerts |
| `security` | App lock and sensitive-action policy |

## Internal Layout

A feature uses only the layers it needs:

```text
feature/
├── domain/          models, rules and feature interfaces
├── data/            network, persistence and platform adapters
├── presentation/    observable UI state and request coordination
└── ui/              screens and feature-owned visual elements
```

Small features may keep files at the feature root when adding layers would only create shallow pass-through modules. Large features must use the internal layout to preserve Locality.

## Shared Modules

The following roots provide shared Steam infrastructure and may be imported by feature modules:

| Module | Shared responsibility |
|---|---|
| `data` | Encrypted Steam account storage and storage-source abstractions |
| `network` | Steam HTTP, protobuf, DNS and session refresh infrastructure |
| `core` | Steam Guard cryptographic primitives |
| `diagnostics` | Crash and support diagnostics |
| `navigation` | Standalone Dock order and navigation preferences |
| `io` | Storage Access Framework helpers |
| `foundation` | Small cross-feature value types only |

Shared modules cannot contain user-facing feature screens.

## Dependency Rules

1. Application hosts depend only on a feature's public UI entry.
2. UI depends on presentation and domain code from the same feature.
3. Presentation depends on domain interfaces and receives data adapters through a factory.
4. Data adapters implement domain interfaces and may use shared data and network modules.
5. Domain code cannot import UI or presentation code.
6. Feature modules cannot import another feature's internal data or presentation package.
7. Shared modules cannot import feature UI.

## File Rules

1. New implementation files cannot be added to the legacy `steam/ui` package.
2. Feature UI files should remain below 500 lines; complex screens split route, content, cards, sheets and detail views.
3. Feature state files should remain below 500 lines; unrelated request families use separate state holders.
4. Existing legacy files are migrated module by module and removed from the allowlist after each migration.
5. Every module migration includes focused tests, Kotlin compilation, a separate commit and a push to `main`.

## Legacy UI Migration List

The following files existed before the module rule and are the complete temporary allowlist:

```text
SteamBackupScreen.kt
SteamBatchSellSheet.kt
SteamHealthScreen.kt
SteamInventoryMarketContent.kt
SteamLibraryScreen.kt
SteamLoginNotificationHelper.kt
SteamScreen.kt
SteamSearchFilters.kt
SteamTradeOffersContent.kt
SteamUiScalePreferences.kt
SteamUiScaleProvider.kt
SteamViewModel.kt
```

The allowlist only prevents additional accumulation. Each child task removes migrated names until `steam/ui` contains no feature implementation.
