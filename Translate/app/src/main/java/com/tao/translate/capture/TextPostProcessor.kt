package com.tao.translate.capture

import com.google.mlkit.vision.text.Text

data class RecognizedLine(
    val text: String,
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val confidence: Float,
) {
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

object TextPostProcessor {

    private val cjkRegex = Regex("[\\u4e00-\\u9fff]")

    fun extractLines(result: Text): List<RecognizedLine> {
        return result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val text = line.text.trim()
                if (text.isEmpty()) return@mapNotNull null
                val box = line.boundingBox

                RecognizedLine(
                    text = text,
                    top = box?.top ?: 0,
                    left = box?.left ?: 0,
                    bottom = box?.bottom ?: ((box?.top ?: 0) + 32),
                    right = box?.right ?: 0,
                    confidence = line.confidence ?: 1f,
                )
            }
        }
    }

    fun mergeLines(latinLines: List<RecognizedLine>, chineseLines: List<RecognizedLine>): List<RecognizedLine> {
        val latinCjkCount = latinLines.sumOf { countCjk(it.text) }
        val chineseCjkCount = chineseLines.sumOf { countCjk(it.text) }

        val primaryLines = when {
            chineseCjkCount >= 2 && chineseCjkCount > latinCjkCount -> chineseLines
            latinLines.isNotEmpty() -> latinLines
            else -> chineseLines
        }

        val secondaryLines = if (primaryLines === chineseLines) latinLines else chineseLines
        val merged = linkedSetOf<RecognizedLine>()

        primaryLines
            .sortedWith(compareBy({ it.top }, { it.left }))
            .forEach { line ->
                if (isAcceptableLine(line)) {
                    merged.add(line)
                }
            }

        secondaryLines
            .sortedWith(compareBy({ it.top }, { it.left }))
            .forEach { line ->
                if (!isAcceptableLine(line)) return@forEach
                if (countCjk(line.text) > 0 || !hasSimilarLine(line, merged)) {
                    merged.add(line)
                }
            }

        return merged.sortedWith(compareBy({ it.top }, { it.left }))
    }

    private fun isAcceptableLine(line: RecognizedLine): Boolean {
        if (line.text.length < 2) return false
        if (line.confidence < 0.55f) return false
        return !isGarbageLine(line.text)
    }

    private fun isGarbageLine(text: String): Boolean {
        if (text.length <= 2 && !text.all { it.isLetterOrDigit() }) return true

        val letters = text.count { it.isLetter() }
        val digits = text.count { it.isDigit() }
        val cjk = countCjk(text)
        val allowedSymbols = text.count {
            it in " .,!?@#%&*()-_':;/\"+=[]{}<>"
        }
        val weird = text.length - letters - digits - cjk - allowedSymbols - text.count { it.isWhitespace() }

        if (letters + cjk == 0 && digits == 0) return true
        if (weird > (text.length * 0.25f).toInt()) return true

        val singleLetterTokens = text.split(Regex("\\s+"))
            .count { token -> token.length == 1 && token[0].isLetter() }
        if (singleLetterTokens >= 2 && text.length < 20) return true

        return false
    }

    private fun hasSimilarLine(candidate: RecognizedLine, existing: Set<RecognizedLine>): Boolean {
        return existing.any { line ->
            normalize(line.text) == normalize(candidate.text) ||
                isFuzzyDuplicate(candidate.text, line.text)
        }
    }

    private fun isFuzzyDuplicate(a: String, b: String): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        if (left.length >= 4 && right.length >= 4) {
            if (left.contains(right) || right.contains(left)) return true
        }
        return false
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fff]"), "")
    }

    private fun countCjk(text: String): Int {
        return cjkRegex.findAll(text).count()
    }
}
