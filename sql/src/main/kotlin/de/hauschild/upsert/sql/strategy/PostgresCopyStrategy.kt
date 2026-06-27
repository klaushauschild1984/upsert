package de.hauschild.upsert.sql.strategy

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Header
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.model.Operation
import de.hauschild.upsert.sql.QualifiedTable
import de.hauschild.upsert.sql.ResolvedRow
import de.hauschild.upsert.sql.result.InstructionResult
import de.hauschild.upsert.sql.timed
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.postgresql.PGConnection
import java.sql.Connection

private const val STAGING = "upsert_staging"

/**
 * High-performance upsert strategy using PostgreSQL's `COPY` protocol.
 *
 * All rows of an instruction are bulk-loaded into a temporary staging table via
 * `COPY … FROM STDIN`, then merged into the target with a single SQL statement.
 * This is significantly faster than row-by-row execution for large datasets.
 *
 * Trade-off: no per-row error tolerance. If the merge statement fails, the entire
 * instruction fails and the transaction is rolled back. The [SqlExecutor] captures
 * the exception in [InstructionResult.cause].
 *
 * Requires a PostgreSQL JDBC connection (uses [PGConnection.copyAPI]).
 */
class PostgresCopyStrategy : UpsertStrategy {

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
        val columns = header.columns
        val colNames = columns.map { it.columnName() }
        val uniqueNames = columns.filter { col -> col.modifiers.any { it is Modifier.Unique } }.map { it.columnName() }
        val nonUniqueNames = colNames - uniqueNames.toSet()

        connection.createStatement().use { stmt ->
            stmt.execute(buildCreateStagingSql(table, colNames))
        }

        Observation.createNotStarted("upsert.copy", observationRegistry)
            .lowCardinalityKeyValue("table", table.toString())
            .highCardinalityKeyValue("rows", rows.size.toString())
            .timed { copyToStaging(connection, colNames, rows) }

        return Observation.createNotStarted("upsert.merge", observationRegistry)
            .lowCardinalityKeyValue("table", table.toString())
            .lowCardinalityKeyValue("operation", header.operation.name)
            .timed { mergeFromStaging(connection, table, header, colNames, uniqueNames, nonUniqueNames) }
    }

    private fun mergeFromStaging(
        connection: Connection,
        table: QualifiedTable,
        header: Header,
        colNames: List<String>,
        uniqueNames: List<String>,
        nonUniqueNames: List<String>,
    ): InstructionResult {
        return when (header.operation) {
            Operation.INSERT -> {
                val affected = connection.createStatement().use { stmt ->
                    stmt.executeUpdate(buildInsertFromStagingSql(table, colNames))
                }
                InstructionResult(header = header, rowsInserted = affected)
            }
            Operation.UPDATE -> {
                val affected = connection.createStatement().use { stmt ->
                    stmt.executeUpdate(buildUpdateFromStagingSql(table, nonUniqueNames, uniqueNames))
                }
                InstructionResult(header = header, rowsUpdated = affected)
            }
            Operation.INSERT_UPDATE -> {
                val (inserted, updated) = connection.createStatement().use { stmt ->
                    stmt.executeQuery(buildUpsertFromStagingSql(table, colNames, uniqueNames, nonUniqueNames)).use { rs ->
                        rs.next()
                        rs.getInt("inserted") to rs.getInt("updated")
                    }
                }
                InstructionResult(header = header, rowsInserted = inserted, rowsUpdated = updated)
            }
            Operation.DELETE -> {
                val affected = connection.createStatement().use { stmt ->
                    stmt.executeUpdate(buildDeleteFromStagingSql(table, uniqueNames))
                }
                InstructionResult(header = header, rowsDeleted = affected)
            }
        }
    }

    private fun copyToStaging(connection: Connection, colNames: List<String>, rows: List<ResolvedRow>) {
        val pgConn = connection.unwrap(PGConnection::class.java)
        val copyIn = pgConn.copyAPI.copyIn(buildCopySql(colNames))
        try {
            for (row in rows) {
                val line = row.columns.joinToString(separator = ",") { col ->
                    formatCsvValue(col.value)
                } + "\n"
                val bytes = line.toByteArray(Charsets.UTF_8)
                copyIn.writeToCopy(bytes, 0, bytes.size)
            }
            copyIn.endCopy()
        } catch (exception: Exception) {
            copyIn.cancelCopy()
            throw exception
        }
    }

    private fun formatCsvValue(value: Any?): String {
        if (value == null) {
            return ""
        }
        val str = value.toString()
        return "\"${str.replace("\"", "\"\"")}\""
    }

    internal fun buildCreateStagingSql(table: QualifiedTable, colNames: List<String>): String {
        val cols = colNames.joinToString()
        return "CREATE TEMPORARY TABLE $STAGING ON COMMIT DROP AS SELECT $cols FROM $table WHERE FALSE"
    }

    internal fun buildCopySql(colNames: List<String>): String {
        val cols = colNames.joinToString()
        return "COPY $STAGING ($cols) FROM STDIN WITH (FORMAT CSV, NULL '')"
    }

    internal fun buildInsertFromStagingSql(table: QualifiedTable, colNames: List<String>): String {
        val cols = colNames.joinToString()
        return "INSERT INTO $table ($cols) SELECT $cols FROM $STAGING"
    }

    internal fun buildUpdateFromStagingSql(
        table: QualifiedTable,
        setCols: List<String>,
        whereCols: List<String>,
    ): String {
        val setClause = setCols.joinToString { "$it = s.$it" }
        val whereClause = whereCols.joinToString(" AND ") { "$table.$it = s.$it" }
        return "UPDATE $table SET $setClause FROM $STAGING s WHERE $whereClause"
    }

    internal fun buildUpsertFromStagingSql(
        table: QualifiedTable,
        allCols: List<String>,
        uniqueCols: List<String>,
        updateCols: List<String>,
    ): String {
        val cols = allCols.joinToString()
        val conflict = uniqueCols.joinToString()
        val update = updateCols.joinToString { "$it = EXCLUDED.$it" }
        return """
            WITH upsert AS (
                INSERT INTO $table ($cols)
                SELECT $cols FROM $STAGING
                ON CONFLICT ($conflict) DO UPDATE SET $update
                RETURNING (xmax = 0) AS is_insert
            )
            SELECT
                count(*) FILTER (WHERE is_insert) AS inserted,
                count(*) FILTER (WHERE NOT is_insert) AS updated
            FROM upsert
        """.trimIndent()
    }

    internal fun buildDeleteFromStagingSql(table: QualifiedTable, whereCols: List<String>): String {
        val whereClause = whereCols.joinToString(" AND ") { "$table.$it = s.$it" }
        return "DELETE FROM $table USING $STAGING s WHERE $whereClause"
    }

    private fun Column.columnName(): String = when (this) {
        is Column.Field -> name
        is Column.ForeignKey -> name
    }
}
