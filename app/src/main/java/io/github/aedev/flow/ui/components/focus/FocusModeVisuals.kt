package io.github.aedev.flow.ui.components.focus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.focus.FocusIcons
import io.github.aedev.flow.data.focus.FocusMode
import io.github.aedev.flow.data.focus.FocusPresets
import kotlinx.coroutines.delay

private const val TICK_MS = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/** The Material symbol for a mode's icon key. */
fun FocusMode.iconVector(): ImageVector =
    when (icon) {
        FocusIcons.STUDY -> Icons.Outlined.School
        FocusIcons.WORK -> Icons.Outlined.WorkOutline
        FocusIcons.LEARN -> Icons.Outlined.Lightbulb
        FocusIcons.MUSIC -> Icons.Outlined.MusicNote
        else -> Icons.Outlined.Explore
    }

/** A preset keeps its translated name until the viewer renames it; a custom mode shows the name it was given. */
@Composable
fun FocusMode.displayName(): String {
    val preset = FocusPresets.all.firstOrNull { it.id == id }
    if (preset == null || name != preset.name) return name
    return when (id) {
        FocusPresets.STUDY -> stringResource(R.string.focus_mode_study)
        FocusPresets.WORK -> stringResource(R.string.focus_mode_work)
        FocusPresets.LEARN -> stringResource(R.string.focus_mode_learn)
        FocusPresets.MUSIC -> stringResource(R.string.focus_mode_music)
        else -> stringResource(R.string.focus_mode_explore)
    }
}

/** One line under a mode: how a productive mode holds you, or a leisure mode's daily limits. */
@Composable
fun FocusMode.summary(): String {
    if (isProductive) return stringResource(R.string.focus_kind_productive)
    val minutes = dailyMinutes
    val videos = dailyVideos
    return when {
        minutes != null && videos != null ->
            stringResource(
                R.string.focus_limit_both,
                stringResource(R.string.focus_minutes, minutes),
                pluralStringResource(R.plurals.focus_videos, videos, videos),
            )
        minutes != null -> stringResource(R.string.focus_limit_one, stringResource(R.string.focus_minutes, minutes))
        videos != null -> stringResource(R.string.focus_limit_one, pluralStringResource(R.plurals.focus_videos, videos, videos))
        else -> stringResource(R.string.focus_no_limit)
    }
}

/** The wall clock, ticking once a second while composed, for countdowns. */
@Composable
fun rememberNow(): State<Long> =
    produceState(System.currentTimeMillis()) {
        while (true) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }

/** [ms] as m:ss, rounded up so a countdown never shows 0:00 while time is left. */
fun formatCountdown(ms: Long): String {
    val totalSeconds = (ms + TICK_MS - 1) / TICK_MS
    return "%d:%02d".format(totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
}

