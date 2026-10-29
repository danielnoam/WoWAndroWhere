package app.gamenative.wow.ui.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.wow.legacy.mods.AndroWherePatch
import app.gamenative.wow.legacy.mods.ClientPatch
import app.gamenative.wow.legacy.mods.PatchRuleSet
import app.gamenative.wow.legacy.mods.PatchRules
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwChip
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw

/** The Client tab on private servers: our patches to install or remove (3.3.5a only), and switches for the patches already in the client. */
@Composable
internal fun ClientModsPanel(c: ClientModsController, modifier: Modifier = Modifier) {
    LaunchedEffect(c) { c.refresh() }
    LazyColumn(modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (c.working ?: c.message)?.let { status ->
            item { Text(status, style = Fw.Secondary.copy(color = if (c.working != null) Fw.Brass else Fw.Warn)) }
        }
        if (c.game.hasAndroWherePatches) {
            item { Text("AndroWhere patches", style = Fw.Label.copy(color = Fw.Ash), modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
            item {
                Text(
                    "Made for this app. Install adds one to your client, Remove puts the client back as it was.",
                    style = Fw.Secondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val ours = c.ours
            if (ours == null) {
                item { Text("Checking the client…", style = Fw.Secondary) }
            } else {
                items(AndroWherePatch.entries, key = { it.name }) { patch ->
                    val installed = ours[patch] == true
                    Row(
                        Modifier.fillMaxWidth().background(Fw.Stone, Fw.Shape).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(patch.title, style = Fw.Text)
                            Text(patch.summary, style = Fw.Secondary)
                            Text(if (installed) "Installed" else "Not installed", style = Fw.Data.copy(color = if (installed) Fw.Brass else Fw.Ash))
                        }
                        FwButton(
                            if (installed) "Remove" else "Install",
                            { c.setInstalled(patch, !installed) },
                            kind = if (installed) FwButtonKind.Ghost else FwButtonKind.Primary,
                            enabled = c.working == null,
                        )
                    }
                }
            }
        }
        item { Text("In your client", style = Fw.Label.copy(color = Fw.Ash), modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) }
        val patches = c.patches
        when {
            patches == null -> item { Text("Reading the client's Data folder…", style = Fw.Secondary) }
            patches.isEmpty() -> item { Text("No extra patch files in this client.", style = Fw.Secondary) }
            else -> {
                val names = patches.associate { it.key to it.name }
                items(patches, key = { it.key }) { patch ->
                    Toggle(patch.name, detail(patch, c.rules, names), patch.enabled, { c.setPatch(patch, !patch.enabled) })
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean, onToggle: () -> Unit) {
    FwRow(selected = false, onClick = onToggle) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Fw.Text)
            Text(detail, style = Fw.Secondary)
        }
        FwChip(if (on) "On" else "Off", on, onToggle)
    }
}

/** With [rules], the client is the HD pack and the row also says what the patch does and what it needs. */
private fun detail(patch: ClientPatch, rules: PatchRuleSet?, names: Map<String, String>) =
    listOfNotNull(
        rules?.let { PatchRules.description(patch.key) },
        rules?.let { PatchRules.hint(it, patch.key, names) }?.let { "$it." },
        "Patch ${patch.key}, ${size(patch.size)}",
    ).joinToString(" ")

private fun size(bytes: Long) = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}
