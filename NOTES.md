# Notes

Why things are the way they are, newest first.

## 2026-10-30: New private server profiles take their version from the client

Five "Private server (x.y.z)" rows asked the player something the client folder already answers. New profile now has one Private server row; Create opens the folder picker, and Wow.exe's version resource (the same `detectBuild` the Client screen uses) picks the game by major version. An unreadable version asks, rather than guessing, and a version the app doesn't support is refused. GameVersion still has the five private entries, so existing profiles, their addons and prefs keys don't move. A profile's version stays fixed after it's made, because its addon flavour, catalog and client patches depend on it; picking a different-version folder on the Client screen still says to make a new profile.

## 2026-10-30: Server list selects on A, edits on Y, and adds through one chooser

Daniel switches servers often, and A opening the edit page each time made switching two presses and a Back. A now only selects; Y opens edit, which is where Remove went, because a Remove on Y was one press from losing a server's password. Touch has a non-focusable pencil on each row so the controller path stays one row per server. Add server asks which kind of server, replacing the separate Your own server section, so every server starts in one place. Because the on-device setup page is no longer always on screen, adding "Solo, this device" to the list once setup has made the account now runs for the whole Server screen (`JoinSoloServer`), not only while that page is open. Profiles that aren't 3.3.5a skip the chooser and go straight to an online server, since the own-server options are 3.3.5a only.

## 2026-10-30: The solo server build starts the server before it publishes

