package de.hauschild.upsert.model

/**
 * The parsed header of an import block, grouping the target operation, table, and column definitions.
 *
 * A header corresponds to the first line of an instruction block and fully describes
 * how the following data rows are to be interpreted and applied to the database.
 *
 * Source representation:
 * ```
 * INSERT_UPDATE person ; name[unique=true] ; age ; gender[default=DIVERSE]
 * ```
 *
 * @property operation the database operation to perform for every data row
 * @property table the name of the target database table
 * @property columns the column descriptors in declaration order, parallel to each [DataRow.values]
 */
data class Header(
    val operation: Operation,
    val table: String,
    val columns: List<Column>,
)
