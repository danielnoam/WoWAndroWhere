# Changelog

Releases up to 0.4.0 are upstream's (YoruDev-Ryland/WoWForeverywhere), which this fork started from.

## Unreleased

- **Your own server: setup no longer crashes at "Creating your account".** The app closed when it stopped the database after making your account. Setup now finishes, and the server also stops cleanly when the game closes. On this device has a new **Share log** button that sends the server's last messages, so problems can be reported.
- **Settings list works by touch.** Tapping App, Battle.net, Addon sources or About on the left of Settings now opens that section. Before, only the controller could move through the list.

## 0.9.0

- **Vanilla 1.12.1 and Burning Crusade 2.4.3 private servers, experimental.** Two new game versions when you make a profile, next to 3.3.5a: Private server (1.12.1), which includes Turtle WoW, and Private server (2.4.3). Choose your own client folder and add your servers as you do for 3.3.5a. The app sets the realm, the resolution and the renderer, and warns you if the folder holds a different version. Addons has a small starter catalog for each (pfUI, pfQuest, ShaguDPS and more), and the Client tab has the switches for your client's patch files. Controller Menus and the Level Up Banner are 3.3.5a only for now, so you type your account and password in the game. The controller layout from 3.3.5a is always on: the sticks move you and aim the mouse, the stick buttons click, and the other buttons press F1 to F12, which you can bind in the game's Key Bindings.
- **Cataclysm 4.3.4 and Mists of Pandaria 5.4.8 private servers, experimental.** Two more versions in New profile, set up the same way. The app runs their 32-bit Wow.exe, since only 32-bit games can run through the emulator. These clients are much heavier than 3.3.5a, so expect a lower frame rate. Turn on Faster emulation and Use lighter graphics in Client. There is no addon catalog for them yet, but WoWInterface and GitHub links work.
- **Your own server (3.3.5a), experimental.** The Server screen has a new section, Your own server. **On this device** sets up a private server that runs on your handheld, just for you: no other players and no internet once it is set up. Setup downloads about 0.7 GB, or 1.4 GB with mob pathing (on by default; without it monsters sometimes walk through walls), and uses 2.6 to 4.8 GB. It keeps going if you leave the app. The server then shows up in your server list as "Solo, this device". Press Play and the app starts it, which takes about a minute, logs you in with an account it made for you, and stops it when the game closes. GM commands such as .tele and .levelup are on for your account and can be turned off. **On a PC** connects to a server you run on your computer, such as Dad's MMO Lab's offline server: enter the PC's address, check the connection, and add it to your servers.

## 0.8.8

- **Keyboard on a button (3.3.5a).** With Controller Menus installed, hold Select for half a second to open the Android keyboard, and again to close it. It works on the login screen, the email code box, character names and in game, so you no longer need Back, then Keyboard. A quick press of Select still reaches the game as before. The login and character creation hints show it.

## 0.8.7

- **Raid frames and loss of control alerts for 3.3.5a.** Two extras from the WotLK HD pack are now in the 3.3.5a addon list for any client: Compact Raid Frames, the party and raid frames from later expansions, and Loss of Control Alerts, which shows what is stopping you and for how long when you are stunned, feared or silenced. If your HD pack already has its patch 5 on, you have these already.
- **Level Up Banner (3.3.5a).** A new AndroWhere add-on in **Addons** → **Client**: when you level up, "Level N" shows across the top of the screen like in later expansions, with a line when you get a talent point. Type /levelbanner in game to see it without levelling.

## 0.8.6

- **HD pack patches explained.** With the WotLK HD client, each patch in **Addons** → **Client** now says what it changes, and the switches follow the pack's own rules. Turning on undead without bones also turns on HD characters, dungeon minimaps turn on dungeon maps, and turning off the one a patch needs turns that patch off too. Only one of the two character creation styles can be on at a time.

## 0.8.5

- **Controller Menus (3.3.5a), formerly AndroWhere Login.** The add-on that lets the controller work on the login, realm and character screens has a new name and no longer needs ConsolePortLK. With only Controller Menus installed, the app now turns on its controller layout too. Without ConsolePortLK, that layout stays on in the world: the sticks move you and aim the mouse, and the face buttons press F1 to F4. With ConsolePortLK installed nothing changes.
- **Our own addon catalog.** The GitHub addon list now comes from WoW AndroWhere's own nightly build instead of upstream's. It still lists every addon upstream does, and if ours can't be reached the app falls back to upstream's.

## 0.8.4