The first three on-device tests each failed one step further (stream closed under the log reader, a multi-table DELETE with no default database, then the login server unable to resolve 127.0.0.1), because CI only compiled the server and imported the database: nothing ran it the way the app does. `solo-server.yml` now runs `.github/solo-server/smoke.sh` in the same Termux container before publishing: it starts MariaDB with the app's flags on a copy of the database, runs the app's account SQL through the same command line, writes the same config keys, starts the login and world servers until they listen, checks RC4 through the bundled OpenSSL legacy provider, and stops everything with SIGTERM. smoke.sh mirrors SoloServer.kt, SoloConfig.kt and SoloSetup.kt by hand, so a change to one needs the other. It runs on a Linux kernel, not Android, so Android-only behaviour (the linker64 start, netd's resolver, the phantom process limit) is still only tested on the phone; the resolver case is handled by parsing literal addresses in AzerothCore's Resolver before calling getaddrinfo. Its first run caught a fourth bug: AzerothCore writes its console log to stdout without flushing, and stdout to a pipe is fully buffered, so the world server's "ready..." line never reached the app; the console appender now flushes each line. Release names now carry a build number (`solo-server-<commit>-<build>`), and installed servers re-download when it changes; the database is kept.

## 2026-10-30: Solo server processes are stopped with SIGTERM, and their output reader can't crash the app

The 0.9.0 solo setup crashed the app at "Creating your account". The likely cause, found by reading the code (no device log yet): `Process.destroy()` on Android closes the process's output streams right after signalling it, while the thread copying that output to the log is still reading, so the read threw and the exception escaped the thread, which kills the app. Stopping the database after the account was made was the first place that happened; stopping the server when the game ends would have been the next. The server processes are now sent SIGTERM directly (MariaDB and AzerothCore shut down cleanly on it) and the reader catches its own errors. Each log is also capped at 4 MB so a process stuck in an error loop can't fill the storage. **Share log** joins the last 256 KB of each log into the external cache and opens the share sheet; nothing leaves the device unless the player picks a target.

## 2026-10-30: The solo server build never replaces a published release

`solo-server.yml` used to run on every branch push that touched its files and delete and recreate the `solo-server-<commit>` release. A rebuild is never byte-identical, so the files changed under the same name while main's `solo-server.json` still pinned the old hashes, and setup from main failed until the manifest caught up (it happened during the 0.9.0 cut). The workflow now runs only by hand and stops if the release for that AzerothCore commit already exists. A new build means a new AzerothCore commit, a new release name and a new manifest.

## 2026-10-29: How the solo server runs on the phone

The app's Wine environment is an Android (bionic) userland, and Android only lets an app run programs from its own storage through the system linker (`/system/bin/linker64 <program>`), which is how Wine is started too. So the server has to be built for Android's libc, not for Linux: the solo-server workflow compiles AzerothCore inside Termux's Docker image on an ARM runner, using Termux's clang, Boost, OpenSSL and MariaDB, and bundles every library they load from Termux. AzerothCore needs a few small changes for bionic and MariaDB (a glibc-only CPU mask type, chrono counts that are long long where int64 is long, gSOAP's strerror_r guess, MySQL 8.3 only calls, and two version checks that misread MariaDB; jemalloc is turned off so Android's own allocator is used); they are applied by `.github/solo-server/patch-azerothcore.py`, which fails if any no longer matches, rather than kept as a fork.

The world database is imported once in CI with AzerothCore's `dbimport` and shipped as a ready MariaDB data directory from the same MariaDB build, so the phone never runs the 15 to 30 minute import, and never needs to start the `mysql` command line tool from the server (which the linker rule would block). Account creation writes the account straight into the auth database with AzerothCore's SRP6 verifier, computed in Kotlin, so the server console is never needed. The server keeps `Updates.EnableDatabases = 0`; a newer server build keeps the player's database rather than replacing it, which is fine for builds with the same database layout but may need a migration step later.

Privacy and access: MariaDB listens only on a Unix socket in the app's private storage (`--skip-networking`), so only this app can reach it, and its database password is a fixed placeholder for that reason. The login and world servers bind to 127.0.0.1 only. The account password is random, sealed in the server's LoginVault like any other server password. The server's files (binaries, database, map data) all live in `files/solo`, which Remove server deletes. Nothing is sent anywhere except the downloads from this repo's release and AzerothCore's client-data release.

Processes: mariadbd, authserver and worldserver are children of the app process, kept as long as it lives; the game ending (GuestProgramTerminated or ForceCloseApp) stops them, worldserver first so characters are saved. If the app is killed while they run, the next start kills the leftovers by pid after checking their command line. Android 12+ can kill extra child processes (the phantom process limit is 32 for all apps), and the game already uses several, so that's a risk to watch on the Odin. Untested on a device: whether the bionic build starts, how long the world takes to load, and how much CPU the idle server takes from the client.

## 2026-10-29: 1.12.1 and 2.4.3 private servers, and what they don't get yet

Daniel asked for older WoW versions after looking at Dad's MMO Lab. 1.12.1 (including Turtle WoW) and 2.4.3 are 32-bit D3D9 clients with a realmlist, like 3.3.5a, so they are two more game versions (`PRIVATE_112`, `PRIVATE_243`) on the same launch path, with `LegacyGame` holding what differs. Profiles, servers, passwords, the Client screen, Faster emulation and the patch MPQ switches work the same.

What differs, and what is a guess:
- 1.12 keeps `realmlist.wtf` next to the exe and has no locale folder, and names its exe `WoW.exe`. The app writes that root file on 1.12 only and finds the exe whatever its case. Android's shared storage ignores case anyway, so that is belt and braces.
- 1.12's `gxApi` values may not include `d3d9` (a guess, not checked on a client), so with DXVK the app drops the line and lets the client use its Direct3D default; OpenGL still writes `opengl` and passes `-opengl`.
- Choosing a folder now reads the exe's version and refuses one whose major version is another game's, so a 3.3.5a folder can't land in a 1.12 profile and get the wrong addons. An exe without a readable version is let through. Turtle WoW's exe version wasn't checked; if it doesn't say 1.x, it will be refused and this needs loosening.
- Addon flavours: 1.12 counts Interface 10000 to 11299 and 2.4.3 counts 20000 to 20499, below Classic Era (113xx and up) and Anniversary (205xx), so Blizzard Classic addons don't show as installable. Both load only `<Folder>.toc`. The starter catalogs (`catalog/vanilla112.json`, `catalog/tbc243.json`) list shagu's addons, picked from memory; the workflow drops any repo whose TOC doesn't match, so its log is the check. They publish next to catalog-335.json on the `addon-catalog-335` branch.
- Controller Menus, the login password fill and the Level Up Banner stay 3.3.5a only: the first patches five bytes of the 12340 exe and both use 3.3.5a's login and frame Lua. Porting needs the 1.12 and 2.4.3 offsets found and tested on Daniel's PC, the way the 3.3.5a ones were. ConsolePortLK is 3.3.5a only too. So the app turns the shared controller layout on for these clients all the time; without it the controller did nothing in them.
- 4.3.4 and 5.4.8 came the same evening, when Daniel asked for newer versions too, since the official ones already run. Both ship a 32-bit Wow.exe beside Wow-64.exe; the app runs the 32-bit one, because wowbox64 only runs 32-bit x86 and FEX x64 isn't bundled. MoP 5.4.8 had been parked as the heaviest client under Box64. It is in now as experimental because adding it costs one enum entry, and only the Odin can tell whether it's playable. Addon flavours sit below the Blizzard Classic ones (404xx, 505xx). 6.x and newer are 64-bit only and need an x64 client patch. They would need FEX x64 bundled, which is in TODO, gated on 5.4.8 being playable.
- Untested on the Odin 3: all of it. The launch path is 3.3.5a's, but neither client has been started under wowbox64 here.
## 2026-10-29: The solo server downloads prebuilt map data

AzerothCore needs map data (dbc, maps, vmaps, mmaps) taken from a 3.3.5a client. It is the same for every 3.3.5a client, so extracting it from the player's own folder gains nothing but time: 20 to 40 minutes on the phone, plus hours for mob pathing, and the extractor tools would need porting to ARM. Daniel chose to download AzerothCore's prebuilt `wowgaming/client-data` release instead, as Dad's MMO Lab does. That data is Blizzard's, extracted and re-hosted by a third party. Nothing goes into the APK or the repo, so the no-Blizzard-files rule holds as written, but the app does fetch Blizzard-derived files from someone else's GitHub at the player's request. The release is pinned and its sha256 checked, so a changed file is refused rather than used. Only GitHub sees the download; the server itself listens on 127.0.0.1.

## 2026-10-29: Hold Select for the keyboard on 3.3.5a

Addons can't reach the app from the login, code and character screens, so typing there meant Back, then Keyboard. Claude offered two ways: open the keyboard by itself when the client starts, or a controller button. Daniel picked the button and asked for it to be part of Controller Menus, so it is only on while that add-on is installed (`PadKeyboardButton`, flagged on the container by `prepareLegacyLaunch`). The Lua can't open the keyboard, so the app watches the pad itself, before the controller profile turns buttons into keys.

Every button already has a job in the shared profile, so it had to be a hold on one of them. Select (F11) does nothing on the login screens and is a plain button for ConsolePortLK in the world, unlike L2 and R2, which are its modifiers and are held all the time. Holding Select for 500 ms toggles the keyboard. A shorter press is held back until Select comes up and then sent as a tap, so Select now reaches the game on release instead of on press and can't be held down in game. If ConsolePortLK ever needs Select held, that's the trade-off to revisit. The keyboard uses the same toggle as the touch gesture's Show Keyboard, without the menu path's half-second delay. Untested on the Odin 3: whether its Select arrives as BUTTON_SELECT, and whether the pad still reaches the game while the keyboard is up (some keyboards take the D-pad), so closing it with Select may need a tap instead.

## 2026-10-29: HD pack extras in the 3.3.5a catalog, and why not all of them

The WotLK HD pack's patch 5 (`data/patch-5.mpq`) carries plain add-ons: CompactRaidFrame, LossOfControl and UnitFrameLayers by RomanSpector, NewLevelFrame by alchem1ster, plus ACP, GarbageProtector and Kader's EclipseBar. Its patch 9 (`patch-enus-9.mpq`) is not add-ons: it replaces FrameXML files (level up display, spellbook, character window) and textures, so it stays a client patch. CompactRaidFrame and LossOfControl are on RomanSpector's GitHub with Interface 30300, and the pack's copies are the same code with more translations and different defaults, so the catalog snapshots RomanSpector's repos like every other entry. Nothing is redistributed: the app downloads from the author's repo. Neither repo has a licence and both are largely Blizzard's retail UI code, which is the norm for backports. UnitFrameLayers was left out because it draws `Interface\RaidFrame\Absorb-Fill`, which the stock 3.3.5a client doesn't have, so it likely needs the pack's textures. NewLevelFrame's 3.3.5a version exists only inside the pack (its repo is the Vanilla build, unlicensed), so Daniel chose to write our own instead: `AndroWhere_LevelUp`, kept in the app's resources and copied into `Interface/AddOns` from the Client tab like Controller Menus. It uses no copied art, only solid colours and the client's font, so nothing of alchem1ster's or Blizzard's is shipped. It is a plain add-on, so it sits in the AndroWhere patches list only because that is where our own installs live; the Installed list's prompt card still only offers Controller Menus, so it doesn't nag about the banner. /levelbanner shows it at the current level, which is how to test it at max level. thierbig's LossOfControlSmart-3.3.5 is a fork with an Ace3 options panel and learned CC; RomanSpector's original went in because it is what the pack ships. If both the pack's patch 5 and the catalog copy are present, the same add-on is there twice; the pack's copy can be turned off with the patch.

## 2026-10-29: The WotLK HD pack's patch rules, checked against the pack

The pack's own patch menu won't let some patches be on alone or together, and our Client tab switches used to ignore that. `PatchRules` reads the rules from the menu's `patchmenu/.config/config_en.json` and only applies them, and the per-patch descriptions, when that file exists, since other packs reuse the same letters for other things. Each patch there has `conditions` like `["a11", "HD characters required"]`: another patch's letter, then the state its data and locale archives must be in (1 on, 0 off). A condition wanting the other patch on is a need, one wanting it off is a conflict. On Daniel's PC on 2026-10-29 that gave: u needs a, n needs m (its locale archive), j and k can't be on together. The same rules are built in for a config that can't be read.

The pack's menu locks a switch until its condition holds. The app instead flips the other patches for you: turning one on turns on what it needs and turns off what it conflicts with, turning one off turns off what needs it. The menu also has partial states the app doesn't, like s "Without tables" (data on, locale off): the app shows that as Off and turns both on.

What each patch letter holds, from the menu config and each archive's file list, read on Daniel's PC on 2026-10-29:

- **a** HD characters: player models for every race, HD helmets and shoulders, baked NPC textures.
- **u** Undead without bones: new Undead (Scourge) models plus Taunka and Vrykul, and its own character tables. Its locale archive replaces the same character tables a's does, which is why it needs a.
- **f** HD models and NPCs: creatures, NPCs, doodads and login screen models, with new creature display tables.
- **6** HD world textures: world and dungeon textures and doodads.
- **7** HD armor: armor textures and models, plus herb and ore nodes. The menu marks it dangerous.
- **8** HD weapons: weapons, shields and quivers.
- **j** Mists of Pandaria character creation and **k** Shadowlands character creation. Both replace `CharacterCreate.lua`, `CharacterCreate.xml` and `GlueParent.lua`, so only one can be on.
- **9** Interface windows: later-expansion target frame, minimap, spellbook, character pane, transmog and level up display.
- **d** Cursor and interface: cursors, minimap tracking icons, a few frame textures and the ACP addon manager.
- **s** New spells: spell models and sounds, with new spell tables in its locale archive.
- **i** Icons. **t** Trees and bushes from later expansions. **w** Water, lava and slime. **x** Stars and sky lighting.
- **m** Dungeon maps: world map pages for dungeons and raids, plus the WDM addon. **n** Dungeon minimaps, with its own copy of WDM. The menu marks n dangerous.
- **5** Addons: CompactRaidFrame, loss of control alerts, unit frame layers, EclipseBarFrame, NewLevelFrame, GarbageProtector and ACP.
- **q** Loading screens. **e** "New music": mostly new spell sounds, plus some zone and city music. **b** Blood splashes and splats. The menu marks b dangerous.
- **c** Screens, servers, accounts: login screen models, music and its own login screen code. Its `interface/loginui.lua` keeps saved account passwords in plain text.

## 2026-10-28: The GitHub addon catalog is the fork's own, with upstream as a fallback

The fork's `addon-catalog` workflow was already publishing nightly to its own `addon-catalog` branch, but the app read upstream's. Now the app reads ours and only fetches upstream's when ours is missing or broken (`GitHubProvider.fallbackCatalogUrl`), so a dead branch here doesn't empty the list. To keep upstream's additions without hand-copying, the workflow merges upstream's `catalog/sources.json` into ours before building (`tools/catalog/merge_upstream_sources.py`): our entries win on the same repo, upstream entries with an odd shape are skipped, and an unreachable upstream just means our list alone. Upstream changes land on the next nightly run, not on push. The trade-off: an addon upstream adds shows up here without review, but it is built by the same script from that repo's own GitHub releases, which is no different from what the app showed before.

## 2026-10-28: Controller Menus works without ConsolePortLK

Daniel wanted the login screen add-on to work on its own and to have a name about what it does. He picked Controller Menus. The add-on already read the F-keys that ConsolePortLK's controller profile sends, so the app now applies that same profile when either ConsolePortLK or Controller Menus is installed, rather than a second, menu-only profile. A second profile would have meant two key layouts and changes to the add-on's Lua for no gain, and the app can't switch profiles when the player leaves character select. Trade-off: without ConsolePortLK the profile stays on in the world, where the face buttons press F1 to F4 (target self and party members). An app-written addon that unbinds those keys in the world was built and taken out again: Daniel didn't want the block. The files inside the archive keep their AndroWhereLogin names; only the label changed, so existing installs carry on.

## 2026-10-28: wowbox64 is built from source in CI, and Faster emulation is opt-in

The 0.4.4 `wowbox64.dll` builds that GameNative and others pass around are CI artifacts from forks or personal repos, and the asset list of Box64's official v0.4.4 release couldn't be checked from Claude's container. So `.github/workflows/wowbox64.yml` builds it from the official `ptitSeb/box64` tag with the same toolchain and flags as Box64's own release workflow (llvm-mingw 20260311, `-DWOW64=1 -DARM_DYNAREC=1`, Release), packs it as `wowbox64-<ver>.tzst` and commits it to the branch, printing both SHA-256s in the run summary. Only the `wow_legacy` container asks for it (`box64Version`); the ARM64 retail container doesn't use Box64 at all. The container also pins Box64's Performance preset instead of relying on `Container`'s default.

Faster emulation appends `BOX64_DYNAREC_SAFEFLAGS=0 FORWARD=1024 DIRTY=1 CALLRET=2` to the container's env, which the launcher applies after the preset, so they win. SAFEFLAGS=0 and DIRTY=1 can break code that relies on exact x86 flags or writes into its own code pages, so it stays off by default and its effect has to be measured on the device with the overlay. CALLRET=2 is Box64 0.4.4's own default, but the Performance preset pins 1, so it only takes effect with this switch.

## 2026-10-28: Profiles, and addons kept apart without moving files

Daniel asked for profiles in place of Settings → Game version: each with its own version, folder, addons and client and server settings, switched from the top left. A profile stores its name, version and folder (`wow/profile`). The active one sets `GameVersions.selected`, the per-version game path and which 3.3.5a settings file is read, so the launch code didn't change. The first 3.3.5a profile keeps the old `wow_legacy` settings and its password vault, so nothing had to move. New 3.3.5a profiles get their own settings file and server ids, so passwords never collide. The Battle.net login stays one for the whole app, in Settings, as Daniel chose.

Addons live in the game folder, so two profiles on one folder can't have separate copies without a second client (17 GB for 3.3.5a). Daniel wanted sharing a folder to work anyway. Moving `AddOns` folders on each launch was ruled out: slow with big addon sets, and a crash midway leaves them mixed. Instead every addon stays on disk once, each profile claims what it installs, and an addon nobody claims belongs to everyone. When a second profile joins a folder, the first one claims what is already there. Before a launch the app writes `<name>: disabled` into every `AddOns.txt` under `WTF/Account` for the addons this profile doesn't see, and turns back on only the ones it turned off itself, so the player's own choices in the game's AddOns menu stay. The 3.3.5a client patches (AndroWhere Login and the patch archive switches) are recorded per profile and put in place on switch and at launch, only on a shared folder. Limits: an account that has never logged in on that client loads every addon on its first login, since it has no AddOns.txt yet, and updating an addon updates it for every profile on that folder.

## 2026-10-27: Type password and Enable Mouse removed

Daniel found 0.6.0's Fill in password doing nothing on the phone because AndroWhere Login wasn't installed there, and the app never said so. He asked to hide the password options until it is installed, and to remove Type password and the Enable Mouse button at the top. He also asked to merge Server login into Client and server, so the password now sits there under the servers, and the private server home screen has no Server login tile. That spot offers Install AndroWhere Login until the add-on is in, and only then shows the password, Fill in password and Log in automatically. Type password (button and menu item) is gone, so the saved password only ever reaches the game through Fill in password. The Enable Mouse button is upstream's shooter mode runtime toggle; both WoW containers keep shooter mode but set `showRuntimeToggle` off at every launch.

## 2026-10-27: Server list on the login screen

Daniel wanted the HD pack's login server list, but with our own servers. The app keeps the list (`LegacyServers`, JSON in the `wow_legacy` prefs, no passwords in it), and `realmlist` in the settings stays the selected server's, so launching is unchanged. Each server's password has its own `LoginVault`; the first server keeps the vault the single password used before, so nothing has to move. At launch the app writes `AndroWhereServers.lua` (names, realmlists and account names, as byte escapes) into AndroWhere Login's archive and leaves it there: none of it is secret. L1 and R1 on the login screen set the `realmList` CVar, the account name and the password for the next server. The switch lasts for that game session: the app writes the selected server's realmlist again at every launch.

With Fill in password on, every saved server's password goes into `AndroWhereLoginData.lua` for the launch, not just the selected one's, so switching servers on the login screen still logs in with A. That widens the window below from one password to all of them, for the same ten seconds or so, and is wiped the same way. Daniel chose all of them over only the selected server's on 2026-10-27.

## 2026-10-27: Log in with A puts the password in the client folder for a moment

Daniel didn't want to log in on the Odin with Back, then Type password, and chose to have A log in. The login screen's Lua can only read files from the client's MPQ archives, so with Log in with A turned on (a checkbox under the password in Server login, off by default) the app adds `AndroWhereLoginData.lua` with the saved server password to AndroWhere Login's archive at launch. Every byte is written as a decimal escape, so no password can break out of the Lua string. The glue reads it into a local, clears the global, fills the password box and keeps the boxes unfocused so the pad's A reaches the login frame. Daniel then asked for two checkboxes: Fill in password, and Log in automatically under it. Auto login presses Log in once per game start, half a second after the screen shows; after a failed login or a disconnect it waits for A, so a wrong saved password is never sent over and over, which could get an account locked.

The app then rewrites the archive without the password: 10 seconds after the client first opens or reads it (FileObserver), after 3 minutes at most, when the game ends, when the launcher resumes, on abort, exit and launch errors, when the password is forgotten and when the switch is turned off. The rewrite goes to a temp file renamed over the archive, never in place: the running client keeps the archive it opened (the old inode), so the game isn't corrupted and still has the password for a disconnect back to the login screen, while storage no longer does. Trade-off, said plainly in the app: from launch until that wipe, the password sits unencrypted in the client folder, which is often shared storage, so any app with access to that folder could read it. If the app is killed in that window, it stays there until the app opens again.

## 2026-10-27: Client add-ons patch Wow.exe

Daniel wanted our own add-ons for the 3.3.5a client itself, starting with the login screens, instead of copying an HD pack's changes. Login screens (GlueXML) can't use normal addons: the client only loads them from its MPQ archives, and the stock exe refuses changed ones ("Your login interface files are corrupt"). Tested on Daniel's PC on 2026-10-27 with his Warmane client: the 1-byte interface patch some guides name did not help, and the five changes around 0x415A25 that the WotLK HD pack's exe also has were enough on their own. So turning on AndroWhere Login checks that Wow.exe is build 12340 with the original bytes, keeps a copy next to it (`Wow.exe.androwhere-original`), and changes only those five spots; turning it off puts the copy back. An exe already patched by something else is left alone and never "restored". Trade-off: a server's anti-cheat can see a changed exe. Warmane accepted the same bytes in the HD pack's exe, but other servers might not.

The add-on is our own Lua in `resources/androwhere/glue/`, written into a small uncompressed `Data/<locale>/patch-<locale>-Z.MPQ` together with the client's own `GlueXML.toc` plus one line. The toc is read on the device from the highest priority archive that has one, so no Blizzard file is in the repo, and an HD pack's own login screen keeps working under ours. Z is the last letter, so ours loads over the packs' lettered patches. The add-on hooks Blizzard's key handlers and translates the ConsolePortLK profile's F-keys into the Enter, Escape and arrow keys those screens already understand. On the login screen itself the password box holds the keyboard, so pad keys never reach Lua there; the app's Type password ends with Enter and logs in instead, or Log in with A (below) fills the box and lets go of the keyboard.

The same Client tab turns patch archives already in the player's client on and off by adding or removing `.disabled`, the convention the HD packs' own menus use. Names come from the pack's `Patch_Lables.txt` or its patch menu config when there is one. Nothing from those packs is shipped.

## 2026-10-24: Only full releases

Daniel stopped test builds. 0.5.1 removed the `test-build` workflow run, its pre-release and the updater's test channel. Pull requests still run the "Checks" workflow, with unit tests and a debug build. Releases come from `release.yml`, dispatched with `tag=vX.Y.Z`, because tag pushes from Claude's container are refused. At Daniel's request, 0.5.1 was published with `prune=true`, which deleted every other release and its tag once 0.5.1 was confirmed as the latest. A phone still on a test build from before 0.5.0's updater can't see releases and needs the 0.5.1 APK installed by hand once.

## 2026-10-24: CurseForge is bring-your-own

Daniel didn't want CurseForge searches going through upstream's `cf.yorudev.com`, and didn't want to run a shared proxy either. So there's no default: Settings, CurseForge takes the player's own API key or the address of a proxy they run, and CurseForge stays off without one (Addons says so; Wago, WoWInterface and GitHub are unaffected). The key is sealed with `LoginVault` in its own prefs, same as the server password, and is only sent as `x-api-key` to `api.curseforge.com`, never to a proxy; with a proxy the app sends no key. Proxy addresses must be https with no login, query or fragment. Saving one clears the other. The `CURSEFORGE_API_KEY` and `CURSEFORGE_PROXY` build settings are gone and `release.yml` no longer writes the key, so a key can't end up in the APK by accident. Trade-off: a player's key sits on their device, so someone with root on that phone could read it; that's the player's own key, not a shared one.

## 2026-10-24: Tap where you touch already exists

Daniel asked for taps to click at the finger instead of at the cursor on 3.3.5a. That's upstream's Touchscreen mode in the in-game menu (Back, then Touchscreen mode), which the legacy container already has and saves per container, so nothing was built. It's not on by default for 3.3.5a because it hides the cursor (Touch gestures can show it again), and the right stick moves the cursor for ConsolePortLK.

## 2026-10-24: 0.5.0 and moving test builds onto releases

Test builds up to 47 only check the `test-build` pre-release, so they can't see 0.5.0 on their own. After the release was published, `test-build.yml` with `release_action=promote` republished the 0.5.0 release APK as the next test build; installing it puts the phone on 0.5.0 and the release channel. From 0.5.0 on, a test build checks releases first (against the version it was built from) and then the test channel. 0.4.1, hidden as a draft during testing, was published again so the release history stays complete.

## 2026-10-24: Immersion is driven by the ConsolePortLK setup addon

Immersion-WotLK (the 3.3.5a backport, in the 3.3.5a catalog) still has upstream's ConsolePort code in `Logic/Controller.lua`, but its TOC doesn't load that file, so with ConsolePortLK the face buttons kept their game bindings (jump, attack) while a quest or gossip window was open. Loading the file by editing Immersion's TOC was ruled out: it's retail code (Azerite and friends) that the backport's author left out, it can't be tried without the phone, and the edit would be lost on every update. Instead `!ConsolePortLK_AndroWhere` hooks Immersion's own `SetPropagateKeyboardInput` and `ClearKeyboardInput`, which it calls when its window takes and releases the keyboard. In between, priority override bindings send the pad's F-keys to a hidden button that calls Immersion's frame directly: A accepts, continues or clicks the highlighted option, B, Back and Start close, X repeats the text, D-pad up and down move the highlight, left and right cycle reward choices. ConsolePortLK's hint bar shows A and B. Nothing is bound in combat, same as Immersion. There's no separate install card: Immersion is in Browse already, and a second GitHub record would fight the catalog's entry over updates.

## 2026-10-24: The 3.3.5a addon catalog is separate and snapshots branches

Most 3.3.5a backports are plain GitHub repos with no releases, so upstream's catalog (releases with packager files, built on upstream's branch) can't list them. `catalog/wotlk335.json` and the "Addon catalog 3.3.5a" workflow build `catalog-335.json` on this fork's `addon-catalog-335` branch instead: each entry is the latest commit's source zip, the commit is the file id so new commits show as updates, and a repo is dropped when its TOCs don't say Interface 303xx. The list started from NoM0Re's WoW-3.3.5a-Addons page, only taking repos maintained on GitHub, not the re-uploaded zips there. Addons are third-party Lua like any other, run only inside the game, and are not checked beyond the TOC. Some repos carry TOCs for several clients (Questie's 3.3.5a fork ships a placeholder `Questie.toc` beside `Questie-335.toc`), so a branch zip's folder is named after a TOC whose Interface fits the client, and the catalog accepts a repo when any of those TOCs does. Installing moves folders by renaming them and copies when the rename is refused, since Questie's 3.3.5a folder failed with "cannot move" on Daniel's phone (2026-10-24). ConsolePortLK stays out of this list: it has its own install card that tracks its GitHub releases, and a branch snapshot entry would offer it as an update.

