package de.hauschild.upsert.integrationtest

import de.hauschild.upsert.sql.ExecutionConfig
import de.hauschild.upsert.sql.SqlExecutor
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresCopyStrategyIntegrationTest : SqlExecutorIntegrationTest() {

    override fun createExecutor(dataSource: DataSource): SqlExecutor =
        SqlExecutor(ExecutionConfig(dataSource = dataSource))

    @Test
    fun `UPDATE with no matching rows succeeds with zero updates`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT NOT NULL, age INT)")
        execute("TRUNCATE person")

        val result = executor.execute(
            parse(
                """
                UPDATE person ; name[unique=true] ; age
                ; NonExistent ; 99
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(0, result.totalUpdated)
        assertEquals(0, result.skippedRows.size)
    }

    @Test
    fun `DELETE with no matching rows succeeds with zero deletes`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT NOT NULL, age INT)")
        execute("TRUNCATE person")

        val result = executor.execute(
            parse(
                """
                DELETE person ; name[unique=true]
                ; NonExistent
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(0, result.totalDeleted)
        assertEquals(0, result.skippedRows.size)
    }

    @Test
    fun `INSERT_UPDATE correctly counts inserts and updates separately`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT UNIQUE NOT NULL, age INT)")
        execute("TRUNCATE person")
        execute("INSERT INTO person (name, age) VALUES ('Ada', 42)")

        val result = executor.execute(
            parse(
                """
                INSERT_UPDATE person ; name[unique=true] ; age
                ; Ada ; 99
                ; John ; 30
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(1, result.totalInserted)
        assertEquals(1, result.totalUpdated)
    }
}
