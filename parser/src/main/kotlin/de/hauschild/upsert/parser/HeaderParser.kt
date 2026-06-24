package de.hauschild.upsert.parser

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Header
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.model.Operation
import de.hauschild.upsert.parser.exception.InvalidHeaderException
import de.hauschild.upsert.parser.exception.UnknownModifierException
import de.hauschild.upsert.parser.exception.UnknownOperationException

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
     * @throws InvalidHeaderException if the tokens are empty or the first token is malformed
     * @throws UnknownOperationException if the operation keyword is not recognized
     * @throws UnknownModifierException if a column modifier key is not recognized
     */
    fun parse(tokens: List<String>): Header {
        if (tokens.isEmpty()) { throw InvalidHeaderException(tokens, "token list must not be empty") }
        val (operation, table) = parseOperationAndTable(tokens.first().trim())
        val columns = tokens.drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { parseColumn(it) }
        return Header(operation, table, columns)
    }

    private fun parseOperationAndTable(token: String): Pair<Operation, String> {
        val parts = token.split(Regex("\\s+"), limit = 2)
        if (parts.size != 2) { throw InvalidHeaderException(listOf(token), "must contain operation and table name") }
        val operationName = parts[0].trim()
        val operation = try {
            Operation.valueOf(operationName)
        } catch (exception: IllegalArgumentException) {
            throw UnknownOperationException(operationName, exception)
        }
        return operation to parts[1].trim()
    }

    private fun parseColumn(token: String): Column {
        val refStart = token.indexOf('(')
        val refEnd = token.indexOf(')')
        val modStart = token.indexOf('[')
        val modifiers = extractModifiers(token)
        val hasFkParens = refStart != -1 || refEnd != -1
        return if (hasFkParens) {
            if (refStart == -1 || refEnd == -1 || refEnd <= refStart) {
                throw InvalidHeaderException(
                    listOf(token),
                    "malformed foreign key reference: parentheses are unbalanced or out of order",
                )
            }
            val name = token.substring(0, refStart).trim()
            if (name.isEmpty()) { throw InvalidHeaderException(listOf(token), "column name must not be empty") }
            Column.ForeignKey(
                name = name,
                attribute = token.substring(refStart + 1, refEnd).trim(),
                modifiers = modifiers,
            )
        } else {
            val name = if (modStart != -1) { token.substring(0, modStart).trim() } else { token }
            if (name.isEmpty()) { throw InvalidHeaderException(listOf(token), "column name must not be empty") }
            Column.Field(
                name = name,
                modifiers = modifiers,
            )
        }
    }

    private fun extractModifiers(token: String): List<Modifier> =
        Regex("\\[([^]]+)]")
            .findAll(token)
            .flatMap { it.groupValues[1].split(',').map { part -> parseModifier(part.trim()) } }
            .toList()

    private fun parseModifier(token: String): Modifier {
        val parts = token.split('=', limit = 2)
        val key = parts[0].trim().lowercase()
        val value = if (parts.size > 1) { parts[1].trim() } else { null }
        return when (key) {
            "unique" -> Modifier.Unique
            "default" -> Modifier.Default(requireValue(token, key, value))
            "dateformat" -> Modifier.DateFormat(requireValue(token, key, value))
            "translator" -> Modifier.Translator(requireValue(token, key, value))
            else -> throw UnknownModifierException(key)
        }
    }

    private fun requireValue(token: String, key: String, value: String?): String =
        value ?: throw InvalidHeaderException(listOf(token), "$key modifier requires a value")
}
