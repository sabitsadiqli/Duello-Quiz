package com.sabit.duelloquiz.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.sabit.duelloquiz.core.designsystem.icon.DuelloIcons
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.FullShape
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import java.util.Locale

private val AzerbaijaniLocale: Locale = Locale.forLanguageTag("az")

/**
 * Placeholder letter of [name] (PROF-02, components.md §13): the first letter or digit, in upper
 * case, Azerbaijani-aware ("i" → "İ", "ı" → "I"). Leading punctuation such as `'`, `.` or `-` is
 * skipped. Works on code points, so a letter outside the BMP is never split. Empty when the name
 * has no letter or digit (the avatar then shows the person icon).
 */
fun avatarLetter(name: String?): String {
    val trimmed = name?.trim().orEmpty()
    val first = trimmed.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst()
    if (!first.isPresent) return ""
    return String(Character.toChars(first.asInt)).uppercase(AzerbaijaniLocale)
}

/** Duel accent ring (components.md §13): self = primary, opponent = tertiary. */
enum class AvatarAccent { None, Self, Opponent }

/**
 * Avatar (components.md §13): the image from [imageUrl] (Coil, centre crop) over a
 * tertiaryContainer circle with the placeholder letter, which stays visible while loading and when
 * there is no URL or the image fails (the duel never waits for an avatar). Without a letter the
 * `person` icon is shown; [deleted] shows the deleted-player variant. Decorative for TalkBack
 * (the surrounding element carries the name).
 */
@Composable
fun DuelloAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp = Sizes.avatar,
    accent: AvatarAccent = AvatarAccent.None,
    deleted: Boolean = false,
) {
    val letter = remember(name) { avatarLetter(name) }
    val large = size >= Sizes.avatarXLarge
    val ringColor = when (accent) {
        AvatarAccent.None -> Color.Unspecified
        AvatarAccent.Self -> MaterialTheme.colorScheme.primary
        AvatarAccent.Opponent -> MaterialTheme.colorScheme.tertiary
    }
    val ringWidth = if (large) Sizes.accentRingLarge else Sizes.accentRing
    val ringModifier = if (accent == AvatarAccent.None) {
        Modifier
    } else {
        Modifier
            .border(ringWidth, ringColor, FullShape)
            .padding(ringWidth)
    }
    val container = if (deleted) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(ringModifier)
            .clip(FullShape)
            .background(container, FullShape)
            .clearAndSetSemantics {},
    ) {
        when {
            deleted -> Icon(
                painter = painterResource(DuelloIcons.PersonOff),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            letter.isEmpty() -> Icon(
                painter = painterResource(DuelloIcons.Person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            else -> Text(
                text = letter,
                style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (!deleted && !imageUrl.isNullOrBlank()) {
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
            Column {
            Row(modifier = Modifier.padding(Spacing.lg)) {
                DuelloAvatar(name = "ilkin")
                DuelloAvatar(name = "Aysel", accent = AvatarAccent.Self, modifier = Modifier.padding(start = Spacing.md))
                DuelloAvatar(name = "ışıq", accent = AvatarAccent.Opponent, modifier = Modifier.padding(start = Spacing.md))
                DuelloAvatar(name = "..", modifier = Modifier.padding(start = Spacing.md))
                DuelloAvatar(name = null, deleted = true, modifier = Modifier.padding(start = Spacing.md))
            }
            DuelloAvatar(
                name = "Murad",
                size = Sizes.avatarXLarge,
                accent = AvatarAccent.Self,
                modifier = Modifier.padding(Spacing.lg),
            )
            }
        }
    }
}
