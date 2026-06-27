package de.hauschild.upsert.sql.result

/**
 * Aggregated outcome of a full [de.hauschild.upsert.sql.SqlExecutor] run.
 *
 * Provides both per-instruction detail via [instructions] and convenient totals
 * across all instructions.
 *
 * @param instructions the individual results, one per processed instruction
 */
data class ExecutionResult(
    val instructions: List<InstructionResult>,
) {

    /** Total number of rows inserted across all instructions. */
    val totalInserted: Int get() = instructions.sumOf { it.rowsInserted }

    /** Total number of rows updated across all instructions. */
    val totalUpdated: Int get() = instructions.sumOf { it.rowsUpdated }

    /** Total number of rows deleted across all instructions. */
    val totalDeleted: Int get() = instructions.sumOf { it.rowsDeleted }

    /** All skipped rows across all instructions. */
    val skippedRows: List<SkippedRow> get() = instructions.flatMap { it.skippedRows }

    /** Returns `true` if no rows were skipped in any instruction. */
    val successful: Boolean get() = instructions.all { it.successful }
}
