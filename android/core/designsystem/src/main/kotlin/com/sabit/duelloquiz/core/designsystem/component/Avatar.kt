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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
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
 * Avatar (components.md §13): the image from [imageUrl] (Coil, centre crop) over a
 * tertiaryContainer circle with the first letter of the display name, which stays visible while
 * loading and when there is no URL or the image fails. Decorative for TalkBack (the surrounding
 * header is merged).
 */
@Composable
fun DuelloAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = Sizes.avatar,
) {
    val letter = remember(name) { avatarLetter(name) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(FullShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer, FullShape)
            .clearAndSetSemantics {},
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
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
