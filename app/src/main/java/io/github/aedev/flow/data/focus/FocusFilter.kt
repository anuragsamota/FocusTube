package io.github.aedev.flow.data.focus

/**
 * Whether content belongs in a profile's focus, from the tags it carries and the subjects it names. This is the
 * single rule every feed uses, so Home, related videos, search and Shorts can't disagree.
 */
object FocusFilter {
    /**
     * True when content with [tags] and [text] (its title and the creator's keywords) may show in [mode]. A mode with
     * subjects shows only what names one of them, so a "Linear algebra" profile keeps other maths out too; one with
     * only tags shows what carries one of them. Untagged content could be anything, so only a mode that allows
     * everything lets it through.
     */
    fun allows(
        mode: FocusMode,
        tags: Set<FocusTag>,
        isShort: Boolean = false,
        text: String = "",
    ): Boolean {
        if (isShort && !mode.allowShorts) return false
        if (tags.any { it in mode.blockedTags }) return false
        if (mode.allowsEverything) return true
        if (mode.topics.isNotEmpty()) return topicMatch(mode, text) >= SUBJECT_MATCH
        return tags.any { it in mode.allowedTags }
    }

    /** The share of a subject's words a video has to name to count as being about it. */
    const val SUBJECT_MATCH = 0.5

    /**
     * How well [title] matches the viewer's [FocusMode.topics], from 0 to 1: the share of a topic's words found in
     * the title, best topic wins. A word counts when the title has it or a longer form of it ("algebra" in
     * "algebraic"), so plurals and adjectives still match.
     */
    fun topicMatch(
        mode: FocusMode,
        title: String,
    ): Double {
        if (mode.topics.isEmpty()) return 0.0
        val titleWords = words(title)
        if (titleWords.isEmpty()) return 0.0
        return mode.topics.maxOf { topic ->
            val topicWords = words(topic)
            if (topicWords.isEmpty()) {
                0.0
            } else {
                topicWords.count { word -> titleWords.any { it.startsWith(word) } }.toDouble() / topicWords.size
            }
        }
    }

    private fun words(text: String): Set<String> =
        text
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}+#]+"))
            .filter { it.length > 1 }
            .toSet()
}
