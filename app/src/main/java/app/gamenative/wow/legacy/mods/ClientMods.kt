package app.gamenative.wow.legacy.mods

import app.gamenative.wow.legacy.LegacyClient
import java.io.File

/** A patch MPQ found in the player's client, with its locale twin when it has one. */
data class ClientPatch(val key: String, val name: String, val files: List<File>, val size: Long, val enabled: Boolean)

/** A server as the login screen sees it. */
data class GlueServer(val name: String, val realmlist: String, val account: String)

/**
 * What the app hands the login screen at launch: its servers, which one is selected, and, with Fill in password,
 * each server's password (null where none is saved) for one launch.
 */
data class LoginData(val servers: List<GlueServer>, val selected: Int, val passwords: List<String?>?, val autoLogin: Boolean)

/** A patch we make for the 3.3.5a client, installed and removed from the Addons screen's Client tab. */
enum class EverywherePatch(val title: String, val summary: String) {
    LOGIN(
        "Controller Menus",
        "Controller support on the login, realm, character select and character creation screens, with button hints at the top. " +
            "A logs you in when Fill in password is on in Server. Hold Select to open or close the keyboard, in game too. " +
            "Works with or without ConsolePortLK. " +
            "Installing it changes 5 bytes of Wow.exe so the client accepts it, and keeps the original to put back when you remove it.",
    ),
    LEVEL_UP(
        "Level Up Banner",
        "Shows the level you reached across the top of the screen, like later expansions, with a line when you get a talent point. " +
            "A plain add-on, so it leaves the client alone. Type /levelbanner in game to see it.",
    ),
}

/**
 * Add-ons that change the 3.3.5a client itself rather than its AddOns folder: our own login screen add-on,
 * and switches for the patch MPQs (HD packs and the like) already in the player's client.
 */
object ClientMods {
    const val DISABLED = ".disabled"

    // Internal names from when the add-on was called AndroWhere Login. The player never sees them.
    private const val LOGIN_FILE = "AndroWhereLogin.lua"
    private const val PASSWORD_FILE = "AndroWhereLoginData.lua"
    private const val SERVERS_FILE = "AndroWhereServers.lua"
    private const val GLUE_DIR = "Interface\\GlueXML\\"
    private const val LOGIN_RESOURCE = "/everywhere/glue/$LOGIN_FILE"
    private const val GLUE_TOC = "Interface\\GlueXML\\GlueXML.toc"
    private const val TOC_LAST = "## This Always Runs Last"

    /** Our archive's letter. Locale patches with letters load above Blizzard's numbered ones and the HD packs' early letters. */
    private const val OUR_SUFFIX = "Z"

    private val PATCH = Regex("patch(?:-([a-z]{2}[A-Z]{2}))?(?:-([0-9A-Za-z]))?\\.mpq", RegexOption.IGNORE_CASE)
    private val LABEL_LINE = Regex("patch-([0-9a-z])\\.mpq\\s*-\\s*(.+)", RegexOption.IGNORE_CASE)
    private val PACK_LABEL = Regex("\"([0-9a-z])\"\\s*:\\s*\\{\\s*\"name\"\\s*:\\s*\"(?:\\(\\d+\\))?([^\"]+)\"", RegexOption.IGNORE_CASE)

    private fun locale(root: File) = LegacyClient.locales(root).firstOrNull() ?: "enUS"

    internal fun archive(root: File) = ourArchive(root)

    private fun ourArchive(root: File): File {
        val locale = locale(root)
        return File(root, "Data/$locale/patch-$locale-$OUR_SUFFIX.MPQ")
    }

    private fun exe(root: File) = LegacyClient.exe(root)

    fun loginEnabled(root: File) = ourArchive(root).isFile

    fun installed(root: File, patch: EverywherePatch) = when (patch) {
        EverywherePatch.LOGIN -> loginEnabled(root)
        EverywherePatch.LEVEL_UP -> LevelUpBanner.installed(root)
    }

    fun setInstalled(root: File, patch: EverywherePatch, installed: Boolean) = when (patch) {
        EverywherePatch.LOGIN -> if (installed) enableLogin(root) else disableLogin(root)
        EverywherePatch.LEVEL_UP -> if (installed) LevelUpBanner.install(root) else LevelUpBanner.remove(root)
    }

