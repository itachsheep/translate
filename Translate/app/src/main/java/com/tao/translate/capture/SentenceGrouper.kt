package com.tao.translate.capture

import kotlin.math.abs

object SentenceGrouper {

    fun group(lines: List<RecognizedLine>): List<String> {
        val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
        if (sorted.isEmpty()) return emptyList()

        val mergedBlocks = mutableListOf<String>()
        var current = sorted.first().text
        var anchor = sorted.first()

        for (index in 1 until sorted.size) {
            val line = sorted[index]
            if (shouldMerge(anchor, line, current)) {
                current = joinText(current, line.text)
            } else {
                mergedBlocks.addAll(splitIntoSentences(current))
                current = line.text
                anchor = line
            }
        }
        mergedBlocks.addAll(splitIntoSentences(current))

        return mergedBlocks
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun shouldMerge(
        previousLine: RecognizedLine,
        nextLine: RecognizedLine,
        currentText: String,
    ): Boolean {
        if (currentText.trim().endsWithSentence()) return false

        val lineHeight = averageLineHeight(previousLine, nextLine)
        val verticalGap = nextLine.top - previousLine.bottom

        if (verticalGap > lineHeight * 1.6f) return false

        val leftDelta = abs(nextLine.left - previousLine.left)
        if (leftDelta > lineHeight * 2.5f && verticalGap > lineHeight * 1.1f) {
            return false
        }

        if (isStandaloneLabel(nextLine.text) && currentText.length > 24 && verticalGap > lineHeight * 1.1f) {
            return false
        }

        return verticalGap <= lineHeight * 1.45f
    }

    private fun joinText(previous: String, next: String): String {
        val left = previous.trimEnd()
        val right = next.trimStart()
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left

        val needsSpace = left.last().isLetterOrDigit() &&
            right.first().isLetterOrDigit() &&
            left.last().code < 128 &&
            right.first().code < 128
        return if (needsSpace) "$left $right" else "$left$right"
    }

    private fun splitIntoSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parts = trimmed.split(SENTENCE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return if (parts.isEmpty()) listOf(trimmed) else parts
    }

    private fun averageLineHeight(first: RecognizedLine, second: RecognizedLine): Float {
        val heights = listOf(first.height, second.height).filter { it > 0 }
        if (heights.isEmpty()) return 48f
        return heights.average().toFloat().coerceAtLeast(24f)
    }

    private fun isStandaloneLabel(text: String): Boolean {
        val words = text.trim().split(Regex("\\s+"))
        return words.size <= 2 && text.length <= 16
    }

    private fun String.endsWithSentence(): Boolean {
        val value = trimEnd()
        if (value.isEmpty()) return false
        val last = value.last()
        return last in SENTENCE_END_CHARS || value.endsWith("...") || value.endsWith("…")
    }

    private val SENTENCE_END_CHARS = setOf('.', '!', '?', '。', '！', '？', '>', '»')
    private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?。！？])\\s+")
}
