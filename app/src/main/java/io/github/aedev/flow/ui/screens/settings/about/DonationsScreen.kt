package io.github.aedev.flow.ui.screens.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.utils.copyPlainText
import kotlinx.coroutines.launch

private const val PATREON_URL = "https://patreon.com/A_EDev"

private class CryptoWallet(
    val name: Int,
    val ticker: String,
    val address: String,
)

private val Wallets =
    listOf(
        CryptoWallet(R.string.donation_currency_bitcoin, "BTC", "bc1qgmkkxxvzvsymtpfazqfl93jw6k4jgy0xmrtnv8"),
        CryptoWallet(R.string.donation_currency_ethereum, "ETH", "0xfbac6f464fec7fe458e318971a42ba45b305b70e"),
        CryptoWallet(R.string.donation_currency_solana, "SOL", "7b3SLgiVPb8qQUvERSPGRWoFoiGEDvkFuY98M1GEngug"),
        CryptoWallet(R.string.donation_currency_usdt, "USDT", "TRz7VDrTWwCLCfQmYBEJakqcZgbFNWfUMP"),
        CryptoWallet(
            R.string.donation_currency_monero,
            "XMR",
            "8AgaxZnpEvT8VXJpczpL7BQejwSEw97saJmKYqq4zKErbe9bkYSwUhJ813msPPbdYhF11oz4N7tfEj4Zi6k27fKD83ca1if",
        ),
    )

private val HeaderPadding = 24.dp
private val HeaderSpacing = 12.dp
private val HeroIconSize = 48.dp

/** Ways to support the project: Patreon, or a wallet address copied with one tap. */
@Composable
fun DonationsScreen(onNavigateBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipLabel = stringResource(R.string.donation_address_clip_label)
    val copiedMessage = stringResource(R.string.address_copied_toast)

    SettingsPage(
        title = stringResource(R.string.support_donations_title),
        onBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
    ) {
        item("donations.header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = HeaderPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HeaderSpacing),
            ) {
                Icon(
                    Icons.Outlined.VolunteerActivism,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(HeroIconSize),
                )
                Text(
                    text = stringResource(R.string.support_flow_dev_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.support_flow_dev_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { runCatching { uriHandler.openUri(PATREON_URL) } },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(IconPatreon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.support_flow_patreon))
                }
            }
        }
        group(key = "donations.crypto", header = R.string.donations_cryptocurrency, footer = R.string.thank_you_support_message) {
            Wallets.forEach { wallet ->
                row("donations.${wallet.ticker}") { shape ->
                    WalletRow(wallet, shape, clipLabel) {
                        scope.launch {
                            clipboard.copyPlainText(clipLabel, wallet.address)
                            snackbarHostState.showSnackbar(copiedMessage)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WalletRow(
    wallet: CryptoWallet,
    shape: Shape,
    copyLabel: String,
    onCopy: () -> Unit,
) {
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        onClick = onCopy,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        supportingContent = { Text(wallet.address, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        trailingContent = { Icon(Icons.Outlined.ContentCopy, contentDescription = copyLabel) },
        overlineContent = { Text(wallet.ticker) },
    ) {
        Text(stringResource(wallet.name))
    }
}
