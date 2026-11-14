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
 * High-tolerance parser supporting single-line delimiter/whitespace strings
 * and multi-line Markdown / plain-text / table / CSV / TSV / JSON-array checklists.
 */
object ChecklistTextParser {

    private const val BULLET_CHARS = """\-*+•·▪▫▸▹◦‣⁃–—●○◆◇➢➤→"""
    private val HORIZONTAL_RULE_REGEX = Regex(
        """^(?:[-*_]{3,}|\|?(?:\s*:?-{2,}:?\s*\|)+\s*:?-{2,}:?\s*\|?)\s*$"""
    )
    private val JSON_BRACKET_LINE_REGEX = Regex("""^[\[\]]\s*,?\s*$""")
    private val HEADING_PREFIX_REGEX = Regex("""^#{1,6}\s+""")
    private val ORDERED_NUMBER_PATTERN = """(?:\(\d+\)|（\d+）|#?\d+[.)、])"""
    private val CHECKED_MARKER_PATTERN = """(?:\[[xX✓✔☑vV]\]|[✓✔☑✅🟢])"""
    private val UNCHECKED_MARKER_PATTERN = """(?:\[\s*\]|[☐□⬜🔲⭕])"""

    private val LIST_OR_CHECKBOX_PREFIX_REGEX = Regex(
        """^(?:$ORDERED_NUMBER_PATTERN\s+|[$BULLET_CHARS]\s+|\[[xX✓✔☑vV\s]?\]\s*|[✓✔☑✅🟢☐□⬜🔲⭕]\s*)"""
    )
    private val CHECKED_PREFIX_REGEX = Regex(
        """^(?:$ORDERED_NUMBER_PATTERN\s*|[$BULLET_CHARS]\s*)?$CHECKED_MARKER_PATTERN\s*"""
    )
    private val UNCHECKED_CHECKBOX_PREFIX_REGEX = Regex(
        """^(?:$ORDERED_NUMBER_PATTERN\s*|[$BULLET_CHARS]\s*)?$UNCHECKED_MARKER_PATTERN\s*"""
    )
    private val UNORDERED_BULLET_PREFIX_REGEX = Regex("""^[$BULLET_CHARS]\s+""")
    private val ORDERED_NUMBER_PREFIX_REGEX = Regex("""^$ORDERED_NUMBER_PATTERN\s*""")

    private val PRIMARY_DELIMITER_SPLIT_REGEX = Regex("""[,，、;；|｜\t]+""")
    private val PRIMARY_DELIMITER_CONTAINS_REGEX = Regex("""[,，、;；|｜\t]""")
    private val WHITESPACE_SPLIT_REGEX = Regex("""[\s\u3000]+""")
    private val WHITESPACE_CONTAINS_REGEX = Regex("""[\s\u3000]""")
    private val INLINE_MARKER_BOUNDARY_REGEX = Regex(
        """(?<=[\s\u3000])(?=(?:\[[xX✓✔☑vV\s]?\]|[✓✔☑✅🟢☐□⬜🔲⭕]|$ORDERED_NUMBER_PATTERN\s+|[$BULLET_CHARS]\s+))"""
    )
    private val SENTENCE_END_REGEX = Regex("""[.。!！?？]$""")

