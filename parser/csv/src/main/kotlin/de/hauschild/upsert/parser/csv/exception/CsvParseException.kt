package de.hauschild.upsert.parser.csv.exception

import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import de.hauschild.upsert.parser.exception.UpsertParseException

/**
 * Thrown when the CSV input is structurally malformed and cannot be tokenized.
 *
 * Wraps a [MalformedCSVException] from the underlying CSV library so that no
 * third-party exception type escapes the parser API boundary.
 *
 * @param cause the underlying library exception
 */
class CsvParseException(cause: MalformedCSVException) :
    UpsertParseException("Malformed CSV input: ${cause.message}", cause)
