package io.github.aedev.flow.ui.components.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.focus.FocusMode
import io.github.aedev.flow.data.focus.FocusPolicy
import io.github.aedev.flow.data.focus.FocusSession
import io.github.aedev.flow.data.focus.SwitchDecision
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionsSheet

private val TopBarIconSize = 24.dp
private val SheetPadding = 20.dp

/** The current focus mode as a top bar action, opening the switcher. Hidden until a mode is chosen. */
@Composable
fun FocusTopBarAction(viewModel: FocusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state?.current ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.focus_action_description, current.displayName())

    IconButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(current.iconVector(), contentDescription = null, modifier = Modifier.size(TopBarIconSize))
    }

    if (open) {
        FocusSwitchSheet(
            modes = state?.modes.orEmpty(),
            session = state?.session,
            onChoose = { mode, minutes -> viewModel.choose(mode.id, minutes) { switched -> if (switched) open = false } },
            onBeginExit = viewModel::beginExitHold,
            onCancelExit = viewModel::cancelExitHold,
            onDismiss = { open = false },
        )
    }
}

/**
 * Switching modes. While a productive mode's minimum time runs, the other modes are shown but can't be picked; the
 * emergency exit unlocks them after [FocusPolicy.EXIT_HOLD_MS] spent on this sheet, and closing the sheet starts that
 * wait over.
 */
@Composable
internal fun FocusSwitchSheet(
    modes: List<FocusMode>,
    session: FocusSession?,
    onChoose: (FocusMode, Int) -> Unit,
    onBeginExit: () -> Unit,
    onCancelExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now by rememberNow()
    val latestCancel by rememberUpdatedState(onCancelExit)
    DisposableEffect(Unit) { onDispose { latestCancel() } }
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = modes.firstOrNull { it.id == pendingId }
    val current = session?.let { s -> modes.firstOrNull { it.id == s.modeId } }
    val lock = FocusPolicy.canSwitch(session, now) as? SwitchDecision.Locked
    val exitRemaining = session?.let { FocusPolicy.exitHoldRemainingMs(it, now) }
    val mayLeave = FocusPolicy.mayLeave(session, now)

    QuickActionsSheet(onDismiss = onDismiss) { _ ->
        if (pending != null) {
            Column(Modifier.padding(horizontal = SheetPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CommitmentChooser(
                    mode = pending,
                    onStart = { minutes -> onChoose(pending, minutes) },
                    onBack = { pendingId = null },
                )
            }
        } else {
            FlowSectionHeader(text = stringResource(R.string.focus_switch_title))
            if (lock != null && current != null) {
                LockNotice(current, lock.remainingMs, exitRemaining, onBeginExit)
                Spacer(Modifier.height(16.dp))
            }
            ModeRows(
                modes = modes,
                highlightId = current?.id,
                highlightText = stringResource(R.string.focus_current),
                enabled = mayLeave,
                onPick = { mode ->
                    when {
                        mode.id == current?.id -> onDismiss()
                        mode.isProductive -> pendingId = mode.id
                        else -> onChoose(mode, 0)
                    }
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LockNotice(
    current: FocusMode,
    remainingMs: Long,
    exitRemainingMs: Long?,
    onBeginExit: () -> Unit,
) {
    Column(Modifier.padding(horizontal = SheetPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.focus_locked, current.displayName(), formatCountdown(remainingMs)),
            style = MaterialTheme.typography.bodyLarge,
        )
        when (exitRemainingMs) {
            null ->
                OutlinedButton(onClick = onBeginExit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.focus_emergency_exit))
                }
            0L ->
                Text(
                    stringResource(R.string.focus_emergency_open, current.displayName()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else ->
                Text(
                    stringResource(R.string.focus_emergency_wait, formatCountdown(exitRemainingMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}
