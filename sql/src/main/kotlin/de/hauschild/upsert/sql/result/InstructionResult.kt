package de.hauschild.upsert.sql.result

import de.hauschild.upsert.model.Header

/**
 * Holds the outcome of executing a single [de.hauschild.upsert.model.Instruction].
 *
 * Counters reflect rows actually affected in the database. Rows that could not be
 * processed are collected in [skippedRows] rather than causing an abort (row-level
 * errors only, e.g. FK resolution failures or constraint violations in tolerant strategies).
 *
 * An instruction-level failure — where the entire instruction could not be executed and
 * the transaction was rolled back — is represented by [cause]. In this case [skippedRows]
 * is empty and all counters are zero.
 *
 * @param header the header of the instruction that was executed
 * @param rowsInserted number of rows inserted
 * @param rowsUpdated number of rows updated
 * @param rowsDeleted number of rows deleted
 * @param skippedRows rows skipped due to tolerable row-level errors
 * @param cause non-null when the entire instruction failed at the instruction level
 */
data class InstructionResult(
    val header: Header,
    val rowsInserted: Int = 0,
    val rowsUpdated: Int = 0,
    val rowsDeleted: Int = 0,
    val skippedRows: List<SkippedRow> = emptyList(),
    val cause: Throwable? = null,
) {

    /**
     * Returns `true` if no rows were skipped and no instruction-level error occurred.
     */
    val successful: Boolean get() = skippedRows.isEmpty() && cause == null
}