    /** Writes our login add-on and patches the exe so the client accepts it. */
    fun enableLogin(root: File, data: LoginData? = null) {
        WowExePatch.apply(exe(root))
        writeLogin(root, data?.let { serversLua(it) } ?: existing(root, SERVERS_FILE), data?.let { passwordsLua(it) })
    }

    fun disableLogin(root: File) {
        ourArchive(root).delete()
        WowExePatch.restore(exe(root))
    }

    /**
     * Rebuilds the add-on at launch so it follows the client's current login file list, e.g. after an HD patch changed.
     * With [data], the login screen gets the app's servers and, when it carries them, their passwords;
     * [wipePassword] takes the passwords out again once the client has the archive open. Without it the server list stays.
     */
    fun refresh(root: File, data: LoginData? = null) {
        if (loginEnabled(root)) enableLogin(root, data)
    }

    fun hasPassword(root: File) = ourArchive(root).isFile && existing(root, PASSWORD_FILE) != null

    /** Rewrites the add-on without the passwords, keeping the server list. A no-op when it holds none. */
    fun wipePassword(root: File) {
        if (hasPassword(root)) writeLogin(root, existing(root, SERVERS_FILE), null)
    }

    private fun existing(root: File, file: String) = Mpq.read(ourArchive(root), GLUE_DIR + file)

    /**
     * Writes a new archive and renames it over ours. The running client keeps reading the archive it opened,
     * so replacing it never corrupts the game, and the old copy, passwords included, is gone from storage.
     */
    @Synchronized
    private fun writeLogin(root: File, servers: ByteArray?, passwords: ByteArray?) {
        val toc = effectiveGlueToc(root) ?: error("Couldn't find the client's login screen files.")
        val lua = checkNotNull(javaClass.getResourceAsStream(LOGIN_RESOURCE)) { "Missing $LOGIN_FILE" }.use { it.readBytes() }
        val archive = ourArchive(root)
        archive.parentFile?.mkdirs()
        val files = mutableMapOf(
            GLUE_TOC to withLoginFile(toc, servers != null, passwords != null).toByteArray(Charsets.ISO_8859_1),
            GLUE_DIR + LOGIN_FILE to lua,
        )
        if (servers != null) files[GLUE_DIR + SERVERS_FILE] = servers
        if (passwords != null) files[GLUE_DIR + PASSWORD_FILE] = passwords
        val temp = File(archive.parentFile, archive.name + ".tmp")
        Mpq.write(temp, files)
        if (!temp.renameTo(archive)) {
            temp.delete()
            error("Couldn't replace ${archive.name}.")
        }
    }

    /** Every byte as a decimal escape, so no name or password can break out of the Lua string. */
    internal fun luaString(text: String) =
        text.toByteArray(Charsets.UTF_8).joinToString("", "\"", "\"") { "\\" + (it.toInt() and 0xFF) }

