package app.gamenative.wow.legacy

import android.content.Context
import app.gamenative.wow.LoginVault
import app.gamenative.wow.profile.Profiles
import java.io.File
import java.io.RandomAccessFile

/** The version of a pre-CASC 32-bit client (1.12, 2.4.3, 3.3.5a) for private servers. Players supply the folder. */
data class ClientBuild(val major: Int, val minor: Int, val patch: Int, val build: Int) {
    override fun toString() = "$major.$minor.$patch.$build"
}

enum class LegacyRenderer(val label: String, val dxwrapper: String, val gxApi: String, val execArgs: String) {
    DXVK("DXVK (D3D9)", "dxvk", "d3d9", ""),
    OPENGL("OpenGL", "wined3d", "opengl", "-opengl"),
}

data class LegacySettings(
    val root: String = "",
    val realmlist: String = "",
    val renderer: LegacyRenderer = LegacyRenderer.DXVK,
    /** Off by default: Wine's warn and fixme output is written line by line and costs frame rate. */
    val saveLog: Boolean = false,
    val perfOverlay: Boolean = false,
    /** Off by default: [LegacyClient.FASTER_EMULATION_ENV] trades Box64 safety checks for speed and can crash some code. */
    val fasterEmulation: Boolean = false,
    /** Off by default: Controller Menus fills in the saved password, which briefly puts it in the client folder. */
    val fastLogin: Boolean = false,
    /** With [fastLogin], the login screen also presses Log in once per game start. */
    val autoLogin: Boolean = false,
    /** The selected one of [LegacyServers]; [realmlist] is its realmlist. */
    val serverId: String = LegacyServers.MAIN,
)

object LegacyClient {
    const val EXE = "Wow.exe"
    const val LOG = "AndroWhere-wine.log"

    /** The bundled 32-bit emulator. FEX's WoW64 DLL isn't bundled, so only Box64 (wowbox64) can run x86 code. */
    const val EMULATOR = "Box64"

    /** The wowbox64 build in `assets/wowbox64`, made by the wowbox64 workflow from the official Box64 tag. */
    const val WOWBOX64_VERSION = "0.4.4"
    private const val PREFS = "wow_legacy"
    private const val DEBUG_CLEARED = "leftover_debug_cleared"
    private const val LOG_WAITING = "log_waiting"

    /** Built by the "Private server addon catalogs" workflow from the lists in `catalog/`. */
    fun catalogUrl(key: String) = "https://raw.githubusercontent.com/danielnoam/WoWEverywhere/addon-catalog-335/catalog-$key.json"

    private const val FIXED_FILE_INFO_SIGNATURE = 0xFEEF04BDL
    private val LOCALE = Regex("[a-z]{2}[A-Z]{2}")
    /** DXVK's own HUD: GPU load well under 100% while frame times are high means the CPU (Box64) is the limit. */
    const val PERF_OVERLAY_ENV = "DXVK_HUD=fps,frametimes,gpuload"

    /**
     * On top of Box64's Performance preset: no flag bookkeeping between blocks, longer forward jumps, deferred
     * memory-protection checks, and the faster return path Box64 0.4.4 made its default.
     */
    const val FASTER_EMULATION_ENV =
        "BOX64_DYNAREC_SAFEFLAGS=0 BOX64_DYNAREC_FORWARD=1024 BOX64_DYNAREC_DIRTY=1 BOX64_DYNAREC_CALLRET=2"

    /** The overlay is DXVK's, so it only shows with the DXVK renderer. */
    fun envVars(settings: LegacySettings): String =
        (if (settings.perfOverlay && settings.renderer == LegacyRenderer.DXVK) " $PERF_OVERLAY_ENV" else "") +
            (if (settings.fasterEmulation) " $FASTER_EMULATION_ENV" else "")

    private val REALMLIST_LINE = Regex("set\\s+realmlist\\s+(.*)", RegexOption.IGNORE_CASE)

    /** The active profile's settings. */
    fun load(context: Context): LegacySettings {
        val p = context.getSharedPreferences(Profiles.legacyPrefs, Context.MODE_PRIVATE)
        return LegacySettings(
            root = p.getString("root", "").orEmpty(),
            realmlist = p.getString("realmlist", "").orEmpty(),
            renderer = LegacyRenderer.entries.firstOrNull { it.name == p.getString("renderer", null) } ?: LegacyRenderer.DXVK,
            saveLog = p.getBoolean("save_log", false),
            perfOverlay = p.getBoolean("perf_overlay", false),
            fasterEmulation = p.getBoolean("faster_emulation", false),
            fastLogin = p.getBoolean("fast_login", false),
            autoLogin = p.getBoolean("auto_login", false),
            serverId = p.getString("server_id", null) ?: LegacyServers.MAIN,
        )
    }

