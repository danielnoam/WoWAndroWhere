# Notes

Why things are the way they are, newest first.

## 2026-10-24: Release builds can bake the CurseForge key into the APK

`release.yml` writes the `CURSEFORGE_API_KEY` secret into `local.properties`, and `app/build.gradle.kts` calls CurseForge directly with the key whenever it is set. So adding that secret to the fork would ship the key in every APK. Leave it unset; release builds then use the proxy. Without `-PCURSEFORGE_PROXY`, that proxy is upstream's `cf.yorudev.com`, so CurseForge searches from the fork pass through a server Daniel doesn't run. It sees which addons a device searches for and its IP, nothing more.

## 2026-10-24: The fork has its own signing key and update channel

The updater only installs APKs signed with the same key as the installed app, and both builds use the package id `app.wowforeverywhere`. The fork signs with its own key, so the first fork install needs an uninstall of the upstream app, and upstream fixes only arrive when merged into the fork. Losing `release.jks` or its password means another uninstall for every update after that.

## 2026-10-24: Private servers mean legacy x86 clients

The app runs the ARM64 client natively under ARM64EC. Legacy private servers (1.12, 2.4.3, 3.3.5a) use 32-bit x86 D3D9 clients configured with `realmlist.wtf`, so they need the x86 WoW64 path (FEX/wowbox64) and a D3D9 route that the bundled aarch64 DXVK doesn't cover. That's why the work starts with an on-device spike instead of a feature. The user supplies the client; no Blizzard files ship.

## 2026-10-24: ConsolePortLK is downloaded, not bundled

It's under Artistic License 2.0. Bundling is allowed with the license and copyright, but downloading the latest GitHub release at install time stays current and avoids redistributing it.

## 2026-10-22: Other game versions are experimental and opt-in

Anyone who never touches Settings → Game version gets exactly the Forever app: same screens, paths and addon results. Each version keeps its own install and addon database so switching can't mix or lose a Forever install. Details in `docs/superpowers/specs/2026-10-22-game-versions-design.md`.

## 2026-10-22: CurseForge goes through a proxy

The key must not be in the public repo or the APK, and CurseForge's terms require it to stay private. `server/curseforge-proxy` adds the key, forwards only the WoW requests the app makes, rate-limits per device and caches nothing. Addon images aren't kept in the disk cache, per CurseForge's terms.

## 2026-10-19: Saved login is sealed and short-lived

AES-256-GCM in the Android Keystore, email included. `login.txt` exists only for seconds, is wiped after the game reads it, and has `.nomedia` next to it. App data is excluded from backups and device transfer. The key no longer requires an unlocked device, because on Android 15 that makes it unusable without a screen lock (0.2.1).

## 2026-10-18: The installer downloads about 0.5 GB, not 67 GB

The spike showed the client boots from `.build.info`, `.flavor.info`, `Config.wtf` and the install-manifest files, then streams the rest of its data itself. Pre-filling local storage would need a CASC writer. Details in `docs/superpowers/notes/2026-10-18-installer-spike.md`.

## 2026-10-18: Storage uses all-files access

Tens of GB to an SD card at speed rules out the Storage Access Framework, and app-specific folders are deleted on uninstall, which would wipe the game. So the app asks for `MANAGE_EXTERNAL_STORAGE` and can't go on the Play Store.
