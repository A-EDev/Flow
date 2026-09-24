package io.github.aedev.flow.ui.startup

import androidx.annotation.StyleRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.aedev.flow.R

private const val DARK_LUMINANCE = 0.5f

/** The background family of the app theme, which the next launch's splash should open on. */
enum class SplashTone { LIGHT, DARK, BLACK }

fun splashTone(background: Color): SplashTone =
    when {
        background == Color.Black -> SplashTone.BLACK
        background.luminance() < DARK_LUMINANCE -> SplashTone.DARK
        else -> SplashTone.LIGHT
    }

/**
 * The starting style for a launcher alias on a tone. The ghost icon is a white outline, so on a
 * light splash it falls back to the Flow badge rather than vanish.
 */
@StyleRes
fun splashThemeFor(
    iconSuffix: String,
    tone: SplashTone,
): Int =
    when (tone) {
        SplashTone.LIGHT -> {
            when (iconSuffix) {
                ".IconFlowPlay" -> R.style.Theme_Flow_Starting_Light_Play
                ".IconAmoled" -> R.style.Theme_Flow_Starting_Light_Amoled
                ".IconMonochrome" -> R.style.Theme_Flow_Starting_Light_Monochrome
                ".IconDynamic" -> R.style.Theme_Flow_Starting_Light_Dynamic
                else -> R.style.Theme_Flow_Starting_Light
            }
        }

        SplashTone.DARK -> {
            when (iconSuffix) {
                ".IconFlowPlay" -> R.style.Theme_Flow_Starting_Dark_Play
                ".IconAmoled" -> R.style.Theme_Flow_Starting_Dark_Amoled
                ".IconMonochrome" -> R.style.Theme_Flow_Starting_Dark_Monochrome
                ".IconGhost" -> R.style.Theme_Flow_Starting_Dark_Ghost
                ".IconDynamic" -> R.style.Theme_Flow_Starting_Dark_Dynamic
                else -> R.style.Theme_Flow_Starting_Dark
            }
        }

        SplashTone.BLACK -> {
            when (iconSuffix) {
                ".IconFlowPlay" -> R.style.Theme_Flow_Starting_Black_Play
                ".IconAmoled" -> R.style.Theme_Flow_Starting_Black_Amoled
                ".IconMonochrome" -> R.style.Theme_Flow_Starting_Black_Monochrome
                ".IconGhost" -> R.style.Theme_Flow_Starting_Black_Ghost
                ".IconDynamic" -> R.style.Theme_Flow_Starting_Black_Dynamic
                else -> R.style.Theme_Flow_Starting_Black
            }
        }
    }
