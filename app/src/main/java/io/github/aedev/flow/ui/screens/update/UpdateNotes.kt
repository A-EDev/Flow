package io.github.aedev.flow.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.update.NoteSpan
import io.github.aedev.flow.data.update.NoteText
import io.github.aedev.flow.data.update.ReleaseNotes
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.flowSegmentShape

private val LinePadding = 16.dp
private val LineVerticalPadding = 12.dp
private val BadgePadding = 10.dp
private val BadgeVerticalPadding = 6.dp
private val BadgeSpacing = 6.dp
private val BadgeIconSize = 16.dp

/** The release notes as list items: the intro paragraphs, then one segmented group per section. */
internal fun LazyListScope.releaseNotes(notes: ReleaseNotes) {
    itemsIndexed(notes.intro, key = { index, _ -> "intro-$index" }) { _, text ->
        NoteParagraph(text, Modifier.padding(vertical = FlowSegmentedGap * 2))
    }
    notes.sections.forEachIndexed { sectionIndex, section ->
        item(key = "section-$sectionIndex") { FlowSectionHeader(section.title) }
        itemsIndexed(section.items, key = { index, _ -> "section-$sectionIndex-$index" }) { index, text ->
            Surface(
                shape = flowSegmentShape(index, section.items.size),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(bottom = FlowSegmentedGap),
            ) {
                NoteParagraph(text, Modifier.padding(horizontal = LinePadding, vertical = LineVerticalPadding))
            }
        }
    }
}

@Composable
private fun NoteParagraph(
    text: NoteText,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    Text(
        text = text.toAnnotated(linkColor),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

private fun NoteText.toAnnotated(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is NoteSpan.Plain -> {
                    append(span.text)
                }

                is NoteSpan.Strong -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
                }

                is NoteSpan.Link -> {
                    val style = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium))
                    withLink(LinkAnnotation.Url(span.url, style)) { append(span.text) }
                }
            }
        }
    }

/** A small fact about the release: the version change, the download size, the date. */
@Composable
internal fun ReleaseFact(
    icon: ImageVector,
    text: String,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = BadgePadding, vertical = BadgeVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(BadgeSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(BadgeIconSize))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
