package app.gamenative.wow.ui

import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.utils.StorageUtils
import app.gamenative.wow.legacy.LegacyClient
import app.gamenative.wow.legacy.LegacyRenderer
import app.gamenative.wow.legacy.LegacyServer
import app.gamenative.wow.legacy.mods.AndroWherePatch
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.ui.addons.ClientModsController
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwChip
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.FwTextField
import app.gamenative.wow.ui.theme.Fw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The 3.3.5a "Server" screen: the profile's servers, the selected server's password and how the login
 * screen uses it. A on a server selects it and opens its details.
 */
@Composable
fun LegacyServerScreen(launcher: LauncherController, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var returnTo by remember { mutableStateOf<String?>(launcher.legacy.serverId) }
    var solo by remember { mutableStateOf<SoloPage?>(null) }
    val server = editing?.let { id -> launcher.servers.firstOrNull { it.id == id } }
    BackHandler(enabled = server != null) {
        returnTo = editing
        editing = null
    }
    BackHandler(enabled = solo != null && server == null) { solo = null }
    HintPage("Server", onBack, launcher.errorMessage) { setHint ->
        if (solo != null && server == null) {
            when (solo) {
                SoloPage.DEVICE -> SoloDevicePage(launcher, onDone = { solo = null }, setHint)
                else -> SoloPcPage(launcher, onAdded = { solo = null; editing = it }, onDone = { solo = null }, setHint)
            }
        } else if (server != null) {
            ServerPage(launcher, server, onDone = { returnTo = editing; editing = null }, setHint)
        } else {
            FwSection("Servers") { ServerList(launcher, returnTo, onEdit = { editing = it }, setHint) }
            if (launcher.version == GameVersion.PRIVATE_335) FwSection("Your own server") { SoloSection(onOpen = { solo = it }, setHint) }
            FwSection(if (launcher.servers.size > 1) "Password for ${launcher.selectedServer.name}" else "Password") {
                PasswordFields(launcher, launcher.selectedServer, setHint)
            }
            FwSection("Login screen") { LoginSection(launcher, setHint) }
        }
    }
}

/** The 3.3.5a "Client" screen: the client folder, graphics and logs. */
@Composable
fun LegacyClientScreen(launcher: LauncherController, onBack: () -> Unit) {
    LaunchedEffect(Unit) { launcher.detectLegacyBuild() }
    HintPage("Client", onBack, launcher.errorMessage) { setHint ->
        FwSection("Folder") { FolderSection(launcher, setHint) }
        FwSection("Graphics") { GraphicsSection(launcher, setHint) }
        FwSection("Logs") {
            val settings = launcher.legacy
            ToggleRow(
                "Save a log",
                settings.saveLog,
                hint = "Slows the game down, so use it only to track down a problem. Saves ${LegacyClient.LOG} in the client folder and offers to share it when the game closes.",
                setHint = setHint,
            ) { launcher.updateLegacy(settings.copy(saveLog = !settings.saveLog)) }
        }
    }
}

