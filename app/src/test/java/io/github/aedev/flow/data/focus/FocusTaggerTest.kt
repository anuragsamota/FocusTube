package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class FocusTaggerTest {
    @Test
    fun `YouTube's category maps to tags, and People and Blogs says nothing`() {
        assertThat(FocusTagger.fromCategory("Education")).containsExactly(FocusTag.EDUCATION)
        assertThat(FocusTagger.fromCategory(" science & technology ")).containsExactly(FocusTag.SCIENCE, FocusTag.TECHNOLOGY)
        assertThat(FocusTagger.fromCategory("People & Blogs")).isEmpty()
        assertThat(FocusTagger.fromCategory(null)).isEmpty()
        assertThat(FocusTagger.fromCategory("Something new")).isEmpty()
    }

    @Test
    fun `title words tag a video as whole words only`() {
        assertThat(FocusTagger.fromText("Linear Algebra lecture 4")).contains(FocusTag.EDUCATION)
        assertThat(FocusTagger.fromText("lofi beats to study to")).contains(FocusTag.FOCUS_MUSIC)
        assertThat(FocusTagger.fromText("Kotlin coroutines in 10 minutes")).containsExactly(FocusTag.TECHNOLOGY)
        assertThat(FocusTagger.fromText("Minecraft hardcore day 100")).containsExactly(FocusTag.GAMING)
        // "math" inside "aftermath" is not maths.
        assertThat(FocusTagger.fromText("The aftermath")).isEmpty()
    }

    @Test
    fun `creator keywords count like the title`() {
        assertThat(FocusTagger.fromText("Episode 12", keywords = listOf("python", "tutorial")))
            .containsAtLeast(FocusTag.TECHNOLOGY, FocusTag.EDUCATION)
    }

    @Test
    fun `an education category keeps leisure words from pulling a lecture out of study`() {
        val tags = FocusTagger.tag(category = "Education", title = "The physics of Minecraft, funny edition")

        assertThat(tags).containsAtLeast(FocusTag.EDUCATION, FocusTag.SCIENCE)
        assertThat(tags).containsNoneOf(FocusTag.GAMING, FocusTag.COMEDY)
        assertThat(FocusTagger.tag(category = "Gaming", title = "Minecraft physics")).contains(FocusTag.GAMING)
    }

    @Test
    fun `the channel's habit stands in only when the video says nothing`() {
        val channel = setOf(FocusTag.EDUCATION)

        assertThat(FocusTagger.tag(category = null, title = "Part 3", channelTags = channel)).isEqualTo(channel)
        assertThat(FocusTagger.tag(category = "Comedy", title = "Part 3", channelTags = channel))
            .containsExactly(FocusTag.COMEDY)
        assertThat(FocusTagger.tag(category = null, title = "Part 3", isMusic = true)).containsExactly(FocusTag.MUSIC)
    }

    @Test
    fun `a channel earns a tag after two videos and a third of its uploads`() {
        var memory = FocusChannelTags()
        memory = memory.learn("UC1", setOf(FocusTag.EDUCATION), now = 1L)
        assertThat(memory.tagsOf("UC1")).isEmpty()

        memory = memory.learn("UC1", setOf(FocusTag.EDUCATION, FocusTag.SCIENCE), now = 2L)
        memory = memory.learn("UC1", emptySet(), now = 3L)
        memory = memory.learn("UC1", emptySet(), now = 4L)
        assertThat(memory.tagsOf("UC1")).containsExactly(FocusTag.EDUCATION)
        assertThat(memory.tagsOf("unknown")).isEmpty()
        assertThat(memory.learn("", setOf(FocusTag.NEWS), now = 5L)).isEqualTo(memory)
    }

    @Test
    fun `the memory keeps the channels seen most recently`() {
        var memory = FocusChannelTags()
        repeat(FocusChannelTags.MAX_CHANNELS + 5) { i -> memory = memory.learn("UC$i", emptySet(), now = i.toLong()) }

        assertThat(memory.channels).hasSize(FocusChannelTags.MAX_CHANNELS)
        assertThat(memory.channels).doesNotContainKey("UC0")
        assertThat(memory.channels).containsKey("UC${FocusChannelTags.MAX_CHANNELS + 4}")
    }

    @Test
    fun `the channel memory survives a round trip through JSON`() {
        val memory = FocusChannelTags().learn("UC1", setOf(FocusTag.MUSIC), now = 1L)
        val json = Json { ignoreUnknownKeys = true }

        val decoded = json.decodeFromString(FocusChannelTags.serializer(), json.encodeToString(FocusChannelTags.serializer(), memory))

        assertThat(decoded).isEqualTo(memory)
    }
}
