# Done

What the app does, by area, with the version each feature came in. Versions up to 0.4.0 are upstream releases (YoruDev-Ryland/WoWForeverywhere) the fork started from; "base" means it came from jaredgei/wow-forever-android before that.

## Running the game

- Runs Blizzard's Windows ARM64 client under Proton 11 ARM64EC, DXVK and Turnip on Adreno 7xx/8xx (base)
- Snapdragon 8 Elite / Adreno 8xx support and Samsung UBWC hint (base)
- Resolution follows the screen size and aspect ratio, keeping the user's game settings (base)
- Handheld graphics defaults and gamepad mode on in Config.wtf (base)
- Warning before launching on a device without an Adreno GPU (0.2.0)
- Stall watchdog writes what blocked the main thread to `crash_logs/` (0.3.0)
- Touchscreen mode in the in-game menu: a tap clicks where the finger is, two fingers right-click, three fingers open the keyboard, pinch scrolls; saved per game (base)

## Installing and updating the game

- Resumable, verified game installer from Blizzard's CDN to internal storage or SD card (0.2.0)
- Install runs as a foreground service with pause, resume, cancel and progress notification (0.2.0)
- Finds and adopts existing installs, including ones copied from a PC (0.2.0)
- Game updates before Play, using the full installer so data files are patched too (0.2.0)
- WoW Forever beta and launch versions, with a switch that reuses game data (0.2.0)
- Install language written to Config.wtf (0.2.0)

## Game versions

- Classic Era, Burning Crusade Anniversary, Mists of Pandaria Classic and Retail, experimental (0.4.0)
- Profiles: name, game version and folder each, switched from the home top bar or with L1 and R1; older setups become profiles on first start (0.8.0); Y edits, wider list and button so names fit (0.8.1)
- New profile screen: pick the version and name, then that version's download, find or 3.3.5a folder setup; replaces Settings → Game version (0.8.0)
- Profiles sharing a folder keep their own addons (AddOns.txt written at launch), server list, passwords and 3.3.5a client patches (0.8.0)
- Private server (3.3.5a) game version: your own client folder and realmlist, its own home screen, Play, and Client and server screen (0.5.0)
- Private server (1.12.1, Turtle WoW included) and (2.4.3) game versions on the same launch path, experimental: realmlist next to WoW.exe on 1.12, folder rejected when its client is another version, own addon flavours and starter catalogs, patch switches in the Client tab, controller layout always on (unreleased)
- Private server (4.3.4) and (5.4.8) game versions on the same path, experimental: 32-bit Wow.exe only, own addon flavours below Cataclysm and MoP Classic, no catalog yet (unreleased)
- Your own server on 3.3.5a profiles, experimental: a solo AzerothCore server (no bots) the app downloads, checks and runs on the device, with Play starting it and the game's end stopping it; map data fetched folder by folder from AzerothCore's client-data with per-file checksums, mob pathing optional; account made for you with GM commands as a switch; Remove server frees the space (unreleased)
- Your own server on a PC: address, connection check and Dad's MMO Lab guides, added to the server list (unreleased)
- 3.3.5a clients run through Box64 WoW64, started directly instead of through winhandler; DXVK renderer, OpenGL as a fallback (0.5.0)
- Saved, encrypted private server password for each server, in Client and server, used by Controller Menus' Fill in password; Install is offered until the add-on is in (0.5.0, Type password replaced 0.7.0); a saved password shows as dots (0.8.1)
- No Enable Mouse button over the WoW containers (0.7.0)
- Settings as a top bar button on the home screen, marked when an app update is out (0.7.0)
- Private server launches can save a Wine log (off by default, it costs frame rate) and offer to share it when the game closes (0.5.0)
- Performance overlay for private server launches: DXVK frame rate, frame times and GPU load (0.5.0)
- Use lighter graphics: one tap writes CPU-light 3.3.5a settings to Config.wtf (0.5.0)
- Faster emulation: opt-in Box64 flags on top of the Performance preset for 3.3.5a (0.8.4)
- 3.3.5a runs on wowbox64 0.4.4, built in CI from the official Box64 tag (0.8.4)

## Addons

