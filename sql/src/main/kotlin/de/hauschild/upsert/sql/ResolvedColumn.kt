package de.hauschild.upsert.sql

import de.hauschild.upsert.model.Column

/**
 * A single column value after FK resolution, default application, and type mapping.
 *
 * The [value] is ready to be bound to a [java.sql.PreparedStatement]: it is either
 * a typed JVM object matching [sqlType], or `null` for nullable empty fields.
 *
 * @param column the original column descriptor from the instruction header
 * @param value the resolved, typed value; `null` for nullable empty fields
 * @param sqlType the JDBC type constant from [java.sql.Types]
 */
data class ResolvedColumn(
    val column: Column,
    val value: Any?,
    val sqlType: Int,
)
