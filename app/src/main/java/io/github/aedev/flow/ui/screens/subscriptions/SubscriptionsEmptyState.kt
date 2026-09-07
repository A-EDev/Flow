package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.extendedColors

private const val ICON_TINT_ALPHA = 0.5f
private val IconSize = 80.dp
private val IconBottomPadding = 16.dp
private val TitleBodySpacing = 8.dp

@Composable
internal fun SubscriptionsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Subscriptions,
            contentDescription = null,
            modifier =
                Modifier
                    .size(IconSize)
                    .padding(bottom = IconBottomPadding),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = ICON_TINT_ALPHA),
        )
        Text(
            text = stringResource(R.string.no_subscriptions_yet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TitleBodySpacing))
        Text(
            text = stringResource(R.string.empty_subscriptions_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
