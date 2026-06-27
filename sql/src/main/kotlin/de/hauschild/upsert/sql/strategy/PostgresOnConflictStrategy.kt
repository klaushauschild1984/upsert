package de.hauschild.upsert.sql.strategy

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Header
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.model.Operation
import de.hauschild.upsert.sql.QualifiedTable
import de.hauschild.upsert.sql.ResolvedColumn
import de.hauschild.upsert.sql.ResolvedRow
import de.hauschild.upsert.sql.result.InstructionResult
import de.hauschild.upsert.sql.result.SkippedRow
import io.micrometer.observation.ObservationRegistry
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Executes upsert instructions using PostgreSQL-native `INSERT ... ON CONFLICT DO UPDATE`.
 *
 * Each row is executed individually so that a single failing row is collected as a
 * [SkippedRow] without aborting the remaining rows of the instruction.
 *
 * SQL generated per operation:
 * - `INSERT` — plain `INSERT INTO … VALUES (…)`
 * - `UPDATE` — `UPDATE … SET … WHERE <unique columns>`
 * - `INSERT_UPDATE` — `INSERT INTO … VALUES (…) ON CONFLICT (…) DO UPDATE SET … = EXCLUDED.…`
 * - `DELETE` — `DELETE FROM … WHERE <unique columns>`
 */
class PostgresOnConflictStrategy : UpsertStrategy {

    override fun execute(
        connection: Connection,
        table: QualifiedTable,
        header: Header,
        rows: List<ResolvedRow>,
        observationRegistry: ObservationRegistry,
    ): InstructionResult {
        if (rows.isEmpty()) {
            return InstructionResult(header = header)
        }
        val sql = buildSql(table, header.columns, header.operation)
        var rowsInserted = 0
        var rowsUpdated = 0
        var rowsDeleted = 0
        val skippedRows = mutableListOf<SkippedRow>()
        connection.prepareStatement(sql).use { stmt ->
            for (row in rows) {
                try {
                    bindRow(stmt, row, header.columns, header.operation)
                    val affected = stmt.executeUpdate()
                    when (header.operation) {
                        Operation.INSERT -> if (affected > 0) { rowsInserted++ }
                        Operation.UPDATE -> if (affected > 0) { rowsUpdated++ } else {
                            skippedRows.add(SkippedRow(row.lineNumber, rawValues(row), "no row matched the unique key for UPDATE"))
                        }
                        Operation.INSERT_UPDATE -> if (affected > 0) { rowsInserted++ }
                        Operation.DELETE -> if (affected > 0) { rowsDeleted++ } else {
                            skippedRows.add(SkippedRow(row.lineNumber, rawValues(row), "no row matched the unique key for DELETE"))
                        }
                    }
                } catch (exception: Exception) {
                    skippedRows.add(SkippedRow(row.lineNumber, rawValues(row), exception.message ?: "SQL execution failed", exception))
                }
            }
        }
        return InstructionResult(
            header = header,
            rowsInserted = rowsInserted,
            rowsUpdated = rowsUpdated,
            rowsDeleted = rowsDeleted,
            skippedRows = skippedRows,
        )
    }

    internal fun buildSql(table: QualifiedTable, columns: List<Column>, operation: Operation): String {
        val allNames = columns.map { it.columnName() }
        val uniqueNames = columns.filter { col -> col.modifiers.any { it is Modifier.Unique } }.map { it.columnName() }
        val nonUniqueNames = allNames - uniqueNames.toSet()
        return when (operation) {
            Operation.INSERT -> buildInsertSql(table, allNames)
            Operation.UPDATE -> buildUpdateSql(table, nonUniqueNames, uniqueNames)
            Operation.INSERT_UPDATE -> buildUpsertSql(table, allNames, uniqueNames, nonUniqueNames)
            Operation.DELETE -> buildDeleteSql(table, uniqueNames)
        }
    }

    private fun buildInsertSql(table: QualifiedTable, columns: List<String>): String {
        val cols = columns.joinToString()
        val placeholders = columns.joinToString { "?" }
        return "INSERT INTO $table ($cols) VALUES ($placeholders)"
    }

    private fun buildUpdateSql(table: QualifiedTable, setCols: List<String>, whereCols: List<String>): String {
        val setClause = setCols.joinToString { "$it = ?" }
        val whereClause = whereCols.joinToString(" AND ") { "$it = ?" }
        return "UPDATE $table SET $setClause WHERE $whereClause"
    }

    private fun buildUpsertSql(
        table: QualifiedTable,
        allCols: List<String>,
        uniqueCols: List<String>,
        updateCols: List<String>,
    ): String {
        val cols = allCols.joinToString()
        val placeholders = allCols.joinToString { "?" }
        val conflict = uniqueCols.joinToString()
        val update = updateCols.joinToString { "$it = EXCLUDED.$it" }
        return "INSERT INTO $table ($cols) VALUES ($placeholders) ON CONFLICT ($conflict) DO UPDATE SET $update"
    }

    private fun buildDeleteSql(table: QualifiedTable, whereCols: List<String>): String {
        val whereClause = whereCols.joinToString(" AND ") { "$it = ?" }
        return "DELETE FROM $table WHERE $whereClause"
    }

    private fun bindRow(stmt: PreparedStatement, row: ResolvedRow, columns: List<Column>, operation: Operation) {
        val uniqueCols = row.columns.filter { col -> col.column.modifiers.any { it is Modifier.Unique } }
        val nonUniqueCols = row.columns.filter { col -> col.column.modifiers.none { it is Modifier.Unique } }
        val bindOrder = when (operation) {
            Operation.INSERT -> row.columns
            Operation.UPDATE -> nonUniqueCols + uniqueCols
            Operation.INSERT_UPDATE -> row.columns
            Operation.DELETE -> uniqueCols
        }
        bindOrder.forEachIndexed { index, col ->
            bindValue(stmt, index + 1, col)
        }
    }

    private fun bindValue(stmt: PreparedStatement, index: Int, col: ResolvedColumn) {
        if (col.value == null) {
            stmt.setNull(index, col.sqlType)
        } else {
            stmt.setObject(index, col.value, col.sqlType)
        }
    }

    private fun rawValues(row: ResolvedRow): List<String> =
        row.columns.map { it.value?.toString() ?: "" }

    private fun Column.columnName(): String = when (this) {
        is Column.Field -> name
        is Column.ForeignKey -> name
    }
}
