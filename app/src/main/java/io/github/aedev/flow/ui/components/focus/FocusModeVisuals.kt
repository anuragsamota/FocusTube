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

/** The Material symbol for a profile's template. */
fun FocusMode.iconVector(): ImageVector = presetIcon(icon)

/** The Material symbol for the template [preset]. */
fun presetIcon(preset: String): ImageVector =
    when (preset) {
        FocusIcons.STUDY -> Icons.Outlined.School
        FocusIcons.WORK -> Icons.Outlined.WorkOutline
        FocusIcons.LEARN -> Icons.Outlined.Lightbulb
        FocusIcons.MUSIC -> Icons.Outlined.MusicNote
        else -> Icons.Outlined.Explore
    }

/** The profile's name; Default's is blank until it is renamed and shows the localized "Default". */
@Composable
fun FocusMode.displayName(): String = name.ifBlank { stringResource(R.string.profile_default_name) }

/** The name a new profile made from the template [preset] starts with. */
@Composable
fun presetName(preset: String): String =
    when (preset) {
        FocusPresets.STUDY -> stringResource(R.string.focus_mode_study)
        FocusPresets.WORK -> stringResource(R.string.focus_mode_work)
        FocusPresets.LEARN -> stringResource(R.string.focus_mode_learn)
        FocusPresets.MUSIC -> stringResource(R.string.focus_mode_music)
        else -> stringResource(R.string.focus_mode_explore)
    }

/** The avatar a profile made from a template starts with; it can be changed like any profile's. */
data class FocusPresetLook(
    val emoji: String,
    val colorKey: String,
)

object FocusPresetLooks {
    fun of(preset: String): FocusPresetLook =
        when (preset) {
            FocusPresets.STUDY -> FocusPresetLook("\uD83D\uDCDA", "blue")
            FocusPresets.WORK -> FocusPresetLook("\uD83D\uDCBC", "teal")
            FocusPresets.LEARN -> FocusPresetLook("\uD83D\uDCA1", "amber")
            FocusPresets.MUSIC -> FocusPresetLook("\uD83C\uDFB5", "purple")
            else -> FocusPresetLook("\uD83E\uDDED", "orange")
        }
}

/** One line under a profile: its subjects, how a locked profile holds you, or its daily limits. */
@Composable
fun FocusMode.summary(): String {
    if (!restricts) return stringResource(R.string.focus_unrestricted)
    if (topics.isNotEmpty()) return stringResource(R.string.focus_subjects_only, topics.joinToString(", "))
    if (lock) return stringResource(R.string.focus_kind_productive)
    val minutes = dailyMinutes
    val videos = dailyVideos
    return when {
        minutes != null && videos != null -> {
            stringResource(
                R.string.focus_limit_both,
                stringResource(R.string.focus_minutes, minutes),
                pluralStringResource(R.plurals.focus_videos, videos, videos),
            )
        }

        minutes != null -> {
            stringResource(R.string.focus_limit_one, stringResource(R.string.focus_minutes, minutes))
        }

        videos != null -> {
            stringResource(R.string.focus_limit_one, pluralStringResource(R.plurals.focus_videos, videos, videos))
        }

        else -> {
            stringResource(R.string.focus_no_limit)
        }
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
