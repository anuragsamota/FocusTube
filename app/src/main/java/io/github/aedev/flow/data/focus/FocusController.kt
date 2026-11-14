package io.github.aedev.flow.data.focus

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.CreateResult
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileAvatar
import io.github.aedev.flow.data.profile.ProfileRepository
import io.github.aedev.flow.data.profile.ProfileSwitcher
import io.github.aedev.flow.data.profile.SlicePart
import io.github.aedev.flow.data.profile.SwitchRefusal
import io.github.aedev.flow.data.profile.SwitchResult
import io.github.aedev.flow.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that starts focus sessions and moves between profiles for them, so the launch picker and the top bar
 * switcher follow the same rules. Each profile is a focus profile: its rules live on it in the registry, and choosing
 * another profile restarts the app into it with the session already started. Whether this launch's question has been
 * answered lives only in memory: every new process asks again, unless a locked session or one chosen moments ago
 * carries on ([FocusPolicy.asksAtLaunch]).
 */
@Singleton
class FocusController
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val store: FocusStore,
        private val profiles: ProfileRepository,
        // Lazy: the switcher brings every engine a switch shuts down, which the app's start shouldn't wait for.
        private val switcher: dagger.Lazy<ProfileSwitcher>,
        tagging: FocusTagging,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val mutex = Mutex()

        private val pickerState = MutableStateFlow<Boolean?>(null)

        /** The profile this process runs as. It never changes in a process: a switch restarts the app. */
        val runningId: String = ActiveProfile.get(context).id

        /** Every profile's focus rules, in the registry's order. */
        val modes: Flow<List<FocusMode>> = profiles.state.map { state -> state.profiles.map(Profile::focusMode) }.distinctUntilChanged()

        val session: Flow<FocusSession?> = store.session
        val tally: Flow<FocusDayTally?> = store.tally

        /** Null until the stored session has been read, then whether the picker still has to ask this launch. */
        val needsPicker: StateFlow<Boolean?> = pickerState.asStateFlow()

        private val limitNotice = MutableStateFlow<FocusLimitNotice?>(null)

        /** The profile whose daily allowance just ran out, for the picker to say so; null otherwise. */
        val limitReached: StateFlow<FocusLimitNotice?> = limitNotice.asStateFlow()

        private val refusal = MutableStateFlow<FocusSwitchRefused?>(null)

        /** Why the last move to another profile didn't happen; null while there is nothing to say. */
        val refused: StateFlow<FocusSwitchRefused?> = refusal.asStateFlow()

        init {
            // Every feed filters by the running profile's rules, from the first launch on and after each edit.
            scope.launch {
                modes
                    .map { all -> all.firstOrNull { it.id == runningId } }
                    .distinctUntilChanged()
                    .collect(FocusGate::setMode)
            }
            scope.launch { tagging.warmUp() }
        }

        /** Reads the stored session once per process and decides whether this launch asks. */
        suspend fun settleLaunch(now: Long = System.currentTimeMillis()) {
            mutex.withLock {
                if (pickerState.value != null) return
                pickerState.value = FocusPolicy.asksAtLaunch(store.session.first(), runningId, now)
            }
        }

        /**
         * Starts a session in [profileId], from the picker or the switcher. For the running profile that is all; for
         * another one the app restarts into it. Returns false when the running profile's minimum time still holds
         * and the emergency exit hasn't opened, when [profileId] has nothing left today, or when the switch was
         * refused ([refused] says why).
         */
        suspend fun choose(
            profileId: String,
            commitMinutes: Int = FocusPolicy.DEFAULT_COMMITMENT_MINUTES,
            now: Long = System.currentTimeMillis(),
        ): Boolean {
            var previous: FocusSession? = null
            mutex.withLock {
                val mode = modes.first().firstOrNull { it.id == profileId } ?: return false
                if (!FocusPolicy.mayStartVideo(mode, todayTally(now))) return false
                val current = store.session.first()
                if (profileId == runningId && current?.modeId == profileId && !FocusPolicy.asksAtLaunch(current, runningId, now)) {
                    finishLaunch()
                    return true
                }
                val leaving = current?.takeIf { it.modeId == runningId }
                val outcome = FocusPolicy.switchTo(leaving, mode, now, commitMinutes) ?: return false
                previous = current
                store.setSession(outcome.session)
                if (outcome.emergency) store.setTally(FocusPolicy.addEmergencyExit(todayTally(now)))
                limitNotice.value = null
                refusal.value = null
                if (profileId == runningId) {
                    finishLaunch()
                    return true
                }
            }
            // Outside the lock: a switch that goes ahead never returns, it restarts the process.
            val result =
                try {
                    switcher.get().switchTo(profileId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            if (result is SwitchResult.Restarting) return true
            mutex.withLock { store.setSession(previous) }
            refusal.value = FocusSwitchRefused((result as? SwitchResult.Refused)?.reason)
            return false
        }

        /**
         * Creates a profile from the template [preset] with this profile's settings and subscriptions, so its feed
         * has channels to start from while its recommendations learn on their own. Returns its id, or null when the
         * profile limit is reached or the copy failed.
         */
        suspend fun create(
            preset: String,
            name: String,
            colorKey: String,
            emoji: String,
        ): String? {
            val result =
                profiles.create(
                    name = name,
                    colorKey = colorKey,
                    avatar = ProfileAvatar(kind = ProfileAvatar.EMOJI, emoji = emoji),
                    copy = setOf(SlicePart.SETTINGS, SlicePart.SUBSCRIPTIONS),
                    focus = FocusPresets.template(preset),
                )
            return (result as? CreateResult.Created)?.profile?.id
        }

        /** Flags [video] as not belonging in the running profile, which then hides it, and its channel once flagged twice. */
        suspend fun flag(video: Video): Boolean =
            mutex.withLock {
                val profile =
                    profiles.state
                        .first()
                        .profiles
                        .firstOrNull { it.id == runningId } ?: return false
                val flagged = FocusFlags.flag(profile.focus, video.id, video.channelId)
                flagged != profile.focus && profiles.setFocus(runningId, flagged)
            }

        fun consumeRefused() {
            refusal.value = null
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

        /** Books [playedMs] of playing on [modeId] today and returns where that profile now stands. */
        suspend fun addPlayed(
            modeId: String,
            playedMs: Long,
            now: Long = System.currentTimeMillis(),
        ): FocusDayTally =
            mutex.withLock {
                FocusPolicy.addPlayed(todayTally(now), modeId, playedMs).also { store.setTally(it) }
            }

        /**
         * Counts a video started in [mode]. Returns false, counting nothing, when the profile's daily allowance is
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

/** A profile's focus rules, named after it. */
fun Profile.focusMode(): FocusMode = FocusMode.of(id = id, name = name, focus = focus)

/** Which profile ran out, and whether it was the video count rather than the time. */
data class FocusLimitNotice(
    val modeId: String,
    val byVideos: Boolean,
)

/** A move to another profile that didn't happen, and why, when the switcher said. */
data class FocusSwitchRefused(
    val reason: SwitchRefusal?,
)
