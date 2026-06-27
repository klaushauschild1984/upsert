package de.hauschild.upsert.sql.exception

import de.hauschild.upsert.model.exception.UpsertException

/**
 * Root of the SQL exception hierarchy.
 *
 * All exceptions thrown during SQL generation or execution extend this class.
 *
 * @param message a human-readable description of the error
 * @param cause the underlying cause, if any
 */
abstract class UpsertSqlException(message: String, cause: Throwable? = null) : UpsertException(message, cause)
