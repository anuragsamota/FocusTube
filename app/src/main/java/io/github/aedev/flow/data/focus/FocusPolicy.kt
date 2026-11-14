package io.github.aedev.flow.data.focus

/** Whether the viewer may leave the current mode now. */
sealed interface SwitchDecision {
    data object Allowed : SwitchDecision

    /** A productive mode's minimum time hasn't passed; [remainingMs] of it is left. */
    data class Locked(
        val remainingMs: Long,
    ) : SwitchDecision
}

/** A switch that went ahead: the new session, and whether it took the emergency exit. */
data class SwitchOutcome(
    val session: FocusSession,
    val emergency: Boolean,
)

/** Where a leisure mode stands against its daily limits today. */
sealed interface LimitState {
    /** Still within the limits. Null means that limit isn't set. */
    data class Within(
        val remainingMs: Long?,
        val remainingVideos: Int?,
    ) : LimitState

    data object TimeReached : LimitState

    data object VideosReached : LimitState
}

/**
 * The rules of focus modes, kept free of Android so they can be tested on their own: which mode to suggest at launch,
 * the minimum-time lock and its emergency exit, and the daily limits of leisure modes.
 */
object FocusPolicy {
    private const val MILLIS_PER_MINUTE = 60_000L

    /** The minimum times the launch picker offers for a productive mode, in minutes. */
    val commitmentChoices: List<Int> = listOf(15, 30, 45, 60, 90, 120)
    const val DEFAULT_COMMITMENT_MINUTES = 30
    const val MIN_COMMITMENT_MINUTES = 5
    const val MAX_COMMITMENT_MINUTES = 4 * 60

    /** How long the emergency exit must be held before it lets the viewer out of a locked mode. */
    const val EXIT_HOLD_MS = 60_000L

    /** [minutes] clamped to what a commitment may be. */
    fun validCommitment(minutes: Int): Int = minutes.coerceIn(MIN_COMMITMENT_MINUTES, MAX_COMMITMENT_MINUTES)

    /**
     * The profile the launch picker pre-selects at [isoDay] (1 is Monday) and [minuteOfDay]: the first whose schedule
     * covers that moment, else the first that restricts nothing, else the first open one with no lock, else the first.
     */
    fun suggest(
        modes: List<FocusMode>,
        isoDay: Int,
        minuteOfDay: Int,
    ): FocusMode? =
        modes.firstOrNull { mode -> mode.schedule.any { covers(it, isoDay, minuteOfDay) } }
            ?: modes.firstOrNull { !it.restricts }
            ?: modes.firstOrNull { it.allowsEverything && !it.lock }
            ?: modes.firstOrNull()

    /** Whether [window] covers [minuteOfDay] on [isoDay]. A window past midnight belongs to the day it starts on. */
    fun covers(
        window: FocusWindow,
        isoDay: Int,
        minuteOfDay: Int,
    ): Boolean {
        val start = window.startMinute
        val end = window.endMinute
        if (start == end) return false
        if (start < end) return isoDay in window.days && minuteOfDay in start until end
        val previousDay = if (isoDay == 1) 7 else isoDay - 1
        return (isoDay in window.days && minuteOfDay >= start) || (previousDay in window.days && minuteOfDay < end)
    }

    /** A new session in [mode] from [now]. Only a mode with a lock takes [commitMinutes]; any other has none. */
    fun start(
        mode: FocusMode,
        now: Long,
        commitMinutes: Int = DEFAULT_COMMITMENT_MINUTES,
    ): FocusSession {
        val lockMs = if (mode.lock) validCommitment(commitMinutes) * MILLIS_PER_MINUTE else 0L
        return FocusSession(modeId = mode.id, startedAt = now, committedUntil = now + lockMs)
    }

    /**
     * Whether [session] may be left at [now]. A clock moved back can't stretch the lock: it never runs longer than
     * the commitment that was chosen when the session started.
     */
    fun canSwitch(
        session: FocusSession?,
        now: Long,
    ): SwitchDecision {
        if (session == null) return SwitchDecision.Allowed
        val chosenMs = (session.committedUntil - session.startedAt).coerceAtLeast(0L)
        val remaining = (session.committedUntil - now).coerceIn(0L, chosenMs)
        return if (remaining > 0L) SwitchDecision.Locked(remaining) else SwitchDecision.Allowed
    }

    /** The session with the emergency exit started at [now], unless it is already being held. */
    fun beginExitHold(
        session: FocusSession,
        now: Long,
    ): FocusSession = if (session.exitHoldStartedAt != null) session else session.copy(exitHoldStartedAt = now)

