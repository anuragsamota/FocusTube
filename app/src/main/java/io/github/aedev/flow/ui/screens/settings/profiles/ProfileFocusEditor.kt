package io.github.aedev.flow.ui.screens.settings.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.focus.FocusKind
import io.github.aedev.flow.data.focus.FocusPresets
import io.github.aedev.flow.data.focus.FocusTag
import io.github.aedev.flow.data.focus.ProfileFocus
import io.github.aedev.flow.ui.components.focus.presetName
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import kotlinx.serialization.json.Json

private val focusJson = Json { ignoreUnknownKeys = true }

/** Keeps a profile's focus being edited across rotation and process death, as the registry's own JSON. */
internal val ProfileFocusSaver: Saver<ProfileFocus, String> =
    Saver(
        save = { focusJson.encodeToString(ProfileFocus.serializer(), it) },
        restore = { runCatching { focusJson.decodeFromString(ProfileFocus.serializer(), it) }.getOrNull() },
    )

private val MinuteChoices = listOf(15, 30, 60, 120)
private val VideoChoices = listOf(5, 10, 20)

/** The tags a viewer can pick; Other is what nothing else fits, so it can't be asked for. */
private val PickableTags = FocusTag.entries.filter { it != FocusTag.OTHER }

/**
 * What a profile is for: its template, the subjects and tags it lets through, whether it holds the viewer for a
 * minimum time, Shorts, and the daily limits of a profile without a lock. [enabled] is false while the running
 * profile's lock holds, so the lock can't be edited away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileFocusEditor(
    focus: ProfileFocus,
    onChange: (ProfileFocus) -> Unit,
    enabled: Boolean,
) {
    var subjectsText by rememberSaveable { mutableStateOf(focus.subjects.joinToString(", ")) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!enabled) {
            Text(
                stringResource(R.string.focus_edit_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(stringResource(R.string.focus_edit_template), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = focus.preset == FocusPresets.NONE,
                enabled = enabled,
                onClick = { onChange(focus.keptOn(ProfileFocus())) },
                label = { Text(stringResource(R.string.focus_edit_template_none)) },
            )
            FocusPresets.templates.forEach { template ->
                FilterChip(
                    selected = focus.preset == template.preset,
                    enabled = enabled,
                    onClick = { onChange(focus.keptOn(template)) },
                    label = { Text(presetName(template.preset)) },
                )
            }
        }

        OutlinedTextField(
            value = subjectsText,
            onValueChange = { text ->
                subjectsText = text
                onChange(focus.copy(subjects = text.split(',').map(String::trim).filter(String::isNotEmpty)))
            },
            enabled = enabled,
            label = { Text(stringResource(R.string.focus_edit_subjects)) },
            supportingText = { Text(stringResource(R.string.focus_edit_subjects_help)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.focus_edit_tags), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.focus_edit_tags_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickableTags.forEach { tag ->
                val selected = tag in focus.allowedTags
                FilterChip(
                    selected = selected,
                    enabled = enabled,
                    onClick = {
                        val tags = if (selected) focus.allowedTags - tag else focus.allowedTags + tag
                        onChange(focus.copy(allowedTags = tags, blockedTags = focus.blockedTags - tag))
                    },
                    label = { Text(tagLabel(tag)) },
                )
            }
        }

        FlowRowGroup {
            FlowSwitchRow(
                title = stringResource(R.string.focus_edit_lock),
                supportingText = stringResource(R.string.focus_edit_lock_summary),
                checked = focus.lock,
                enabled = enabled,
                onCheckedChange = { lock ->
                    val kind = if (lock) FocusKind.PRODUCTIVE else FocusKind.LEISURE
                    onChange(focus.copy(lock = lock, kind = kind))
                },
                shape = flowRowGroupShape(0, 2),
            )
            FlowSwitchRow(
                title = stringResource(R.string.focus_edit_shorts),
                checked = focus.allowShorts,
                enabled = enabled,
                onCheckedChange = { onChange(focus.copy(allowShorts = it)) },
                shape = flowRowGroupShape(1, 2),
            )
        }

        if (!focus.lock) {
            Text(stringResource(R.string.focus_edit_daily_time), style = MaterialTheme.typography.titleSmall)
            LimitChips(
                choices = MinuteChoices,
                selected = focus.dailyMinutes,
                enabled = enabled,
                label = { stringResource(R.string.focus_minutes, it) },
                onSelect = { onChange(focus.copy(dailyMinutes = it)) },
            )
            Text(stringResource(R.string.focus_edit_daily_videos), style = MaterialTheme.typography.titleSmall)
            LimitChips(
                choices = VideoChoices,
                selected = focus.dailyVideos,
                enabled = enabled,
                label = { pluralStringResource(R.plurals.focus_videos, it, it) },
                onSelect = { onChange(focus.copy(dailyVideos = it)) },
            )
        }
    }
}

/** [template] with what the viewer gave this profile themselves: its subjects and the videos they flagged. */
private fun ProfileFocus.keptOn(template: ProfileFocus): ProfileFocus =
    template.copy(subjects = subjects, hiddenVideos = hiddenVideos, channelFlags = channelFlags)

/** No limit, then each choice; picking the selected one again turns the limit off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitChips(
    choices: List<Int>,
    selected: Int?,
    enabled: Boolean,
    label: @Composable (Int) -> String,
    onSelect: (Int?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            enabled = enabled,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.focus_edit_no_limit)) },
        )
        choices.forEach { choice ->
            FilterChip(
                selected = selected == choice,
                enabled = enabled,
                onClick = { onSelect(choice.takeUnless { it == selected }) },
                label = { Text(label(choice)) },
            )
        }
    }
}

@Composable
private fun tagLabel(tag: FocusTag): String =
    stringResource(
        when (tag) {
            FocusTag.EDUCATION -> R.string.focus_tag_education
            FocusTag.SCIENCE -> R.string.focus_tag_science
            FocusTag.TECHNOLOGY -> R.string.focus_tag_technology
            FocusTag.HOWTO -> R.string.focus_tag_howto
            FocusTag.MUSIC -> R.string.focus_tag_music
            FocusTag.FOCUS_MUSIC -> R.string.focus_tag_focus_music
            FocusTag.NEWS -> R.string.focus_tag_news
            FocusTag.GAMING -> R.string.focus_tag_gaming
            FocusTag.ENTERTAINMENT -> R.string.focus_tag_entertainment
            FocusTag.COMEDY -> R.string.focus_tag_comedy
            FocusTag.SPORTS -> R.string.focus_tag_sports
            FocusTag.LIFESTYLE -> R.string.focus_tag_lifestyle
            FocusTag.HEALTH -> R.string.focus_tag_health
            FocusTag.CREATIVE -> R.string.focus_tag_creative
            FocusTag.TRAVEL -> R.string.focus_tag_travel
            FocusTag.FILM -> R.string.focus_tag_film
            FocusTag.OTHER -> R.string.focus_tag_other
        },
    )
