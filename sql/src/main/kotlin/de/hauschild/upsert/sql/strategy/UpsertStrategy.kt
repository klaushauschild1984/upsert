package de.hauschild.upsert.sql.strategy

import de.hauschild.upsert.model.Header
import de.hauschild.upsert.sql.QualifiedTable
import de.hauschild.upsert.sql.ResolvedRow
import de.hauschild.upsert.sql.result.InstructionResult
import io.micrometer.observation.ObservationRegistry
import java.sql.Connection

/**
 * Executes a batch of resolved rows against a database using a specific SQL strategy.
 *
 * Each strategy encapsulates the SQL dialect used to perform the four operations defined
 * in [de.hauschild.upsert.model.Operation].
 *
 * The [observationRegistry] is provided so strategies may instrument their internal phases
 * (e.g. COPY duration, merge duration). Strategies that do not observe anything may ignore it.
 * [ObservationRegistry.NOOP] results in zero overhead.
 *
 * Built-in implementations:
 * - [PostgresCopyStrategy] — bulk COPY into a staging table, then a single merge statement
 * - [PostgresOnConflictStrategy] — row-by-row `INSERT … ON CONFLICT DO UPDATE`
 */
fun interface UpsertStrategy {

    /**
     * Executes [rows] against the given [connection] for the specified [table].
     *
     * The [connection] has auto-commit disabled; the strategy must not commit or roll back.
     * Rows that cannot be processed must be reported in [InstructionResult.skippedRows]
     * rather than throwing.
     *
     * @param connection an open connection with auto-commit disabled
     * @param table the qualified target table
     * @param header the instruction header, providing operation type and column descriptors
     * @param rows the resolved rows to execute
     * @param observationRegistry registry for internal phase observations
     * @return the result of the execution
     */
    fun execute(
        connection: Connection,
        table: QualifiedTable,
        header: Header,
        rows: List<ResolvedRow>,
        observationRegistry: ObservationRegistry,
    ): InstructionResult
}
