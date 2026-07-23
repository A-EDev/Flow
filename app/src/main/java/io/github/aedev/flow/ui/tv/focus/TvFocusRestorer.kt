package io.github.aedev.flow.ui.tv.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier

/**
 * Focus contract for every TV row/grid: children form one focus group so D-pad
 * traversal enters the container as a unit, and 2D focus search picks the
 * spatially nearest child on re-entry.
 *
 * Deliberately NOT using focusRestorer(): on compose-ui 1.7.x it pins the
 * last-focused lazy child and double-releases the pin when the container
 * detaches (e.g. shell -> full-screen player swap), crashing with
 * "Release should only be called once" (fixed upstream in 1.8, which we can't
 * take while Kotlin 1.9 caps the BOM at 2025.02.00).
 */
fun Modifier.tvRowFocus(): Modifier = this.focusGroup()
