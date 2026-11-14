package io.github.aedev.flow.data.focus

/**
 * The templates a new profile can start from. A profile keeps its template's id in [ProfileFocus.preset] for its icon
 * and its suggested hours; everything else can be changed afterwards. Several profiles may share a template, such as
 * one Study profile per subject.
 */
object FocusPresets {
    /** An ordinary profile, with no restrictions. */
    const val NONE = ""
    const val STUDY = FocusIcons.STUDY
    const val WORK = FocusIcons.WORK
    const val LEARN = FocusIcons.LEARN
    const val MUSIC = FocusIcons.MUSIC
    const val EXPLORE = FocusIcons.EXPLORE

    private const val NINE_AM = 9 * 60
    private const val FOUR_PM = 16 * 60
    private const val SIX_PM = 18 * 60
    private const val EIGHT_PM = 20 * 60
    private const val TEN_PM = 22 * 60

    /** The templates in the order the picker offers them. */
    val templates: List<ProfileFocus> =
        listOf(
            ProfileFocus(
                preset = STUDY,
                kind = FocusKind.PRODUCTIVE,
                allowedTags = setOf(FocusTag.EDUCATION, FocusTag.SCIENCE, FocusTag.FOCUS_MUSIC),
                schedule = listOf(FocusWindow(FocusWindow.WEEKDAYS, startMinute = FOUR_PM, endMinute = EIGHT_PM)),
            ),
            ProfileFocus(
                preset = WORK,
                kind = FocusKind.PRODUCTIVE,
                allowedTags = setOf(FocusTag.TECHNOLOGY, FocusTag.HOWTO, FocusTag.FOCUS_MUSIC),
                schedule = listOf(FocusWindow(FocusWindow.WEEKDAYS, startMinute = NINE_AM, endMinute = FOUR_PM)),
            ),
            ProfileFocus(
                preset = LEARN,
                kind = FocusKind.PRODUCTIVE,
                allowedTags = setOf(FocusTag.EDUCATION, FocusTag.SCIENCE, FocusTag.TECHNOLOGY, FocusTag.HOWTO),
                schedule = listOf(FocusWindow(FocusWindow.WEEKEND, startMinute = NINE_AM, endMinute = SIX_PM)),
            ),
            ProfileFocus(
                preset = MUSIC,
                kind = FocusKind.LEISURE,
                allowedTags = setOf(FocusTag.MUSIC, FocusTag.FOCUS_MUSIC),
                allowShorts = false,
            ),
            ProfileFocus(
                preset = EXPLORE,
                kind = FocusKind.LEISURE,
                schedule = listOf(FocusWindow(FocusWindow.ALL_DAYS, startMinute = EIGHT_PM, endMinute = TEN_PM)),
                dailyMinutes = 60,
                dailyVideos = 10,
            ),
        )

    /** The template [preset] names, or an unrestricted profile's rules for any other id. */
    fun template(preset: String): ProfileFocus = templates.firstOrNull { it.preset == preset } ?: ProfileFocus()

    /** Each template as the rules of a profile whose id and name are the template's own, for tests and previews. */
    val all: List<FocusMode> = templates.map { FocusMode.of(id = it.preset, name = it.preset, focus = it) }
}
