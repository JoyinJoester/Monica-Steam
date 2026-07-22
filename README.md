# Monica Steam

Monica Steam is a standalone Android build of Monica's Steam experience. It keeps the Steam screen, account storage, Steam Guard codes, login approvals, authorized devices, inventory, market tools, and QR sign-in while removing the rest of Monica from the application entry points and manifest.

## Independence

- Application ID: `takagi.ru.monica.steamapp`
- Project name: `MonicaSteam`
- Local databases and preferences are isolated from Monica Android by the Android application sandbox.
- Monica Android remains a separate project and can be installed beside Monica Steam.

## Build

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

APK names use the `Monica-Steam-Android` prefix.

## Included Steam Features

- Steam Guard codes and multiple accounts
- maFile, key-only, credential, and QR imports
- Login approvals and mobile confirmations
- Authorized-device management and authenticator removal
- Inventory, market pricing, batch listing, and listing cancellation
- Encrypted local Steam account storage and optional Monica MDBX-backed storage

This project is derived from Monica Android and remains under the repository's GPL-3.0 license.
