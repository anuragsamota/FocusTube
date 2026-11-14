package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class FocusFlagsTest {
    private val study = FocusPresets.template(FocusPresets.STUDY)

    @Test
    fun `a flagged video is hidden, and its channel after a second flag`() {
        val once = FocusFlags.flag(study, "v1", "UCgames")
        val twice = FocusFlags.flag(once, "v2", "UCgames")

        assertThat(FocusFlags.flag(once, "v1", "UCgames")).isEqualTo(once)
        assertThat(FocusMode.of("p", "Study", once).hiddenChannels).isEmpty()
        val mode = FocusMode.of("p", "Study", twice)
        assertThat(mode.hiddenVideos).containsExactly("v1", "v2")
        assertThat(mode.hiddenChannels).containsExactly("UCgames")
    }

    @Test
    fun `flags hide content the tags would let in, even in an open profile`() {
        val open = FocusFlags.flag(ProfileFocus(), "v1", "UC1")
        val mode = FocusMode.of("p", "Default", open)
        val tutorial =
            Video(
                id = "v1",
                title = "Python tutorial",
                channelName = "",
                channelId = "UC1",
                thumbnailUrl = "",
                duration = 0,
                viewCount = 0,
                uploadDate = "",
            )

        assertThat(FocusGate.filters(mode)).isTrue()
        assertThat(FocusGate.blocks(mode, FocusChannelTags(), tutorial)).isTrue()
        assertThat(FocusGate.blocks(mode, FocusChannelTags(), tutorial.copy(id = "v2"))).isFalse()
    }

    @Test
    fun `the oldest flags go first`() {
        var focus = ProfileFocus()
        repeat(FocusFlags.MAX_VIDEOS + 3) { focus = FocusFlags.flag(focus, "v$it", "") }

        assertThat(focus.hiddenVideos).hasSize(FocusFlags.MAX_VIDEOS)
        assertThat(focus.hiddenVideos.first()).isEqualTo("v3")
    }
}
