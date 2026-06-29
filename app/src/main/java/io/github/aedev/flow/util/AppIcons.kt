package io.github.aedev.flow.util

object AppIcons {
    /** Component name prefix shared by every alias (the application id namespace). */
    const val NAMESPACE = "io.github.aedev.flow"

    /** The alias enabled by default in the manifest. Used as a safe fallback. */
    const val DEFAULT_SUFFIX = ".IconFlowRed"

    /** Every launcher alias suffix, in manifest declaration order. */
    val ALL_SUFFIXES = listOf(
        ".IconFlowRed",
        ".IconFlowLight",
        ".IconFlowPlay",
        ".IconAmoled",
        ".IconMonochrome",
        ".IconGhost",
        ".IconDynamic",
        ".IconMaterialSky",
        ".IconMaterialMint"
    )
}
