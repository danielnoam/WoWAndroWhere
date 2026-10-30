package app.gamenative.wow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gamenative.wow.product.GameRegion
import app.gamenative.wow.product.GameRegions
import app.gamenative.wow.product.RenderSize
import app.gamenative.wow.product.RenderSizes
import app.gamenative.ui.screen.wow.fitScreenSizeToWindow
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.BuildConfig
import app.gamenative.utils.renderReleaseNotes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.ui.platform.LocalContext
import app.gamenative.wow.AppName
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwChip
import app.gamenative.wow.ui.components.Links
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwProgress
import app.gamenative.wow.ui.components.FwTextField
import app.gamenative.wow.ui.components.FwTopBar
import app.gamenative.wow.ui.theme.Fw

@Composable
internal fun FwPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        FwTopBar(title, onBack)
        HorizontalDivider(color = Fw.Rule)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) { content() }
    }
}

@Composable
internal fun FwSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Fw.Label.copy(color = Fw.Brass, fontSize = 12.sp, letterSpacing = 1.6.sp))
        content()
    }
}

private val settingsPanes = listOf(
    PaneSpec("App", "App version and updates."),
    PaneSpec("Battle.net", "One saved login for every Blizzard profile."),
    PaneSpec("Addon sources", "Where Addons searches, and your CurseForge key or proxy."),
    PaneSpec("About", "This device, the project and its source code."),
)

@Composable
fun SettingsScreen(
    launcher: LauncherController,
    onBack: () -> Unit,
    onEditLogin: () -> Unit,
) {
    PaneScreen("Settings", settingsPanes, onBack) { pane, setHint ->
        when (pane) {
            0 -> AppPane(launcher, setHint)
            1 -> BattleNetPane(launcher, onEditLogin, setHint)
            2 -> {
                ProviderLine("CurseForge", launcher.curseForge.enabled, offText = "Off: no key or proxy")
                ProviderLine("Wago Addons", BuildConfig.WAGO_API_KEY.isNotBlank())
                ProviderLine("WoWInterface", true)
                ProviderLine("GitHub (addon catalog and links)", true)
                CurseForgeSection(launcher, setHint)
            }
            else -> AboutPane(launcher, setHint)
        }
    }
}

@Composable
private fun AppPane(launcher: LauncherController, setHint: (String) -> Unit) {
    Text("${AppName.FULL} v${BuildConfig.VERSION_NAME}", style = Fw.Text)
    val update = launcher.appUpdate
    if (update == null) {
        Text("No app update found.", style = Fw.Secondary)
        FwButton(
            "Check for app update",
            launcher::checkAppUpdate,
            modifier = Modifier.hint("Looks for a newer release of this app on GitHub.", setHint),
            kind = FwButtonKind.Ghost,
        )
    } else {
        Text("Version ${update.version} is available.", style = Fw.Text.copy(color = Fw.Brass))
        if (update.notes.isNotBlank()) Text(renderReleaseNotes(update.notes), style = Fw.Secondary)
        if (launcher.isDownloadingAppUpdate) {
            Text("Downloading ${(launcher.appUpdateProgress * 100).toInt()}%", style = Fw.Data)
            FwProgress(launcher.appUpdateProgress)
        } else {
            FwButton(
                if (launcher.downloadedApk?.exists() == true) "Install update" else "Update app",
                launcher::performAppUpdate,
                modifier = Modifier.hint("Installs over this version and keeps your settings, logins, game files and addons.", setHint),
            )
        }
    }
    ResolutionChoice(launcher, setHint)
}

/** Settings, App: the game's screen size, from the device profile or picked here. */
@Composable
private fun ResolutionChoice(launcher: LauncherController, setHint: (String) -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(RenderSizes.selected) }
    Text("Resolution", style = Fw.Text, modifier = Modifier.padding(top = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RenderSize.entries.forEach { r ->
            val result = remember(r) { fitScreenSizeToWindow(context, launcher.gpu.screenSize, r) }
            FwRow(
                selected = r == current,
                onClick = {
                    RenderSizes.save(context, r)
                    current = r
                },
                modifier = Modifier.hint(
                    "The size the game runs at, for every profile. Higher looks sharper but is harder on the device. A change applies the next time you start the game.",
                    setHint,
                ),
            ) {
                Text(if (r == RenderSize.AUTO) "Automatic, for this device" else r.label, style = Fw.Text, modifier = Modifier.weight(1f))
                Text(result, style = Fw.Data.copy(color = Fw.Ash))
                if (r == current) Text("Selected", style = Fw.Data.copy(color = Fw.Brass))
            }
        }
    }
}

