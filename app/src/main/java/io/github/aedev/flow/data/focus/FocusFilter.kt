package io.github.aedev.flow.data.focus

/**
 * Whether content belongs in a focus mode, from the tags it carries. Phase 3 fills the tags in from FlowNeuro; this
 * is the single rule every feed will use, so Home, related videos, search and Shorts can't disagree.
 */
object FocusFilter {
    /**
     * True when content with [tags] may show in [mode]. Untagged content could be anything, so only a mode that
     * allows everything lets it through.
     */
    fun allows(
        mode: FocusMode,
        tags: Set<FocusTag>,
        isShort: Boolean = false,
    ): Boolean {
        if (isShort && !mode.allowShorts) return false
        if (tags.any { it in mode.blockedTags }) return false
        if (mode.allowsEverything) return true
        return tags.any { it in mode.allowedTags }
    }

    /**
     * How well [title] matches the viewer's [FocusMode.topics], from 0 to 1: the share of a topic's words found in
     * the title, best topic wins. Productive feeds sort by this so the subjects the viewer named come first.
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
            if (topicWords.isEmpty()) 0.0 else topicWords.count { it in titleWords }.toDouble() / topicWords.size
        }
    }

    private fun words(text: String): Set<String> =
        text
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}+#]+"))
            .filter { it.length > 1 }
            .toSet()
}
