package de.hauschild.upsert.parser

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Header
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.model.Operation

/**
 * Parses a list of raw string tokens into a [Header].
 *
 * The token list corresponds to a header row split by the format-specific separator
 * (semicolon for CSV, cells for Excel, array elements for JSON). The first token contains
 * the operation and table name; all subsequent tokens are column definitions.
 *
 * ```kotlin
 * // "INSERT_UPDATE person ; name[unique=true] ; age" split by ";"
 * HeaderParser().parse(listOf("INSERT_UPDATE person", "name[unique=true]", "age"))
 * ```
 */
class HeaderParser {

    /**
     * Parses the given [tokens] into a [Header].
     *
     * @param tokens the raw tokens of a header row; must not be empty
     * @return the parsed header
     * @throws IllegalArgumentException if the tokens are empty, malformed, or contain unknown modifiers
     */
    fun parse(tokens: List<String>): Header {
        require(tokens.isNotEmpty()) { "Header tokens must not be empty" }
        val (operation, table) = parseOperationAndTable(tokens.first().trim())
        val columns = tokens.drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parseColumn(it) }
        return Header(operation, table, columns)
    }

    private fun parseOperationAndTable(token: String): Pair<Operation, String> {
        val parts = token.split(Regex("\\s+"), limit = 2)
        require(parts.size == 2) { "First header token must contain operation and table name: '$token'" }
        return Operation.valueOf(parts[0].trim()) to parts[1].trim()
    }

    private fun parseColumn(token: String): Column {
        val refStart = token.indexOf('(')
        val refEnd = token.indexOf(')')
        val modStart = token.indexOf('[')
        val modifiers = extractModifiers(token)
        return if (refStart != -1 && refEnd != -1) {
            Column.ForeignKey(
                name = token.substring(0, refStart).trim(),
                attribute = token.substring(refStart + 1, refEnd).trim(),
                modifiers = modifiers,
            )
        } else {
            Column.Field(
                name = if (modStart != -1) token.substring(0, modStart).trim() else token,
                modifiers = modifiers,
            )
        }
    }

    private fun extractModifiers(token: String): List<Modifier> {
        val modifiers = mutableListOf<Modifier>()
        var searchFrom = 0
        while (true) {
            val start = token.indexOf('[', searchFrom)
            if (start == -1) break
            val end = token.indexOf(']', start)
            if (end == -1) break
            modifiers += token.substring(start + 1, end).split(',').map { parseModifier(it.trim()) }
            searchFrom = end + 1
        }
        return modifiers
    }

    private fun parseModifier(token: String): Modifier {
        val parts = token.split('=', limit = 2)
        val key = parts[0].trim().lowercase()
        val value = if (parts.size > 1) parts[1].trim() else null
        return when (key) {
            "unique" -> Modifier.Unique
            "default" -> Modifier.Default(requireNotNull(value) { "default modifier requires a value" })
            "dateformat" -> Modifier.DateFormat(requireNotNull(value) { "dateformat modifier requires a value" })
            "translator" -> Modifier.Translator(requireNotNull(value) { "translator modifier requires a value" })
            else -> throw IllegalArgumentException("Unknown modifier: '$key'")
        }
    }
}
