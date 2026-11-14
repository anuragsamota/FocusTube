package io.github.aedev.flow.ui.components.focus

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.focus.FocusMode
import io.github.aedev.flow.data.focus.FocusPolicy
import io.github.aedev.flow.data.focus.FocusPresets
import io.github.aedev.flow.ui.components.profiles.switchRefusalText
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import java.time.LocalDateTime

private val ContentMaxWidth = 480.dp

/**
 * What Focus Tube asks before it shows anything: which profile the viewer is here for. The one that fits the time of
 * day is marked; a profile with a lock then asks how long to stay, and one that isn't running restarts the app into
 * it. New focus profiles are made from a template right here. Back leaves the app rather than reaching the feed.
 */
@Composable
internal fun FocusPickerScreen(
    state: FocusUiState,
    onChoose: (modeId: String, commitMinutes: Int) -> Unit,
    onCreate: (preset: String, name: String, onCreated: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = state.modes
    val context = LocalContext.current
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFailed by rememberSaveable { mutableStateOf(false) }
    val pending = modes.firstOrNull { it.id == pendingId }
    val pick: (FocusMode) -> Unit = { mode -> if (mode.lock) pendingId = mode.id else onChoose(mode.id, 0) }
    val newName = presetNames()

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (pending == null) {
                    ModeList(state = state, onPick = pick)
                    NewProfiles(
                        failed = createFailed,
                        onCreate = { preset ->
                            createFailed = false
                            onCreate(preset, newName.getValue(preset)) { id ->
                                when {
                                    id == null -> createFailed = true
                                    FocusPresets.template(preset).lock -> pendingId = id
                                    else -> onChoose(id, 0)
                                }
                            }
                        },
                    )
                } else {
                    CommitmentChooser(
                        mode = pending,
                        onStart = { minutes -> onChoose(pending.id, minutes) },
                        onBack = { pendingId = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeList(
    state: FocusUiState,
    onPick: (FocusMode) -> Unit,
) {
    val modes = state.modes
    val suggested =
        remember(modes, state.today) {
            val now = LocalDateTime.now()
            FocusPolicy.suggest(modes.filter(state::canEnter), now.dayOfWeek.value, now.hour * 60 + now.minute)
        }
    state.limitReached?.let { notice ->
        modes.firstOrNull { it.id == notice.modeId }?.let { spent -> LimitNotice(spent, notice.byVideos) }
    }
    state.refused?.let { refused -> Notice(stringResource(switchRefusalText(refused.reason))) }
    Text(stringResource(R.string.focus_picker_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.focus_picker_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModeRows(
        modes = modes,
        highlightId = suggested?.id,
        highlightText = stringResource(R.string.focus_picker_suggested),
        onPick = onPick,
        isEnabled = state::canEnter,
    )
}

@Composable
private fun LimitNotice(
    mode: FocusMode,
    byVideos: Boolean,
) {
    Notice(
        stringResource(
            if (byVideos) R.string.focus_limit_reached_videos else R.string.focus_limit_reached_time,
            mode.displayName(),
        ),
    )
}

/** The templates a new profile can start from, each one tap away. */
@Composable
private fun NewProfiles(
    failed: Boolean,
    onCreate: (preset: String) -> Unit,
) {
    Text(stringResource(R.string.focus_new_title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.focus_new_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (failed) Notice(stringResource(R.string.focus_new_failed))
    val templates = FocusPresets.templates
    FlowRowGroup {
        templates.forEachIndexed { index, template ->
            FlowNavRow(
                title = presetName(template.preset),
                supportingText = FocusMode.of(id = template.preset, name = "", focus = template).summary(),
                leadingIcon = presetIcon(template.preset),
                showChevron = false,
                shape = flowRowGroupShape(index, templates.size),
                onClick = { onCreate(template.preset) },
            )
        }
    }
}

/** Every template's starting name, read while composing so a callback can use it. */
@Composable
private fun presetNames(): Map<String, String> = FocusPresets.templates.associate { it.preset to presetName(it.preset) }

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** The profiles as one group of rows; [highlightId]'s row is selected and carries [highlightText]. */
@Composable
internal fun ModeRows(
    modes: List<FocusMode>,
    highlightId: String?,
    highlightText: String?,
    onPick: (FocusMode) -> Unit,
    isEnabled: (FocusMode) -> Boolean = { true },
) {
    FlowRowGroup {
        modes.forEachIndexed { index, mode ->
            FlowNavRow(
                title = mode.displayName(),
                supportingText = if (isEnabled(mode)) mode.summary() else stringResource(R.string.focus_spent_today),
                leadingIcon = mode.iconVector(),
                trailingText = highlightText.takeIf { mode.id == highlightId },
                selected = mode.id == highlightId,
                enabled = isEnabled(mode),
                showChevron = mode.lock,
                shape = flowRowGroupShape(index, modes.size),
                onClick = { onPick(mode) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommitmentChooser(
    mode: FocusMode,
    onStart: (minutes: Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var minutes by rememberSaveable { mutableIntStateOf(FocusPolicy.DEFAULT_COMMITMENT_MINUTES) }
    Text(
        stringResource(R.string.focus_commit_title, mode.displayName()),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        stringResource(R.string.focus_commit_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusPolicy.commitmentChoices.forEach { choice ->
            FilterChip(
                selected = choice == minutes,
                onClick = { minutes = choice },
                label = { Text(stringResource(R.string.focus_minutes, choice)) },
            )
        }
    }
    Button(onClick = { onStart(minutes) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.focus_start))
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.focus_back))
    }
}
