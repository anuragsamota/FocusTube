package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Test

class FocusFilterTest {
    private val study = FocusPresets.all.first { it.id == FocusPresets.STUDY }
    private val explore = FocusPresets.all.first { it.id == FocusPresets.EXPLORE }

    @Test
    fun `study shows study content and holds back the rest`() {
        assertThat(FocusFilter.allows(study, setOf(FocusTag.EDUCATION))).isTrue()
        assertThat(FocusFilter.allows(study, setOf(FocusTag.GAMING, FocusTag.SCIENCE))).isTrue()
        assertThat(FocusFilter.allows(study, setOf(FocusTag.GAMING))).isFalse()
        assertThat(FocusFilter.allows(study, emptySet())).isFalse()
    }

    @Test
    fun `explore shows everything but blocked tags and untagged content`() {
        val noGaming = explore.copy(blockedTags = setOf(FocusTag.GAMING))

        assertThat(FocusFilter.allows(explore, emptySet())).isTrue()
        assertThat(FocusFilter.allows(noGaming, setOf(FocusTag.COMEDY))).isTrue()
        assertThat(FocusFilter.allows(noGaming, setOf(FocusTag.GAMING, FocusTag.COMEDY))).isFalse()
    }

    @Test
    fun `shorts are off in productive modes by default`() {
        assertThat(FocusFilter.allows(study, setOf(FocusTag.EDUCATION), isShort = true)).isFalse()
        assertThat(FocusFilter.allows(explore, setOf(FocusTag.COMEDY), isShort = true)).isTrue()
        assertThat(study.copy(allowShorts = true).let { FocusFilter.allows(it, setOf(FocusTag.EDUCATION), true) })
            .isTrue()
    }

    @Test
    fun `the viewer's topics rank matching titles first`() {
        val algebra = study.copy(topics = listOf("Linear Algebra", "Kotlin coroutines"))

        assertThat(FocusFilter.topicMatch(algebra, "Linear algebra, lecture 4: eigenvalues")).isEqualTo(1.0)
        assertThat(FocusFilter.topicMatch(algebra, "Kotlin in 100 seconds")).isEqualTo(0.5)
        assertThat(FocusFilter.topicMatch(algebra, "Top 10 funny cats")).isEqualTo(0.0)
        assertThat(FocusFilter.topicMatch(study, "Linear algebra")).isEqualTo(0.0)
    }

    @Test
    fun `presets come back when missing and custom modes are kept after them`() {
        val editedStudy = study.copy(topics = listOf("Physics"), builtIn = false)
        val custom = FocusMode(id = "custom_1", name = "Cooking", builtIn = true)

        val merged = FocusPresets.merge(listOf(custom, editedStudy))

        assertThat(merged.map { it.id })
            .containsExactly(
                FocusPresets.STUDY,
                FocusPresets.WORK,
                FocusPresets.LEARN,
                FocusPresets.MUSIC,
                FocusPresets.EXPLORE,
                "custom_1",
            ).inOrder()
        assertThat(merged.first().topics).containsExactly("Physics")
        assertThat(merged.first().builtIn).isTrue()
        assertThat(merged.last().builtIn).isFalse()
    }

    @Test
    fun `modes survive a round trip through JSON`() {
        val serializer = ListSerializer(FocusMode.serializer())
        val json = Json { ignoreUnknownKeys = true }

        val decoded = json.decodeFromString(serializer, json.encodeToString(serializer, FocusPresets.all))

        assertThat(decoded).isEqualTo(FocusPresets.all)
    }
}
