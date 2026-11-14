package io.github.aedev.flow.data.focus

/**
 * "Doesn't belong here": a video the tags let into a profile by mistake. Flagging hides that video from the profile,
 * and a channel flagged [CHANNEL_FLAGS] times is hidden from it too, so a gaming channel that posts one "tutorial"
 * stops coming back to Study. Kept on the profile, so each profile learns its own mistakes.
 */
object FocusFlags {
    const val CHANNEL_FLAGS = 2

    /** The most flagged videos a profile keeps; the oldest go first. */
    const val MAX_VIDEOS = 300

    /** The most channels a profile counts flags for. */
    const val MAX_CHANNELS = 300

    /** [focus] with [videoId] from [channelId] flagged. Flagging the same video again changes nothing. */
    fun flag(
        focus: ProfileFocus,
        videoId: String,
        channelId: String,
    ): ProfileFocus {
        if (videoId.isBlank() || videoId in focus.hiddenVideos) return focus
        val videos = (focus.hiddenVideos + videoId).takeLast(MAX_VIDEOS)
        val flags =
            if (channelId.isBlank()) {
                focus.channelFlags
            } else {
                val counted = focus.channelFlags - channelId + (channelId to (focus.channelFlags[channelId] ?: 0) + 1)
                counted.entries
                    .toList()
                    .takeLast(MAX_CHANNELS)
                    .associate { it.key to it.value }
            }
        return focus.copy(hiddenVideos = videos, channelFlags = flags)
    }

    /** The channels [focus] no longer shows. */
    fun hiddenChannels(focus: ProfileFocus): Set<String> = focus.channelFlags.filterValues { it >= CHANNEL_FLAGS }.keys
}
