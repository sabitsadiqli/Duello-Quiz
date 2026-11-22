package com.sabit.duelloquiz.feature.exams.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.sabit.duelloquiz.core.designsystem.R
import com.sabit.duelloquiz.core.designsystem.component.SectionOverline
import com.sabit.duelloquiz.core.designsystem.theme.GameSizes
import com.sabit.duelloquiz.core.designsystem.theme.Sizes
import com.sabit.duelloquiz.core.designsystem.theme.Spacing
import com.sabit.duelloquiz.core.designsystem.theme.tabularFigures
import kotlinx.coroutines.flow.distinctUntilChanged

private const val GAP_ID = "gap"

/** Builds the styled text of [spans]; marked words are underline + bold (two cues, components.md §41). */
internal fun annotated(spans: List<ExamMarkup.Span>, gapAlt: String): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when (span) {
            ExamMarkup.Span.Gap -> appendInlineContent(GAP_ID, gapAlt)
            is ExamMarkup.Span.Text -> withStyle(
                SpanStyle(
                    fontWeight = if (ExamMarkup.Style.BOLD in span.styles || ExamMarkup.Style.UNDERLINE in span.styles) FontWeight.Bold else null,
                    fontStyle = if (ExamMarkup.Style.ITALIC in span.styles) FontStyle.Italic else null,
                    textDecoration = if (ExamMarkup.Style.UNDERLINE in span.styles) TextDecoration.Underline else null,
                ),
            ) { append(span.text) }
        }
    }
}

/** What TalkBack reads: marked words announced ("altından xətt çəkilmiş söz: …"), gaps as "boşluq". */
internal fun accessibleText(spans: List<ExamMarkup.Span>, gap: String, marked: (String) -> String): String =
    spans.joinToString("") { span ->
        when (span) {
            ExamMarkup.Span.Gap -> " $gap "
            is ExamMarkup.Span.Text -> if (ExamMarkup.Style.UNDERLINE in span.styles) marked(span.text) else span.text
        }
    }

/** A stem or paragraph in the restricted markup with `{gap}` lines (components.md §41). */
@Composable
fun ExamRichText(
    source: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val spans = remember(source) { ExamMarkup.spans(source) }
    ExamRichSpans(spans, style, modifier, color)
}

@Composable
internal fun ExamRichSpans(
    spans: List<ExamMarkup.Span>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val gap = stringResource(R.string.exam_gap_cd)
    val markedTemplate = stringResource(R.string.exam_marked_word_cd, "%s")
    val text = remember(spans, gap) { annotated(spans, gap) }
    val description = remember(spans, gap, markedTemplate) {
        accessibleText(spans, gap) { word -> markedTemplate.replace("%s", word) }
    }
    val density = LocalDensity.current
    val lineColor = color
    val inline = remember(density, lineColor) {
        mapOf(
            GAP_ID to InlineTextContent(
                Placeholder(
                    width = with(density) { GameSizes.examGapWidth.toSp() },
                    height = 1.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
                ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val stroke = GameSizes.examMarkedUnderline.toPx()
                            drawLine(lineColor, Offset(0f, size.height - stroke), Offset(size.width, size.height - stroke), stroke)
                        },
                )
            },
        )
    }
    val hasMarks = spans.any { it is ExamMarkup.Span.Gap || (it is ExamMarkup.Span.Text && ExamMarkup.Style.UNDERLINE in it.styles) }
    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inline,
        modifier = if (hasMarks) modifier.semantics { contentDescription = description } else modifier,
    )
}

/**
 * Reading passage (components.md §41) as lazy paragraphs with numbers in the start gutter. The
 * first visible paragraph's character offset is reported (EXAM-09: restored by text offset, so a
 * font-scale change lands on the same text) and restored from [initialOffset].
 */
@Composable
fun ExamPassage(
    title: String?,
    passage: String,
    initialOffset: Int,
    onOffsetChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val paragraphs = remember(passage) { ExamMarkup.paragraphs(passage) }
    val headerItems = if (header != null) 1 else 0
    val initialIndex = remember(paragraphs) {
        paragraphs.indexOfLast { it.startOffset <= initialOffset }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (initialOffset > 0) initialIndex + headerItems + 1 else 0)
    LaunchedEffect(listState, paragraphs) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                val paragraph = paragraphs.getOrNull(index - headerItems - 1) ?: paragraphs.firstOrNull()
                onOffsetChanged(if (index <= headerItems) 0 else paragraph?.startOffset ?: 0)
            }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.lg),
        modifier = modifier.fillMaxSize(),
    ) {
        header?.invoke(this)
        item(key = "passage_title") { PassageTitle(title) }
        itemsIndexed(paragraphs, key = { _, p -> "p${p.number}" }) { _, paragraph ->
            PassageParagraph(paragraph, Modifier.padding(top = Spacing.md))
        }
    }
}

/** The passage as a plain column (review card, self-assessment sheet), selection is the caller's. */
@Composable
fun ExamPassageColumn(title: String?, passage: String, modifier: Modifier = Modifier) {
    val paragraphs = remember(passage) { ExamMarkup.paragraphs(passage) }
    Column(modifier = modifier.fillMaxWidth()) {
        PassageTitle(title)
        paragraphs.forEach { PassageParagraph(it, Modifier.padding(top = Spacing.md)) }
    }
}

@Composable
private fun PassageTitle(title: String?) {
    Column {
        SectionOverline(text = stringResource(R.string.exam_passage_overline))
        if (!title.isNullOrBlank()) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PassageParagraph(paragraph: ExamMarkup.Paragraph, modifier: Modifier = Modifier) {
    val numberCd = stringResource(R.string.exam_paragraph_cd, paragraph.number)
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = paragraph.number.toString(),
            style = MaterialTheme.typography.labelMedium.tabularFigures(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(Sizes.examPassageGutter)
                .semantics { contentDescription = numberCd },
        )
        ExamRichSpans(
            spans = paragraph.spans,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
