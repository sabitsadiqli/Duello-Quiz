package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import java.util.Locale

private val AzerbaijaniLocale: Locale = Locale.forLanguageTag("az")

/**
 * First letter of [name] in upper case, Azerbaijani-aware ("i" → "İ", "ı" → "I").
 * Works on code points, so a letter outside the BMP is never split. Empty for blank names.
 */
fun avatarLetter(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    val first = trimmed.codePointAt(0)
    return String(Character.toChars(first)).uppercase(AzerbaijaniLocale)
}

/**
 * Avatar (components.md §13), letter fallback only: tertiaryContainer circle with the first
 * letter of the display name. Decorative for TalkBack (the surrounding header is merged).
 * Image loading (Coil 3, Google photo URL) comes with the profile feature.
 */
@Composable
fun DuelloAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatar,
) {
    val letter = remember(name) { avatarLetter(name) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.tertiaryContainer, FullShape)
            .clearAndSetSemantics {},
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@PreviewLightDark
@Composable
private fun AvatarPreview() {
    DuelloTheme {
        Surface {
            Row(modifier = Modifier.padding(Spacing.lg)) {
                DuelloAvatar(name = "ilkin")
                DuelloAvatar(name = "Aysel", modifier = Modifier.padding(start = Spacing.md))
                DuelloAvatar(name = "ışıq", modifier = Modifier.padding(start = Spacing.md))
            }
        }
    }
}
