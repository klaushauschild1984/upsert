package de.hauschild.upsert.integrationtest

import de.hauschild.upsert.sql.ExecutionConfig
import de.hauschild.upsert.sql.SqlExecutor
import de.hauschild.upsert.sql.strategy.PostgresOnConflictStrategy
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresOnConflictStrategyIntegrationTest : SqlExecutorIntegrationTest() {

    override fun createExecutor(dataSource: DataSource): SqlExecutor =
        SqlExecutor(ExecutionConfig(dataSource = dataSource, strategy = PostgresOnConflictStrategy()))

    @Test
    fun `skips row when UPDATE finds no matching unique key`() {
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

        assertEquals(0, result.totalUpdated)
        assertEquals(1, result.skippedRows.size)
    }

    @Test
    fun `skips row when DELETE finds no matching unique key`() {
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

        assertEquals(0, result.totalDeleted)
        assertEquals(1, result.skippedRows.size)
    }

    @Test
    fun `continues processing remaining rows after a skipped row`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT NOT NULL, age INT)")
        execute("TRUNCATE person")
        execute("INSERT INTO person (name, age) VALUES ('Ada', 42)")

        val result = executor.execute(
            parse(
                """
                UPDATE person ; name[unique=true] ; age
                ; NonExistent ; 0
                ; Ada ; 99
                """,
            ),
        )

        assertEquals(1, result.totalUpdated)
        assertEquals(1, result.skippedRows.size)
        assertEquals(99, query("SELECT age FROM person WHERE name = 'Ada'").single()["age"])
    }
}
