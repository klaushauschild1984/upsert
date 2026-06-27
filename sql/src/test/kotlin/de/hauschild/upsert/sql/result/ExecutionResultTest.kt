package de.hauschild.upsert.sql.result

import de.hauschild.upsert.model.Header
import de.hauschild.upsert.model.Operation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionResultTest {

    private val header = Header(Operation.INSERT_UPDATE, "person", emptyList())

    @Test
    fun `totalInserted sums across all instructions`() {
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, rowsInserted = 2),
                InstructionResult(header, rowsInserted = 3),
            ),
        )
        assertEquals(5, result.totalInserted)
    }

    @Test
    fun `totalUpdated sums across all instructions`() {
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, rowsUpdated = 1),
                InstructionResult(header, rowsUpdated = 4),
            ),
        )
        assertEquals(5, result.totalUpdated)
    }

    @Test
    fun `totalDeleted sums across all instructions`() {
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, rowsDeleted = 2),
                InstructionResult(header, rowsDeleted = 1),
            ),
        )
        assertEquals(3, result.totalDeleted)
    }

    @Test
    fun `skippedRows flattens across all instructions`() {
        val skip1 = SkippedRow(1, listOf("Ada"), "reason A")
        val skip2 = SkippedRow(2, listOf("John"), "reason B")
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, skippedRows = listOf(skip1)),
                InstructionResult(header, skippedRows = listOf(skip2)),
            ),
        )
        assertEquals(listOf(skip1, skip2), result.skippedRows)
    }

    @Test
    fun `successful is true when no rows were skipped`() {
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, rowsInserted = 2),
                InstructionResult(header, rowsInserted = 1),
            ),
        )
        assertTrue(result.successful)
    }

    @Test
    fun `successful is false when any instruction has skipped rows`() {
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, rowsInserted = 2),
                InstructionResult(header, skippedRows = listOf(SkippedRow(3, listOf("x"), "failed"))),
            ),
        )
        assertFalse(result.successful)
    }

    @Test
    fun `successful is false when any instruction has a cause`() {
        val result = ExecutionResult(
            listOf(
                InstructionResult(header, rowsInserted = 2),
                InstructionResult(header, cause = RuntimeException("table not found")),
            ),
        )
        assertFalse(result.successful)
    }

    @Test
    fun `InstructionResult with cause has successful false`() {
        val result = InstructionResult(header, cause = RuntimeException("connection lost"))
        assertFalse(result.successful)
    }
}
