package io.github.aedev.flow.data.focus

/**
 * The modes every viewer starts with. They can be edited but not deleted, and keep their ids so a later version can
 * tell them apart from custom modes.
 */
object FocusPresets {
    const val STUDY = "preset_study"
    const val WORK = "preset_work"
    const val LEARN = "preset_learn"
    const val MUSIC = "preset_music"
    const val EXPLORE = "preset_explore"

    private const val NINE_AM = 9 * 60
    private const val SIX_PM = 18 * 60
    private const val TEN_PM = 22 * 60

    val all: List<FocusMode> =
        listOf(
            FocusMode(
                id = STUDY,
                name = "Study",
                icon = FocusIcons.STUDY,
                kind = FocusKind.PRODUCTIVE,
                allowedTags = setOf(FocusTag.EDUCATION, FocusTag.SCIENCE, FocusTag.FOCUS_MUSIC),
                schedule = listOf(FocusWindow(FocusWindow.WEEKDAYS, startMinute = 16 * 60, endMinute = 20 * 60)),
                builtIn = true,
            ),
            FocusMode(
                id = WORK,
                name = "Work",
                icon = FocusIcons.WORK,
                kind = FocusKind.PRODUCTIVE,
                allowedTags = setOf(FocusTag.TECHNOLOGY, FocusTag.HOWTO, FocusTag.FOCUS_MUSIC),
                schedule = listOf(FocusWindow(FocusWindow.WEEKDAYS, startMinute = NINE_AM, endMinute = 16 * 60)),
                builtIn = true,
            ),
            FocusMode(
                id = LEARN,
                name = "Learn",
                icon = FocusIcons.LEARN,
                kind = FocusKind.PRODUCTIVE,
                allowedTags = setOf(FocusTag.EDUCATION, FocusTag.SCIENCE, FocusTag.TECHNOLOGY, FocusTag.HOWTO),
                schedule = listOf(FocusWindow(FocusWindow.WEEKEND, startMinute = NINE_AM, endMinute = SIX_PM)),
                builtIn = true,
            ),
            FocusMode(
                id = MUSIC,
                name = "Music",
                icon = FocusIcons.MUSIC,
                kind = FocusKind.LEISURE,
                allowedTags = setOf(FocusTag.MUSIC, FocusTag.FOCUS_MUSIC),
                allowShorts = false,
                builtIn = true,
            ),
            FocusMode(
                id = EXPLORE,
                name = "Explore",
                icon = FocusIcons.EXPLORE,
                kind = FocusKind.LEISURE,
                schedule = listOf(FocusWindow(FocusWindow.ALL_DAYS, startMinute = 20 * 60, endMinute = TEN_PM)),
                dailyMinutes = 60,
                dailyVideos = 10,
                builtIn = true,
            ),
        )

    /** [stored] with any preset it lacks put back, in preset order first, so a deleted or new preset reappears. */
    fun merge(stored: List<FocusMode>): List<FocusMode> {
        val byId = stored.associateBy { it.id }
        val presets = all.map { preset -> byId[preset.id]?.copy(builtIn = true) ?: preset }
        val custom = stored.filter { mode -> all.none { it.id == mode.id } }.map { it.copy(builtIn = false) }
        return presets + custom
    }
}
