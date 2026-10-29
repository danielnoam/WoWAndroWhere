package app.gamenative.wow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.gamenative.wow.ui.components.ForeverArt
import app.gamenative.wow.ui.components.Links
import app.gamenative.wow.ui.components.FwActionBar
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwTile
import app.gamenative.wow.ui.theme.Fw
import java.io.File

@Composable
fun HomeScreen(
    launcher: LauncherController,
    addonUpdates: Int?,
    addonCount: Int?,
    onOpenAddons: () -> Unit,
    onOpenGame: () -> Unit,
    onOpenClient: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewProfile: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
    var showProfiles by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Fw.Ground)
            .onPreviewKeyEvent { e ->
                when {
                    e.pressed(android.view.KeyEvent.KEYCODE_BUTTON_L1) -> true.also { launcher.cycleProfile(-1) }
                    e.pressed(android.view.KeyEvent.KEYCODE_BUTTON_R1) -> true.also { launcher.cycleProfile(1) }
                    else -> false
                }
            }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(launcher, onOpenSettings, onOpenProfiles = { showProfiles = true })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val play: @Composable (Modifier) -> Unit = { m -> PlayTile(launcher, onOpenGame, onOpenClient, m.focusRequester(playFocus)) }
            val tiles: @Composable (Modifier) -> Unit = { m ->
                SmallTiles(launcher, addonUpdates, addonCount, onOpenAddons, onOpenGame, onOpenClient, m)
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    play(Modifier.weight(1f).fillMaxHeight())
                    tiles(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                // Portrait: the small tiles keep a fixed height and Play takes all the rest.
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    play(Modifier.fillMaxWidth().weight(1f))
                    tiles(Modifier.fillMaxWidth().height(if (launcher.isPrivate) 340.dp else 163.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (launcher.profiles.size > 1) "A  Select      B  Back      L1 R1  Profile" else "A  Select      B  Back", style = Fw.Data, modifier = Modifier.weight(1f))
            SupportLink()
        }
    }
    if (showProfiles) {
        ProfileMenu(
            launcher,
            onDismiss = { showProfiles = false },
            onNew = {
                showProfiles = false
                onNewProfile()
            },
        )
    }
}

/** A quiet Ko-fi link in the hint row: muted, small, and only bronze when focused. */
@Composable
private fun SupportLink() {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .border(if (focused) 2.dp else 0.dp, if (focused) Fw.Brass else Color.Transparent, Fw.Pill)
            .clickable(interactionSource = interaction, indication = null) { Links.open(context, Links.KOFI) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.LocalCafe, contentDescription = null, tint = if (focused) Fw.Brass else Fw.Ash, modifier = Modifier.size(14.dp))
        Text("Support on Ko-fi", style = Fw.Data.copy(color = if (focused) Fw.Brass else Fw.Ash))
    }
}

@Composable
private fun Header(launcher: LauncherController, onOpenSettings: () -> Unit, onOpenProfiles: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ProfileButton(launcher, onOpenProfiles, Modifier.weight(1f, fill = false))
        }
        val version = launcher.versionStatus
        // A private server client never updates, so there's no status to show for it.
        val (text, color) = when {
            launcher.isPrivate -> null to Fw.Ash
            launcher.filesMissing -> "Game not installed" to Fw.Ash
            launcher.isCheckingVersion || !launcher.versionChecked -> "Checking for game updates…" to Fw.Ash
            version == null -> "Game version unknown (offline?)" to Fw.Ash
            version.isOutdated -> "Game update ${version.remoteVersion} available" to Fw.Warn
            else -> "Game up to date" to Fw.Ok
        }
        text?.let { Text(it, style = Fw.Data.copy(color = color), modifier = Modifier.background(Fw.Stone, Fw.Pill).padding(horizontal = 14.dp, vertical = 6.dp)) }
        FwButton(
            if (launcher.appUpdate != null) "Settings · Update" else "Settings",
            onOpenSettings,
            modifier = Modifier.padding(start = 10.dp),
            kind = if (launcher.appUpdate != null) FwButtonKind.Primary else FwButtonKind.Ghost,
            icon = Icons.Filled.Settings,
        )
    }
    launcher.errorMessage?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
}

