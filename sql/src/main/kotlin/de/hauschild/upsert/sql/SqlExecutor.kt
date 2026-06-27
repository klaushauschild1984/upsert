package de.hauschild.upsert.sql

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.DataRow
import de.hauschild.upsert.model.Instruction
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.sql.exception.TableNotFoundException
import de.hauschild.upsert.sql.metadata.TableMetadata
import de.hauschild.upsert.sql.resolver.ForeignKeyResolver
import de.hauschild.upsert.sql.result.ExecutionResult
import de.hauschild.upsert.sql.result.InstructionResult
import de.hauschild.upsert.sql.result.SkippedRow
import io.micrometer.observation.Observation

/**
 * Executes a sequence of [Instruction] objects against a relational database via JDBC.
 *
 * Each instruction is executed in its own transaction: the connection is obtained from the
 * configured [ExecutionConfig.dataSource], auto-commit is disabled, and the transaction is
 * committed after the instruction completes. If an unrecoverable error occurs at the
 * instruction level, the transaction is rolled back and all rows of that instruction are
 * reported as skipped.
 *
 * Row-level errors (FK resolution failures, type-mapping issues, SQL constraint violations)
 * are tolerated: the failing row is added to [InstructionResult.skippedRows] and processing
 * continues with the next row.
 *
 * Table metadata is cached across instructions for the duration of a single [execute] call.
 *
 * @param config the execution configuration
 */
class SqlExecutor(private val config: ExecutionConfig) {

    /**
     * Executes all [instructions] and returns an aggregated [ExecutionResult].
     *
     * @param instructions the instructions to execute, consumed lazily
     * @return the aggregated result across all instructions
     */
    fun execute(instructions: Sequence<Instruction>): ExecutionResult {
        val metadataCache = mutableMapOf<String, TableMetadata>()
        val results = instructions.map { executeInstruction(it, metadataCache) }.toList()
        return ExecutionResult(results)
    }

    private fun executeInstruction(
        instruction: Instruction,
        metadataCache: MutableMap<String, TableMetadata>,
    ): InstructionResult {
        val table = QualifiedTable.of(instruction.header.table, config.defaultSchema)
        val rows = instruction.rows.toList()
        return Observation.createNotStarted("upsert.instruction", config.observationRegistry)
            .lowCardinalityKeyValue("table", table.toString())
            .lowCardinalityKeyValue("operation", instruction.header.operation.name)
            .highCardinalityKeyValue("rows", rows.size.toString())
            .timed { executeInstructionInternal(instruction, table, rows, metadataCache) }
    }

    private fun executeInstructionInternal(
        instruction: Instruction,
        table: QualifiedTable,
        rows: List<DataRow>,
        metadataCache: MutableMap<String, TableMetadata>,
    ): InstructionResult {
        config.dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val metadata = metadataCache.getOrPut(table.toString()) {
                    TableMetadata.load(connection, table)
                }
                val resolver = ForeignKeyResolver(connection, config.defaultSchema, config.observationRegistry)
                primeResolver(resolver, instruction.header.columns, rows)
                val (resolvedRows, preSkipped) = resolveRows(rows, instruction.header.columns, metadata, resolver)
                val result = config.strategy.execute(
                    connection, table, instruction.header, resolvedRows, config.observationRegistry,
                )
                connection.commit()
                return result.copy(skippedRows = preSkipped + result.skippedRows)
            } catch (exception: Exception) {
                connection.rollback()
                return InstructionResult(header = instruction.header, cause = exception)
            }
        }
    }

    private fun resolveRows(
        rows: List<DataRow>,
        columns: List<Column>,
        metadata: TableMetadata,
        resolver: ForeignKeyResolver,
    ): Pair<List<ResolvedRow>, List<SkippedRow>> {
        val resolved = mutableListOf<ResolvedRow>()
        val skipped = mutableListOf<SkippedRow>()
        for (dataRow in rows) {
            try {
                resolved.add(resolveRow(dataRow, columns, metadata, resolver))
            } catch (exception: Exception) {
                skipped.add(
                    SkippedRow(
                        dataRow.lineNumber,
                        dataRow.values,
                        exception.message ?: "row resolution failed",
                        exception,
                    ),
                )
            }
        }
        return resolved to skipped
    }

    private fun primeResolver(resolver: ForeignKeyResolver, columns: List<Column>, rows: List<DataRow>) {
        val fkColumns = columns.filterIsInstance<Column.ForeignKey>()
        if (fkColumns.isEmpty()) {
            return
        }
        for (fkColumn in fkColumns) {
            val colIndex = columns.indexOf(fkColumn)
            val values = rows
                .map { it.values.getOrElse(colIndex) { "" } }
                .filter { it.isNotEmpty() }
                .toSet()
            resolver.prime(fkColumn, values)
        }
    }

    private fun resolveRow(
        dataRow: de.hauschild.upsert.model.DataRow,
        columns: List<Column>,
        metadata: TableMetadata,
        resolver: ForeignKeyResolver,
    ): ResolvedRow {
        val resolvedColumns = columns.mapIndexed { index, column ->
            val rawValue = dataRow.values.getOrElse(index) { "" }
            val colName = column.columnName()
            val colMeta = metadata.column(colName)
                ?: throw TableNotFoundException("Column '$colName' not found in table '${metadata.table}'")
            val effectiveValue = if (rawValue.isEmpty()) {
                column.modifiers.filterIsInstance<Modifier.Default>().firstOrNull()?.value ?: ""
            } else {
                rawValue
            }
            val resolvedValue = when {
                column is Column.ForeignKey && effectiveValue.isNotEmpty() -> resolver.resolve(column, effectiveValue)
                effectiveValue.isEmpty() -> if (colMeta.nullable) { null } else { "" }
                else -> effectiveValue
            }
            ResolvedColumn(column, resolvedValue, colMeta.sqlType)
        }
        return ResolvedRow(dataRow.lineNumber, resolvedColumns)
    }

    private fun Column.columnName(): String = when (this) {
        is Column.Field -> name
        is Column.ForeignKey -> name
    }
}
