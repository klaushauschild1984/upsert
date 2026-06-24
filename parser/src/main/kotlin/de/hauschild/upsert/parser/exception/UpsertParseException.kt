package de.hauschild.upsert.parser.exception

import de.hauschild.upsert.model.exception.UpsertException

/**
 * Root of the parse exception hierarchy.
 *
 * Thrown when the input source cannot be interpreted as valid upsert instructions.
 * All concrete parse exceptions extend this class.
 *
 * @param message a human-readable description of the parse error
 * @param cause the underlying cause, if any
 */
abstract class UpsertParseException(message: String, cause: Throwable? = null) : UpsertException(message, cause)
