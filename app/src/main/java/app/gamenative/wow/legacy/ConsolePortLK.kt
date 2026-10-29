package app.gamenative.wow.legacy

import android.content.Context
import app.gamenative.wow.legacy.mods.ClientMods
import app.gamenative.wow.profile.ProfileAddons
import app.gamenative.wow.profile.Profiles
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlsProfile
import com.winlator.inputcontrols.InputControlsManager
import java.io.File
import org.json.JSONObject

/**
 * ConsolePortLK, the 3.3.5a backport of ConsolePort. The 3.3.5a client can't read a gamepad, so the addon
 * expects controller buttons to arrive as keys: [applyControls] gives the legacy container a profile that
 * turns the controller into F1-F12, Shift and Ctrl (the addon's two modifiers), WASD and the mouse, and
 * a setup addon presets ConsolePortLK's calibration to those keys. Our login screen add-on reads the same keys,
 * so the profile is also used when only that is installed.
 */
object ConsolePortLK {
    const val REPO_URL = "https://github.com/leoaviana/ConsolePortLK"
    const val PROFILE_NAME = "ConsolePortLK"
    private const val PROFILE_ASSET = "wow/consoleportlk.icp"
    const val MAIN_FOLDER = "ConsolePort"

    fun isInstalled(addonsDir: File) = File(addonsDir, "$MAIN_FOLDER/$MAIN_FOLDER.toc").isFile

    /**
     * Uses the controller profile while ConsolePortLK or our login screen add-on is installed, or [always] (1.12 and
     * 2.4.3, which have neither), and the player hasn't picked another profile for the container. Goes back to the
     * default profile once both are gone.
     */
    fun applyControls(context: Context, container: Container, clientRoot: File, always: Boolean = false) {
        val manager = InputControlsManager(context)
        val current = container.getExtra("profileId", "0").toIntOrNull() ?: 0
        val existing = manager.getProfiles(false).firstOrNull { it.name == PROFILE_NAME }
        val addonsDir = File(clientRoot, "Interface/AddOns")
        // On a folder shared by profiles, the app may have turned ConsolePort off for this one in AddOns.txt.
        val off = Profiles.active?.let { MAIN_FOLDER in ProfileAddons.disabledFolders(context, it.home) } ?: false
        val consolePort = isInstalled(addonsDir) && !off
        if (consolePort) writeSetupAddon(context, addonsDir) else File(addonsDir, SETUP_FOLDER).deleteRecursively()
        if (!always && !consolePort && !ClientMods.loginEnabled(clientRoot)) {
            if (existing != null && current == existing.id) container.putExtra("profileId", null)
            return
        }
        val ours = existing?.id ?: ((manager.getProfiles(false).maxOfOrNull { it.id } ?: 0) + 1)
        writeIfOutdated(context, ours)
        val chosenOther = current != 0 && current != ours && manager.getProfile(current) != null
        if (!chosenOther) container.putExtra("profileId", ours.toString())
    }

    /**
     * Writes the shipped profile under [id] when it's missing or an older mapping was written. The version
     * lives in prefs because Edit controls rewrites the file without unknown keys.
     */
    private fun writeIfOutdated(context: Context, id: Int) {
        val shipped = JSONObject(context.assets.open(PROFILE_ASSET).bufferedReader().use { it.readText() })
        val version = shipped.optInt(MAPPING_VERSION)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val file = ControlsProfile.getProfileFile(context, id)
        if (file.isFile && prefs.getInt(MAPPING_VERSION, 0) >= version) return
        shipped.put("id", id)
        file.writeText(shipped.toString())
        prefs.edit().putInt(MAPPING_VERSION, version).apply()
    }

    /**
     * A tiny addon that loads before ConsolePort and presets its Xbox layout and calibration to the keys
     * the profile sends, so the player never has to calibrate. Rewritten every launch.
     */
    private fun writeSetupAddon(context: Context, addonsDir: File) {
        val dir = File(addonsDir, SETUP_FOLDER).apply { mkdirs() }
        File(dir, "$SETUP_FOLDER.toc").writeText(context.assets.open("$SETUP_ASSETS/setup.toc").bufferedReader().use { it.readText() })
        File(dir, "Setup.lua").writeText(context.assets.open("$SETUP_ASSETS/Setup.lua").bufferedReader().use { it.readText() })
    }

    const val SETUP_FOLDER = "!ConsolePortLK_AndroWhere"
    private const val SETUP_ASSETS = "wow/consoleportlk-setup"
    private const val PREFS = "wow_consoleportlk"
    private const val MAPPING_VERSION = "mappingVersion"
}
