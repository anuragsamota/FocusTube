package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusPolicyTest {
    private val minute = 60_000L
    private val now = 1_800_000_000_000L
    private val study = FocusPresets.all.first { it.id == FocusPresets.STUDY }
    private val explore = FocusPresets.all.first { it.id == FocusPresets.EXPLORE }
    private val music = FocusPresets.all.first { it.id == FocusPresets.MUSIC }

    @Test
    fun `the picker suggests the mode scheduled for now, else Explore`() {
        val modes = FocusPresets.all

        assertThat(FocusPolicy.suggest(modes, isoDay = 2, minuteOfDay = 10 * 60)?.id).isEqualTo(FocusPresets.WORK)
        assertThat(FocusPolicy.suggest(modes, isoDay = 2, minuteOfDay = 17 * 60)?.id).isEqualTo(FocusPresets.STUDY)
        assertThat(FocusPolicy.suggest(modes, isoDay = 6, minuteOfDay = 11 * 60)?.id).isEqualTo(FocusPresets.LEARN)
        assertThat(FocusPolicy.suggest(modes, isoDay = 3, minuteOfDay = 3 * 60)?.id).isEqualTo(FocusPresets.EXPLORE)
        assertThat(FocusPolicy.suggest(emptyList(), isoDay = 3, minuteOfDay = 0)).isNull()
    }

    @Test
    fun `a window past midnight belongs to the day it starts on`() {
        val friNight = FocusWindow(days = setOf(5), startMinute = 22 * 60, endMinute = 2 * 60)

        assertThat(FocusPolicy.covers(friNight, isoDay = 5, minuteOfDay = 23 * 60)).isTrue()
        assertThat(FocusPolicy.covers(friNight, isoDay = 6, minuteOfDay = 60)).isTrue()
        assertThat(FocusPolicy.covers(friNight, isoDay = 6, minuteOfDay = 23 * 60)).isFalse()
        assertThat(FocusPolicy.covers(friNight, isoDay = 5, minuteOfDay = 60)).isFalse()
        val sunNight = FocusWindow(days = setOf(7), startMinute = 22 * 60, endMinute = 60)
        assertThat(FocusPolicy.covers(sunNight, isoDay = 1, minuteOfDay = 30)).isTrue()
    }

    @Test
    fun `a productive mode locks switching for the chosen minimum`() {
        val session = FocusPolicy.start(study, now, commitMinutes = 45)

        assertThat(FocusPolicy.canSwitch(session, now + 10 * minute)).isEqualTo(SwitchDecision.Locked(35 * minute))
        assertThat(FocusPolicy.canSwitch(session, now + 45 * minute)).isEqualTo(SwitchDecision.Allowed)
        assertThat(FocusPolicy.canSwitch(null, now)).isEqualTo(SwitchDecision.Allowed)
    }

    @Test
    fun `a leisure mode never locks`() {
        val session = FocusPolicy.start(explore, now, commitMinutes = 90)

        assertThat(session.committedUntil).isEqualTo(now)
        assertThat(FocusPolicy.canSwitch(session, now)).isEqualTo(SwitchDecision.Allowed)
    }

    @Test
    fun `the commitment is clamped and moving the clock back can't lengthen it`() {
        assertThat(FocusPolicy.start(study, now, commitMinutes = 1).committedUntil).isEqualTo(now + 5 * minute)
        assertThat(FocusPolicy.start(study, now, commitMinutes = 1_000).committedUntil).isEqualTo(now + 240 * minute)

        val session = FocusPolicy.start(study, now, commitMinutes = 30)
        assertThat(FocusPolicy.canSwitch(session, now - 600 * minute)).isEqualTo(SwitchDecision.Locked(30 * minute))
    }

    @Test
    fun `the emergency exit opens after a full minute of holding`() {
        val locked = FocusPolicy.start(study, now, commitMinutes = 60)
        assertThat(FocusPolicy.exitHoldRemainingMs(locked, now)).isNull()

        val held = FocusPolicy.beginExitHold(locked, now + minute)
        assertThat(FocusPolicy.beginExitHold(held, now + 2 * minute)).isEqualTo(held)
        assertThat(FocusPolicy.exitHoldRemainingMs(held, now + minute + 20_000L)).isEqualTo(40_000L)
        assertThat(FocusPolicy.mayLeave(held, now + minute + 59_000L)).isFalse()
        assertThat(FocusPolicy.mayLeave(held, now + 2 * minute)).isTrue()
        assertThat(FocusPolicy.mayLeave(FocusPolicy.cancelExitHold(held), now + 2 * minute)).isFalse()
        assertThat(FocusPolicy.mayLeave(locked, now + 60 * minute)).isTrue()
    }

    @Test
    fun `a new day starts from nothing, a clock moved back keeps the stored day`() {
        val day = 20_000L
        val tally = FocusPolicy.addVideo(FocusDayTally(day), explore.id)

        assertThat(FocusPolicy.rollover(tally, day + 1)).isEqualTo(FocusDayTally(day + 1))
        assertThat(FocusPolicy.rollover(tally, day - 1)).isEqualTo(tally)
        assertThat(FocusPolicy.rollover(null, day)).isEqualTo(FocusDayTally(day))
    }

    @Test
    fun `time and videos are counted per mode`() {
        var tally = FocusDayTally(1L)
        tally = FocusPolicy.addPlayed(tally, explore.id, 10 * minute)
        tally = FocusPolicy.addPlayed(tally, explore.id, 5 * minute)
        tally = FocusPolicy.addPlayed(tally, study.id, 20 * minute)
        tally = FocusPolicy.addPlayed(tally, study.id, -5 * minute)
        tally = FocusPolicy.addVideo(FocusPolicy.addVideo(tally, explore.id), explore.id)

        assertThat(tally.playedMs).containsExactly(explore.id, 15 * minute, study.id, 20 * minute)
        assertThat(tally.videos).containsExactly(explore.id, 2)
    }

    @Test
    fun `a leisure mode stops at its time limit, then at its video limit`() {
        val day = FocusDayTally(1L)

        assertThat(FocusPolicy.limitState(explore, day)).isEqualTo(LimitState.Within(60 * minute, 10))

        val nineVideos = (1..9).fold(day) { t, _ -> FocusPolicy.addVideo(t, explore.id) }
        assertThat(FocusPolicy.mayStartVideo(explore, nineVideos)).isTrue()
        assertThat(FocusPolicy.limitState(explore, FocusPolicy.addVideo(nineVideos, explore.id)))
            .isEqualTo(LimitState.VideosReached)

        val hour = FocusPolicy.addPlayed(FocusPolicy.addVideo(nineVideos, explore.id), explore.id, 60 * minute)
        assertThat(FocusPolicy.limitState(explore, hour)).isEqualTo(LimitState.TimeReached)
        assertThat(FocusPolicy.mayStartVideo(explore, hour)).isFalse()
    }

    @Test
    fun `no limit set means no limit, and productive modes have none`() {
        val busy = FocusPolicy.addPlayed(FocusDayTally(1L), music.id, 600 * minute)

        assertThat(FocusPolicy.limitState(music, busy)).isEqualTo(LimitState.Within(null, null))
        val capped = study.copy(dailyMinutes = 1, dailyVideos = 1)
        assertThat(FocusPolicy.mayStartVideo(capped, FocusPolicy.addPlayed(busy, study.id, 600 * minute))).isTrue()
    }

    @Test
    fun `launch asks again unless a locked session is still running`() {
        val locked = FocusPolicy.start(study, now, commitMinutes = 30)

        assertThat(FocusPolicy.asksAtLaunch(null, now)).isTrue()
        assertThat(FocusPolicy.asksAtLaunch(locked, now + 10 * minute)).isFalse()
        assertThat(FocusPolicy.asksAtLaunch(locked, now + 30 * minute)).isTrue()
        assertThat(FocusPolicy.asksAtLaunch(FocusPolicy.start(explore, now), now)).isTrue()
    }

    @Test
    fun `switching is refused while locked and counted as an emergency once the exit opens`() {
        val locked = FocusPolicy.start(study, now, commitMinutes = 30)

        assertThat(FocusPolicy.switchTo(locked, explore, now + minute)).isNull()

        val held = FocusPolicy.beginExitHold(locked, now + minute)
        val out = FocusPolicy.switchTo(held, explore, now + 2 * minute)
        assertThat(out?.emergency).isTrue()
        assertThat(out?.session?.modeId).isEqualTo(explore.id)

        val later = FocusPolicy.switchTo(locked, music, now + 31 * minute)
        assertThat(later?.emergency).isFalse()
        assertThat(FocusPolicy.switchTo(null, study, now, commitMinutes = 15)?.session?.committedUntil)
            .isEqualTo(now + 15 * minute)
    }
}
