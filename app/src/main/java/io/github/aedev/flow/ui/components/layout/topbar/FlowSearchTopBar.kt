package io.github.aedev.flow.ui.components.layout.topbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

/** Search variant for filtering a screen's own content in place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    autoFocus: Boolean = true,
    windowInsets: WindowInsets = FlowTopBarDefaults.WindowInsets,
) {
    val focusRequester = remember { FocusRequester() }
    val reduceMotion = rememberFlowReduceMotion()

    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    FlowTopBar(
        modifier = modifier,
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                placeholder = {
                    Text(text = placeholder, style = MaterialTheme.typography.bodyLarge)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )
        },
        onBack = onClose,
        actions = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter =
                    fadeIn(
                        tween(
                            durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.EnterEasing,
                        ),
                    ) +
                        expandHorizontally(
                            tween(
                                durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                                easing = FlowMotion.EnterEasing,
                            ),
                        ),
                exit =
                    fadeOut(
                        tween(
                            durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.ExitEasing,
                        ),
                    ) +
                        shrinkHorizontally(
                            tween(
                                durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                                easing = FlowMotion.ExitEasing,
                            ),
                        ),
                label = "searchClearAction",
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.top_bar_clear_search),
                    )
                }
            }
            actions()
        },
        globalActions = FlowGlobalActionsMode.None,
        windowInsets = windowInsets,
    )
}