- **Faster emulation for private servers.** **Client** → Graphics has a new **Faster emulation** switch. It turns off some of Box64's safety checks so the 3.3.5a client runs faster. It's off by default; if an area or addon crashes or looks wrong with it on, turn it off.
- **Newer Box64 for private servers.** The 3.3.5a client now runs on Box64 0.4.4 (was 0.4.2), which starts and runs games a bit faster.

## 0.8.3

- **Server and Client tiles.** On a 3.3.5a profile the home screen's Client and server tile is now two tiles, **Server** and **Client**, with Addons below them. **Server** has your servers, the selected server's password (with the saved dots, to change or forget it) and the login screen options. A on a server selects it and opens its name, realmlist and account name; X adds a server and Y removes one. **Client** has the client folder, graphics and logs.
- **Settings in two panes.** Settings now has App, Battle.net, Addon sources and About on the left, like the old Client and server, with one hint line at the bottom instead of paragraphs.

## 0.8.2

- **Client and server, reworked.** The long page is now a short list on the left (Server, Login, Graphics, Client) with that part's options on the right. The D-pad moves, right goes into the options and B goes back to the list. **Server** lists your servers: A selects one and opens it to edit its name, realmlist, account name and password, X adds a server and Y removes one. **Login** holds Fill in password and Log in automatically, **Graphics** the renderer, lighter graphics and the performance overlay, and **Client** the folder and logs. The explanations moved to one line at the bottom that describes whatever is highlighted.
- **Login screen (3.3.5a).** With AndroWhere Login, B in the account or password box now stops typing so the pad works again, and so does a tap anywhere outside the boxes, as on character creation. With no box in use, B quits the game.
- **Fixed:** the Play card on a 3.3.5a profile said there was no saved login. That line is about the Battle.net login and now only shows for Blizzard versions.

## 0.8.1

- **The keyboard waits for A.** Moving onto a text box with the D-pad, such as a server's Name in Client and server, now only highlights it. Press A, or tap it, to type and open the keyboard. Done or B closes it. This works the same in every text box in the launcher, including the addon search and the Battle.net login.
- **A saved password shows.** Client and server now shows **Saved password ••••••••** once the server's password is saved, and **Password saved** right after you save it. The box below it is for changing it.
- **Simpler profile list.** The current profile is no longer ringed, only marked Current, and the Edit buttons are gone. Press Y on a profile to edit it. The list and the profile button at the top left are wider, so longer names fit.
- **Quieter home screen.** The Experimental label and the 3.3.5a client version are gone from the top bar. The Play card on 3.3.5a shows just the server's realmlist, without the password line.

## 0.8.0

- **Profiles.** A profile is one game setup: a name, a game version and its folder. The current profile shows at the top left of the home screen. Press it to see every profile, switch to another, or press Y or Edit to rename or delete one. L1 and R1 on the home screen switch to the previous or next profile. Your existing setups become profiles on the first start, named after their version, with nothing to set up again.
- **New profile.** Choose **New profile** in the profile list, pick the game version and give it a name. A Blizzard version then offers Download or a copy already on the device, and Private server (3.3.5a) opens Client and server to choose the client folder and add servers. Settings → Game version is gone: profiles replace it.
- **Profiles can share a folder.** Two profiles can use the same game folder, for example one 3.3.5a client for two servers. Each keeps its own addons, server list, passwords and, on 3.3.5a, client patches such as AndroWhere Login. When you press Play, the app turns off the other profile's addons in the game's own addon list, so nothing is moved or copied. The files themselves are shared, so updating an addon updates it for both.
- **Battle.net login in Settings.** The Account tile is gone from the home screen. The saved Battle.net login is now under **Settings**, and every Blizzard profile uses it.

## 0.7.0

- **Several private servers (3.3.5a).** **Client and server** now keeps a list of servers, each with a name, realmlist and optional account name, and **Add server** adds another. Play starts on the selected one, and each server keeps its own password. With AndroWhere Login installed and more than one server saved, the login screen shows the server's name at the top, and L1 and R1 switch to the previous or next one, account name and password included.
- **Password moves into Client and server.** The Server login screen and its home tile are gone. The password, Fill in password and Log in automatically are now in **Client and server**, under the servers, and show once AndroWhere Login is installed. Until then that spot offers Install. Before, the boxes could be ticked with nothing happening on the login screen.
- **Settings in the top bar.** The home screen's Settings tile is now a Settings button at the right of the top bar. It turns bronze and says Update when a new app version is out.
- **Type password is gone.** The Type password button on the login screen and the menu item are removed. Fill in password and Log in automatically replace them.
- **No Enable Mouse button over the game.** The WoW versions no longer show the Enable Mouse button at the top of the screen.

