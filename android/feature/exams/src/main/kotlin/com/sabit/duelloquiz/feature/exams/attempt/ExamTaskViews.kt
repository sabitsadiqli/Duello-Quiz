package com.sabit.duelloquiz.feature.exams.attempt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.ExamFlagToggle
import com.sabit.duelloquiz.core.designsystem.component.OptionCard
import com.sabit.duelloquiz.core.designsystem.component.OptionCardState
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.ExamWrittenFieldShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import com.sabit.duelloquiz.core.model.exam.ExamTaskType

/** B1 task line: "Tapşırıq 14 · Qapalı" + the flag toggle. */
@Composable
internal fun TaskLine(task: ExamTaskUi, readOnly: Boolean, onAction: (ExamAttemptAction) -> Unit) {
    val type = stringResource(if (task.type == ExamTaskType.WRITTEN) R.string.exam_task_type_written else R.string.exam_task_type_closed)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.exam_task_label, task.number, type),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ExamFlagToggle(
            flagged = task.flagged,
            label = stringResource(if (task.flagged) R.string.exam_flagged else R.string.exam_flag),
            onToggle = { onAction(ExamAttemptAction.ToggleFlag(task.id)) },
            enabled = !readOnly,
        )
    }
}

/** B2: option cards A–E (Chosen = §3a), "Cavabı sil" once chosen; no feedback before submit. */
@Composable
internal fun ClosedAnswer(task: ExamTaskUi, readOnly: Boolean, onAction: (ExamAttemptAction) -> Unit) {
    val selectedCd = stringResource(R.string.exam_option_selected_cd)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.selectableGroup()) {
        task.options.forEach { option ->
            val chosen = option.id == task.selectedOptionId
            OptionCard(
                letter = option.letter,
                text = option.text,
                state = if (chosen) OptionCardState.Chosen else OptionCardState.Default,
                contentDescription = stringResource(R.string.exam_option_cd, option.letter, option.text),
                stateDescription = if (chosen) selectedCd else null,
                onClick = { onAction(ExamAttemptAction.SelectOption(task.id, option.id)) },
                enabled = !readOnly,
            )
        }
        if (task.selectedOptionId != null && !readOnly) {
            TextButton(
                onClick = { onAction(ExamAttemptAction.ClearAnswer(task.id)) },
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = Sizes.minTouchTarget),
            ) {
                Icon(painterResource(DuelloIcons.Backspace), contentDescription = null, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.width(Spacing.xs))
                Text(text = stringResource(R.string.exam_clear_answer), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** B4 / §43: grows with the text, 1 000 characters, autocorrect off, English keyboard hint. */
@Composable
internal fun WrittenAnswer(task: ExamTaskUi, maxLength: Int, language: String, readOnly: Boolean, onAction: (ExamAttemptAction) -> Unit) {
    val atLimit = task.text.length >= maxLength
    val hint = remember(language) { LocaleList(Locale(language)) }
    Column {
        Text(text = stringResource(R.string.exam_written_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(Spacing.xs))
        OutlinedTextField(
            value = task.text,
            onValueChange = { onAction(ExamAttemptAction.TextChanged(task.id, it.take(maxLength))) },
            readOnly = readOnly,
            isError = atLimit,
            placeholder = { Text(stringResource(R.string.exam_written_placeholder)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = ExamWrittenFieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
                hintLocales = hint,
            ),
            supportingText = {
                Row {
                    Text(
                        text = if (atLimit) stringResource(R.string.exam_written_limit, maxLength) else stringResource(R.string.exam_written_supporting),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.exam_written_counter, task.text.length, maxLength),
                        style = MaterialTheme.typography.bodySmall.tabularFigures(),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.examWrittenFieldMinHeight)
        )
    }
}
