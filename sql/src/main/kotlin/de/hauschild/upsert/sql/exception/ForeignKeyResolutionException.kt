package de.hauschild.upsert.sql.exception

/**
 * Thrown when a foreign key natural key value cannot be resolved to a primary key.
 *
 * @param table the referenced table
 * @param attribute the natural key column
 * @param value the value that could not be resolved
 */
class ForeignKeyResolutionException(table: String, attribute: String, value: String) :
    UpsertSqlException("No row found in '$table' where '$attribute' = '$value'")