@Composable
private fun ServerList(launcher: LauncherController, returnTo: String?, onEdit: (String?) -> Unit, setHint: (String) -> Unit) {
    var removing by remember { mutableStateOf<LegacyServer?>(null) }
    // Where focus lands after closing a server or the remove dialog: that server, or the first one.
    var focusAfter by remember { mutableStateOf(returnTo) }
    var tick by remember { mutableStateOf(0) }
    val refocus = remember { FocusRequester() }
    val target = focusAfter?.takeIf { id -> launcher.servers.any { it.id == id } } ?: launcher.servers.first().id
    LaunchedEffect(focusAfter, tick) { if (focusAfter != null) runCatching { refocus.requestFocus() } }
    val canRemove = launcher.servers.size > 1
    val rowHint = if (canRemove) "A  Select and edit      X  Add server      Y  Remove" else "A  Select and edit      X  Add server"
    val add = {
        launcher.addServer()
        onEdit(launcher.legacy.serverId)
    }
    launcher.servers.forEach { s ->
        FwRow(
            selected = false,
            onClick = {
                launcher.selectServer(s.id)
                onEdit(s.id)
            },
            modifier = Modifier
                .then(if (s.id == target) Modifier.focusRequester(refocus) else Modifier)
                .hint(rowHint, setHint)
                .onPreviewKeyEvent { e ->
                    when {
                        e.pressed(AndroidKey.KEYCODE_BUTTON_X) -> {
                            add()
                            true
                        }
                        e.pressed(AndroidKey.KEYCODE_BUTTON_Y) && canRemove -> {
                            removing = s
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = Fw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.realmlist.ifBlank { "No realmlist yet" }, style = Fw.Data, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (s.id == launcher.legacy.serverId) Text("Selected", style = Fw.Data.copy(color = Fw.Brass))
        }
    }
    FwRow(selected = false, onClick = add, modifier = Modifier.hint("Adds a server and opens it.", setHint)) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Fw.Brass, modifier = Modifier.size(24.dp))
        Text("Add server", style = Fw.Text)
    }
    removing?.let { s ->
        FwDialog(
            title = "Remove ${s.name}?",
            message = "Its realmlist, account name and saved password are removed from this profile.",
            confirm = "Remove",
            onConfirm = {
                launcher.removeServer(s.id)
                removing = null
                focusAfter = launcher.servers.first().id
                tick++
            },
            onDismiss = {
                removing = null
                focusAfter = s.id
                tick++
            },
        )
    }
}

/** One server's name, realmlist and account name. Its password is on the Server screen while it's selected. */
@Composable
private fun ServerPage(launcher: LauncherController, server: LegacyServer, onDone: () -> Unit, setHint: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(server.id) { runCatching { first.requestFocus() } }
    key(server.id) {
        var name by remember { mutableStateOf(server.name) }
        var realmlist by remember { mutableStateOf(server.realmlist) }
        var account by remember { mutableStateOf(server.account) }
        val save = { launcher.updateServer(server.copy(name = name.ifBlank { "Server" }, realmlist = realmlist.trim(), account = account.trim())) }
        Text(server.name, style = Fw.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FwTextField(
            name,
            { name = it; save() },
            "Name",
            Modifier.fillMaxWidth().hint("A name for the server list and the login screen.", setHint),
            focusRequester = first,
        )
        FwTextField(
            realmlist,
            { realmlist = it; save() },
            "Realmlist, e.g. logon.example.com",
            Modifier.fillMaxWidth().hint("The server's logon address, from its website.", setHint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        FwTextField(
            account,
            { account = it; save() },
            "Account name (optional)",
            Modifier.fillMaxWidth().hint("Fills the login screen's account box. Leave it empty to use the one the game remembers.", setHint),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FwButton("Done", onDone, modifier = Modifier.hint("Back to the server list. Changes are saved as you type.", setHint))
            if (launcher.servers.size > 1) {
                FwButton(
                    "Remove server",
                    {
                        launcher.removeServer(server.id)
                        onDone()
                    },
                    modifier = Modifier.hint("Removes this server and its saved password.", setHint),
                    kind = FwButtonKind.Danger,
                )
            }
        }
    }
}

@Composable
private fun PasswordFields(launcher: LauncherController, server: LegacyServer, setHint: (String) -> Unit) {
    var password by remember(server.id) { mutableStateOf("") }
    var justSaved by remember(server.id) { mutableStateOf(false) }
    val saved = launcher.hasServerPassword
    if (saved) Text(if (justSaved) "Password saved  ••••••••" else "Saved password  ••••••••", style = Fw.Text.copy(color = Fw.Ok))
    FwTextField(
        password,
        { password = it; justSaved = false },
        if (saved) "Change password" else "Password",
        Modifier.fillMaxWidth().hint("Saved on this device, encrypted with the Android keystore. Login uses it.", setHint),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwButton(
            "Save password",
            {
                launcher.saveServerPassword(password)
                password = ""
                justSaved = true
            },
            kind = FwButtonKind.Ghost,
            enabled = password.isNotEmpty(),
        )
        if (saved) FwButton("Forget password", launcher::forgetServerPassword, kind = FwButtonKind.Danger)
    }
}

@Composable
private fun LoginSection(launcher: LauncherController, setHint: (String) -> Unit) {
    val settings = launcher.legacy
    if (settings.root.isBlank()) {
        Text("Choose your client folder on the Client screen first.", style = Fw.Text)
        return
    }
    if (!launcher.legacyGame.hasAndroWherePatches) {
        Text("Controller Menus, which fills in your password on the login screen, is for 3.3.5a only for now. Type your account and password in the game.", style = Fw.Text)
        return
    }
    val root = File(settings.root)
    val scope = rememberCoroutineScope()
    val mods = remember(root) { ClientModsController(root, scope, launcher::recordClientMods) }
    LaunchedEffect(root) { mods.refresh() }
    when (mods.ours?.get(AndroWherePatch.LOGIN)) {
        null -> Text("Checking the client…", style = Fw.Secondary)
        false -> {
            Text(
                mods.working ?: mods.message
                    ?: "Controller Menus lets the controller work on the login, realm and character screens, and fills in your password.",
                style = Fw.Text,
            )
            FwButton(
                "Install Controller Menus",
                { mods.setInstalled(AndroWherePatch.LOGIN, true) },
                modifier = Modifier.hint("Changes 5 bytes of Wow.exe; the original is kept and put back if you remove it.", setHint),
                enabled = mods.working == null,
            )
        }
        true -> {
            if (!launcher.hasServerPassword) {
                Text("Save a password above to use these.", style = Fw.Secondary.copy(color = Fw.Warn))
            }
            ToggleRow(
                "Fill in password",
                settings.fastLogin,
                hint = "The app puts the password in the client folder at launch and removes it about 10 seconds after the game reads it, or after 3 minutes at most.",
                setHint = setHint,
            ) { launcher.updateLegacy(settings.copy(fastLogin = !settings.fastLogin)) }
            ToggleRow(
                "Log in automatically",
                settings.fastLogin && settings.autoLogin,
                enabled = settings.fastLogin,
                hint = if (settings.fastLogin) "Presses Log in once each time the game starts. If it fails, press A to try again." else "Needs Fill in password.",
                setHint = setHint,
            ) { launcher.updateLegacy(settings.copy(autoLogin = !settings.autoLogin)) }
        }
    }
}

@Composable
private fun GraphicsSection(launcher: LauncherController, setHint: (String) -> Unit) {
    val settings = launcher.legacy
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LegacyRenderer.entries.forEach { r ->
            FwChip(
                r.label,
                settings.renderer == r,
                { launcher.updateLegacy(settings.copy(renderer = r)) },
                Modifier.hint("Use DXVK. OpenGL is much slower and only a fallback for when DXVK doesn't start.", setHint),
            )
        }
    }
    FwButton(
        "Use lighter graphics",
        launcher::applyHandheldGraphics,
        modifier = Modifier.hint("Lowers view distance, ground clutter, particles, weather, shadows and glow, which eases the load on the processor.", setHint),
        kind = FwButtonKind.Ghost,
        enabled = settings.root.isNotBlank(),
    )
    if (launcher.legacyGraphicsApplied) {
        Text("Lighter graphics set. They apply the next time the game starts.", style = Fw.Secondary.copy(color = Fw.Ok))
    }
    ToggleRow(
        "Performance overlay",
        settings.perfOverlay,
        hint = "Frame rate, frame times and GPU load in the game, DXVK only. Low GPU load with a low frame rate means the processor is the limit.",
        setHint = setHint,
    ) { launcher.updateLegacy(settings.copy(perfOverlay = !settings.perfOverlay)) }
    ToggleRow(
        "Faster emulation",
        settings.fasterEmulation,
        hint = "Skips some of Box64's safety checks so the game runs faster. Some areas or addons can crash or glitch with it; turn it off if they do.",
        setHint = setHint,
    ) { launcher.updateLegacy(settings.copy(fasterEmulation = !settings.fasterEmulation)) }
}

@Composable
private fun FolderSection(launcher: LauncherController, setHint: (String) -> Unit) {
    val context = LocalContext.current
    val settings = launcher.legacy
    var folderError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val game = launcher.legacyGame
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = StorageUtils.getPathFromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        scope.launch {
            val root = withContext(Dispatchers.IO) { LegacyClient.findRoot(File(path)) }
            val build = root?.let { withContext(Dispatchers.IO) { LegacyClient.detectBuild(LegacyClient.exe(it)) } }
            folderError = when {
                root == null -> "No ${LegacyClient.EXE} with a Data folder in $path."
                !game.accepts(build) -> "This folder has a $build client, and this profile is for ${game.label}. Make a profile for that version instead."
                else -> null
            }
            if (folderError != null || root == null) return@launch
            val existing = withContext(Dispatchers.IO) { LegacyClient.readRealmlist(root) }
            launcher.updateLegacy(launcher.legacy.copy(root = root.absolutePath, realmlist = existing ?: launcher.legacy.realmlist))
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    if (settings.root.isBlank()) {
        Text("No folder chosen.", style = Fw.Text)
    } else {
        Text(settings.root, style = Fw.Data)
        Text(launcher.legacyBuild?.let { "Client build $it" } ?: "Client build unknown", style = Fw.Data)
        SharedFolderNote(launcher)
    }
    folderError?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
    FwButton(
        "Choose folder",
        { picker.launch(null) },
        modifier = Modifier.focusRequester(first).hint(
            "The folder with ${LegacyClient.EXE} and Data. The app only changes its realmlist, its settings and the client add-ons you turn on.",
            setHint,
        ),
        kind = if (settings.root.isBlank()) FwButtonKind.Primary else FwButtonKind.Ghost,
    )
    Text("Client patches are in Addons, on the Client tab.", style = Fw.Secondary)
}
