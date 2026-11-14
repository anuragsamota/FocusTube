package io.github.aedev.flow.data.focus

import kotlinx.serialization.Serializable

/**
 * Whether a focus mode holds the viewer in it ([PRODUCTIVE]: a minimum time, no Shorts by default) or lets them come
 * and go under a daily allowance ([LEISURE]: time and video-count limits).
 */
@Serializable
enum class FocusKind { PRODUCTIVE, LEISURE }

/**
 * The tags a channel or video can carry. Each one maps from FlowNeuro's topic catalog and YouTube's own categories,
 * so tagging needs no service off the phone. Ids are stable: they are stored in focus modes and must not be renamed.
 */
@Serializable
enum class FocusTag(
    val id: String,
) {
    EDUCATION("education"),
    SCIENCE("science"),
    TECHNOLOGY("technology"),
    HOWTO("howto"),
    MUSIC("music"),
    FOCUS_MUSIC("focus_music"),
    NEWS("news"),
    GAMING("gaming"),
    ENTERTAINMENT("entertainment"),
    COMEDY("comedy"),
    SPORTS("sports"),
    LIFESTYLE("lifestyle"),
    HEALTH("health"),
    CREATIVE("creative"),
    TRAVEL("travel"),
    FILM("film"),
    OTHER("other"),
    ;

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): FocusTag? = BY_ID[id]
    }
}

/**
 * A stretch of the week when a mode is the one the launch picker suggests. [days] are ISO day numbers (1 is Monday,
 * 7 is Sunday). [startMinute] and [endMinute] count from midnight; an end before the start runs past midnight.
 */
@Serializable
data class FocusWindow(
    val days: Set<Int> = ALL_DAYS,
    val startMinute: Int = 0,
    val endMinute: Int = MINUTES_PER_DAY,
) {
    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        val ALL_DAYS: Set<Int> = (1..7).toSet()
        val WEEKDAYS: Set<Int> = (1..5).toSet()
        val WEEKEND: Set<Int> = setOf(6, 7)
    }
}

/**
 * One focus mode. Content is shown in it when it carries one of [allowedTags] and none of [blockedTags]; for a
 * productive mode the viewer's [topics] (for example "linear algebra") then lift matching videos to the top.
 *
 * [dailyMinutes] and [dailyVideos] limit a leisure mode per local day; null means no limit. They are ignored for a
 * productive mode, whose time is open-ended once its minimum has passed.
 */
@Serializable
data class FocusMode(
    val id: String,
    val name: String,
    val icon: String = FocusIcons.DEFAULT,
    val kind: FocusKind = FocusKind.LEISURE,
    val allowedTags: Set<FocusTag> = emptySet(),
    val blockedTags: Set<FocusTag> = emptySet(),
    val topics: List<String> = emptyList(),
    val allowShorts: Boolean = kind == FocusKind.LEISURE,
    val schedule: List<FocusWindow> = emptyList(),
    val dailyMinutes: Int? = null,
    val dailyVideos: Int? = null,
    val builtIn: Boolean = false,
) {
    val isProductive: Boolean get() = kind == FocusKind.PRODUCTIVE

    /** An empty [allowedTags] allows every tag that isn't blocked, which is how Explore works. */
    val allowsEverything: Boolean get() = allowedTags.isEmpty()
}

/** The icon keys a mode can use; the UI maps each to a Material symbol. */
object FocusIcons {
    const val STUDY = "study"
    const val WORK = "work"
    const val LEARN = "learn"
    const val MUSIC = "music"
    const val EXPLORE = "explore"
    const val DEFAULT = EXPLORE
}

/**
 * The mode the viewer is in now. [committedUntil] is when the minimum time they chose runs out; before then a
 * productive mode can't be left except through the emergency exit, which starts at [exitHoldStartedAt].
 */
@Serializable
data class FocusSession(
    val modeId: String,
    val startedAt: Long,
    val committedUntil: Long = startedAt,
    val exitHoldStartedAt: Long? = null,
)

/**
 * What one local day ([day] is an epoch day) has counted per mode id: playing time and videos started. Emergency
 * exits are counted too, so the stats screen can show them.
 */
@Serializable
data class FocusDayTally(
    val day: Long,
    val playedMs: Map<String, Long> = emptyMap(),
    val videos: Map<String, Int> = emptyMap(),
    val emergencyExits: Int = 0,
)
