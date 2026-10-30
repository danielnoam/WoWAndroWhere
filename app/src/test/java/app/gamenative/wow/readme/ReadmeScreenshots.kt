package app.gamenative.wow.readme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.gamenative.ui.screen.wow.GpuProfile
import app.gamenative.wow.legacy.LegacyServer
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.profile.Profiles
import app.gamenative.wow.ui.HomeScreen
import app.gamenative.wow.ui.LauncherController
import app.gamenative.wow.ui.LegacyClientScreen
import app.gamenative.wow.ui.LegacyServerScreen
import app.gamenative.wow.ui.SettingsScreen
import app.gamenative.wow.ui.addons.AddonsController
import app.gamenative.wow.ui.addons.AddonsScreen
import app.gamenative.wow.ui.addons.legacyAddonsControllerFor
import app.gamenative.wow.ui.theme.Fw
import app.gamenative.wow.ui.theme.ForeverywhereTheme
import io.mockk.every
import io.mockk.mockkObject
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the launcher's screens for the README at the Odin 3's 1920x1080, with a sample 3.3.5a profile.
 * Not part of the PR checks: the "README screenshots" workflow runs it and commits the PNGs.
 */
@Config(sdk = [29], qualifiers = "w768dp-h432dp-land-400dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ReadmeScreenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("readme.out") ?: "build/readme-screens").apply { mkdirs() }

    private enum class Screen { HOME, SERVER, CLIENT, ADDONS, PATCHES, BROWSE, SETTINGS }

    @Test
    fun capture() {
        // GPUInformation needs the app's native library; this is the Odin 3's Adreno 8xx profile.
        mockkObject(GpuProfile.Companion)
        every { GpuProfile.detect(any()) } returns
            GpuProfile("Turnip-V32-RP6sched", "noconform,sysmem", "1280x720", "Adreno 8xx", "Snapdragon 8 Elite / Adreno 8xx")
        val client = sampleClient()
        Profiles.load(context)
        val profile = Profiles.create(context, "Warmane", GameVersion.PRIVATE_335)
        Profiles.activate(context, profile.id)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val launcher = LauncherController(context, scope) {}
        launcher.updateLegacy(launcher.legacy.copy(root = client.absolutePath, fastLogin = true))
        launcher.updateServer(launcher.selectedServer.copy(name = "Warmane", realmlist = "logon.warmane.com"))
        launcher.addSoloServer(LegacyServer("solo", "Solo, this device", "127.0.0.1", solo = LegacyServer.SOLO_DEVICE))
        launcher.selectServer(launcher.servers.first().id)
        val addons = legacyAddonsControllerFor(client.absolutePath, GameVersion.PRIVATE_335, scope)!!

        var screen by mutableStateOf(Screen.HOME)
        compose.setContent {
            ForeverywhereTheme {
                Box(Modifier.fillMaxSize().background(Fw.Ground)) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(launcher, 0, 4, {}, {}, {}, {}, {})
                        Screen.SERVER -> LegacyServerScreen(launcher) {}
                        Screen.CLIENT -> LegacyClientScreen(launcher) {}
                        Screen.ADDONS, Screen.PATCHES, Screen.BROWSE -> AddonsScreen(addons) {}
                        Screen.SETTINGS -> SettingsScreen(launcher, onBack = {}, onEditLogin = {})
                    }
                }
            }
        }

        val failed = mutableListOf<String>()
        Screen.entries.forEach { s ->
            runCatching {
                when (s) {
                    Screen.ADDONS -> addons.switchMode(AddonsController.Mode.INSTALLED)
                    Screen.PATCHES -> addons.switchMode(AddonsController.Mode.CLIENT)
                    Screen.BROWSE -> addons.switchMode(AddonsController.Mode.BROWSE)
                    else -> Unit
                }
                screen = s
                settle(if (s == Screen.BROWSE || s == Screen.HOME) 40 else 8)
                save(s.name.lowercase())
            }.onFailure {
                it.printStackTrace()
                failed += s.name
            }
        }
        assertTrue("Failed: $failed", failed.isEmpty())
    }

    /** Lets network art and catalog loads on the IO threads land before the frame is taken. */
    private fun settle(rounds: Int) = repeat(rounds) {
        Thread.sleep(250)
        compose.waitForIdle()
    }

    /** Draws the window in software: Compose's captureToImage waits on a hardware redraw Robolectric never makes. */
    private fun save(name: String) {
        val view = compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A 3.3.5a client folder: a Wow.exe with only its version resource, a realmlist and a few add-ons. */
    private fun sampleClient(): File {
        val root = File(context.filesDir, "World of Warcraft 3.3.5a").apply { mkdirs() }
        File(root, "Wow.exe").writeBytes(versionResource(3, 3, 5, 12340))
        File(root, "Data/enUS").mkdirs()
        File(root, "Data/enUS/realmlist.wtf").writeText("set realmlist logon.warmane.com\r\n")
        listOf(
            "Questie" to "Questie",
            "Bartender4" to "Bartender4",
            "Details" to "Details! Damage Meter",
            "AtlasLoot" to "AtlasLoot Enhanced",
        ).forEach { (folder, title) ->
            File(root, "Interface/AddOns/$folder").mkdirs()
            File(root, "Interface/AddOns/$folder/$folder.toc").writeText("## Interface: 30300\n## Title: $title\n## Version: 1.0\n")
        }
        return root
    }

    private fun versionResource(major: Int, minor: Int, patch: Int, build: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.position(16)
        b.putInt(0xFEEF04BD.toInt()).putInt(0x00010000).putInt((major shl 16) or minor).putInt((patch shl 16) or build)
        return b.array()
    }
}
