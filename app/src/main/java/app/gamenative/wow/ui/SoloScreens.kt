package app.gamenative.wow.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.gamenative.wow.legacy.LegacyServer
import app.gamenative.wow.solo.SoloManifest
import app.gamenative.wow.solo.SoloPaths
import app.gamenative.wow.solo.SoloServer
import app.gamenative.wow.solo.SoloSetup
import app.gamenative.wow.solo.SoloSetupService
import app.gamenative.wow.solo.SoloSetupState
import app.gamenative.wow.solo.SoloState
import app.gamenative.wow.solo.SoloStatus
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwProgress
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.FwTextField
import app.gamenative.wow.ui.theme.Fw
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SoloPage { DEVICE, PC }

/** The Server screen's "Your own server" rows: one the app runs on this device, or one on the player's PC. */
@Composable
internal fun SoloSection(onOpen: (SoloPage) -> Unit, setHint: (String) -> Unit) {
    val context = LocalContext.current
    val setup by SoloSetup.state.collectAsState()
    val status by SoloServer.status.collectAsState()
    val ready = remember(setup) { SoloState.load(SoloPaths.of(context)).account.isNotEmpty() }
    FwRow(selected = false, onClick = { onOpen(SoloPage.DEVICE) }, modifier = Modifier.hint("Runs a server on this device, just for you. No other players, no internet needed once it's set up.", setHint)) {
        Column(Modifier.weight(1f)) {
            Text("On this device", style = Fw.Text)
            Text(deviceSummary(ready, setup, status), style = Fw.Data)
        }
    }
    FwRow(selected = false, onClick = { onOpen(SoloPage.PC) }, modifier = Modifier.hint("Connects to a server you run on your computer, over your home Wi-Fi.", setHint)) {
        Column(Modifier.weight(1f)) {
            Text("On a PC", style = Fw.Text)
            Text("Dad's MMO Lab or any 3.3.5a server on your network", style = Fw.Data)
        }
    }
}

private fun deviceSummary(ready: Boolean, setup: SoloSetupState, status: SoloStatus) = when {
    setup is SoloSetupState.Running -> setup.step
    setup is SoloSetupState.Failed -> "Setup stopped"
    !ready -> "Not set up"
    status is SoloStatus.Running -> "Running"
    status is SoloStatus.Starting -> status.step
    else -> "Set up, stopped"
}

/** Setting up, starting and removing the server this device runs. */
@Composable
internal fun SoloDevicePage(launcher: LauncherController, onDone: () -> Unit, setHint: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manifest = remember { SoloManifest.load(context) }
    val setup by SoloSetup.state.collectAsState()
    val status by SoloServer.status.collectAsState()
    val paths = remember { SoloPaths.of(context) }
    var state by remember(setup) { mutableStateOf(SoloState.load(paths)) }
    var pathing by remember { mutableStateOf(state.pathfinding || state.dataFolders.isEmpty()) }
    var gm by remember { mutableStateOf(state.gm) }
    var removing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    // Once setup has made the account, the server joins this profile's list like any other.
    LaunchedEffect(state.account, state.serverId) {
        if (state.account.isNotEmpty() && launcher.servers.none { it.id == state.serverId }) {
            launcher.addSoloServer(LegacyServer(state.serverId, "Solo, this device", "127.0.0.1", state.account, LegacyServer.SOLO_DEVICE))
        }
    }

    Text("Solo server on this device", style = Fw.Title)
    if (manifest == null) {
        Text("This version of the app doesn't include a solo server build yet.", style = Fw.Text)
        FwButton("Done", onDone, modifier = Modifier.focusRequester(first))
        return
    }
    val running = setup is SoloSetupState.Running
    val ready = state.isReady(manifest, pathing)
    val needsSetup = !ready || pathing != state.pathfinding

    if (needsSetup || running) {
        val sizes = SoloSetup.sizes(manifest, pathing)
        Text(
            "Downloads AzerothCore and its world database (built by this app's project), then the map data from AzerothCore's client-data release. " +
                "Everything is checked against pinned checksums before it's used.",
            style = Fw.Text,
        )
        Text("Download ${SoloSetup.gb(sizes.download)}, uses ${SoloSetup.gb(sizes.disk)}. Free on this device: ${SoloSetup.gb(SoloSetup.freeSpace(context))}.", style = Fw.Data)
    }
    ToggleRow(
        "Mob pathing",
        pathing,
        enabled = !running,
        hint = "Lets monsters walk around walls and follow you properly. Adds 0.7 GB to the download and 2.2 GB on the device. Without it they sometimes walk through things.",
        setHint = setHint,
    ) { pathing = !pathing }
    ToggleRow(
        "GM commands for my account",
        gm,
        enabled = !running,
        hint = "Lets you use commands like .tele and .levelup in chat. Only affects your own server.",
        setHint = setHint,
    ) {
        gm = !gm
        if (state.account.isNotEmpty()) scope.launch {
            runCatching { SoloSetup.setGm(context, gm) }.onFailure { message = it.message }
            state = SoloState.load(paths)
        }
    }

    when (val s = setup) {
        is SoloSetupState.Running -> {
            Text(s.step, style = Fw.Text)
            FwProgress(s.fraction)
            Text("Setup keeps going if you leave the app.", style = Fw.Secondary)
        }
        is SoloSetupState.Failed -> Text(s.message, style = Fw.Secondary.copy(color = Fw.Warn))
        else -> Unit
    }
    message?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }

    if (!needsSetup && !running) {
        Text(
            when (val st = status) {
                SoloStatus.Running -> "Running. Pick \"Solo, this device\" and press Play."
                is SoloStatus.Starting -> st.step
                is SoloStatus.Failed -> st.message
                SoloStatus.Stopped -> "Stopped. Play starts it for you and stops it when the game closes."
            },
            style = Fw.Text.copy(color = if (status is SoloStatus.Failed) Fw.Warn else Fw.Bone),
        )
        if (status is SoloStatus.Starting) FwProgress(null)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (needsSetup || running) {
            FwButton(
                if (setup is SoloSetupState.Failed || state.server.isNotEmpty()) "Continue setup" else "Set up",
                { message = null; SoloSetupService.start(context, pathing, gm) },
                modifier = Modifier.focusRequester(first).hint("Runs in the background. Use Wi-Fi: it's a large download.", setHint),
                enabled = !running,
            )
        } else {
            if (status is SoloStatus.Running) {
                FwButton("Stop", { scope.launch { SoloServer.stop() } }, modifier = Modifier.focusRequester(first), kind = FwButtonKind.Ghost)
            } else {
                FwButton(
                    "Start",
                    { scope.launch { runCatching { SoloServer.start(context) }.onFailure { message = it.message } } },
                    modifier = Modifier.focusRequester(first).hint("Starts the server now, so Play doesn't wait for it.", setHint),
                    kind = FwButtonKind.Ghost,
                    enabled = status !is SoloStatus.Starting,
                )
            }
        }
        FwButton("Done", onDone, kind = FwButtonKind.Ghost)
        if (state.server.isNotEmpty() && !running) {
            FwButton("Remove server", { removing = true }, kind = FwButtonKind.Danger)
        }
    }

    if (removing) {
        FwDialog(
            title = "Remove the solo server?",
            message = "Deletes the server, its map data and your characters on it, freeing ${SoloSetup.gb(SoloSetup.onDisk(paths))}.",
            confirm = "Remove",
            onConfirm = {
                removing = false
                val id = state.serverId
                scope.launch {
                    SoloSetup.remove(context)
                    if (id.isNotEmpty() && launcher.servers.size > 1) launcher.removeServer(id)
                    state = SoloState()
                }
            },
            onDismiss = { removing = false },
        )
    }
}

