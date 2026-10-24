# Done

What the app does, by area, with the version each feature came in. Versions up to 0.4.0 are upstream releases (YoruDev-Ryland/WoWForeverywhere) the fork started from; "base" means it came from jaredgei/wow-forever-android before that.

## Running the game

- Runs Blizzard's Windows ARM64 client under Proton 11 ARM64EC, DXVK and Turnip on Adreno 7xx/8xx (base)
- Snapdragon 8 Elite / Adreno 8xx support and Samsung UBWC hint (base)
- Resolution follows the screen size and aspect ratio, keeping the user's game settings (base)
- Handheld graphics defaults and gamepad mode on in Config.wtf (base)
- Warning before launching on a device without an Adreno GPU (0.2.0)
- Stall watchdog writes what blocked the main thread to `crash_logs/` (0.3.0)

## Installing and updating the game

- Resumable, verified game installer from Blizzard's CDN to internal storage or SD card (0.2.0)
- Install runs as a foreground service with pause, resume, cancel and progress notification (0.2.0)
- Finds and adopts existing installs, including ones copied from a PC (0.2.0)
- Game updates before Play, using the full installer so data files are patched too (0.2.0)
- WoW Forever beta and launch versions, with a switch that reuses game data (0.2.0)
- Install language written to Config.wtf (0.2.0)

## Game versions

- Settings → Game version: Classic Era, Burning Crusade Anniversary, Mists of Pandaria Classic and Retail, experimental, with a warning (0.4.0)
- Each version keeps its own install, addons and addon database; launch, first run and detection follow the selected version (0.4.0)

## Addons

- Addon manager: browse, search, install, update, update all and safe removal, with dependency resolution (0.2.0)
- Merges the same addon across sites despite author or title differences (0.2.0)
- Icons, descriptions, screenshots and category browsing (0.2.0)
- Forever and Classic marks with Blizzard's round game icons (0.2.0)
- WoWInterface source (0.2.0)
- Keyless GitHub addon catalog with nightly rebuild (0.2.0)
- Open addon pages on their source site, Ko-fi support links (0.2.0)
- CurseForge source through a key-holding proxy (0.3.0)
- Addon sources follow the selected game version (0.4.0)

## Sign-in and security

- Saved Battle.net login, AES-256-GCM in the Android Keystore, handed to the game through a short-lived `login.txt` (0.2.0)
- `login.txt` wiped 20 s after the game reads it, with Hotfix.log and 3-minute backstops (0.2.0)
- App data excluded from cloud backup and device transfer; login dialog blocks screenshots (0.2.0)
- Saving the login works on Android 15 devices without a screen lock (0.2.1)

## App

- Dashboard home with Play, Addons, Game files, Account and Settings tiles, controller-first (0.2.0)
- First-run flow: welcome and device check, find game, install, sign in (0.2.0)
- Art-led look in WoW Forever's colours, applied to upstream screens too (0.2.0)
- In-app app updater from GitHub releases (base)
- Signed release builds published by tag (0.2.0)
- App updates and the Source code link come from this fork, signed with its own key (0.4.1)
- Pull requests run the launcher unit tests and a build; manual test builds publish a release-signed APK as a pre-release the updater ignores (0.4.1)
