package io.github.aedev.flow.data.focus

import kotlinx.serialization.Serializable

/**
 * Tags a video from what the phone already has: YouTube's category (read from the video's microformat), the words of
 * its title and creator keywords, and what its channel usually posts ([FocusChannelTags]). Nothing leaves the device.
 *
 * The category is the strongest signal but often missing from feed rows, so text and the channel fill in. Text never
 * adds GAMING, COMEDY or ENTERTAINMENT on its own word alone when the category says EDUCATION, so a lecture titled
 * "the physics of Minecraft" stays study material.
 */
object FocusTagger {
    /** YouTube's categories, by their English name as the microformat writes it. */
    private val CATEGORY_TAGS: Map<String, Set<FocusTag>> =
        mapOf(
            "film & animation" to setOf(FocusTag.FILM, FocusTag.ENTERTAINMENT),
            "autos & vehicles" to setOf(FocusTag.LIFESTYLE),
            "music" to setOf(FocusTag.MUSIC),
            "pets & animals" to setOf(FocusTag.LIFESTYLE),
            "sports" to setOf(FocusTag.SPORTS),
            "travel & events" to setOf(FocusTag.TRAVEL),
            "gaming" to setOf(FocusTag.GAMING),
            "people & blogs" to emptySet(),
            "comedy" to setOf(FocusTag.COMEDY),
            "entertainment" to setOf(FocusTag.ENTERTAINMENT),
            "news & politics" to setOf(FocusTag.NEWS),
            "howto & style" to setOf(FocusTag.HOWTO),
            "education" to setOf(FocusTag.EDUCATION),
            "science & technology" to setOf(FocusTag.SCIENCE, FocusTag.TECHNOLOGY),
            "nonprofits & activism" to setOf(FocusTag.NEWS),
        )

    /** Words and phrases that point at a tag, matched as whole words in the title and keywords. */
    private val KEYWORDS: Map<FocusTag, List<String>> =
        mapOf(
            FocusTag.FOCUS_MUSIC to
                listOf(
                    "lofi",
                    "lo-fi",
                    "study music",
                    "focus music",
                    "music for studying",
                    "music for work",
                    "deep focus",
                    "concentration music",
                    "ambient",
                    "white noise",
                    "brown noise",
                    "pink noise",
                    "binaural",
                    "rain sounds",
                    "study with me",
                    "pomodoro",
                    "coding music",
                    "chillhop",
                ),
            FocusTag.EDUCATION to
                listOf(
                    "lecture",
                    "lesson",
                    "course",
                    "tutorial",
                    "explained",
                    "explainer",
                    "crash course",
                    "exam",
                    "revision",
                    "study",
                    "mathematics",
                    "math",
                    "maths",
                    "calculus",
                    "algebra",
                    "geometry",
                    "statistics",
                    "history",
                    "geography",
                    "economics",
                    "philosophy",
                    "psychology",
                    "grammar",
                    "language learning",
                    "learn english",
                    "university",
                    "college",
                    "ncert",
                    "jee",
                    "neet",
                    "upsc",
                    "gate exam",
                    "sat prep",
                    "ielts",
                ),
            FocusTag.SCIENCE to
                listOf(
                    "physics",
                    "chemistry",
                    "biology",
                    "astronomy",
                    "nasa",
                    "quantum",
                    "experiment",
                    "neuroscience",
                    "evolution",
                    "geology",
                    "climate",
                    "engineering",
                ),
            FocusTag.TECHNOLOGY to
                listOf(
                    "programming",
                    "coding",
                    "developer",
                    "software",
                    "kotlin",
                    "java",
                    "python",
                    "javascript",
                    "typescript",
                    "rust",
                    "golang",
                    "c++",
                    "android",
                    "linux",
                    "web development",
                    "machine learning",
                    "deep learning",
                    "artificial intelligence",
                    "data science",
                    "algorithm",
                    "algorithms",
                    "data structures",
                    "system design",
                    "devops",
                    "database",
                    "sql",
                    "cybersecurity",
                    "tech review",
                    "smartphone",
                    "laptop",
                ),
            FocusTag.HOWTO to listOf("how to", "diy", "step by step", "beginner's guide"),
            FocusTag.MUSIC to
                listOf(
                    "official music video",
                    "official video",
                    "official audio",
                    "lyrics",
                    "lyric video",
                    "live performance",
                    "concert",
                    "album",
                    "remix",
                    "cover song",
                    "acoustic",
                    "music video",
                ),
            FocusTag.NEWS to
                listOf("news", "breaking", "politics", "election", "headlines", "current affairs", "world news"),
            FocusTag.GAMING to
                listOf(
                    "gameplay",
                    "gaming",
                    "walkthrough",
                    "let's play",
                    "speedrun",
                    "minecraft",
                    "fortnite",
                    "gta",
                    "valorant",
                    "roblox",
                    "pubg",
                    "bgmi",
                    "call of duty",
                    "league of legends",
                    "esports",
                ),
            FocusTag.COMEDY to listOf("comedy", "funny", "prank", "stand-up", "standup", "memes", "sketch"),
            FocusTag.ENTERTAINMENT to
                listOf(
                    "trailer",
                    "reaction",
                    "vlog",
                    "challenge",
                    "celebrity",
                    "gossip",
                    "web series",
                    "episode",
                    "reality show",
                    "unboxing",
                ),
            FocusTag.SPORTS to
                listOf("cricket", "football", "soccer", "nba", "basketball", "tennis", "highlights", "ipl", "ufc"),
            FocusTag.HEALTH to
                listOf("workout", "fitness", "yoga", "meditation", "nutrition", "gym", "mental health"),
            FocusTag.LIFESTYLE to listOf("recipe", "cooking", "fashion", "makeup", "skincare", "home decor"),
            FocusTag.CREATIVE to listOf("drawing", "painting", "animation", "graphic design", "video editing"),
            FocusTag.TRAVEL to listOf("travel", "trip", "tour", "itinerary"),
            FocusTag.FILM to listOf("movie", "film review", "film analysis", "short film", "documentary"),
        )

