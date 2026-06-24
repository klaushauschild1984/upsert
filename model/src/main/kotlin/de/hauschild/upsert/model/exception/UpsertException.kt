package de.hauschild.upsert.model.exception

/**
 * Root of the upsert exception hierarchy.
 *
 * All exceptions thrown by the upsert library extend this class, allowing callers to
 * catch any library-specific error with a single catch block when fine-grained handling
 * is not required.
 *
 * @param message a human-readable description of the error
 * @param cause the underlying cause, if any
 */
abstract class UpsertException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