    internal fun serversLua(data: LoginData): ByteArray = buildString {
        append("AndroWhereServers = {\r\n")
        data.servers.forEach {
            append("    { name = ${luaString(it.name)}, realmlist = ${luaString(it.realmlist)}, account = ${luaString(it.account)} },\r\n")
        }
        append("}\r\nAndroWhereServerIndex = ${data.selected + 1}\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    /** Null when no password goes to the login screen. */
    internal fun passwordsLua(data: LoginData): ByteArray? {
        val passwords = data.passwords?.takeIf { list -> list.any { it != null } } ?: return null
        return buildString {
            append("AndroWhereLoginPasswords = {\r\n")
            passwords.forEachIndexed { index, password -> if (password != null) append("    [${index + 1}] = ${luaString(password)},\r\n") }
            append("}\r\nAndroWhereLoginAuto = ${data.autoLogin}\r\n")
        }.toByteArray(Charsets.ISO_8859_1)
    }

    /** Adds our files before the line Blizzard keeps last, so they run after every stock login file. */
    internal fun withLoginFile(toc: String, withServers: Boolean = false, withPassword: Boolean = false): String {
        val ours = setOf(LOGIN_FILE, SERVERS_FILE, PASSWORD_FILE)
        val lines = toc.replace("\r", "").split("\n").filterNot { it.trim() in ours }.toMutableList()
        val last = lines.indexOfFirst { it.startsWith(TOC_LAST) }.takeIf { it >= 0 } ?: lines.size
        lines.addAll(last, listOfNotNull(SERVERS_FILE.takeIf { withServers }, PASSWORD_FILE.takeIf { withPassword }, LOGIN_FILE))
        return lines.joinToString("\r\n")
    }

    /** The login file list the client would load without us: the highest priority archive that has one. */
    private fun effectiveGlueToc(root: File): String? {
        val locale = locale(root)
        val ours = ourArchive(root).name
        val candidates = (File(root, "Data/$locale").listFiles().orEmpty().filter { it.isFile && it.name != ours && PATCH.matches(it.name) }
            .sortedByDescending { priority(it.name) }) +
            listOf(File(root, "Data/$locale/locale-$locale.MPQ"))
        return candidates.firstNotNullOfOrNull { Mpq.read(it, GLUE_TOC) }?.toString(Charsets.ISO_8859_1)
    }

    /** Lettered patches load over numbered ones, which load over the plain patch archive. */
    internal fun priority(name: String): Int {
        val suffix = PATCH.matchEntire(name)?.groupValues?.get(2).orEmpty().uppercase()
        return when {
            suffix.isEmpty() -> 0
            suffix[0].isDigit() -> 1 + suffix[0].digitToInt()
            else -> 100 + (suffix[0] - 'A')
        }
    }

    /** Patch archives the player added, grouped by letter, without Blizzard's own or ours. */
    fun patches(root: File): List<ClientPatch> {
        val locale = locale(root)
        val labels = labels(root)
        val files = listOf(File(root, "Data"), File(root, "Data/$locale")).flatMap { dir -> dir.listFiles().orEmpty().filter { it.isFile } }
        return files.mapNotNull { file ->
            val enabled = !file.name.endsWith(DISABLED, ignoreCase = true)
            val match = PATCH.matchEntire(file.name.removeSuffix(DISABLED).removeSuffix(DISABLED.uppercase())) ?: return@mapNotNull null
            val key = match.groupValues[2].uppercase()
            if (key.isEmpty() || key == "2" || key == "3") return@mapNotNull null
            if (file.name.equals(ourArchive(root).name, ignoreCase = true)) return@mapNotNull null
            key to (file to enabled)
        }.groupBy({ it.first }, { it.second }).map { (key, entries) ->
            ClientPatch(
                key = key,
                name = labels[key] ?: "Patch $key",
                files = entries.map { it.first },
                size = entries.sumOf { it.first.length() },
                enabled = entries.all { it.second },
            )
        }.sortedBy { it.key }
    }

    /** Turns a patch on or off the way the HD packs' own menus do, by adding or removing `.disabled`. */
    fun setPatchEnabled(patch: ClientPatch, enabled: Boolean) {
        patch.files.forEach { file ->
            val off = file.name.endsWith(DISABLED, ignoreCase = true)
            val target = when {
                enabled && off -> File(file.parentFile, file.name.dropLast(DISABLED.length))
                !enabled && !off -> File(file.parentFile, file.name + DISABLED)
                else -> return@forEach
            }
            check(file.renameTo(target)) { "Couldn't rename ${file.name}." }
        }
    }

    /** Names from a pack's own label file or patch menu config, by patch letter. */
    internal fun labels(root: File): Map<String, String> {
        val listed = File(root, "Data/Patch_Lables.txt").takeIf { it.isFile }?.readLines().orEmpty()
            .mapNotNull { LABEL_LINE.matchEntire(it.trim())?.destructured?.let { (key, name) -> key.uppercase() to name.trim() } }
        val pack = File(root, "patchmenu/.config/config_en.json").takeIf { it.isFile }?.readText()
            ?.let { text -> PACK_LABEL.findAll(text).map { it.groupValues[1].uppercase() to it.groupValues[2].trim() }.toList() }
            .orEmpty()
        return (pack + listed).toMap()
    }
}
