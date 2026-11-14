package io.github.aedev.flow.ui.components.focus

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.focus.FocusController
import io.github.aedev.flow.data.focus.FocusDayTally
import io.github.aedev.flow.data.focus.FocusLimitNotice
import io.github.aedev.flow.data.focus.FocusMode
import io.github.aedev.flow.data.focus.FocusPolicy
import io.github.aedev.flow.data.focus.FocusSession
import io.github.aedev.flow.data.focus.FocusSwitchRefused
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Every profile's focus rules and the running session, as the picker and the switcher show them. */
@Immutable
data class FocusUiState(
    val modes: List<FocusMode>,
    val session: FocusSession?,
    val today: FocusDayTally,
    val runningId: String,
    val limitReached: FocusLimitNotice? = null,
    val refused: FocusSwitchRefused? = null,
) {
    /** The profile this app runs as. */
    val current: FocusMode? get() = modes.firstOrNull { it.id == runningId }

    /** The running session, when it belongs to this profile; one left from another profile holds nothing here. */
    val runningSession: FocusSession? get() = session?.takeIf { it.modeId == runningId }

    /** Whether [mode] can be entered now: a profile can't once today's allowance is spent. */
    fun canEnter(mode: FocusMode): Boolean = FocusPolicy.mayStartVideo(mode, today)
}

/** The launch picker's and the top bar switcher's way into [FocusController]. */
@HiltViewModel
class FocusViewModel
    @Inject
    constructor(
        private val controller: FocusController,
    ) : ViewModel() {
        val state: StateFlow<FocusUiState?> =
            combine(
                controller.modes,
                controller.session,
                controller.tally,
                controller.limitReached,
                controller.refused,
            ) { modes, session, tally, notice, refused ->
                FocusUiState(
                    modes = modes,
                    session = session,
                    today = FocusPolicy.rollover(tally, LocalDate.now().toEpochDay()),
                    runningId = controller.runningId,
                    limitReached = notice,
                    refused = refused,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Null until known, then whether this launch still has to pick a mode. */
        val needsPicker: StateFlow<Boolean?> = controller.needsPicker

        init {
            viewModelScope.launch { controller.settleLaunch() }
        }

        private var busy = false

        /** Starts a session in the profile [modeId], restarting into it when it isn't this one. */
        fun choose(
            modeId: String,
            commitMinutes: Int,
            onDone: (Boolean) -> Unit = {},
        ) {
            if (busy) return
            busy = true
            viewModelScope.launch {
                val done = controller.choose(modeId, commitMinutes)
                busy = false
                onDone(done)
            }
        }

        /** Makes a profile from the template [preset]; [onCreated] hears its id, or null when it couldn't be made. */
        fun create(
            preset: String,
            name: String,
            onCreated: (String?) -> Unit,
        ) {
            if (busy) return
            busy = true
            viewModelScope.launch {
                val look = FocusPresetLooks.of(preset)
                val id = controller.create(preset, name, look.colorKey, look.emoji)
                busy = false
                onCreated(id)
            }
        }

        fun consumeRefused() = controller.consumeRefused()

        fun beginExitHold() {
            viewModelScope.launch { controller.beginExitHold() }
        }

        fun cancelExitHold() {
            viewModelScope.launch { controller.cancelExitHold() }
        }
    }
