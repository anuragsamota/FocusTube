package io.github.aedev.flow.data.focus

import io.github.aedev.flow.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that starts, switches and leaves focus sessions, so the launch picker and the top bar switcher follow
 * the same rules. Whether this launch's question has been answered lives only in memory: every new process asks
 * again, unless a locked session carries on ([FocusPolicy.asksAtLaunch]).
 */
@Singleton
class FocusController
    @Inject
    constructor(
        private val store: FocusStore,
        tagging: FocusTagging,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val mutex = Mutex()

        private val pickerState = MutableStateFlow<Boolean?>(null)

        val modes: Flow<List<FocusMode>> = store.modes
        val session: Flow<FocusSession?> = store.session
        val tally: Flow<FocusDayTally?> = store.tally

        /** Null until the stored session has been read, then whether the picker still has to ask this launch. */
        val needsPicker: StateFlow<Boolean?> = pickerState.asStateFlow()

        private val limitNotice = MutableStateFlow<FocusLimitNotice?>(null)

        /** The leisure mode whose daily allowance just ran out, for the picker to say so; null otherwise. */
        val limitReached: StateFlow<FocusLimitNotice?> = limitNotice.asStateFlow()

        init {
            // Every feed filters by the mode in force, so it follows the stored session from the first launch on.
            scope.launch {
                combine(store.modes, store.session) { modes, session -> session?.let { s -> modes.firstOrNull { it.id == s.modeId } } }
                    .distinctUntilChanged()
                    .collect(FocusGate::setMode)
            }
            scope.launch { tagging.warmUp() }
        }

        /** Reads the stored session once per process and decides whether this launch asks. */
        suspend fun settleLaunch(now: Long = System.currentTimeMillis()) {
            mutex.withLock {
                if (pickerState.value != null) return
                pickerState.value = FocusPolicy.asksAtLaunch(store.session.first(), now)
            }
        }

        /**
         * Starts [modeId], from the picker or the switcher. Returns false when the current mode's minimum time still
         * holds and the emergency exit hasn't opened, or when [modeId] is a leisure mode with nothing left today.
         */
        suspend fun choose(
            modeId: String,
            commitMinutes: Int = FocusPolicy.DEFAULT_COMMITMENT_MINUTES,
            now: Long = System.currentTimeMillis(),
        ): Boolean =
            mutex.withLock {
                val mode = store.modes.first().firstOrNull { it.id == modeId } ?: return false
                if (!FocusPolicy.mayStartVideo(mode, todayTally(now))) return false
                val current = store.session.first()
                if (current?.modeId == modeId && !FocusPolicy.asksAtLaunch(current, now)) {
                    finishLaunch()
                    return true
                }
                val outcome = FocusPolicy.switchTo(current, mode, now, commitMinutes) ?: return false
                store.setSession(outcome.session)
                if (outcome.emergency) store.setTally(FocusPolicy.addEmergencyExit(todayTally(now)))
                limitNotice.value = null
                finishLaunch()
                true
            }

        suspend fun beginExitHold(now: Long = System.currentTimeMillis()) {
            mutex.withLock {
                val current = store.session.first() ?: return
                store.setSession(FocusPolicy.beginExitHold(current, now))
            }
        }

        suspend fun cancelExitHold() {
            mutex.withLock {
                val current = store.session.first() ?: return
                if (current.exitHoldStartedAt != null) store.setSession(FocusPolicy.cancelExitHold(current))
            }
        }

        /** Today's count, rolled over to today. */
        suspend fun today(now: Long = System.currentTimeMillis()): FocusDayTally = mutex.withLock { todayTally(now) }

        /** Books [playedMs] of playing on [modeId] today and returns where that mode now stands. */
        suspend fun addPlayed(
            modeId: String,
            playedMs: Long,
            now: Long = System.currentTimeMillis(),
        ): FocusDayTally =
            mutex.withLock {
                FocusPolicy.addPlayed(todayTally(now), modeId, playedMs).also { store.setTally(it) }
            }

        /**
         * Counts a video started in [mode]. Returns false, counting nothing, when the mode's daily allowance is
         * already spent: the caller stops the video and [reachLimit]s.
         */
        suspend fun startVideo(
            mode: FocusMode,
            now: Long = System.currentTimeMillis(),
        ): Boolean =
            mutex.withLock {
                val today = todayTally(now)
                if (!FocusPolicy.mayStartVideo(mode, today)) return false
                store.setTally(FocusPolicy.addVideo(today, mode.id))
                true
            }

        /** [mode] ran out for today: the picker comes back, saying why, so the viewer chooses what to do next. */
        fun reachLimit(
            mode: FocusMode,
            state: LimitState,
        ) {
            limitNotice.value = FocusLimitNotice(mode.id, byVideos = state == LimitState.VideosReached)
            pickerState.value = true
        }

        private suspend fun todayTally(now: Long): FocusDayTally {
            val stored = store.tally.first()
            val today = FocusPolicy.rollover(stored, epochDay(now))
            if (today != stored) store.setTally(today)
            return today
        }

        private fun finishLaunch() {
            pickerState.value = false
        }

        private fun epochDay(now: Long): Long =
            Instant
                .ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toEpochDay()
    }

/** Which leisure mode ran out, and whether it was the video count rather than the time. */
data class FocusLimitNotice(
    val modeId: String,
    val byVideos: Boolean,
)