/** Adding a server the player runs on their PC, with a connection check and where to get one. */
@Composable
internal fun SoloPcPage(launcher: LauncherController, onAdded: (String) -> Unit, onDone: () -> Unit, setHint: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(launcher.servers.firstOrNull { it.solo == LegacyServer.SOLO_PC }?.realmlist.orEmpty()) }
    var check by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Text("Solo server on a PC", style = Fw.Title)
    Text(
        "Run the server on your computer and play on this device over your home Wi-Fi. Dad's MMO Lab has a Windows installer for an offline " +
            "AzerothCore server. Set its realm address to your PC's local IP address, as its networking guide shows, then enter that address here.",
        style = Fw.Text,
    )
    FwTextField(
        address,
        { address = it; check = null },
        "PC address, e.g. 192.168.1.20",
        Modifier.fillMaxWidth().hint("Your PC's local IP address. On Windows, ipconfig shows it as IPv4 Address.", setHint),
        focusRequester = first,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    check?.let { Text(it, style = Fw.Secondary.copy(color = if (it.startsWith("The server")) Fw.Ok else Fw.Warn)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwButton(
            "Check connection",
            {
                checking = true
                scope.launch {
                    check = withContext(Dispatchers.IO) { checkLogin(address.trim()) }
                    checking = false
                }
            },
            modifier = Modifier.hint("Tries the server's login port, 3724, from this device.", setHint),
            kind = FwButtonKind.Ghost,
            enabled = address.isNotBlank() && !checking,
        )
        FwButton(
            "Add to servers",
            {
                val existing = launcher.servers.firstOrNull { it.solo == LegacyServer.SOLO_PC }
                val server = (existing ?: LegacyServer(java.util.UUID.randomUUID().toString().take(8), "Solo, PC", "", solo = LegacyServer.SOLO_PC))
                    .copy(realmlist = address.trim())
                launcher.addSoloServer(server)
                onAdded(server.id)
            },
            modifier = Modifier.hint("Adds it to the server list and opens it, so you can enter the account you made on the PC.", setHint),
            enabled = address.isNotBlank(),
        )
        FwButton("Done", onDone, kind = FwButtonKind.Ghost)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwButton("Windows guide", { open(DML_WINDOWS) }, modifier = Modifier.hint("Opens Dad's MMO Lab's Windows install guide in the browser.", setHint), kind = FwButtonKind.Ghost)
        FwButton("Networking guide", { open(DML_NETWORKING) }, modifier = Modifier.hint("How to make the server reachable from other devices on your network.", setHint), kind = FwButtonKind.Ghost)
    }
}

private fun checkLogin(address: String): String = runCatching {
    Socket().use { it.connect(InetSocketAddress(address, 3724), 3000) }
    "The server answers on $address."
}.getOrElse { "No answer from $address on port 3724. Check that the server is running and that the PC's firewall allows it." }

private const val DML_WINDOWS = "https://github.com/DadsMmoLab/dads-mmo-lab/blob/main/guides/DML-Windows/DML-Windows-HOWTO.md"
private const val DML_NETWORKING = "https://github.com/DadsMmoLab/dads-mmo-lab/blob/main/guides/wow-wotlk/WoW-Wotlk-NETWORKING.md"
