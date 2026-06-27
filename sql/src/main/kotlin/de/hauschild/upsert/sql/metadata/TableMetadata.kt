package de.hauschild.upsert.sql.metadata

import de.hauschild.upsert.sql.QualifiedTable
import de.hauschild.upsert.sql.exception.TableNotFoundException
import java.sql.Connection
import java.sql.DatabaseMetaData

/**
 * Metadata for a database table as reported by [DatabaseMetaData].
 *
 * Columns are keyed by lowercase name for case-insensitive lookup. The primary key
 * column name is resolved once at load time and used during FK resolution.
 *
 * @param table the qualified table name
 * @param columns all columns of the table, keyed by lowercase name
 * @param primaryKeyColumn the name of the primary key column, or `null` if not determinable
 */
data class TableMetadata(
    val table: QualifiedTable,
    val columns: Map<String, ColumnMetadata>,
    val primaryKeyColumn: String?,
) {

    /**
     * Returns the [ColumnMetadata] for [columnName], case-insensitively.
     *
     * @param columnName the column name to look up
     * @return the column metadata, or `null` if the column is not in the table
     */
    fun column(columnName: String): ColumnMetadata? = columns[columnName.lowercase()]

    companion object {

        /**
         * Loads metadata for [table] from [connection].
         *
         * @param connection an open JDBC connection
         * @param table the qualified table to inspect
         * @return the loaded metadata
         * @throws TableNotFoundException if no columns are found for the given table
         */
        fun load(connection: Connection, table: QualifiedTable): TableMetadata {
            val meta = connection.metaData
            val columns = loadColumns(meta, table)
            if (columns.isEmpty()) { throw TableNotFoundException(table.toString()) }
            val primaryKeyColumn = loadPrimaryKey(meta, table)
            return TableMetadata(table, columns, primaryKeyColumn)
        }

        private fun loadColumns(meta: DatabaseMetaData, table: QualifiedTable): Map<String, ColumnMetadata> {
            val columns = mutableMapOf<String, ColumnMetadata>()
            meta.getColumns(null, table.schema, table.name, null).use { rs ->
                while (rs.next()) {
                    val name = rs.getString("COLUMN_NAME").lowercase()
                    val sqlType = rs.getInt("DATA_TYPE")
                    val nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable
                    columns[name] = ColumnMetadata(name, sqlType, nullable)
                }
            }
            return columns
        }

        private fun loadPrimaryKey(meta: DatabaseMetaData, table: QualifiedTable): String? {
            meta.getPrimaryKeys(null, table.schema, table.name).use { rs ->
                return if (rs.next()) { rs.getString("COLUMN_NAME").lowercase() } else { null }
            }
        }
    }
}
