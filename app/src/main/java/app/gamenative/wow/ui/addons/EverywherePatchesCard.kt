package app.gamenative.wow.ui.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gamenative.wow.legacy.mods.EverywherePatch
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.theme.Fw

/** On the 3.3.5a Installed list: offers Controller Menus while it isn't installed. The rest are in the Client tab. */
@Composable
internal fun EverywherePatchesCard(c: AddonsController) {
    val mods = c.clientMods ?: return
    LaunchedEffect(mods) { mods.refresh() }
    if (c.mode != AddonsController.Mode.INSTALLED) return
    if (mods.ours?.get(EverywherePatch.LOGIN) != false) return
    val login = EverywherePatch.LOGIN
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(Fw.Stone, Fw.Shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("WoWEverywhere patch: ${login.title}", style = Fw.Text)
            Text("Use the controller on the login, realm and character screens too. More WoWEverywhere patches are in the Client tab.", style = Fw.Secondary)
        }
        FwButton("Install", { mods.setInstalled(login, true) }, enabled = mods.working == null)
        FwButton("Client tab", { c.switchMode(AddonsController.Mode.CLIENT) }, kind = FwButtonKind.Ghost)
    }
}
