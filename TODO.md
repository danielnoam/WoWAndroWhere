# TODO

Only work that's left. Shipped items move to DONE.md, rejected ones to DROPPED.md.

## Fork housekeeping

1. **Other upstream links.** The GitHub addon catalog (`GitHubProvider.CATALOG_URL`) and the README (badges, download links, issues) still point at upstream. Decide per link: the catalog can stay on upstream's nightly build (no cost, but it's their branch) or the fork's `addon-catalog` workflow can publish its own; the README should point at the fork's releases.

## Private servers (3.3.5a)

Daniel picked 3.3.5a (WotLK, build 12340). Modern-client servers are out (see DROPPED.md).

1. **Launch spike** (in progress). A branch that launches a client folder Daniel provides through the x86 path (FEX/wowbox64 WoW64) instead of the ARM64EC path. Daniel checks on his device whether it reaches the login screen. Open risk: 3.3.5a is 32-bit x86 D3D9 and the bundled DXVK is aarch64 only, so rendering is unverified. If it doesn't draw, stop here.
2. **Private server version** (only if the spike works). A "Private server" entry under Settings → Game version: pick a client folder, enter a realmlist, build detected from `Wow.exe`. The app writes `realmlist.wtf` and the D3D9 config and launches with the x86 container. No CDN install, game updater, Battle.net sign-in or CurseForge browser for it; addons install from a link or zip.

## Controller support on 3.3.5a: ConsolePortLK

Depends on the 3.3.5a spike reaching the login screen. ConsolePortLK only targets 3.3.5a (`## Interface: 30300`); every official version the app runs already has native gamepad support.

1. **One-tap install.** With the 3.3.5a client selected, Addons shows a "Controller support: install ConsolePortLK" card that pulls the latest GitHub release of `leoaviana/ConsolePortLK` into `Interface/AddOns` and tracks updates like any other addon. Downloaded at install time, not bundled (Artistic License 2.0).
2. **Controller-to-keyboard profile.** 3.3.5a can't read a gamepad, so ship a "ConsolePortLK" input profile (buttons and triggers to keys, right stick to mouse) and use it automatically for that client. Without it the addon does nothing.

## Proposed by Claude, not agreed yet

- **Own the CurseForge proxy.** Release builds without a key send every CurseForge request to `cf.yorudev.com`, upstream's server. Either run `server/curseforge-proxy` with Daniel's own key and build with `-PCURSEFORGE_PROXY=...`, or accept upstream's server and say so in NOTES.md.