- Addon manager: browse, search, install, update, update all and safe removal, with dependency resolution (0.2.0)
- Merges the same addon across sites despite author or title differences (0.2.0)
- Icons, descriptions, screenshots and category browsing (0.2.0)
- Forever and Classic marks with Blizzard's round game icons (0.2.0)
- WoWInterface source (0.2.0)
- Keyless GitHub addon catalog with nightly rebuild (0.2.0)
- GitHub addon catalog built and hosted by this fork, with upstream's addons merged in and upstream's copy as a fallback (0.8.5)
- Open addon pages on their source site, Ko-fi support links (0.2.0)
- CurseForge source through a key-holding proxy (0.3.0)
- CurseForge with your own API key (stored encrypted) or your own proxy, set in Settings; no shared default (0.5.1)
- Addon sources follow the selected game version (0.4.0)
- 3.3.5a addons: WoWInterface filtered to 3.3, GitHub links without releases install from the default branch and get renamed after their TOC (0.5.0)
- 3.3.5a addon catalog: over 30 GitHub backports from `catalog/wotlk335.json`, snapshotted nightly by commit and kept only when their TOC says Interface 303xx (0.5.0)
- Compact Raid Frames and Loss of Control Alerts (RomanSpector's 3.3.5a backports, the ones the WotLK HD pack bundles) in the 3.3.5a addon catalog (0.8.7)
- Level Up Banner: our own 3.3.5a add-on, installed and removed from the Client tab's AndroWhere patches, /levelbanner to preview (0.8.7)
- One-tap ConsolePortLK install on the 3.3.5a client, with a controller-to-keyboard profile used automatically while it is installed (0.5.0)
- Immersion's quest and gossip windows work with the controller on 3.3.5a when ConsolePortLK is installed (0.5.0)
- Client tab in Addons on 3.3.5a, for add-ons that change the client itself, with an AndroWhere patches section (Install and Remove) and a card on the Installed list for ones not installed yet (0.6.0)
- Controller Menus (was AndroWhere Login): controller and button hints on the 3.3.5a character select, realm list and dialogs, using a 5-byte Wow.exe patch that is undone when turned off (0.6.0)
- Log in with A on the 3.3.5a login screen, and optional auto login once per game start: opt-in, the saved password goes into Controller Menus for one launch and is wiped right after the client opens it (0.6.0)
- Controller on 3.3.5a character creation through Controller Menus: rows for gender, race, class, looks and name, X for a new character on character select (0.6.0)
- On the 3.3.5a login screen, B or a tap outside the boxes stops typing, and B with no box in use quits (0.8.2)
- Several saved private servers (name, realmlist, account name, own sealed password), switched with L1 and R1 on the login screen through Controller Menus (0.7.0)
- Controller Menus works without ConsolePortLK: the app turns on its controller profile when either is installed, and the add-on was renamed from AndroWhere Login (0.8.5)
- Hold Select to open or close the Android keyboard in the 3.3.5a client while Controller Menus is installed; a short press still goes to the game (0.8.8)
- Switches for the patch archives in the player's 3.3.5a client, such as HD packs, named from the pack's own labels (0.6.0)
- WotLK HD pack patches in the Client tab say what each changes and follow the pack's rules: undead without bones turns on HD characters, dungeon minimaps turn on dungeon maps, and only one character creation style is on (0.8.6)

## Sign-in and security

- Saved Battle.net login, AES-256-GCM in the Android Keystore, handed to the game through a short-lived `login.txt` (0.2.0)
- `login.txt` wiped 20 s after the game reads it, with Hotfix.log and 3-minute backstops (0.2.0)
- App data excluded from cloud backup and device transfer; login dialog blocks screenshots (0.2.0)
- Saving the login works on Android 15 devices without a screen lock (0.2.1)

## App

- Dashboard home with Play, Addons and Game files tiles, controller-first (0.2.0)
- Battle.net login managed in Settings, shared by every Blizzard profile; no Account tile (0.8.0)
- Text boxes open the keyboard only when A is pressed or the box is tapped, never when the D-pad passes over them (0.8.1)
- 3.3.5a Server and Client as two home tiles (0.8.2 two-pane screen, split in 0.8.3): servers, password and login screen options on Server, folder, graphics and logs on Client; X adds, Y removes, a hint line instead of long text
- Settings in two panes (App, Battle.net, Addon sources, About) with a hint line (0.8.3)
- Quieter top bar: no Experimental label, no 3.3.5a client version; the Play card shows only the server (0.8.1)
- First-run flow: welcome and device check, find game, install, sign in (0.2.0)
- Art-led look in WoW Forever's colours, applied to upstream screens too (0.2.0)
- In-app app updater from GitHub releases (base)
- Signed release builds published by tag (0.2.0)
- App updates and the Source code link come from this fork, signed with its own key (0.4.1)
- Named WoW AndroWhere in the app, launcher label (all languages), README and release downloads (0.5.0)
- Pull requests run the launcher unit tests and a build (0.4.1)
