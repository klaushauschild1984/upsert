package de.hauschild.upsert.sql.result

/**
 * Represents a data row that could not be processed and was skipped.
 *
 * The [lineNumber] corresponds to the original line in the source file, allowing precise
 * identification of problematic rows — unlike formats that only report a total failure count.
 *
 * @param lineNumber the 1-based line number in the source file
 * @param values the raw string values of the row as read from the source
 * @param reason a human-readable explanation of why the row was skipped
 * @param cause the underlying exception, if available
 */
data class SkippedRow(
    val lineNumber: Int,
    val values: List<String>,
    val reason: String,
    val cause: Throwable? = null,
)
