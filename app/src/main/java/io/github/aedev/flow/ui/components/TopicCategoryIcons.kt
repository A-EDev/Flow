package io.github.aedev.flow.ui.components

import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.Icons
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
import io.github.aedev.flow.data.recommendation.NeuroTopicCatalog

fun topicCategoryIcon(iconKey: String): ImageVector = when (iconKey) {
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
