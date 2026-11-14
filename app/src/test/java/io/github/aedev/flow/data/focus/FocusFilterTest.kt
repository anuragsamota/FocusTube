package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
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
    fun `a profile with subjects shows only what names one of them`() {
        val algebra = study.copy(topics = listOf("Linear algebra"))

        assertThat(FocusFilter.allows(algebra, setOf(FocusTag.EDUCATION), text = "Linear algebra lecture 4")).isTrue()
        assertThat(FocusFilter.allows(algebra, setOf(FocusTag.EDUCATION), text = "Algebraic geometry, part 2")).isTrue()
        assertThat(FocusFilter.allows(algebra, setOf(FocusTag.EDUCATION), text = "Organic chemistry basics")).isFalse()
        assertThat(FocusFilter.allows(algebra, emptySet(), text = "Linear algebra in 10 minutes")).isTrue()
        assertThat(algebra.allowsEverything).isFalse()
    }

    @Test
    fun `a profile's focus survives the registry's JSON, and an older registry reads as unrestricted`() {
        val json = Json { ignoreUnknownKeys = true }
        val study = FocusPresets.template(FocusPresets.STUDY).copy(subjects = listOf("Physics"))

        val decoded = json.decodeFromString(ProfileFocus.serializer(), json.encodeToString(ProfileFocus.serializer(), study))

        assertThat(decoded).isEqualTo(study)
        val old = json.decodeFromString(ProfileFocus.serializer(), "{}")
        assertThat(FocusMode.of("p", "Default", old).restricts).isFalse()
        assertThat(FocusMode.of("p", "Study", study).lock).isTrue()
    }
}