/** Settings, Battle.net: the region the game plays in, written as its portal at the next launch. */
@Composable
private fun RegionChoice(launcher: LauncherController, setHint: (String) -> Unit) {
    val context = LocalContext.current
    val branch = remember(launcher.gamePath, launcher.files.product) {
        app.gamenative.wow.product.BuildInfo.read(java.io.File(launcher.gamePath))?.active(launcher.files.product)?.get("Branch")
    }
    var current by remember(branch) { mutableStateOf(GameRegions.effective(branch)) }
    Text("Region", style = Fw.Text, modifier = Modifier.padding(top = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GameRegion.entries.forEach { r ->
            FwRow(
                selected = r == current,
                onClick = {
                    GameRegions.save(context, r)
                    current = r
                },
                modifier = Modifier.hint("The Battle.net region you play in. A change applies the next time you start the game.", setHint),
            ) {
                Text(r.label, style = Fw.Text, modifier = Modifier.weight(1f))
                Text(r.portal, style = Fw.Data.copy(color = Fw.Ash))
                if (r == current) Text("Selected", style = Fw.Data.copy(color = Fw.Brass))
            }
        }
    }
}

/** Settings, Battle.net login: one saved login for every Blizzard profile. */
@Composable
private fun BattleNetPane(launcher: LauncherController, onEditLogin: () -> Unit, setHint: (String) -> Unit) {
    if (launcher.hasSavedLogin) {
        Text("Login saved  ••••••••", style = Fw.Text.copy(color = Fw.Ok))
    } else {
        Text("No login saved. You'll sign in inside the game.", style = Fw.Text)
    }
    val how = "Saved encrypted with the Android keystore. At Play the game gets it in a temporary file, wiped once read or after ten minutes at most."
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwButton(if (launcher.hasSavedLogin) "Change login" else "Save login", onEditLogin, modifier = Modifier.hint(how, setHint))
        if (launcher.hasSavedLogin) {
            FwButton(
                "Forget login",
                launcher::forgetLogin,
                modifier = Modifier.hint("Removes the saved login from this device.", setHint),
                kind = FwButtonKind.Danger,
            )
        }
    }
    Text("Authenticator codes are still typed in the game. Private servers use the password on their Server screen.", style = Fw.Secondary)
    RegionChoice(launcher, setHint)
}

@Composable
private fun AboutPane(launcher: LauncherController, setHint: (String) -> Unit) {
    val context = LocalContext.current
    Text("${launcher.gpu.edition} profile · render size ${launcher.gpu.screenSize}", style = Fw.Data)
    Text(
        "Unofficial community project, not affiliated with Blizzard Entertainment. The game is downloaded from Blizzard's own servers and needs your own Battle.net account. Based on jaredgei/wow-forever-android (GPL-3.0).",
        style = Fw.Secondary,
    )
    FwButton(
        "Source code on GitHub",
        { Links.open(context, Links.REPO) },
        modifier = Modifier.hint("Opens the project on GitHub in your browser.", setHint),
        kind = FwButtonKind.Ghost,
        icon = Icons.AutoMirrored.Filled.OpenInNew,
    )
    FwButton(
        "Support on Ko-fi",
        { Links.open(context, Links.KOFI) },
        modifier = Modifier.hint("${AppName.FULL} is built on WoW Foreverywhere, a free and open source project you can support on Ko-fi.", setHint),
        kind = FwButtonKind.Ghost,
        icon = Icons.Filled.LocalCafe,
    )
}

@Composable
private fun ProviderLine(name: String, enabled: Boolean, offText: String = "Off: no API key") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = Fw.Text)
        Text(if (enabled) "On" else offText, style = Fw.Data.copy(color = if (enabled) Fw.Ok else Fw.Ash))
    }
}

/** Settings, CurseForge: the player's own API key or proxy. There is no shared one. */
@Composable
private fun CurseForgeSection(launcher: LauncherController, setHint: (String) -> Unit) {
    val setup = launcher.curseForge
    var useProxy by remember { mutableStateOf(setup.usesProxy) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun pick(proxy: Boolean) {
        useProxy = proxy
        input = ""
        error = null
    }
    FwSection("CurseForge") {
        Text(
            when {
                setup.usesKey -> "On, with your API key. It's saved on this device, encrypted with the Android keystore, and only sent to CurseForge."
                setup.usesProxy -> "On, through your proxy at ${setup.proxyUrl}. The app sends no key to it."
                else -> "Off. CurseForge needs your own API key or a proxy you run. Wago, WoWInterface and GitHub work without either."
            },
            style = Fw.Text,
        )
        val modeHint = "A free key from console.curseforge.com, for you only, or a proxy you run (server/curseforge-proxy in the source) so no key is on the device."
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FwChip("Your API key", !useProxy, { pick(false) }, Modifier.hint(modeHint, setHint))
            FwChip("Your proxy", useProxy, { pick(true) }, Modifier.hint(modeHint, setHint))
        }
        FwTextField(
            input,
            {
                input = it
                error = null
            },
            when {
                useProxy -> "Proxy address, e.g. https://cf.example.com"
                setup.usesKey -> "New API key"
                else -> "API key"
            },
            Modifier.fillMaxWidth().hint(if (useProxy) "Searches then go through your server." else "Saving a key replaces a saved proxy.", setHint),
            visualTransformation = if (useProxy) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (useProxy) KeyboardType.Uri else KeyboardType.Password),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FwButton(
                if (useProxy) "Save proxy" else "Save key",
                {
                    val saved = if (useProxy) launcher.saveCurseForgeProxy(input) else launcher.saveCurseForgeKey(input)
                    error = when {
                        saved -> null
                        useProxy -> "Enter an https address, like https://cf.example.com."
                        else -> "Couldn't save the key on this device."
                    }
                    if (saved) input = ""
                },
                enabled = input.isNotBlank(),
            )
            if (setup.enabled) {
                FwButton(
                    if (setup.usesKey) "Forget key" else "Forget proxy",
                    {
                        launcher.forgetCurseForge()
                        input = ""
                        error = null
                    },
                    kind = FwButtonKind.Danger,
                )
            }
        }
        error?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
    }
}
