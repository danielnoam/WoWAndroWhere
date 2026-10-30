package app.gamenative.wow.legacy

import app.gamenative.wow.product.GameVersion

/**
 * What differs between the private server clients. All are 32-bit D3D9 clients with a realmlist (4.3.4 and 5.4.8
 * also ship a 64-bit exe, which isn't used: the bundled emulator only runs 32-bit x86),
 * so they launch the same way; only the client add-ons the app makes are tied to 3.3.5a's Wow.exe and login screen.
 */
enum class LegacyGame(val version: GameVersion, val label: String, val major: Int, val catalogUrl: String?) {
    VANILLA(GameVersion.PRIVATE_112, "1.12.1", 1, LegacyClient.catalogUrl("112")),
    TBC(GameVersion.PRIVATE_243, "2.4.3", 2, LegacyClient.catalogUrl("243")),
    WOTLK(GameVersion.PRIVATE_335, "3.3.5a", 3, LegacyClient.catalogUrl("335")),
    CATA(GameVersion.PRIVATE_434, "4.3.4", 4, null),
    MOP(GameVersion.PRIVATE_548, "5.4.8", 5, null),
    ;

    /** Controller Menus and the Level Up Banner: their Wow.exe changes and Lua are for 3.3.5a (12340) only. */
    val hasEverywherePatches get() = this == WOTLK

    /** 1.12 keeps `realmlist.wtf` next to Wow.exe; later clients keep one per locale under `Data`. */
    val rootRealmlist get() = this == VANILLA

    /** Whether a client folder holds this game: an unreadable build gets the benefit of the doubt. */
    fun accepts(build: ClientBuild?) = build == null || build.major == major

    companion object {
        fun of(version: GameVersion): LegacyGame = entries.firstOrNull { it.version == version } ?: WOTLK
    }
}
