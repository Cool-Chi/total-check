package com.example.totalcheck.data.parser

enum class ImportFormatMode {
    SINGLE_LINE,
    MULTILINE_MARKDOWN,
    EMPTY
}

data class ParsedItemDraft(
    val label: String,
    val isChecked: Boolean = false
)

data class ParsedChecklistDraft(
    val title: String,
    val items: List<ParsedItemDraft>,
    val detectedMode: ImportFormatMode
)

/**
 * High-tolerance parser supporting both single-line delimiter strings
 * and multi-line Markdown / plain-text / CSV checklists.
 */
object ChecklistTextParser {

    private val HORIZONTAL_RULE_REGEX = Regex("""^[-*_]{3,}\s*$""")
    private val HEADING_PREFIX_REGEX = Regex("""^#{1,6}\s+""")
    private val LIST_OR_CHECKBOX_PREFIX_REGEX = Regex(
        """^(?:\d+[.)、]\s+|[-*+•·]\s+|\[[xX✓✔☑\s]?\]\s*|[✓✔☑☐□]\s*)"""
    )
    private val CHECKED_PREFIX_REGEX = Regex(
        """^(?:\d+[.)、]\s*|[-*+•·]\s*)?(?:\[[xX✓✔☑]\]|[✓✔☑])\s*"""
    )
    private val UNCHECKED_CHECKBOX_PREFIX_REGEX = Regex(
        """^(?:\d+[.)、]\s*|[-*+•·]\s*)?(?:\[\s*\]|[☐□])\s*"""
    )
    private val UNORDERED_BULLET_PREFIX_REGEX = Regex("""^[-*+•·]\s+""")
    private val ORDERED_NUMBER_PREFIX_REGEX = Regex("""^\d+[.)、]\s*""")
    private val DELIMITER_SPLIT_REGEX = Regex("""[,，、;；]+""")
    private val DELIMITER_CONTAINS_REGEX = Regex("""[,，、;；]""")
    private val CSV_HEADER_REGEX = Regex(
        """^"?(?:label|item|name|項目|名稱|项目|名称)"?\s*[,，]\s*"?(?:ischecked|is_checked|checked|status|狀態|状态|已備|已备)"?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val CSV_ROW_REGEX = Regex(
        """^(?:"([^"]+)"|([^,，]+?))\s*[,，]\s*"?(true|false|1|0|checked|unchecked|已備|已备|待備|待备|yes|no|✓|✔|☑)"?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val CHECKED_CSV_VALUES = setOf("true", "1", "checked", "已備", "已备", "yes", "✓", "✔", "☑")

    fun parse(rawText: String): ParsedChecklistDraft {
        val cleanedLines = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.replace("\uFEFF", "").trim() }
            .filter { it.isNotEmpty() && !HORIZONTAL_RULE_REGEX.matches(it) }
            .toList()

        if (cleanedLines.isEmpty()) {
            return ParsedChecklistDraft(
                title = "",
                items = emptyList(),
                detectedMode = ImportFormatMode.EMPTY
            )
        }

        // Handle two-line variant where line 1 is strictly a header ending in colon
        // and line 2 is a delimiter-separated single-line list.
        val isTwoLineHeaderPlusSingleLine = cleanedLines.size == 2 &&
            endsWithColon(cleanedLines[0]) &&
            !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(cleanedLines[0]) &&
            !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(cleanedLines[1]) &&
            !CSV_ROW_REGEX.matches(cleanedLines[1]) &&
            DELIMITER_CONTAINS_REGEX.containsMatchIn(cleanedLines[1])

        val draft = if (cleanedLines.size == 1 || isTwoLineHeaderPlusSingleLine) {
            parseSingleLineMode(cleanedLines)
        } else {
            parseMultiLineMode(cleanedLines)
        }