## 0.6.0

- **Client tab in Addons (3.3.5a).** Addons now has a **Client** tab for add-ons that change the game client itself. Its **AndroWhere patches** section lists the patches made for this app, each with Install and Remove. Once ConsolePortLK is installed, the Installed list also offers any AndroWhere patch you don't have yet.
- **AndroWhere Login.** Install it from the Client tab to use the controller on the login, realm and character screens. On character select, the D-pad picks a character, A enters the world, B goes back and Y opens the realm list. In the realm list, the D-pad moves, L1 and R1 switch tabs, A accepts and B cancels. Each screen shows its buttons at the top. On the login screen, press Back and choose Type password, which logs you in, or turn on Log in with A. It needs ConsolePortLK installed, and it changes 5 bytes of Wow.exe so the client accepts it. The original is kept and put back when you remove it. Some servers may not allow a changed Wow.exe.
- **Log in with A (3.3.5a).** With AndroWhere Login on and your server password saved, tick **Fill in password** under the password in Server login. The login screen then fills in your password and A logs you in. Tick **Log in automatically** too and it presses Log in for you once each time the game starts. It's off by default because, to do this, the app puts the password in the client folder when the game starts and removes it about 10 seconds after the game reads it, or after 3 minutes at most.
- **Create characters with the controller (3.3.5a).** With AndroWhere Login installed, X on character select starts a new character. On the creation screen, up and down pick gender, race, class, each look option or the name, and left and right change it. X picks a random look, L1 and R1 turn the character, and A on the name row opens the name box. Type the name, then press Enter to create.
- **Turn your client's patches on and off.** If your 3.3.5a client has extra patch files, such as an HD pack, the Client tab lists them by name and size so you can turn each one off or on again. This helps when a heavy patch slows the game down.

## 0.5.1

- **CurseForge uses your own key or proxy.** The app no longer sends CurseForge searches through the WoW Foreverywhere project's server. To use CurseForge, open Settings → CurseForge and enter your own CurseForge API key, which is stored encrypted on the device and only sent to CurseForge, or the address of a proxy you run. Without either, CurseForge is off and Addons says so. Wago, WoWInterface and the GitHub catalog work as before.

## 0.5.0

- **The app is now called WoW AndroWhere.** The new name shows on the home screen, in the app drawer and in Settings, in every language. The app ID is unchanged, so this installs as a normal update and keeps your settings, saved login, game files and addons. The release download is now **WoW-AndroWhere.apk**.
- **Private servers (3.3.5a).** Settings → Game version → Private server (3.3.5a) runs your own Wrath of the Lich King 3.3.5a client. Choose its folder and enter your server's realmlist in **Client and server**, then press Play. The home screen, Play and Addons work for it like for the other versions. It's experimental.
- **3.3.5a addons.** Addons has its own list of over 30 popular 3.3.5a addons, such as Questie, ElvUI, WeakAuras, Deadly Boss Mods (Warmane), Details! and Bagnon, checked every night and updated when their authors change them. It also lists WoWInterface addons made for 3.3, and **Install from link** takes a GitHub repository even when it has no releases.
- **Server password.** Save your private server password under **Server login**. On the game's login screen, tap the **Type password** button in the top right corner, or press Back and choose **Type password** from the menu. It's stored encrypted on the device and never goes into the game folder.
- **Wine logs for private servers.** Turn on **Save a log** in **Client and server** to save a log of the next launches; after the game closes, the app asks whether to share it. Logs are off by default because writing them slows the game down. Nothing is sent unless you choose where to share it.
- **Performance overlay for private servers.** **Client and server** → Performance shows frame rate, frame times and GPU load in the game (DXVK renderer). **Use lighter graphics** lowers view distance, ground clutter, particles, weather, shadows and full-screen glow, which helps when the processor is the limit.
- **Controller support on 3.3.5a.** The 3.3.5a client can't use a controller on its own. Addons now offers **Install ConsolePortLK**, and while it's installed the app maps your controller to the keys the addon expects. The app also sets the addon up for you, Xbox layout included, so there's no calibration to do.
- **Immersion with a controller on 3.3.5a.** With ConsolePortLK installed, Immersion's quest and gossip windows now respond to the controller: A accepts, continues or picks the highlighted option, B closes, X repeats the text, D-pad up and down move between options, and left and right pick a quest reward. Before, the buttons kept jumping and attacking while you talked to an NPC.

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