    fun cancelExitHold(session: FocusSession): FocusSession = session.copy(exitHoldStartedAt = null)

    /** How much of the emergency exit's hold is left at [now]; 0 means it has opened. Null when it isn't held. */
    fun exitHoldRemainingMs(
        session: FocusSession,
        now: Long,
    ): Long? {
        val started = session.exitHoldStartedAt ?: return null
        val held = (now - started).coerceAtLeast(0L)
        return (EXIT_HOLD_MS - held).coerceAtLeast(0L)
    }

    /** True when the viewer may switch at [now], by the lock running out or the emergency exit opening. */
    fun mayLeave(
        session: FocusSession?,
        now: Long,
    ): Boolean {
        if (session == null || canSwitch(session, now) == SwitchDecision.Allowed) return true
        return exitHoldRemainingMs(session, now) == 0L
    }

    /**
     * Whether the launch picker should ask at [now] in the profile [runningId]. A locked session still inside its
     * minimum time carries on without asking, so closing and reopening the app is no way out of it. A session the
     * viewer just started in this profile carries on too: choosing another profile restarts the app into it, and the
     * question was already answered. Anything else asks again.
     */
    fun asksAtLaunch(
        session: FocusSession?,
        runningId: String,
        now: Long,
    ): Boolean {
        if (session == null || session.modeId != runningId) return true
        if (canSwitch(session, now) != SwitchDecision.Allowed) return false
        return now - session.startedAt !in 0L until FRESH_SESSION_MS
    }

    /** How long a session chosen at the picker counts as this launch's answer, across the restart into it. */
    const val FRESH_SESSION_MS = 2 * 60_000L

    /**
     * The session that switching to [target] at [now] leaves behind, or null when the viewer may not leave yet.
     * [emergency] is true when the lock was still running and only the emergency exit let them out, which the stats
     * count.
     */
    fun switchTo(
        current: FocusSession?,
        target: FocusMode,
        now: Long,
        commitMinutes: Int = DEFAULT_COMMITMENT_MINUTES,
    ): SwitchOutcome? {
        if (!mayLeave(current, now)) return null
        val emergency = current != null && canSwitch(current, now) != SwitchDecision.Allowed
        return SwitchOutcome(start(target, now, commitMinutes), emergency)
    }

    /** The tally to count on for [today]. As with the daily limit, a clock moved back keeps counting the stored day. */
    fun rollover(
        tally: FocusDayTally?,
        today: Long,
    ): FocusDayTally = if (tally == null || today > tally.day) FocusDayTally(today) else tally

    fun addPlayed(
        tally: FocusDayTally,
        modeId: String,
        playedMs: Long,
    ): FocusDayTally {
        val added = playedMs.coerceAtLeast(0L)
        if (added == 0L) return tally
        return tally.copy(playedMs = tally.playedMs + (modeId to (tally.playedMs[modeId] ?: 0L) + added))
    }

    fun addVideo(
        tally: FocusDayTally,
        modeId: String,
    ): FocusDayTally = tally.copy(videos = tally.videos + (modeId to (tally.videos[modeId] ?: 0) + 1))

    fun addEmergencyExit(tally: FocusDayTally): FocusDayTally = tally.copy(emergencyExits = tally.emergencyExits + 1)

    /**
     * Where [mode] stands against its daily limits in [tally]. A mode with a lock is always within: its limit is the
     * lock, not an allowance. Time is checked before the video count.
     */
    fun limitState(
        mode: FocusMode,
        tally: FocusDayTally,
    ): LimitState {
        if (mode.lock) return LimitState.Within(remainingMs = null, remainingVideos = null)
        val remainingMs =
            mode.dailyMinutes?.let { minutes ->
                (minutes * MILLIS_PER_MINUTE - (tally.playedMs[mode.id] ?: 0L)).coerceAtLeast(0L)
            }
        if (remainingMs == 0L) return LimitState.TimeReached
        val remainingVideos = mode.dailyVideos?.let { (it - (tally.videos[mode.id] ?: 0)).coerceAtLeast(0) }
        if (remainingVideos == 0) return LimitState.VideosReached
        return LimitState.Within(remainingMs, remainingVideos)
    }

    /** Whether a new video may start in [mode]. Starting one is what the video limit counts. */
    fun mayStartVideo(
        mode: FocusMode,
        tally: FocusDayTally,
    ): Boolean = limitState(mode, tally) is LimitState.Within
}