## 2026-10-24: The Type password button hides after one tap

The 3.3.5a login screen can't be detected from outside the game, so the on-screen button shows from launch until it's tapped once; after that it would only sit over the game. A second login in the same session (after a disconnect) uses the quick menu item.

## 2026-10-24: 3.3.5a speed: logs off by default, Box64 already on its fastest preset

The legacy container wrote a Wine log on every launch with `WINEDEBUG=+warn,+err,+fixme,+loaddll`. Wine formats those lines inside the emulated process and the app appends each one to a file, opening and closing it per line; if that reader falls behind, the pipe fills and the game blocks on its next write. That was spike tooling, so it is now opt-in (Save a log).

What was checked and left alone: the container gets Box64's Performance preset (`Container` default, BIGBLOCK=3, CALLRET=1, STRONGMEM=0, SAFEFLAGS=1, FASTNAN/FASTROUND=1), so there is no safe Box64 setting left to turn up. Render size on Adreno 8xx is already 1280x720. In WoW64 the 32-bit `d3d9.dll` (DXVK or WineD3D) is x86 code too, so it runs under Box64 like the game; every draw call costs emulated CPU time. That makes 3.3.5a CPU-bound on this path, and object count (view distance, ground clutter, shadows, glow) matters more than resolution. The overlay (`DXVK_HUD=fps,frametimes,gpuload`) is there to confirm that on the device before tuning further.

