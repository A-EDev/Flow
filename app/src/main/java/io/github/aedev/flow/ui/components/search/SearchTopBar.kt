package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import kotlinx.coroutines.launch

/**
 * The Search tab's bar.
 *
 * Material's own search bar owns the collapse/expand morph, the full-screen suggestion surface,
 * predictive back and the keyboard — all of which the previous hand-rolled field re-implemented.
 * The leading affordance mirrors in RTL, matching the music search screen and the rest of the app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchTopBar(
    state: SearchBarState,
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onVoiceSearch: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    suggestions: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val collapse: () -> Unit = { scope.launch { state.animateToCollapsed() } }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState,
            state,
            { submitted ->
                collapse()
                onSearch(submitted)
            },
            placeholder = { Text(stringResource(R.string.search_videos_channels_placeholder)) },
            leadingIcon = {
                if (state.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { collapse() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                        )
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                        )
                    }
                }
            },
            trailingIcon = {
                if (textFieldState.text.isEmpty()) {
                    IconButton(onClick = onVoiceSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = stringResource(R.string.voice_search_cd),
                        )
                    }
                } else {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            },
        )
    }

    TopSearchBar(
        state = state,
        inputField = inputField,
        modifier = modifier,
        windowInsets = windowInsets,
    )

    ExpandedFullScreenSearchBar(
        state = state,
        inputField = inputField,
        content = suggestions,
    )
}
