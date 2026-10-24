# Changelog

Releases up to 0.4.0 are upstream's (YoruDev-Ryland/WoWForeverywhere), which this fork started from.

## 0.4.1

The first release from this fork.

- **App updates come from WoW AndroWhere.** The app checks this fork's releases for updates, and Settings → Source code opens it. If your current copy came from the upstream WoW Foreverywhere releases, uninstall it before installing this one. Your game files and addons stay on your storage; the saved login and app settings need to be set again.

## 0.4.0

- **Other WoW versions (experimental).** Settings → Game version lets you pick Classic Era, Burning Crusade Anniversary, Mists of Pandaria Classic or Retail. The whole app then installs, updates, starts and finds addons for that version. Each version keeps its own install and addons, and you can switch back to WoW Forever without losing anything. These versions may run poorly or not start, and automatic sign-in may not work on all of them.
- Settings now shows CurseForge as on.
- Game icons no longer go missing when several load at once.

## 0.3.0

- **CurseForge in the addon browser.** Search, browse, install and update CurseForge addons, with Forever files picked first. No account or key needed. Addons whose authors don't allow third-party downloads show **Open on website** instead.
- Addons land in the right categories more reliably (raid frames under Unit frames, single professions under Professions).
- If the app stops responding, it saves details about what it was doing to help track down reports.

## 0.2.1

- Saving your Battle.net login no longer crashes the app on Android 15 devices without a screen lock. If the device refuses, the app shows a message instead.
- Removed an unused system permission (`READ_LOGS`).

## 0.2.0

The first release: WoW Forever on Snapdragon Android handhelds without a PC.

- Installs the game from Blizzard's CDN to internal storage or an SD card, with pause, resume, repair and background downloading.
- Finds installs already on the device, including ones copied from a PC.
- Runs the WoW Forever beta and the launch version, with a switch that reuses your game data.
- Addon manager for WoWInterface and a curated GitHub catalog, with Forever and Classic builds marked.
- Optional saved Battle.net login, sealed with the Android Keystore and handed to the game for a few seconds only.
- Controller-first home screen with WoW Forever artwork.