        return if (draft.items.isEmpty()) {
            draft.copy(detectedMode = ImportFormatMode.EMPTY)
        } else {
            draft
        }
    }

    private fun parseSingleLineMode(lines: List<String>): ParsedChecklistDraft {
        val title: String
        val itemsText: String

        if (lines.size == 2) {
            title = cleanTitle(lines[0].dropLast(1))
            itemsText = lines[1]
        } else {
            val line = lines[0]
            val colonIdx = findTitleColonIndex(line)
            if (colonIdx > 0) {
                title = cleanTitle(line.substring(0, colonIdx))
                itemsText = line.substring(colonIdx + 1)
            } else {
                title = ""
                itemsText = line
            }
        }

        val items = splitSingleLineItems(itemsText)
        return ParsedChecklistDraft(
            title = title,
            items = items,
            detectedMode = ImportFormatMode.SINGLE_LINE
        )
    }

    private fun parseMultiLineMode(rawLines: List<String>): ParsedChecklistDraft {
        val lines = if (rawLines.isNotEmpty() && CSV_HEADER_REGEX.matches(rawLines[0])) {
            rawLines.drop(1)
        } else {
            rawLines
        }

        if (lines.isEmpty()) {
            return ParsedChecklistDraft("", emptyList(), ImportFormatMode.EMPTY)
        }

        val firstLine = lines[0]
        var title = ""
        val bodyLines = mutableListOf<String>()

        val firstIsHeading = HEADING_PREFIX_REGEX.containsMatchIn(firstLine)
        val firstEndsWithColon = endsWithColon(firstLine) &&
            !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(firstLine)
        val firstInlineColonIdx = findTitleColonIndex(firstLine)
        val firstIsInlineTitleWithItems = firstInlineColonIdx > 0 &&
            firstInlineColonIdx < firstLine.lastIndex &&
            !CSV_ROW_REGEX.matches(firstLine)
        val firstHasNoPrefix = !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(firstLine) &&
            !CSV_ROW_REGEX.matches(firstLine) &&
            !DELIMITER_CONTAINS_REGEX.containsMatchIn(firstLine)
        val secondHasListFeature = lines.size >= 2 && (
            LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(lines[1]) ||
                CSV_ROW_REGEX.matches(lines[1])
            )

        when {
            firstIsHeading -> {
                title = cleanTitle(firstLine)
                bodyLines.addAll(lines.drop(1))
            }
            firstEndsWithColon -> {
                title = cleanTitle(firstLine.dropLast(1))
                bodyLines.addAll(lines.drop(1))
            }
            firstIsInlineTitleWithItems -> {
                title = cleanTitle(firstLine.substring(0, firstInlineColonIdx))
                val remainder = firstLine.substring(firstInlineColonIdx + 1).trim()
                if (remainder.isNotEmpty()) {
                    bodyLines.add(remainder)
                }
                bodyLines.addAll(lines.drop(1))
            }
            firstHasNoPrefix && secondHasListFeature -> {
                title = cleanTitle(firstLine)
                bodyLines.addAll(lines.drop(1))
            }
            else -> {
                bodyLines.addAll(lines)
            }
        }

        val items = mutableListOf<ParsedItemDraft>()
        for (line in bodyLines) {
            if (CSV_HEADER_REGEX.matches(line)) continue
            parseMultiLineEntry(line, items)
        }

        return ParsedChecklistDraft(
            title = title,
            items = items,
            detectedMode = ImportFormatMode.MULTILINE_MARKDOWN
        )
    }

    private fun parseMultiLineEntry(line: String, outItems: MutableList<ParsedItemDraft>) {
        // 1. Check CSV 2-column format first: "label",true or label,1
        val csvMatch = CSV_ROW_REGEX.matchEntire(line)
        if (csvMatch != null) {
            val rawLabel = csvMatch.groupValues[1].ifEmpty { csvMatch.groupValues[2] }
            val statusToken = csvMatch.groupValues[3].lowercase()
            val clean = cleanItemLabel(rawLabel)
            if (clean.isNotEmpty()) {
                outItems.add(
                    ParsedItemDraft(
                        label = clean,
                        isChecked = statusToken in CHECKED_CSV_VALUES
                    )
                )
            }
            return
        }

        // 2. Check explicit checkbox / checked marker
        val hasCheckedMarker = CHECKED_PREFIX_REGEX.containsMatchIn(line)
        val hasUncheckedCheckbox = !hasCheckedMarker && UNCHECKED_CHECKBOX_PREFIX_REGEX.containsMatchIn(line)
        if (hasCheckedMarker || hasUncheckedCheckbox) {
            val stripped = stripPrefixes(line, isChecked = hasCheckedMarker)
            val clean = cleanItemLabel(stripped)
            if (clean.isNotEmpty()) {
                outItems.add(ParsedItemDraft(label = clean, isChecked = hasCheckedMarker))
            }
            return
        }

        // 3. No explicit checkbox: strip bullet / numbered list markers
        val stripped = stripPlainListPrefix(line)

        // 4. Smart short-phrase expansion if delimiter-separated tokens form >= 2 short items
        if (DELIMITER_CONTAINS_REGEX.containsMatchIn(stripped)) {
            val candidateTokens = DELIMITER_SPLIT_REGEX.split(stripped)
                .map { cleanItemLabel(it) }
                .filter { it.isNotEmpty() }
            if (candidateTokens.size >= 2 && candidateTokens.all { it.length <= 32 }) {
                for (token in candidateTokens) {
                    val tokenChecked = CHECKED_PREFIX_REGEX.containsMatchIn(token)
                    val tokenLabel = cleanItemLabel(stripPrefixes(token, isChecked = tokenChecked))
                    if (tokenLabel.isNotEmpty()) {
                        outItems.add(ParsedItemDraft(label = tokenLabel, isChecked = tokenChecked))
                    }
                }
                return
            }
        }

        val clean = cleanItemLabel(stripped)
        if (clean.isNotEmpty()) {
            outItems.add(ParsedItemDraft(label = clean, isChecked = false))
        }
    }

    private fun splitSingleLineItems(itemsText: String): List<ParsedItemDraft> {
        val rawTokens = DELIMITER_SPLIT_REGEX.split(itemsText)
        val result = ArrayList<ParsedItemDraft>(rawTokens.size)
        for (raw in rawTokens) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            val isChecked = CHECKED_PREFIX_REGEX.containsMatchIn(token)
            val stripped = stripPrefixes(token, isChecked = isChecked)
            val clean = cleanItemLabel(stripped)
            if (clean.isNotEmpty()) {
                result.add(ParsedItemDraft(label = clean, isChecked = isChecked))
            }
        }
        return result
    }

    private fun endsWithColon(text: String): Boolean {
        return text.endsWith(':') || text.endsWith('：')
    }

    private fun findTitleColonIndex(line: String): Int {
        val halfIdx = line.indexOf(':')
        val fullIdx = line.indexOf('：')
        val colonIdx = when {
            halfIdx >= 0 && fullIdx >= 0 -> minOf(halfIdx, fullIdx)
            halfIdx >= 0 -> halfIdx
            else -> fullIdx
        }
        if (colonIdx <= 0) return -1
        val left = line.substring(0, colonIdx).trim()
        if (left.isEmpty() || LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(left)) {
            return -1
        }
        return colonIdx
    }

    private fun stripPrefixes(text: String, isChecked: Boolean): String {
        val withoutCheckbox = if (isChecked) {
            text.replaceFirst(CHECKED_PREFIX_REGEX, "")
        } else {
            text.replaceFirst(UNCHECKED_CHECKBOX_PREFIX_REGEX, "")
        }
        return stripPlainListPrefix(withoutCheckbox)
    }

    private fun stripPlainListPrefix(text: String): String {
        var current = text.trimStart()
        current = current.replaceFirst(UNORDERED_BULLET_PREFIX_REGEX, "")
        current = current.replaceFirst(ORDERED_NUMBER_PREFIX_REGEX, "")
        return current.trim()
    }

    private fun cleanTitle(raw: String): String {
        var cleaned = raw.trim()
        cleaned = cleaned.replaceFirst(HEADING_PREFIX_REGEX, "")
        cleaned = cleaned.trim().trim('#', '*', '[', ']', '"', '\'', '「', '」', ':', '：').trim()
        return cleaned
    }

    private fun cleanItemLabel(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.length >= 2) {
            val first = cleaned.first()
            val last = cleaned.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                cleaned = cleaned.substring(1, cleaned.length - 1).trim()
            }
        }
        return cleaned.trim('"', '\'').trim()
    }
}