    private val MARKDOWN_TABLE_ROW_REGEX = Regex("""^\|(.+)\|\s*$""")
    private val CSV_OR_TSV_HEADER_REGEX = Regex(
        """^"?(?:label|item|name|項目|名稱|项目|名称)"?\s*[,，\t|]\s*"?(?:ischecked|is_checked|checked|status|狀態|状态|已備|已备)"?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val CSV_OR_TSV_ROW_REGEX = Regex(
        """^(?:"([^"]+)"|([^,，\t]+?))\s*[,，\t]\s*"?(true|false|1|0|checked|unchecked|已備|已备|待備|待备|yes|no|✓|✔|☑|✅|\[[xX✓✔☑vV]\]|\[\s*\])"?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val CHECKBOX_START_REGEX = Regex("""^\[[xX✓✔☑vV\s]?\]""")
    private val CHECKED_STATUS_VALUES = setOf(
        "true", "1", "checked", "已備", "已备", "yes", "✓", "✔", "☑", "✅", "[x]", "[v]", "[✓]", "[✔]", "[☑]"
    )

    fun parse(rawText: String): ParsedChecklistDraft {
        val cleanedLines = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { it.replace("\uFEFF", "").trim() }
            .filter {
                it.isNotEmpty() &&
                    !HORIZONTAL_RULE_REGEX.matches(it) &&
                    !JSON_BRACKET_LINE_REGEX.matches(it)
            }
            .toList()

        if (cleanedLines.isEmpty()) {
            return ParsedChecklistDraft(
                title = "",
                items = emptyList(),
                detectedMode = ImportFormatMode.EMPTY
            )
        }

        // Handle two-line variant where line 1 is strictly a header ending in colon
        // and line 2 is a delimiter- or whitespace-separated single-line list.
        val isTwoLineHeaderPlusSingleLine = cleanedLines.size == 2 &&
            endsWithColon(cleanedLines[0]) &&
            !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(cleanedLines[0]) &&
            !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(cleanedLines[1]) &&
            !CSV_OR_TSV_ROW_REGEX.matches(cleanedLines[1]) &&
            !MARKDOWN_TABLE_ROW_REGEX.matches(cleanedLines[1]) &&
            (
                PRIMARY_DELIMITER_CONTAINS_REGEX.containsMatchIn(cleanedLines[1]) ||
                    WHITESPACE_CONTAINS_REGEX.containsMatchIn(cleanedLines[1])
                )

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
            val line = unwrapBracketArray(lines[0])
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
        val lines = if (rawLines.isNotEmpty() && isHeaderRow(rawLines[0])) {
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
            !CSV_OR_TSV_ROW_REGEX.matches(firstLine) &&
            !MARKDOWN_TABLE_ROW_REGEX.matches(firstLine)
        val firstHasNoPrefix = !LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(firstLine) &&
            !CSV_OR_TSV_ROW_REGEX.matches(firstLine) &&
            !MARKDOWN_TABLE_ROW_REGEX.matches(firstLine) &&
            !PRIMARY_DELIMITER_CONTAINS_REGEX.containsMatchIn(firstLine)
        val secondHasStructuredPrefix = lines.size >= 2 && (
            LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(lines[1]) ||
                CSV_OR_TSV_ROW_REGEX.matches(lines[1]) ||
                MARKDOWN_TABLE_ROW_REGEX.matches(lines[1])
            )
        val secondHasDelimitedOrSpacedItems = lines.size >= 2 &&
            !WHITESPACE_CONTAINS_REGEX.containsMatchIn(firstLine) &&
            (
                PRIMARY_DELIMITER_CONTAINS_REGEX.containsMatchIn(lines[1]) ||
                    WHITESPACE_CONTAINS_REGEX.containsMatchIn(lines[1])
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
            firstHasNoPrefix && (secondHasStructuredPrefix || secondHasDelimitedOrSpacedItems) -> {
                title = cleanTitle(firstLine)
                bodyLines.addAll(lines.drop(1))
            }
            else -> {
                bodyLines.addAll(lines)
            }
        }

        val items = mutableListOf<ParsedItemDraft>()
        for (line in bodyLines) {
            if (isHeaderRow(line)) continue
            parseMultiLineEntry(line, items)
        }

        return ParsedChecklistDraft(
            title = title,
            items = items,
            detectedMode = ImportFormatMode.MULTILINE_MARKDOWN
        )
    }

    private fun parseMultiLineEntry(line: String, outItems: MutableList<ParsedItemDraft>) {
        // 1. Markdown table row: | item | status | or | item |
        val tableMatch = MARKDOWN_TABLE_ROW_REGEX.matchEntire(line)
        if (tableMatch != null) {
            val cells = tableMatch.groupValues[1]
                .split('|')
                .map { it.trim() }
            if (cells.isNotEmpty()) {
                val rawLabel = cells[0]
                val clean = cleanItemLabel(rawLabel)
                if (clean.isNotEmpty()) {
                    val statusCell = cells.getOrNull(1)?.lowercase().orEmpty()
                    val isChecked = statusCell in CHECKED_STATUS_VALUES ||
                        CHECKED_PREFIX_REGEX.containsMatchIn(rawLabel)
                    outItems.add(ParsedItemDraft(label = clean, isChecked = isChecked))
                }
            }
            return
        }

        // 2. Check CSV / TSV 2-column format: "label",true or label\t1
        val csvMatch = CSV_OR_TSV_ROW_REGEX.matchEntire(line)
        if (csvMatch != null) {
            val rawLabel = csvMatch.groupValues[1].ifEmpty { csvMatch.groupValues[2] }
            val statusToken = csvMatch.groupValues[3].lowercase()
            val clean = cleanItemLabel(rawLabel)
            if (clean.isNotEmpty()) {
                outItems.add(
                    ParsedItemDraft(
                        label = clean,
                        isChecked = statusToken in CHECKED_STATUS_VALUES
                    )
                )
            }
            return
        }

        // 3. Check explicit checkbox / checked marker
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

        // 4. Strip bullet / numbered list markers if present
        val hadListPrefix = UNORDERED_BULLET_PREFIX_REGEX.containsMatchIn(line) ||
            ORDERED_NUMBER_PREFIX_REGEX.containsMatchIn(line)
        val stripped = stripPlainListPrefix(line)

        // 5. Smart expansion for delimiter-separated short items
        if (PRIMARY_DELIMITER_CONTAINS_REGEX.containsMatchIn(stripped)) {
            val candidateTokens = PRIMARY_DELIMITER_SPLIT_REGEX.split(stripped)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (candidateTokens.size >= 2 && candidateTokens.all { it.length <= 32 }) {
                addParsedTokens(candidateTokens, outItems)
                return
            }
        }

        // 6. Smart whitespace expansion ONLY on plain unprefixed lines (preserving e.g. "1. AA Battery")
        if (!hadListPrefix && WHITESPACE_CONTAINS_REGEX.containsMatchIn(stripped)) {
            val candidateTokens = WHITESPACE_SPLIT_REGEX.split(stripped)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (
                candidateTokens.size >= 2 &&
                candidateTokens.all { it.length <= 24 } &&
                !SENTENCE_END_REGEX.containsMatchIn(stripped)
            ) {
                addParsedTokens(candidateTokens, outItems)
                return
            }
        }

        val clean = cleanItemLabel(stripped)
        if (clean.isNotEmpty()) {
            outItems.add(ParsedItemDraft(label = clean, isChecked = false))
        }
    }

    private fun splitSingleLineItems(itemsText: String): List<ParsedItemDraft> {
        val trimmed = unwrapBracketArray(itemsText.trim())
        if (trimmed.isEmpty()) return emptyList()

        val rawTokens = when {
            INLINE_MARKER_BOUNDARY_REGEX.containsMatchIn(trimmed) ->
                INLINE_MARKER_BOUNDARY_REGEX.split(trimmed)
            PRIMARY_DELIMITER_CONTAINS_REGEX.containsMatchIn(trimmed) ->
                PRIMARY_DELIMITER_SPLIT_REGEX.split(trimmed)
            LIST_OR_CHECKBOX_PREFIX_REGEX.containsMatchIn(trimmed) ->
                listOf(trimmed)
            WHITESPACE_CONTAINS_REGEX.containsMatchIn(trimmed) ->
                WHITESPACE_SPLIT_REGEX.split(trimmed)
            else ->
                listOf(trimmed)
        }

        val result = ArrayList<ParsedItemDraft>(rawTokens.size)
        addParsedTokens(rawTokens, result)
        return result
    }

    private fun addParsedTokens(rawTokens: List<String>, outItems: MutableList<ParsedItemDraft>) {
        for (raw in rawTokens) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            val isChecked = CHECKED_PREFIX_REGEX.containsMatchIn(token)
            val stripped = stripPrefixes(token, isChecked = isChecked)
            val clean = cleanItemLabel(stripped)
            if (clean.isNotEmpty()) {
                outItems.add(ParsedItemDraft(label = clean, isChecked = isChecked))
            }
        }
    }

    private fun isHeaderRow(line: String): Boolean {
        if (CSV_OR_TSV_HEADER_REGEX.matches(line)) return true
        val tableMatch = MARKDOWN_TABLE_ROW_REGEX.matchEntire(line) ?: return false
        val cells = tableMatch.groupValues[1].split('|').map { it.trim().trim('"', '\'') }
        if (cells.size >= 2) {
            val joined = "${cells[0]},${cells[1]}"
            if (CSV_OR_TSV_HEADER_REGEX.matches(joined)) return true
        }
        return false
    }

    private fun unwrapBracketArray(text: String): String {
        val t = text.trim()
        val isCheckboxPrefix = CHECKBOX_START_REGEX.containsMatchIn(t)
        if (!isCheckboxPrefix && t.length >= 2 && t.startsWith('[') && t.endsWith(']')) {
            return t.substring(1, t.length - 1).trim()
        }
        return t
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
        if (colonIdx + 2 < line.length && line[colonIdx + 1] == '/' && line[colonIdx + 2] == '/') {
            return -1
        }
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
        cleaned = cleaned.trim()
            .trim('#', '*', '[', ']', '"', '\'', '「', '」', '【', '】', '《', '》', ':', '：')
            .trim()
        return cleaned
    }

    private fun cleanItemLabel(raw: String): String {
        var cleaned = raw.trim().trimEnd(',').trim()
        if (cleaned.length >= 4 && cleaned.startsWith("~~") && cleaned.endsWith("~~")) {
            cleaned = cleaned.substring(2, cleaned.length - 2).trim()
        }
        if (cleaned.length >= 4 && cleaned.startsWith("**") && cleaned.endsWith("**")) {
            cleaned = cleaned.substring(2, cleaned.length - 2).trim()
        }
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
