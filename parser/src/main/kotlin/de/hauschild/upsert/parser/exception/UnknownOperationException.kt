package de.hauschild.upsert.parser.exception

/**
 * Thrown when a header line starts with an unrecognized operation keyword.
 *
 * @param operation the unrecognized operation string
 */
class UnknownOperationException(operation: String, cause: Throwable? = null) :
    UpsertParseException("Unknown operation: '$operation'", cause)
