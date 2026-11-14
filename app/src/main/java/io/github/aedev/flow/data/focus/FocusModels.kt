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
 * The focus rules a profile carries, kept in the profile registry so every profile's rules can be read from any
 * process: the launch picker lists them all, and the profile switcher enforces the lock of the one that is running.
 *
 * [preset] is the template the profile was made from ([FocusPresets]); [FocusPresets.NONE] is an ordinary profile with
 * no restrictions, like Default. Content shows when it carries one of [allowedTags] or matches one of [subjects], and
 * none of [blockedTags]. With neither tags nor subjects set, everything that isn't blocked shows.
 *
 * [lock] asks for a minimum time at launch and holds the viewer in the profile for it. [dailyMinutes] and
 * [dailyVideos] limit a profile without a lock per local day; null means no limit.
 */
@Serializable
data class ProfileFocus(
    val preset: String = FocusPresets.NONE,
    val kind: FocusKind = FocusKind.LEISURE,
    val allowedTags: Set<FocusTag> = emptySet(),
    val blockedTags: Set<FocusTag> = emptySet(),
    val subjects: List<String> = emptyList(),
    val lock: Boolean = kind == FocusKind.PRODUCTIVE,
    val allowShorts: Boolean = kind == FocusKind.LEISURE,
    val schedule: List<FocusWindow> = emptyList(),
    val dailyMinutes: Int? = null,
    val dailyVideos: Int? = null,
    /** Videos flagged as not belonging here, newest last; see [FocusFlags]. */
    val hiddenVideos: List<String> = emptyList(),
    /** How often a video from each channel was flagged here; a channel is hidden at [FocusFlags.CHANNEL_FLAGS]. */
    val channelFlags: Map<String, Int> = emptyMap(),
)

/**
 * A profile's focus rules together with the profile they belong to, as the picker, the feeds and the usage guard use
 * them. [id] is the profile id, so the day's tally and the session are kept per profile.
 */
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
    val lock: Boolean = kind == FocusKind.PRODUCTIVE,
    val hiddenVideos: Set<String> = emptySet(),
    val hiddenChannels: Set<String> = emptySet(),
) {
    val isProductive: Boolean get() = kind == FocusKind.PRODUCTIVE

    /** With no tags and no subjects every tag that isn't blocked shows, which is how an ordinary profile works. */
    val allowsEverything: Boolean get() = allowedTags.isEmpty() && topics.isEmpty()

    /** Whether this profile restricts anything at all; an unrestricted one is a plain Flow profile. */
    val restricts: Boolean
        get() = !allowsEverything || blockedTags.isNotEmpty() || !allowShorts || lock || dailyMinutes != null || dailyVideos != null

    companion object {
        /** [focus] as the rules of the profile [id] named [name]. */
        fun of(
            id: String,
            name: String,
            focus: ProfileFocus,
        ): FocusMode =
            FocusMode(
                id = id,
                name = name,
                icon = focus.preset.ifBlank { FocusIcons.DEFAULT },
                kind = focus.kind,
                allowedTags = focus.allowedTags,
                blockedTags = focus.blockedTags,
                topics = focus.subjects.map(String::trim).filter(String::isNotEmpty),
                allowShorts = focus.allowShorts,
                schedule = focus.schedule,
                dailyMinutes = focus.dailyMinutes?.takeIf { it > 0 },
                dailyVideos = focus.dailyVideos?.takeIf { it > 0 },
                lock = focus.lock,
                hiddenVideos = focus.hiddenVideos.toSet(),
                hiddenChannels = FocusFlags.hiddenChannels(focus),
            )
    }
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
 * The profile the viewer chose to be in ([modeId] is its profile id). [committedUntil] is when the minimum time they chose runs out; before then a
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
