# TODO

Only work that's left. Shipped items move to DONE.md, rejected ones to DROPPED.md.

## Next: your own 3.3.5a server

Agreed with Daniel on 2026-10-29, after looking at Dad's MMO Lab (offline AzerothCore servers on the Steam Deck). One release, marked experimental, since it can only be tested on the Odin.

- **"Your own server" section on the Server screen.** Under Servers on private server profiles, two rows: On this device and On a PC. A server set up from either shows in the Servers list like any other ("Solo, this device" or "Solo, PC"), so Play, Controller Menus and auto login work unchanged. New code in `wow/solo` and its own screen file, with only a section call added to `LegacyServerScreen`.
- **Solo server on this device: the server.** AzerothCore 3.3.5a (no playerbots) and MariaDB, built for ARM64 Linux by a CI workflow from pinned tags and published as a release asset with a sha256. The app downloads them on first setup (about 300 MB, a guess), never in the APK. They run as child processes inside the app's existing Linux image (imagefs), kept alive by a foreground service while the game runs. That this works at all is the biggest unknown, so it's the first thing to try. Listens on 127.0.0.1 only.
- **Solo server on this device: map data.** AzerothCore's prebuilt client-data (wowgaming/client-data v20.0, `Data.zip`, 1.2 GB, 3.2 GB unpacked, sha256 `a3d4df635ae6c2c8f08052c32a79e0f806955150ad36b014a823dd08a32a4610`), checked before use. The app reads the zip's file list and fetches only the folders it needs with range requests, so mob pathing (mmaps, 2.19 GB) is a switch, on by default; without it the rest is about 1 GB. See NOTES.md for why this is downloaded and not extracted.
- **Solo server on this device: setup page.** Shows what will be downloaded and its size, free space left, one Set up button, then progress for download, check and the first world database import (about 15 to 30 minutes on the phone, a guess). After setup: status (Stopped, Starting, Running), Start and Stop, "GM commands for my account", and Remove server with the space it frees. The app creates the account through the server console and keeps its password in LoginVault, so the console is never shown.
- **Solo server on this device: Play.** Picking the solo server and pressing Play starts the server if it's stopped, waits for the world server's ready line, then launches the client with realmlist 127.0.0.1 and auto login. The server stops when the game exits. Measure fps with the server running against Warmane on the same spot, since the client is CPU-bound and the server takes some of it.
- **Solo server on a PC.** A page to type the PC's address, with Check connection (a TCP connect to port 3724) and a short how-to for Dad's MMO Lab's Windows installer, including setting the realm address to the PC's LAN IP. Saving adds it to the Servers list. No code runs on the PC from the app.

## Next: older private server versions, the rest

1.12.1, 2.4.3, 4.3.4 and 5.4.8 basic play is built (see DONE.md). What they still lack:

- **Controller Menus on 1.12.1 and 2.4.3.** Find which bytes of the 5875 and 8606 exes stop the "login interface files are corrupt" check, on Daniel's PC like the 3.3.5a ones, then port the login, realm and character Lua to each client's GlueXML. Add each to `LegacyGame.hasAndroWherePatches` and give `WowExePatch` a spot list per build.
- **Grow the 1.12.1 and 2.4.3 addon catalogs.** Read the first workflow run's log to see which starter repos were kept, then add more from the community lists, the way `wotlk335.json` was filled.
- **Addon catalogs for 4.3.4 and 5.4.8.** Same as above, once Daniel says either runs well enough to play.
- **Find the fastest settings for 4.3.4 and 5.4.8.** Measure fps against 3.3.5a on the same spot, then decide whether their own lighter-graphics values or Box64 flags are worth it.

## Later: 6.x and newer private servers

Daniel asked for this on 2026-10-29, gated on 5.4.8 being playable on the Odin. If 5.4.8 isn't, it isn't worth building.

- **Bundle FEX's ARM64EC library (`libarm64ecfex.dll`) so Proton can run x64 code.** Private server clients from 6.x on are x64 only. Blizzard's ARM64 build can't be used, because the servers' client patches (Arctium) only fit the old x64 builds. Fetch FEX from a pinned release in CI with a sha256, like wowbox64, and turn it on only for these profiles. Check that it doesn't change how the native ARM64 clients start.
- **Run the client patcher under emulation.** Arctium's launcher is an x64 exe that patches the client in memory at start. Start it as the profile's exe instead of Wow.exe, with the realm address it needs. Try it with one well-known build first (7.3.5 or 3.4.x private servers, whichever Daniel plays).
- **Measure before going further.** Compare frame rate with 5.4.8 on the same spot. x64 emulation through FEX is likely slower than wowbox64 on 32-bit code (a guess), on a client that's heavier again.
