package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class FocusGateTest {
    private val study = FocusPresets.all.first { it.id == FocusPresets.STUDY }
    private val explore = FocusPresets.all.first { it.id == FocusPresets.EXPLORE }
    private val music = FocusPresets.all.first { it.id == FocusPresets.MUSIC }
    private val noMemory = FocusChannelTags()

    private fun video(
        title: String,
        category: String? = null,
        channelId: String = "UC1",
        isShort: Boolean = false,
    ) = Video(
        id = title,
        title = title,
        channelName = "Channel",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 600,
        viewCount = 0,
        uploadDate = "",
        category = category,
        isShort = isShort,
    )

    @Test
    fun `no mode, or a mode that allows everything, filters nothing`() {
        val game = video("Minecraft day 100", category = "Gaming")

        assertThat(FocusGate.blocks(null, noMemory, game)).isFalse()
        assertThat(FocusGate.filters(explore)).isFalse()
        assertThat(FocusGate.blocks(explore, noMemory, game)).isFalse()
    }

    @Test
    fun `study keeps lectures and drops games, shorts and untagged videos`() {
        assertThat(FocusGate.blocks(study, noMemory, video("Calculus lecture 3"))).isFalse()
        assertThat(FocusGate.blocks(study, noMemory, video("Anything", category = "Education"))).isFalse()
        assertThat(FocusGate.blocks(study, noMemory, video("Minecraft day 100", category = "Gaming"))).isTrue()
        assertThat(FocusGate.blocks(study, noMemory, video("Calculus lecture 3", isShort = true))).isTrue()
        assertThat(FocusGate.blocks(study, noMemory, video("Part 7"))).isTrue()
    }

    @Test
    fun `a channel known for teaching carries its untitled uploads into study`() {
        val memory =
            noMemory
                .learn("UCteach", setOf(FocusTag.EDUCATION), now = 1L)
                .learn("UCteach", setOf(FocusTag.EDUCATION), now = 2L)

        assertThat(FocusGate.blocks(study, memory, video("Part 7", channelId = "UCteach"))).isFalse()
        assertThat(FocusGate.blocks(study, memory, video("Part 7", channelId = "UCother"))).isTrue()
    }

    @Test
    fun `music mode shows music and focus music only`() {
        assertThat(FocusGate.blocks(music, noMemory, video("Official Music Video"))).isFalse()
        assertThat(FocusGate.blocks(music, noMemory, video("lofi hip hop radio"))).isFalse()
        assertThat(FocusGate.blocks(music, noMemory, video("Breaking news tonight"))).isTrue()
    }
}
