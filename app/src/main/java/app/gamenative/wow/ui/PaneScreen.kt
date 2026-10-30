package app.gamenative.wow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.FwTopBar
import app.gamenative.wow.ui.theme.Fw

/** One entry in a [PaneScreen]'s left list, with the hint shown while it's highlighted. */
internal class PaneSpec(val title: String, val hint: String)

/**
 * A settings screen in two panes: the list on the left, the chosen pane's options on the right. Moving
 * through the list shows each pane; right enters it and B goes back to the list ([listBack] false lets a
 * pane handle B itself, e.g. while a sub-page is open). One line at the bottom explains whatever is
 * highlighted, or shows [error].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PaneScreen(
    title: String,
    panes: List<PaneSpec>,
    onBack: () -> Unit,
    initial: Int = 0,
    error: String? = null,
    listBack: Boolean = true,
    onPaneChange: (Int) -> Unit = {},
    content: @Composable ColumnScope.(pane: Int, setHint: (String) -> Unit) -> Unit,
) {
    var pane by remember { mutableIntStateOf(initial) }
    var hint by remember { mutableStateOf(panes[initial].hint) }
    var inPane by remember { mutableStateOf(false) }
    val nav = remember(panes.size) { List(panes.size) { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { nav[pane].requestFocus() } }
    BackHandler(enabled = inPane && listBack) { runCatching { nav[pane].requestFocus() } }
    fun select(index: Int) {
        if (pane != index) {
            pane = index
            onPaneChange(index)
        }
        hint = panes[index].hint
    }

    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        FwTopBar(title, onBack)
        HorizontalDivider(color = Fw.Rule)
        Row(Modifier.weight(1f)) {
            Column(
                Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .padding(16.dp)
                    .focusProperties { enter = { nav[pane] } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                panes.forEachIndexed { index, p ->
                    FwRow(
                        selected = index == pane,
                        // Clickables can't take focus in touch mode, so a tap selects directly.
                        onClick = {
                            select(index)
                            runCatching { nav[index].requestFocus() }
                        },
                        modifier = Modifier
                            .focusRequester(nav[index])
                            .onFocusChanged { if (it.hasFocus) select(index) },
                    ) { Text(p.title, style = Fw.Text) }
                }
            }
            VerticalDivider(color = Fw.Rule)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { inPane = it.hasFocus }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content(pane) { hint = it } }
        }
        HintLine(error ?: hint, error != null)
    }
}

/** A one-column screen with the same hint line at the bottom as [PaneScreen]. */
@Composable
internal fun HintPage(
    title: String,
    onBack: () -> Unit,
    error: String? = null,
    content: @Composable ColumnScope.(setHint: (String) -> Unit) -> Unit,
) {
    var hint by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        FwTopBar(title, onBack)
        HorizontalDivider(color = Fw.Rule)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content { hint = it } }
        HintLine(error ?: hint, error != null)
    }
}

@Composable
private fun HintLine(text: String, warn: Boolean) {
    HorizontalDivider(color = Fw.Rule)
    Text(
        text,
        style = Fw.Secondary.copy(color = if (warn) Fw.Warn else Fw.Ash),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** Shows [text] in a [PaneScreen]'s hint line while this item is highlighted. */
internal fun Modifier.hint(text: String, setHint: (String) -> Unit) = onFocusChanged { if (it.hasFocus) setHint(text) }

/** A row with a checkbox; its explanation goes in the hint line. */
@Composable
internal fun ToggleRow(title: String, checked: Boolean, enabled: Boolean = true, hint: String, setHint: (String) -> Unit, onToggle: () -> Unit) {
    FwRow(selected = false, onClick = { if (enabled) onToggle() }, modifier = Modifier.hint(hint, setHint)) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Fw.Brass, uncheckedColor = Fw.Ash, checkmarkColor = Fw.BrassInk),
        )
        Text(title, style = if (enabled) Fw.Text else Fw.Text.copy(color = Fw.Ash), modifier = Modifier.weight(1f))
    }
}
