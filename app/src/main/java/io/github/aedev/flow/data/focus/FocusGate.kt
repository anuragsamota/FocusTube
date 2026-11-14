package io.github.aedev.flow.data.focus

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The running profile's focus, which every feed filters by, read synchronously where feeds are assembled.
 * [FocusController] sets it from the profile registry and [FocusTagging] the channel memory; [io.github.aedev.flow.data.recommendation.FeedExclusions] asks [blocks].
 *
 * A process-wide object because the feeds that need it are built far from Hilt, inside FlowNeuro's snapshot.
 */
object FocusGate {
    private val current = MutableStateFlow<FocusMode?>(null)

    @Volatile
    private var channels: FocusChannelTags = FocusChannelTags()

    /** The rules feeds filter by; null until the registry has been read, which filters nothing. */
    val mode: StateFlow<FocusMode?> = current.asStateFlow()

    fun setMode(mode: FocusMode?) {
        current.value = mode
    }

    fun setChannels(memory: FocusChannelTags) {
        channels = memory
    }

    /** Whether the current mode keeps [video] out of every feed. */
    fun blocks(video: Video): Boolean = blocks(current.value, channels, video)

    /** Whether [mode] filters anything at all, so a feed can skip tagging when it doesn't. */
    fun filters(mode: FocusMode?): Boolean = mode != null && (!mode.allowsEverything || mode.blockedTags.isNotEmpty() || !mode.allowShorts)

    /** The rule behind [blocks], with its inputs spelled out for tests. */
    fun blocks(
        mode: FocusMode?,
        channels: FocusChannelTags,
        video: Video,
    ): Boolean {
        if (mode == null || !filters(mode)) return false
        val tags =
            FocusTagger.tag(
                category = video.category,
                title = video.title,
                keywords = video.tags,
                channelTags = channels.tagsOf(video.channelId),
                isMusic = video.isMusic,
            )
        val text = (listOf(video.title) + video.tags).joinToString(" ")
        return !FocusFilter.allows(mode, tags, isShort = video.isShort, text = text)
    }
}
