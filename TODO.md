# TODO

Only work that's left. Shipped items move to DONE.md, rejected ones to DROPPED.md.

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
