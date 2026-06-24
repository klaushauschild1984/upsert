package de.hauschild.upsert.parser.exception

import de.hauschild.upsert.model.Header

/**
 * Thrown when a header token list cannot be parsed into a valid [Header].
 *
 * @param tokens the raw tokens that could not be parsed
 * @param reason a human-readable explanation of why the tokens are invalid
 */
class InvalidHeaderException(tokens: List<String>, reason: String) :
    UpsertParseException("Invalid header $tokens: $reason")
