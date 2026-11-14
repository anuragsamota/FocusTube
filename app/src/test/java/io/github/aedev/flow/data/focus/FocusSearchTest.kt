package io.github.aedev.flow.data.focus

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FocusSearchTest {
    private val calculus = FocusMode(id = "p", name = "Calculus", kind = FocusKind.PRODUCTIVE, topics = listOf("Calculus"))

    @Test
    fun `a query that names no subject gets the subjects added`() {
        assertThat(FocusSearch.steer(calculus, " derivatives ")).isEqualTo("derivatives Calculus")
    }

    @Test
    fun `a query that names a subject, and a profile without subjects, search as typed`() {
        assertThat(FocusSearch.steer(calculus, "calculus derivatives")).isEqualTo("calculus derivatives")
        assertThat(FocusSearch.steer(calculus.copy(topics = emptyList()), "derivatives")).isEqualTo("derivatives")
        assertThat(FocusSearch.steer(null, "derivatives")).isEqualTo("derivatives")
        assertThat(FocusSearch.steer(calculus, "  ")).isEqualTo("  ")
    }

    @Test
    fun `only the first subjects are added`() {
        val many = calculus.copy(topics = listOf("Calculus", "Linear algebra", "Statistics"))

        assertThat(FocusSearch.steer(many, "proofs")).isEqualTo("proofs Calculus Linear algebra")
    }
}