    /** Tags that a study or work category vouches against when only the words suggest them. */
    private val LEISURE_TEXT_TAGS = setOf(FocusTag.GAMING, FocusTag.COMEDY, FocusTag.ENTERTAINMENT)
    private val SERIOUS_CATEGORY_TAGS = setOf(FocusTag.EDUCATION, FocusTag.SCIENCE, FocusTag.TECHNOLOGY, FocusTag.HOWTO)

    private val keywordPatterns: List<Pair<FocusTag, Regex>> =
        KEYWORDS.flatMap { (tag, words) ->
            words.map { word -> tag to Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])") }
        }

    /** The tags YouTube's [category] stands for; empty when it is missing or says nothing ("People & Blogs"). */
    fun fromCategory(category: String?): Set<FocusTag> =
        category
            ?.trim()
            ?.lowercase()
            ?.let(CATEGORY_TAGS::get)
            .orEmpty()

    /** The tags the words of [title] and the creator [keywords] point at. */
    fun fromText(
        title: String,
        keywords: List<String> = emptyList(),
    ): Set<FocusTag> {
        val text = (listOf(title) + keywords).joinToString(" \n ").lowercase()
        return keywordPatterns.filter { (_, pattern) -> pattern.containsMatchIn(text) }.mapTo(LinkedHashSet()) { it.first }
    }

    /**
     * Every tag for one video. [channelTags] stand in only when the video itself says nothing: a channel's habit is a
     * weaker hint than the video's own category or title.
     */
    fun tag(
        category: String?,
        title: String,
        keywords: List<String> = emptyList(),
        channelTags: Set<FocusTag> = emptySet(),
        isMusic: Boolean = false,
    ): Set<FocusTag> {
        val fromCategory = fromCategory(category)
        var fromText = fromText(title, keywords)
        if (fromCategory.any { it in SERIOUS_CATEGORY_TAGS }) fromText = fromText - LEISURE_TEXT_TAGS
        val own = fromCategory + fromText + if (isMusic) setOf(FocusTag.MUSIC) else emptySet()
        return own.ifEmpty { channelTags }
    }
}

/**
 * What each channel usually posts, learned from the uploads the app sees: per channel, how many videos carried each
 * tag. A channel is given a tag once it shows on at least [MIN_SHARE] of at least [MIN_VIDEOS] videos.
 */
@Serializable
data class FocusChannelTags(
    val channels: Map<String, ChannelTally> = emptyMap(),
) {
    @Serializable
    data class ChannelTally(
        val videos: Int = 0,
        val counts: Map<FocusTag, Int> = emptyMap(),
        val lastSeen: Long = 0L,
    )

    /** The channel's tags, or none while too little of it has been seen. */
    fun tagsOf(channelId: String): Set<FocusTag> {
        val tally = channels[channelId] ?: return emptySet()
        if (tally.videos < MIN_VIDEOS) return emptySet()
        return tally.counts.filterValues { it >= tally.videos * MIN_SHARE }.keys
    }

    /** This memory after seeing a video of [channelId] with its own [tags]. Untagged videos count toward the total. */
    fun learn(
        channelId: String,
        tags: Set<FocusTag>,
        now: Long,
    ): FocusChannelTags {
        if (channelId.isBlank()) return this
        val old = channels[channelId] ?: ChannelTally()
        val counts = old.counts.toMutableMap()
        tags.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val updated = channels + (channelId to ChannelTally(old.videos + 1, counts, now))
        val kept =
            if (updated.size <= MAX_CHANNELS) {
                updated
            } else {
                updated.entries
                    .sortedByDescending { it.value.lastSeen }
                    .take(MAX_CHANNELS)
                    .associate { it.key to it.value }
            }
        return FocusChannelTags(kept)
    }

    companion object {
        const val MIN_VIDEOS = 2
        const val MIN_SHARE = 0.3
        const val MAX_CHANNELS = 2_000
    }
}