## 2026-10-24: ConsolePortLK gets keys, not a gamepad

3.3.5a has no gamepad API, and the `wow_legacy` container skips winhandler, so no XInput reaches the game either. ConsolePortLK expects a mapper (WoWpadX on PC) to turn buttons into keys and calibrates once in game. `wow/legacy/ConsolePortLK.kt` installs the `ConsolePortLK` input profile (`assets/wow/consoleportlk.icp`) under a free id the first time it's needed and sets it as the container's `profileId` while `Interface/AddOns/ConsolePort` exists, unless the player picked another profile. Mapping: face buttons, D-pad, LB, RB, Back and Start to F1-F12; LT and RT to Shift and Ctrl, the addon's two modifiers; L3 and R3 to left and right click; left stick to WASD; right stick to the mouse; Guide still opens the app's menu. F-keys because numpad keys change meaning with Num Lock under Wine. The profile carries a `mappingVersion`; the app rewrites it only when the shipped version is newer than the one it last wrote (kept in prefs, since Edit controls saves the file without unknown keys), so edits stick until the shipped mapping changes. Version 2 fixed the left stick: `getKeyCodeForAxis` reports a stick pushed forward as `AXIS_Y_POSITIVE`. ConsolePortLK's own calibration (three presses per button, and the PlayStation layout puts Shift on L1) stalled on the Odin 3, so the app also writes `!ConsolePortLK_AndroWhere`, a small addon that loads before ConsolePort ("!" sorts first) and, when ConsolePort's saved variables arrive, sets the Xbox layout and the calibration to the profile's keys. It runs once per `PRESET`, so later changes in `/cp` stick. It's deleted when ConsolePortLK is removed. The addon is downloaded from GitHub at install time, not bundled (Artistic License 2.0).

