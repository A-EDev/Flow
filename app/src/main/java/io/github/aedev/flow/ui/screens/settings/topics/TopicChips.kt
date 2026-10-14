package io.github.aedev.flow.ui.screens.settings.topics

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val ChipSpacing = 8.dp
private val SectionSpacing = 12.dp
private val InputSpacing = 12.dp
private val InputTopPadding = 8.dp

/** A titled cloud of chips. */
@Composable
internal fun TopicChipSection(
    title: String,
    modifier: Modifier = Modifier,
    chips: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        chips()
    }
}

/** Topics that can be taken off the list, each with a remove icon a screen reader names. */
@Composable
internal fun RemovableTopicChips(
    topics: List<String>,
    @StringRes removeDescription: Int,
    onRemove: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
        topics.forEach { topic ->
            InputChip(
                selected = false,
                onClick = { onRemove(topic) },
                label = { Text(topic) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(removeDescription, topic),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
internal fun TopicSuggestionChips(
    topics: List<String>,
    onClick: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
        topics.forEach { topic ->
            SuggestionChip(
                onClick = { onClick(topic) },
                label = { Text(topic) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) },
            )
        }
    }
}

/** A single-line field that adds what was typed, from its button or the keyboard's Done key. */
@Composable
internal fun TopicInput(
    label: String,
    placeholder: String,
    actionLabel: String,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }

    fun submit() {
        if (text.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = InputTopPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InputSpacing),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = ::submit, enabled = text.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = actionLabel)
        }
    }
}
