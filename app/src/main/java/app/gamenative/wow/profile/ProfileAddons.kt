package app.gamenative.wow.profile

import android.content.Context
import app.gamenative.wow.addons.AddonDb
import app.gamenative.wow.addons.InstalledAddon
import app.gamenative.wow.legacy.mods.EverywherePatch
import app.gamenative.wow.legacy.mods.ClientMods
import java.io.File

/**
 * Keeps addons and 3.3.5a client patches apart for profiles that share one game folder. Every addon stays
 * on disk once; each profile claims the ones it installed, and an addon no profile claims belongs to all.
 * At launch the game's own AddOns.txt turns off the addons the profile doesn't see, so nothing is moved.
 * A folder used by one profile is left as it is.
 */
object ProfileAddons {
    private const val PREFS = "wow_profiles"
    private const val CLAIMS = "claims_"
    private const val OFF = "app_disabled_"
    private const val MODS = "mods_"
    private const val LOGIN = "login"
    private const val PATCH_OFF = "off:"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun claims(context: Context, id: String): Set<String> = prefs(context).getStringSet(CLAIMS + id, null).orEmpty()

    private fun setClaims(context: Context, id: String, ids: Set<String>) {
        prefs(context).edit().putStringSet(CLAIMS + id, ids).apply()
    }

    /** Which addon ids [profile] sees: all on a folder of its own, else its claims and the unclaimed ones. */
    fun visibility(context: Context, profile: Profile): (String) -> Boolean {
        val sharers = Profiles.sharers(profile)
        if (sharers.isEmpty()) return { true }
        val mine = claims(context, profile.id)
        val claimed = sharers.flatMap { claims(context, it.id) }.toSet() + mine
        return { id -> id in mine || id !in claimed }
    }

    fun claim(context: Context, profile: Profile, ids: Set<String>) {
        if (ids.isEmpty()) return
        setClaims(context, profile.id, claims(context, profile.id) + ids)
    }

    /**
     * Takes [addon] away from [profile]. True when another profile on the folder still uses it, so its
     * files must stay; false when the caller should delete it.
     */
    fun release(context: Context, profile: Profile, addon: InstalledAddon): Boolean {
        val sharers = Profiles.sharers(profile)
        val mine = claims(context, profile.id)
        setClaims(context, profile.id, mine - addon.id)
        if (sharers.isEmpty()) return false
        val claimedBy = sharers.filter { addon.id in claims(context, it.id) }
        if (claimedBy.isNotEmpty()) return true
        if (addon.id in mine) return false
        // Nobody claimed it, so everyone saw it: the others keep it by claiming it now.
        sharers.forEach { setClaims(context, it.id, claims(context, it.id) + addon.id) }
        return true
    }

    /**
     * Called when [profile] starts using a folder. If others already use it, they claim what is installed
     * now and [profile] starts with no addons and Controller Menus off. Each records its client patches.
     */
    fun joined(context: Context, profile: Profile, addonsDir: File, clientRoot: File?) {
        val sharers = Profiles.sharers(profile)
        if (sharers.isEmpty()) return
        val installed = runCatching { AddonDb.forAddonsDir(addonsDir).load().map { it.id }.toSet() }.getOrDefault(emptySet())
        val claimed = sharers.flatMap { claims(context, it.id) }.toSet()
        val unclaimed = installed - claimed
        sharers.forEach { setClaims(context, it.id, claims(context, it.id) + unclaimed) }
        setClaims(context, profile.id, emptySet())
        if (clientRoot != null) {
            val now = modsOnDisk(clientRoot)
            sharers.filter { prefs(context).getStringSet(MODS + it.id, null) == null }.forEach { saveMods(context, it.id, now) }
            saveMods(context, profile.id, now - LOGIN)
        }
    }

    /** Remembers the client patches [profile] has turned on or off, so a shared folder can switch to them. */
    fun recordMods(context: Context, profile: Profile, clientRoot: File) {
        saveMods(context, profile.id, modsOnDisk(clientRoot))
    }

