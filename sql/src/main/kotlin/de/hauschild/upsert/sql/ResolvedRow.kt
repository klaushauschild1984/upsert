package de.hauschild.upsert.sql

/**
 * A fully resolved data row ready for SQL execution.
 *
 * All FK references have been resolved to primary keys, defaults have been applied,
 * and values have been converted to their JDBC types.
 *
 * @param lineNumber the 1-based line number in the source file, for error reporting
 * @param columns the resolved columns in header order
 */
data class ResolvedRow(
    val lineNumber: Int,
    val columns: List<ResolvedColumn>,
)
