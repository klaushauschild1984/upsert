package de.hauschild.upsert.sql.exception

/**
 * Thrown when a table referenced in a header cannot be found in the database metadata.
 *
 * @param table the qualified table name that was not found
 */
class TableNotFoundException(table: String) :
    UpsertSqlException("Table not found in database metadata: '$table'")
