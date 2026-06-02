package io.github.aedev.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val DONATION_PROMPT_GRACE_MS = 3L * 24L * 60L * 60L * 1000L
private const val DONATION_PROMPT_INTERVAL_MS = 75L * 24L * 60L * 60L * 1000L
private const val DONATION_PROMPT_SHOW_DELAY_MS = 1_500L

@Composable
fun DonationPromptHost(
    enabled: Boolean,
    onNavigateToDonations: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()

    var visible by remember { mutableStateOf(false) }
    var evaluated by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled || evaluated) return@LaunchedEffect
        delay(DONATION_PROMPT_SHOW_DELAY_MS)
        evaluated = true

        if (preferences.donationPromptDisabled.first()) return@LaunchedEffect

        val now = System.currentTimeMillis()
        val firstLaunch = preferences.donationFirstLaunchTime.first()
        if (firstLaunch == 0L) {
            preferences.setDonationFirstLaunchTime(now)
            return@LaunchedEffect
        }
        if (now - firstLaunch < DONATION_PROMPT_GRACE_MS) return@LaunchedEffect

        val lastShown = preferences.donationPromptLastShownTime.first()
        if (lastShown != 0L && now - lastShown < DONATION_PROMPT_INTERVAL_MS) return@LaunchedEffect

        preferences.setDonationPromptShown(now)
        visible = true
    }

    if (visible) {
        DonationPromptDialog(
            onSupport = {
                visible = false
                scope.launch {
                    preferences.setDonationPromptShown(System.currentTimeMillis())
                    onNavigateToDonations()
                }
            },
            onLater = {
                visible = false
                scope.launch {
                    preferences.setDonationPromptShown(System.currentTimeMillis())
                }
            },
            onNever = {
                visible = false
                scope.launch {
                    preferences.setDonationPromptShown(System.currentTimeMillis())
                    preferences.setDonationPromptDisabled(true)
                }
            }
        )
    }
}

@Composable
private fun DonationPromptDialog(
    onSupport: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VolunteerActivism,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.donation_prompt_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.donation_prompt_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.donation_prompt_methods),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onSupport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.donation_prompt_support),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(6.dp))

                TextButton(
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.donation_prompt_later))
                }

                TextButton(
                    onClick = onNever,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.donation_prompt_never),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
