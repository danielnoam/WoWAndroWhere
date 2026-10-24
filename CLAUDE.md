# WoW AndroWhere

Daniel's fork of [YoruDev-Ryland/WoWForeverywhere](https://github.com/YoruDev-Ryland/WoWForeverywhere), an Android launcher that installs and runs Blizzard's Windows ARM64 WoW client under Proton ARM64EC + DXVK + Turnip on Snapdragon handhelds. That project is itself a fork of [jaredgei/wow-forever-android](https://github.com/jaredgei/wow-forever-android) (GameNative/Winlator underneath).

- Design and plans: `docs/superpowers/specs`, `docs/superpowers/plans`, findings in `docs/superpowers/notes`.
- Launcher code: `app/src/main/java/app/gamenative/ui/screen/wow/` and `app/src/main/java/app/gamenative/wow/`.
- Releases: pushing a `vX.Y.Z` tag runs `.github/workflows/release.yml`, which builds the signed APK and publishes `.github/release-notes.md` as the release body. `versionName` must match the tag.
- No Android SDK or device in Claude's cloud container: builds, tests and on-device checks need CI or Daniel's phone.

## Working with Daniel

### Notes files

- **TODO.md** holds only work that's left, grouped into releases or sections, each item with a short plan of how it would be built.
- **DONE.md** lists what the app does, grouped by area, one line per feature with the version it came in. Every release adds a line for each feature it ships.
- **DROPPED.md** lists ideas decided against, each with the reason, so they can come back if the reason stops being true.
- **NOTES.md** says why things are the way they are, newest first.
- **CHANGELOG.md** describes every release in plain, user-facing words.
- When something ships, or Daniel says he doesn't want it, take it out of TODO.md in the same change: shipped work becomes a line in DONE.md and the changelog, a no becomes a line in DROPPED.md with his reason. Remove anything stale or already covered. Never leave done work in TODO.md.
- The project's shared folder (`/mnt/project-files/notes/`) keeps copies of TODO.md and DONE.md. After every change to them, copy the repo's versions over the copies.
- Before suggesting ideas, check DONE.md, TODO.md and DROPPED.md so nothing shipped, planned or turned down is suggested again.
- "Show the todo" means short: the next items numbered, then the later and parked groups in one line each.

### How the work is done

- Merge to main without asking once the checks are green.
- "Don't build yet" or "fixes only": commit locally and don't push until Daniel says build.
- "Add to TODO" means plan it, not build it. Small asks without that, build now.
- Several asks go out as one release, or a few grouped releases, not many small ones.
- For a new look, a reworked screen or a big change, show or explain it first and wait for Daniel's OK before building.
- Secure and private as far as it can go: least access (narrow tokens and scopes), nothing leaves the device unless Daniel turned it on, untrusted content (addon zips, downloaded clients, web text) stays sandboxed, and any trade-off is said plainly and written in NOTES.md.
- Android only. iOS, browsers and desktop computers are not targets, so don't add work, abstractions or tests for them.
- Speed matters: measure before and after anything that could make it slower (launch time, frame time, install throughput, screen load).
- After shipping, say in a line or two what to try on the phone, especially anything that couldn't be tested without it.
- Be direct. Push back when an idea or plan is weak, and say what was checked and what is a guess.

### Project rules

- Keep merging upstream possible: new code goes in new files or packages, edits to upstream files stay thin. Upstream is `YoruDev-Ryland/WoWForeverywhere`; its game-running side comes from `jaredgei/wow-forever-android`.
- Never ship Blizzard files or API keys in the APK or the repo. Keys come from `local.properties` or CI secrets.
- Visual direction is in `docs/superpowers/notes/2026-10-18-visual-direction.md`: no gradients, no blue/purple accents, no accent-bar cards, no emoji icons. Every screen works with a controller (D-pad and face buttons).
- User-facing text follows the README voice: plain, professional, no em dashes.
