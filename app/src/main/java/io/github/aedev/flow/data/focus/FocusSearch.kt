package io.github.aedev.flow.data.focus

/**
 * Keeps a search inside the running profile's subjects. A query that already names one of them goes out as typed;
 * any other gets the subjects added, so "derivatives" in a Calculus profile searches "derivatives calculus" and the
 * results come back on topic instead of being filtered down to nothing. Results are still filtered by [FocusGate].
 */
object FocusSearch {
    /** Subjects added to a query, at most; a profile with more adds its first ones. */
    const val MAX_ADDED_SUBJECTS = 2

    /** The query to send for [query] in [mode]. */
    fun steer(
        mode: FocusMode?,
        query: String,
    ): String {
        val typed = query.trim()
        if (mode == null || mode.topics.isEmpty() || typed.isEmpty()) return query
        if (FocusFilter.topicMatch(mode, typed) >= FocusFilter.SUBJECT_MATCH) return query
        return (listOf(typed) + mode.topics.take(MAX_ADDED_SUBJECTS)).joinToString(" ")
    }
}
