package de.hauschild.upsert.model

/**
 * The database operation to perform for an [Instruction].
 *
 * Each operation determines how existing rows — identified by all [Header]s marked with [Modifier.Unique] —
 * are handled relative to incoming data rows.
 */
enum class Operation {

    /**
     * Inserts each data row as a new record.
     *
     * Skips silently if a row matching all unique columns already exists.
     */
    INSERT,

    /**
     * Finds the existing record by its unique columns and updates all other fields.
     *
     * Skips silently if no matching record is found.
     */
    UPDATE,

    /**
     * Inserts the record if it does not exist or updates it if it does.
     */
    INSERT_UPDATE,

    /**
     * Deletes the record identified by its unique columns.
     *
     * Skips silently if no matching record is found.
     */
    DELETE,
}
