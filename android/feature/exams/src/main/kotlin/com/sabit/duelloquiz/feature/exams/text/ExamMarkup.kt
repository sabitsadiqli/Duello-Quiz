package com.sabit.duelloquiz.feature.exams.text

/**
 * Restricted markup of passages and stems (ADR 0002 §2, components.md §41): paragraphs separated
 * by a blank line, line breaks, `**bold**`, `*italic*`, `__underline__` (marked words) and the
 * `{gap}` token. No HTML. Unbalanced markers are shown as typed. Pure, unit-tested.
 */
object ExamMarkup {

    enum class Style { BOLD, ITALIC, UNDERLINE }

    sealed interface Span {
        data class Text(val text: String, val styles: Set<Style>) : Span

        /** `{gap}`: drawn as a 40dp line, read as "boşluq". */
        data object Gap : Span
    }

    /** A paragraph with its character offset in the source (EXAM-09 scroll restore by text offset). */
    data class Paragraph(val number: Int, val startOffset: Int, val spans: List<Span>)

    fun paragraphs(source: String): List<Paragraph> {
        val normalized = source.replace("\r\n", "\n")
        val result = mutableListOf<Paragraph>()
        var offset = 0
        var number = 0
        for (block in normalized.split(PARAGRAPH_BREAK)) {
            val start = normalized.indexOf(block, offset).coerceAtLeast(offset)
            offset = start + block.length
            val trimmed = block.trim('\n')
            if (trimmed.isBlank()) continue
            number++
            result += Paragraph(number, start, spans(trimmed))
        }
        return result
    }

    fun spans(source: String): List<Span> {
        val out = mutableListOf<Span>()
        val active = mutableSetOf<Style>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                out += Span.Text(buffer.toString(), active.toSet())
                buffer.clear()
            }
        }
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith(GAP, i) -> {
                    flush()
                    out += Span.Gap
                    i += GAP.length
                }
                source.startsWith("**", i) && (Style.BOLD in active || source.indexOf("**", i + 2) > i + 2) -> {
                    flush()
                    toggle(active, Style.BOLD)
                    i += 2
                }
                source.startsWith("__", i) && (Style.UNDERLINE in active || source.indexOf("__", i + 2) > i + 2) -> {
                    flush()
                    toggle(active, Style.UNDERLINE)
                    i += 2
                }
                source[i] == '*' && (Style.ITALIC in active || hasClosingSingleStar(source, i + 1)) -> {
                    flush()
                    toggle(active, Style.ITALIC)
                    i += 1
                }
                else -> {
                    buffer.append(source[i])
                    i += 1
                }
            }
        }
        flush()
        return out
    }

    /** Plain text without markers (TalkBack fallback, previews). */
    fun plain(source: String, gap: String): String = spans(source).joinToString("") {
        when (it) {
            is Span.Text -> it.text
            Span.Gap -> gap
        }
    }

    private fun toggle(active: MutableSet<Style>, style: Style) {
        if (!active.remove(style)) active += style
    }

    private fun hasClosingSingleStar(source: String, from: Int): Boolean {
        var j = from
        while (j < source.length) {
            if (source[j] == '*' && !source.startsWith("**", j)) return j > from
            if (source.startsWith("**", j)) j += 2 else j++
        }
        return false
    }

    private const val GAP = "{gap}"
    private val PARAGRAPH_BREAK = Regex("\n[ \t]*\n")
}
