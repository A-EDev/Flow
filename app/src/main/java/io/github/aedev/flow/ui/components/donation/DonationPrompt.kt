@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.components.donation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

private val HeartSize = 64.dp
private val HeartIconSize = 28.dp
private val BodySpacing = 12.dp
private const val METHOD_SEPARATOR = " · "

/** Asks, now and then, for support; [enabled] keeps it away from onboarding, the player and PiP. */
@Composable
fun DonationPromptHost(
    enabled: Boolean,
    onNavigateToDonations: () -> Unit,
) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    val viewModel: DonationPromptViewModel = hiltViewModel(activity)
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    LaunchedEffect(enabled) { if (enabled) viewModel.evaluate() }
    if (!visible) return

    var dontAskAgain by rememberSaveable { mutableStateOf(false) }
    val methods =
        (listOf(stringResource(R.string.donation_method_patreon)) + DonationWallets.map { stringResource(it.name) })
            .joinToString(METHOD_SEPARATOR)
    FlowAlertDialog(
        onDismissRequest = { viewModel.dismiss(dontAskAgain) },
        icon = { Heart() },
        title = { Text(stringResource(R.string.donation_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BodySpacing)) {
                Text(stringResource(R.string.donation_prompt_message))
                Text(methods, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = dontAskAgain, role = Role.Checkbox, onValueChange = { dontAskAgain = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontAskAgain, onCheckedChange = null)
                    Text(stringResource(R.string.donation_prompt_never), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.dismiss(dontAskAgain)
                    onNavigateToDonations()
                },
            ) { Text(stringResource(R.string.donation_prompt_support)) }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.dismiss(dontAskAgain) },
            ) { Text(stringResource(R.string.donation_prompt_later)) }
        },
    )
}

@Composable
private fun Heart() {
    Surface(
        shape = MaterialShapes.Heart.toShape(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(HeartSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VolunteerActivism, contentDescription = null, modifier = Modifier.size(HeartIconSize))
        }
    }
}
