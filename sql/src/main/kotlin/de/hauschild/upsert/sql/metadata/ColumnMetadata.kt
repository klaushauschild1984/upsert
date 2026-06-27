package de.hauschild.upsert.sql.metadata

/**
 * Metadata for a single database column as reported by [java.sql.DatabaseMetaData].
 *
 * @param name the column name in lowercase
 * @param sqlType the JDBC type constant from [java.sql.Types]
 * @param nullable whether the column accepts `NULL` values
 */
data class ColumnMetadata(
    val name: String,
    val sqlType: Int,
    val nullable: Boolean,
)
