package de.hauschild.upsert.model

/**
 * A single data row from an [Instruction], carrying its raw string values and origin line number.
 *
 * Values are parallel to the [Instruction.headers] list: `values[i]` corresponds to `headers[i]`.
 * All values are untrimmed raw strings as read from the source file; trimming and type conversion
 * are applied later during SQL processing.
 *
 * The [lineNumber] refers to the physical line in the source file and is used to produce
 * actionable error messages when a row fails to import.
 *
 * @property lineNumber the 1-based line number of this row in the source file
 * @property values the raw cell values in column order
 */
data class DataRow(val lineNumber: Int, val values: List<String>)
