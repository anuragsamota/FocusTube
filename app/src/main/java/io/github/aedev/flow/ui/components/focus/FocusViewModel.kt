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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The modes and the running session, as the picker and the switcher show them. */
@Immutable
data class FocusUiState(
    val modes: List<FocusMode>,
    val session: FocusSession?,
    val today: FocusDayTally,
    val limitReached: FocusLimitNotice? = null,
) {
    val current: FocusMode? get() = session?.let { s -> modes.firstOrNull { it.id == s.modeId } }

    /** Whether [mode] can be entered now: a leisure mode can't once today's allowance is spent. */
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
            combine(controller.modes, controller.session, controller.tally, controller.limitReached) { modes, session, tally, notice ->
                FocusUiState(modes, session, FocusPolicy.rollover(tally, LocalDate.now().toEpochDay()), notice)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Null until known, then whether this launch still has to pick a mode. */
        val needsPicker: StateFlow<Boolean?> = controller.needsPicker

        init {
            viewModelScope.launch { controller.settleLaunch() }
        }

        /** Starts [modeId]; [onDone] hears whether the switch went ahead. */
        fun choose(
            modeId: String,
            commitMinutes: Int,
            onDone: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch { onDone(controller.choose(modeId, commitMinutes)) }
        }

        fun beginExitHold() {
            viewModelScope.launch { controller.beginExitHold() }
        }

        fun cancelExitHold() {
            viewModelScope.launch { controller.cancelExitHold() }
        }
    }
