package app.gamenative.wow.ui

import android.view.KeyEvent as AndroidKey
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.wow.legacy.LegacyGame
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.profile.Profile
import app.gamenative.wow.profile.Profiles
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.FwTextField
import app.gamenative.wow.ui.components.GameIconImage
import app.gamenative.wow.ui.components.icon
import app.gamenative.wow.ui.theme.Fw

/** True for a key press of [code] on a controller or keyboard. */
internal fun androidx.compose.ui.input.key.KeyEvent.pressed(code: Int) = type == KeyEventType.KeyDown && nativeKeyEvent.keyCode == code

/** The active profile at the left of the home top bar. Opens the profile list. */
@Composable
fun ProfileButton(launcher: LauncherController, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val profile = launcher.profile
    Row(
        modifier
            .border(2.dp, if (focused) Fw.Brass else Color.Transparent, Fw.Shape)
            .background(if (focused) Fw.Stone2 else Fw.Stone, Fw.Shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameIconImage(launcher.version.icon, 30.dp) { Box(Modifier.size(30.dp)) }
        Column(Modifier.widthIn(max = 360.dp)) {
            Text(profile?.name ?: "No profile", style = Fw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(launcher.version.displayName, style = Fw.Data, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = if (focused) Fw.Brass else Fw.Ash)
    }
}

/** Every profile, to switch to, edit or add one. */
@Composable
fun ProfileMenu(launcher: LauncherController, onDismiss: () -> Unit, onNew: () -> Unit) {
    var editing by remember { mutableStateOf<Profile?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            Modifier.fillMaxWidth(0.9f).widthIn(max = 680.dp).background(Fw.Stone, RoundedCornerShape(20.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Profiles", style = Fw.Title)
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                launcher.profiles.forEachIndexed { index, p ->
                    val current = p.id == launcher.profile?.id
                    FwRow(
                        selected = false,
                        onClick = { if (launcher.switchProfile(p.id)) onDismiss() },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .onPreviewKeyEvent { e -> e.pressed(AndroidKey.KEYCODE_BUTTON_Y).also { if (it) editing = p } },
                    ) {
                        GameIconImage(p.gameVersion.icon, 28.dp) { Box(Modifier.size(28.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = Fw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                p.gameVersion.displayName + if (p.root.isBlank()) " · not set up" else "",
                                style = Fw.Data,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (current) Text("Current", style = Fw.Data.copy(color = Fw.Brass))
                    }
                }
            }
            FwRow(selected = false, onClick = onNew, modifier = if (launcher.profiles.isEmpty()) Modifier.focusRequester(first) else Modifier) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Fw.Brass, modifier = Modifier.size(28.dp))
                Text("New profile", style = Fw.Text)
            }
            launcher.switchBlocked?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
            Text("A  Switch      Y  Edit      B  Close      L1 R1  Switch from the home screen", style = Fw.Data)
        }
    }
    editing?.let { p -> ProfileEditDialog(launcher, p, onDismiss = { editing = null }) }
}

@Composable
private fun ProfileEditDialog(launcher: LauncherController, profile: Profile, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = profile.id == launcher.profile?.id
    if (confirmDelete) {
        FwDialog(
            title = "Delete ${profile.name}?",
            message = "The game files stay on storage. This profile's server list, saved server passwords and addon choices are removed.",
            confirm = "Delete",
            onConfirm = {
                launcher.deleteProfile(profile.id)
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
        return
    }
    FwDialog(
        title = "Edit profile",
        message = profile.gameVersion.displayName + profile.root.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty(),
        confirm = "Save",
        onConfirm = {
            launcher.renameProfile(profile.id, name)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        FwTextField(name, { name = it }, "Name", Modifier.fillMaxWidth())
        if (current) {
            Text("Switch to another profile to delete this one.", style = Fw.Secondary)
        } else {
            FwButton("Delete profile", { confirmDelete = true }, kind = FwButtonKind.Danger)
        }
    }
}

/** Step 1 of a new profile: the game version and a name. Step 2 is the version's own setup. */
@Composable
fun NewProfileScreen(launcher: LauncherController, onCreated: (GameVersion) -> Unit, onBack: () -> Unit) {
    var version by remember { mutableStateOf<GameVersion?>(if (launcher.profiles.isEmpty()) GameVersion.FOREVER else null) }
    var name by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    FwPage("New profile", onBack) {
        FwSection("Game version") {
            Text("Pick the game this profile runs. The app is made for WoW Forever; the other versions are experimental.", style = Fw.Secondary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameVersion.entries.forEachIndexed { index, v ->
                    FwRow(
                        selected = v == version,
                        onClick = { version = v },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        GameIconImage(v.icon, 24.dp) { Box(Modifier.size(24.dp)) }
                        Text(v.displayName, style = Fw.Text, modifier = Modifier.weight(1f))
                        if (v.isExperimental) Text("Experimental", style = Fw.Data.copy(color = Fw.Ash))
                        if (v == version) Text("Selected", style = Fw.Data.copy(color = Fw.Brass))
                    }
                }
            }
        }
        FwSection("Name") {
            FwTextField(
                name,
                { name = it },
                "Name",
                Modifier.fillMaxWidth(),
                placeholder = version?.let { Profiles.freeName(it.displayName) } ?: "Pick a version first",
            )
            Text("Leave it empty to use the version's name.", style = Fw.Secondary)
        }
        version?.let { v ->
            Text(
                if (v.isPrivateServer) {
                    "Next, choose your ${LegacyGame.of(v).label} client folder and add your servers. The folder can be one another profile uses: each profile keeps its own addons, client patches and servers."
                } else {
                    "Next, download ${v.displayName}, or pick a copy already on this device. A copy another profile uses works too: each profile keeps its own addons."
                },
                style = Fw.Secondary,
            )
        }
        launcher.switchBlocked?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
        FwButton(
            "Create profile",
            {
                val v = version ?: return@FwButton
                if (launcher.createProfile(name, v)) onCreated(v)
            },
            enabled = version != null && launcher.switchBlocked == null,
        )
    }
}

/** Under a folder that another profile also uses: what stays separate and what doesn't. */
@Composable
fun SharedFolderNote(launcher: LauncherController) {
    val others = launcher.profile?.let { Profiles.sharers(it) }.orEmpty().takeIf { it.isNotEmpty() } ?: return
    val names = others.joinToString(", ") { it.name }
    Text(
        "Also used by $names. Each profile keeps its own addons${if (launcher.isPrivate) ", client patches and servers" else ""}. " +
            "The files are shared, so updating an addon updates it for both.",
        style = Fw.Secondary,
    )
}
