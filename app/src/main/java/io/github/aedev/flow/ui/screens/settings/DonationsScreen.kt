package io.github.aedev.flow.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar

/** Patreon logo path used by the donation action. */
private val IconPatreon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Patreon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(22.957f, 7.21f)
                curveToRelative(-0.004f, -3.064f, -2.391f, -5.576f, -5.191f, -6.482f)
                curveToRelative(-3.478f, -1.125f, -8.064f, -0.962f, -11.384f, 0.604f)
                curveTo(2.357f, 3.231f, 1.093f, 7.391f, 1.046f, 11.54f)
                curveToRelative(-0.039f, 3.411f, 0.302f, 12.396f, 5.369f, 12.46f)
                curveToRelative(3.765f, 0.047f, 4.326f, -4.804f, 6.068f, -7.141f)
                curveToRelative(1.24f, -1.662f, 2.836f, -2.132f, 4.801f, -2.618f)
                curveToRelative(3.376f, -0.836f, 5.678f, -3.501f, 5.673f, -7.031f)
                close()
            }
        }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonationsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.support_donations_title),
                onBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "support") {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VolunteerActivism,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.support_flow_dev_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.support_flow_dev_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { uriHandler.openUri("https://patreon.com/A_EDev") },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                    ) {
                        Icon(
                            imageVector = IconPatreon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.support_flow_patreon),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            item(key = "support-divider") { HorizontalDivider() }

            item(key = "crypto-header") {
                Text(
                    text = stringResource(R.string.donations_cryptocurrency),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
                )
            }

            item(key = "crypto-addresses") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.large,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    Column {
                        DonationRow(
                            currencyName = "Bitcoin",
                            ticker = "BTC",
                            address = "bc1qgmkkxxvzvsymtpfazqfl93jw6k4jgy0xmrtnv8",
                            context = context,
                        )
                        CryptoDivider()
                        DonationRow(
                            currencyName = "Ethereum",
                            ticker = "ETH",
                            address = "0xfbac6f464fec7fe458e318971a42ba45b305b70e",
                            context = context,
                        )
                        CryptoDivider()
                        DonationRow(
                            currencyName = "Solana",
                            ticker = "SOL",
                            address = "7b3SLgiVPb8qQUvERSPGRWoFoiGEDvkFuY98M1GEngug",
                            context = context,
                        )
                        CryptoDivider()
                        DonationRow(
                            currencyName = "USDT (TRC-20)",
                            ticker = "USDT",
                            address = "TRz7VDrTWwCLCfQmYBEJakqcZgbFNWfUMP",
                            context = context,
                        )
                        CryptoDivider()
                        DonationRow(
                            currencyName = "Monero",
                            ticker = "XMR",
                            address = "8AgaxZnpEvT8VXJpczpL7BQejwSEw97saJmKYqq4zKErbe9bkYSwUhJ813msPPbdYhF11oz4N7tfEj4Zi6k27fKD83ca1if",
                            context = context,
                        )
                    }
                }
            }

            item(key = "thanks") {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.thank_you_support_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun DonationRow(
    currencyName: String,
    ticker: String,
    address: String,
    context: Context,
) {
    val copyLabel = stringResource(R.string.donation_address_clip_label)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = copyLabel,
                    role = Role.Button,
                    onClick = { copyToClipboard(context, address) },
                ).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currencyName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = ticker,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CryptoDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(context.getString(R.string.donation_address_clip_label), text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.address_copied_toast), Toast.LENGTH_SHORT).show()
}