    fun save(context: Context, settings: LegacySettings) {
        context.getSharedPreferences(Profiles.legacyPrefs, Context.MODE_PRIVATE).edit()
            .putString("root", settings.root)
            .putString("realmlist", settings.realmlist)
            .putString("renderer", settings.renderer.name)
            .putBoolean("save_log", settings.saveLog)
            .putBoolean("perf_overlay", settings.perfOverlay)
            .putBoolean("faster_emulation", settings.fasterEmulation)
            .putBoolean("fast_login", settings.fastLogin)
            .putBoolean("auto_login", settings.autoLogin)
            .putString("server_id", settings.serverId)
            .apply()
    }

    /**
     * Test builds 12 to 18 turned the global Wine debug setting on for legacy launches and could leave it on.
     * The app has no other switch for it, so turn it off once. The legacy container now logs on its own.
     */
    fun clearLeftoverDebug(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(DEBUG_CLEARED, false)) return false
        p.edit().putBoolean(DEBUG_CLEARED, true).remove("log_pending_previous_debug").remove("log_flag_migrated").apply()
        return true
    }

    /** Whether a legacy launch's log is waiting for the player to share or dismiss it. Survives the launcher being rebuilt. */
    fun isLogWaiting(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(LOG_WAITING, false)

    fun setLogWaiting(context: Context, waiting: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(LOG_WAITING, waiting).apply()
    }

    /**
     * The private server password, sealed like the Battle.net login (see [LoginVault]) in its own prefs.
     * With Fill in password, Controller Menus carries it to the login screen for one launch (see [app.gamenative.wow.legacy.mods.FastLogin]).
     */
    fun hasServerPassword(context: Context): Boolean = runCatching { serverPassword(context) != null }.getOrDefault(false)

    fun saveServerPassword(context: Context, password: String): Boolean =
        runCatching { LegacyServers.vault(context, load(context).serverId).save("", password) }.isSuccess

    fun forgetServerPassword(context: Context) {
        LegacyServers.vault(context, load(context).serverId).clear()
    }

    /** The selected server's saved password. */
    internal fun serverPassword(context: Context): String? = LegacyServers.password(context, load(context).serverId)

    /** The client's exe, named `WoW.exe` on 1.12, under whatever case it has. */
    fun exe(root: File): File = root.listFiles()?.firstOrNull { it.isFile && it.name.equals(EXE, ignoreCase = true) } ?: File(root, EXE)

    /** The folder holding [EXE] and `Data/`, searching [dir] and one level below it. */
    fun findRoot(dir: File): File? =
        sequenceOf(dir).plus(dir.listFiles()?.filter { it.isDirectory }.orEmpty())
            .firstOrNull { exe(it).isFile && File(it, "Data").isDirectory }

    fun detectBuild(exe: File): ClientBuild? = runCatching {
        RandomAccessFile(exe, "r").use { file ->
            val bytes = ByteArray(minOf(file.length(), MAX_EXE_BYTES).toInt())
            file.readFully(bytes)
            parseBuild(bytes)
        }
    }.getOrNull()

    /** Reads `VS_FIXEDFILEINFO.dwFileVersion` from the PE version resource. */
    internal fun parseBuild(bytes: ByteArray): ClientBuild? {
        for (i in bytes.size - 16 downTo 0) {
            if (u32(bytes, i) != FIXED_FILE_INFO_SIGNATURE) continue
            val ms = u32(bytes, i + 8)
            val ls = u32(bytes, i + 12)
            return ClientBuild((ms ushr 16).toInt(), (ms and 0xFFFF).toInt(), (ls ushr 16).toInt(), (ls and 0xFFFF).toInt())
        }
        return null
    }

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
            ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)

    fun locales(root: File): List<String> =
        File(root, "Data").listFiles()?.filter { it.isDirectory && LOCALE.matches(it.name) }?.map { it.name }?.sorted().orEmpty()

    /** The realm the client is already set to, from the first `realmlist.wtf` found. */
    fun readRealmlist(root: File): String? =
        (locales(root).map { File(root, "Data/$it/realmlist.wtf") } + File(root, "realmlist.wtf"))
            .firstOrNull { it.isFile }
            ?.readLines()
            ?.firstNotNullOfOrNull { REALMLIST_LINE.matchEntire(it.trim())?.groupValues?.get(1)?.trim('"', ' ') }
            ?.takeIf { it.isNotBlank() }

    /**
     * Sets the realm in every locale's `realmlist.wtf` (the one next to Wow.exe on 1.12), keeping its other lines,
     * and in `WTF/Config.wtf`.
     */
    fun applyConfig(root: File, settings: LegacySettings, screenSize: String, game: LegacyGame = LegacyGame.WOTLK) {
        val locales = locales(root)
        val realmlists = locales.map { File(root, "Data/$it/realmlist.wtf") } + listOfNotNull(File(root, "realmlist.wtf").takeIf { game.rootRealmlist })
        realmlists.forEach { file ->
            file.writeText(updateRealmlist(file.takeIf { it.isFile }?.readText(), settings.realmlist))
        }
        val config = File(root, "WTF/Config.wtf")
        val lines = buildConfigWtf(config.takeIf { it.isFile }?.readLines(), settings, screenSize, locales.firstOrNull() ?: "enUS", game)
        config.parentFile?.mkdirs()
        config.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    /**
     * Lighter 3.3.5a settings for when the processor is the limit: every draw call runs under Box64,
     * so these draw fewer objects and effects. Only written when the player asks.
     */
    internal val HANDHELD_GRAPHICS = linkedMapOf(
        "farclip" to "400",
        "groundEffectDensity" to "16",
        "groundEffectDist" to "70",
        "environmentDetail" to "0.5",
        "particleDensity" to "0.3",
        "weatherDensity" to "0",
        "extShadowQuality" to "0",
        "projectedTextures" to "0",
        "ffxGlow" to "0",
        "ffxDeath" to "0",
    )

    fun applyHandheldGraphics(root: File) {
        val config = File(root, "WTF/Config.wtf")
        val lines = withValues(config.takeIf { it.isFile }?.readLines(), HANDHELD_GRAPHICS)
        config.parentFile?.mkdirs()
        config.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    internal fun withValues(existing: List<String>?, values: Map<String, String>): List<String> =
        existing.orEmpty().filter { line -> values.keys.none { line.startsWith("SET $it ", ignoreCase = true) } } +
            values.map { "SET ${it.key} \"${it.value}\"" }

    internal fun updateRealmlist(existing: String?, realmlist: String): String {
        val line = "set realmlist ${realmlist.trim()}"
        val others = existing.orEmpty().lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() && !REALMLIST_LINE.matches(it.trim()) }
        return (listOf(line) + others).joinToString("\n", postfix = "\n")
    }

    /**
     * Realm, renderer and resolution are always enforced; the rest is only a first-boot default so in-game changes stick.
     * 1.12 may not know `d3d9`, so with DXVK its gxApi line is dropped and the client falls back to Direct3D, its default.
     */
    internal fun buildConfigWtf(
        existing: List<String>?,
        settings: LegacySettings,
        screenSize: String,
        locale: String,
        game: LegacyGame = LegacyGame.WOTLK,
    ): List<String> {
        val gxApi = settings.renderer.gxApi.takeUnless { game == LegacyGame.VANILLA && settings.renderer == LegacyRenderer.DXVK }
        val forced = linkedMapOf(
            "realmList" to settings.realmlist.trim(),
            "gxApi" to gxApi,
            "gxResolution" to screenSize,
        )
        val defaults = linkedMapOf(
            "locale" to locale,
            "gxWindow" to "1",
            "gxMaximize" to "1",
            "movie" to "0",
        )
        val lines = existing.orEmpty().filter { line -> forced.keys.none { line.startsWith("SET $it ", ignoreCase = true) } }
        val missing = defaults.filterKeys { key -> lines.none { it.startsWith("SET $key ", ignoreCase = true) } }
        return lines + (forced.filterValues { it != null } + missing).map { "SET ${it.key} \"${it.value}\"" }
    }

    private const val MAX_EXE_BYTES = 64L * 1024 * 1024

    /** Logs over this size keep their start (DLL loading) and their end (the crash) so they stay shareable. */
    const val LOG_HEAD = 1L shl 20
    const val LOG_TAIL = 3L shl 20

    fun trimLog(source: File, target: File) {
        val size = source.length()
        if (size <= LOG_HEAD + LOG_TAIL) {
            source.copyTo(target, overwrite = true)
            return
        }
        RandomAccessFile(source, "r").use { input ->
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 shl 10)
                fun copy(from: Long, length: Long) {
                    input.seek(from)
                    var left = length
                    while (left > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        left -= read
                    }
                }
                copy(0, LOG_HEAD)
                out.write("\n[... ${size - LOG_HEAD - LOG_TAIL} bytes cut ...]\n".toByteArray())
                copy(size - LOG_TAIL, LOG_TAIL)
            }
        }
    }
}