    private fun saveMods(context: Context, id: String, mods: Set<String>) {
        prefs(context).edit().putStringSet(MODS + id, mods).apply()
    }

    private fun modsOnDisk(root: File): Set<String> =
        setOfNotNull(LOGIN.takeIf { ClientMods.loginEnabled(root) }) + ClientMods.patches(root).filterNot { it.enabled }.map { PATCH_OFF + it.key }

    /** Puts [profile]'s client patches in place on a shared folder. Nothing changes on a folder of its own. */
    fun applyMods(context: Context, profile: Profile, clientRoot: File) {
        if (Profiles.sharers(profile).isEmpty()) return
        val wanted = prefs(context).getStringSet(MODS + profile.id, null) ?: return
        val login = LOGIN in wanted
        if (ClientMods.loginEnabled(clientRoot) != login) ClientMods.setInstalled(clientRoot, EverywherePatch.LOGIN, login)
        ClientMods.patches(clientRoot).forEach { patch ->
            val on = PATCH_OFF + patch.key !in wanted
            if (patch.enabled != on) ClientMods.setPatchEnabled(patch, on)
        }
    }

    /** Folders the app turned off at the last launch from [home], so the next profile can turn them back on. */
    fun disabledFolders(context: Context, home: String): Set<String> = prefs(context).getStringSet(OFF + home.hashCode(), null).orEmpty()

    /**
     * Before a launch: turns off, in every AddOns.txt under [wtfDir], the addons [profile] doesn't see, and
     * turns back on the ones the app turned off for another profile. The player's own choices stay.
     */
    fun applyAtLaunch(context: Context, profile: Profile, addonsDir: File, wtfDir: File) {
        if (profile.home.isEmpty()) return
        val visible = visibility(context, profile)
        val records = AddonDb.forAddonsDir(addonsDir).load()
        val shown = records.filter { visible(it.id) }.flatMap { it.folders }.toSet()
        val hidden = records.filterNot { visible(it.id) }.flatMap { it.folders }.toSet() - shown
        val before = disabledFolders(context, profile.home)
        if (hidden.isEmpty() && before.isEmpty()) return
        addonsFiles(wtfDir, create = hidden.isNotEmpty()).forEach { file ->
            val lines = if (file.isFile) file.readLines(Charsets.UTF_8) else emptyList()
            file.writeText(withStates(lines, hidden, before - hidden).joinToString("\r\n", postfix = "\r\n"), Charsets.UTF_8)
        }
        prefs(context).edit().putStringSet(OFF + profile.home.hashCode(), hidden).apply()
    }

    /** The AddOns.txt of each account and character, plus a new one per account when [create]. */
    private fun addonsFiles(wtfDir: File, create: Boolean): List<File> {
        val accounts = File(wtfDir, "Account").listFiles().orEmpty().filter { it.isDirectory }
        return accounts.flatMap { account ->
            val own = File(account, ADDONS_TXT).takeIf { it.isFile || create }
            val characters = account.walkTopDown().maxDepth(3).filter { it.isFile && it.name.equals(ADDONS_TXT, true) && it.parentFile != account }
            listOfNotNull(own) + characters
        }
    }

    /** [lines] with [off] marked disabled and [on] marked enabled; other lines are kept as they are. */
    internal fun withStates(lines: List<String>, off: Set<String>, on: Set<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = lines.filter { it.isNotBlank() }.map { line ->
            val name = line.substringBefore(':').trim()
            seen += name.lowercase()
            when {
                off.any { it.equals(name, true) } -> "$name: disabled"
                on.any { it.equals(name, true) } -> "$name: enabled"
                else -> line
            }
        }
        return result + off.filter { it.lowercase() !in seen }.sorted().map { "$it: disabled" }
    }

    fun forget(context: Context, id: String) {
        prefs(context).edit().remove(CLAIMS + id).remove(MODS + id).apply()
    }

    private const val ADDONS_TXT = "AddOns.txt"
}
