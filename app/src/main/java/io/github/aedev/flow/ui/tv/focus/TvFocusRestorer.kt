package io.github.aedev.flow.ui.tv.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer

/**
 * Focus contract for every TV row and grid: the children form one focus group, and coming back
 * into it returns focus to the item that last held it, falling back to [fallback] (by default the
 * spatially nearest child) the first time.
 */
fun Modifier.tvRowFocus(fallback: FocusRequester = FocusRequester.Default): Modifier = focusRestorer(fallback).focusGroup()