## 2026-10-24: Private server (3.3.5a) is a game version without a Blizzard product

`GameVersion.PRIVATE_335` has no products, so the launcher treats it apart: no CDN install, update check, Battle.net login or auto-launch; Play runs `launchLegacy`, Game files is the Client and server screen, Account is Server login. Addons go to `<client>/Interface/AddOns`. CurseForge and Wago are off for it because their "Wrath" files are for Blizzard's 3.4 Classic and mostly break on 3.3.5a; WoWInterface entries marked 3.3 and GitHub links are used instead.

## 2026-10-24: Why the 3.3.5a client starts without winhandler

Upstream starts every non-ARM64 exe through `winhandler.exe`, which is x64 and needs FEX's `libarm64ecfex.dll`. The app doesn't ship that DLL, so the first 3.3.5a launches died with c0000135 before `Wow.exe` loaded. The `wow_legacy` container sets `skipWinHandler` and `Wow.exe` is started directly (WoW64 with wowbox64). FEX's `libwow64fex.dll` isn't bundled either, so Box64 is the only x86 emulator.

## 2026-10-24: The private server password is typed, not passed

The 3.3.5a client has no way to take a password from outside, and addons can't run on the login screen. So the password sits in its own `LoginVault` (AES-GCM, Android Keystore) and the in-game menu's Type password sends it as key presses plus Enter. It only types when the player picks it, never on a timer, so it can't land in chat if the login screen is slow. Trade-off: whatever has focus when the player picks it receives the password.

