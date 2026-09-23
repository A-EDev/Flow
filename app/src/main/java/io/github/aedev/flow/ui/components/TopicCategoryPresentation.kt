package io.github.aedev.flow.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog

fun topicCategoryIcon(iconKey: String): ImageVector =
    when (iconKey) {
        NeuroTopicCatalog.ICON_GAMING -> Icons.Outlined.SportsEsports
        NeuroTopicCatalog.ICON_MUSIC -> Icons.Outlined.MusicNote
        NeuroTopicCatalog.ICON_TECHNOLOGY -> Icons.Outlined.Terminal
        NeuroTopicCatalog.ICON_ENTERTAINMENT -> Icons.Outlined.Movie
        NeuroTopicCatalog.ICON_EDUCATION -> Icons.Outlined.School
        NeuroTopicCatalog.ICON_HEALTH -> Icons.Outlined.FitnessCenter
        NeuroTopicCatalog.ICON_LIFESTYLE -> Icons.Outlined.Restaurant
        NeuroTopicCatalog.ICON_CREATIVE -> Icons.Outlined.Brush
        NeuroTopicCatalog.ICON_SCIENCE -> Icons.Outlined.Science
        NeuroTopicCatalog.ICON_NEWS -> Icons.AutoMirrored.Outlined.Article
        else -> Icons.Outlined.Category
    }

/** The localized name of a catalogue category; the catalogue itself only carries English keys. */
@StringRes
fun topicCategoryNameRes(categoryName: String): Int =
    when {
        categoryName.contains("Gaming") -> R.string.category_gaming
        categoryName.contains("Music") -> R.string.category_music
        categoryName.contains("Technology") -> R.string.category_technology
        categoryName.contains("Entertainment") -> R.string.category_entertainment
        categoryName.contains("Education") -> R.string.category_education
        categoryName.contains("Health & Fitness") -> R.string.category_health_fitness
        categoryName.contains("Lifestyle") -> R.string.category_lifestyle
        categoryName.contains("Creative") -> R.string.category_creative
        categoryName.contains("Science & Nature") -> R.string.category_science_nature
        else -> R.string.category_news_current_events
    }
