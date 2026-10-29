package app.gamenative.wow.product

import android.content.Context

/**
 * The WoW version the whole app works with, chosen in Settings. WoW Forever is the default and the
 * app's purpose; the others are experimental. Each groups the [products] that count as that version.
 */
enum class GameVersion(val key: String, val displayName: String, val products: List<WowProduct>, val defaultFolder: String) {
    FOREVER("forever", "WoW Forever", listOf(WowProduct.FOREVER, WowProduct.BETA), "WoW Forever"),
    CLASSIC_ERA("classic_era", "Classic Era", listOf(WowProduct.CLASSIC_ERA), "World of Warcraft"),
    ANNIVERSARY("anniversary", "Burning Crusade Anniversary", listOf(WowProduct.ANNIVERSARY), "World of Warcraft"),
    MISTS("mists", "Mists of Pandaria Classic", listOf(WowProduct.MISTS), "World of Warcraft"),
    RETAIL("retail", "Retail", listOf(WowProduct.RETAIL), "World of Warcraft"),

    /** Clients the player brings, for private servers. No Blizzard product: see `wow/legacy`. */
    PRIVATE_112("private_112", "Private server (1.12.1)", emptyList(), ""),
    PRIVATE_243("private_243", "Private server (2.4.3)", emptyList(), ""),
    PRIVATE_335("private_335", "Private server (3.3.5a)", emptyList(), ""),
    PRIVATE_434("private_434", "Private server (4.3.4)", emptyList(), ""),
    PRIVATE_548("private_548", "Private server (5.4.8)", emptyList(), ""),
    ;

    val isExperimental get() = this != FOREVER
    val isPrivateServer get() = products.isEmpty()

    companion object {
        fun of(product: WowProduct): GameVersion = entries.first { product in it.products }

        /** The version saved under [key]; WoW Forever when there's none or it's unknown. */
        fun fromKey(key: String?): GameVersion = entries.firstOrNull { it.key == key } ?: FOREVER
    }
}

/** The selected [GameVersion], kept in memory for the process and saved in SharedPreferences. */
object GameVersions {
    private const val PREFS = "wow_settings"
    private const val KEY = "game_version"

    @Volatile
    var selected: GameVersion = GameVersion.FOREVER

    fun load(context: Context) {
        selected = GameVersion.fromKey(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun save(context: Context, version: GameVersion) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, version.key).apply()
        selected = version
    }
}