## 2026-10-24: Wine logs come from the legacy container, and sharing is opt-in

The launcher is rebuilt while the game runs, so state held in memory is gone when the player comes back. The `wow_legacy` container carries a `wineLog` extra that makes XServerScreen write the Wine log, without touching the global debug setting, and a "log waiting" pref survives until the player shares or dismisses it. Sharing goes through Android's share sheet: an automatic upload would need a GitHub token in the APK, which anyone could extract, and the logs would be public on the repo.

## 2026-10-24: Renamed to WoW AndroWhere, but only what people see

The name lives in `wow/AppName.kt` and `app_name` (no longer translated, since the translations still said GameNative). The package id `app.wowforeverywhere`, the Keystore alias `foreverywhere_battle_net_v2` and the on-disk files (`.foreverywhere-install.json`, `.foreverywhere-addons.json`) keep the old name: changing them would break updates, drop the saved login, or make the app forget installs and addons. Kotlin names like `ForeverywhereApp` stay so upstream merges stay clean. Ko-fi links still go to upstream's author and say so, since the fork takes no donations. The user agent sent to CurseForge and GitHub is now `WoW-AndroWhere`.

## 2026-10-24: Claude releases by a manual run, not a tag push

Claude's container can only push its own working branch, not tags. So a release from Claude is a manual run of `release.yml` on `main` with the version as input: it builds `main` and creates the tag on that commit when it publishes. A pushed tag still works the same as before.

## 2026-10-24: Release builds can bake the CurseForge key into the APK

`release.yml` writes the `CURSEFORGE_API_KEY` secret into `local.properties`, and `app/build.gradle.kts` calls CurseForge directly with the key whenever it is set. So adding that secret to the fork would ship the key in every APK. Leave it unset; release builds then use the proxy. Without `-PCURSEFORGE_PROXY`, that proxy is upstream's `cf.yorudev.com`, so CurseForge searches from the fork pass through a server Daniel doesn't run. It sees which addons a device searches for and its IP, nothing more. (Superseded: CurseForge is now bring-your-own, see above.)

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