@Composable
private fun PlayTile(launcher: LauncherController, onOpenGame: () -> Unit, onOpenClient: () -> Unit, modifier: Modifier) {
    val files = launcher.files
    val version = launcher.versionStatus
    FwTile(
        title = if (!launcher.filesMissing) "Play" else if (launcher.isPrivate) "Set up" else "Install",
        modifier = modifier,
        art = ForeverArt.KEY_ART,
        titleStyle = Fw.Hero,
        footer = {
            when {
                launcher.busy -> FwActionBar("Working…", enabled = false)
                launcher.filesMissing -> FwActionBar("Set up")
                version?.isOutdated == true -> FwActionBar("Update")
                else -> FwActionBar("Play", enabled = launcher.canPlay)
            }
        },
        onClick = {
            when {
                launcher.isPrivate && launcher.filesMissing -> onOpenClient()
                launcher.filesMissing || (launcher.isPrivate && !launcher.canPlay && !launcher.busy) -> onOpenGame()
                version?.isOutdated == true -> launcher.performUpdate()
                launcher.canPlay -> launcher.launchGame()
            }
        },
    ) {
        Text(
            when {
                launcher.isPrivate && !launcher.filesMissing -> launcher.legacy.realmlist.ifBlank { "No server set" }
                    .let { if (launcher.servers.size > 1) "${launcher.selectedServer.name} · $it" else it }
                else -> files.product?.let { listOfNotNull(it.displayName, version?.localVersion).joinToString(" · ") } ?: "Set up ${launcher.version.displayName} to start"
            },
            style = Fw.Data,
        )
        when {
            launcher.busy -> Text(if (launcher.isUpdating) launcher.updateStatusText else launcher.statusText, style = Fw.Text)
            launcher.isPrivate && launcher.filesMissing -> Text("Choose your ${launcher.legacyGame.label} client folder and enter your server's realmlist.", style = Fw.Secondary)
            launcher.isPrivate && launcher.legacy.realmlist.isBlank() -> Text("Enter your server's realmlist in Server.", style = Fw.Secondary.copy(color = Fw.Warn))
            // The Battle.net login below is for Blizzard versions only.
            launcher.isPrivate -> Unit
            launcher.filesMissing -> Text("Download the game, or find the copy already on this device.", style = Fw.Secondary)
            version?.isOutdated == true -> {
                Text("Live servers need ${version.remoteVersion}. Press A to update.", style = Fw.Secondary.copy(color = Fw.Warn))
                FwButton("Launch anyway", launcher::launchGame, kind = FwButtonKind.Ghost)
            }
            !launcher.hasSavedLogin -> Text("No saved login: you'll sign in inside the game.", style = Fw.Secondary)
        }
    }
}

@Composable
private fun SmallTiles(
    launcher: LauncherController,
    addonUpdates: Int?,
    addonCount: Int?,
    onOpenAddons: () -> Unit,
    onOpenGame: () -> Unit,
    onOpenClient: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Private servers: Server and Client on top, Addons below.
        if (launcher.isPrivate) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FwTile("Server", Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.ASHENVALE, onClick = onOpenGame) {
                    Text(
                        if (launcher.servers.size > 1) "${launcher.selectedServer.name} · ${launcher.servers.size} servers" else launcher.legacy.realmlist.ifBlank { "No server set" },
                        style = Fw.Secondary,
                        maxLines = 1,
                    )
                }
                FwTile("Client", Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.CAMP, onClick = onOpenClient) {
                    Text(if (launcher.filesMissing) "Not set up" else launcher.legacy.renderer.label, style = Fw.Secondary, maxLines = 1)
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FwTile("Addons", Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.INTERFACE, badge = addonUpdates?.takeIf { it > 0 }?.toString(), onClick = onOpenAddons) {
                Text(
                    when {
                        addonCount == null -> "Browse and install addons"
                        addonUpdates != null && addonUpdates > 0 -> "$addonCount installed · $addonUpdates updates"
                        else -> "$addonCount installed"
                    },
                    style = Fw.Secondary,
                )
            }
            if (!launcher.isPrivate) {
                FwTile("Game files", Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.ASHENVALE, onClick = onOpenGame) {
                    Text(if (launcher.filesMissing) "Not installed" else launcher.files.product?.displayName.orEmpty(), style = Fw.Secondary)
                    if (launcher.canConvertToLaunch) Text("Launch version available", style = Fw.Secondary.copy(color = Fw.Brass))
                }
            }
        }
    }
}
