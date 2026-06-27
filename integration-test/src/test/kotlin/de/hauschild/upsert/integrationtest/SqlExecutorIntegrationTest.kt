package de.hauschild.upsert.integrationtest

import de.hauschild.upsert.parser.csv.CsvInstructionParser
import de.hauschild.upsert.sql.SqlExecutor
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
abstract class SqlExecutorIntegrationTest {

    companion object {

        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
    }

    protected lateinit var dataSource: DataSource
    protected lateinit var executor: SqlExecutor

    protected abstract fun createExecutor(dataSource: DataSource): SqlExecutor

    @BeforeTest
    fun setUp() {
        dataSource = PGSimpleDataSource().apply {
            setUrl(postgres.jdbcUrl)
            user = postgres.username
            password = postgres.password
        }
        executor = createExecutor(dataSource)
    }

    protected fun execute(sql: String) {
        dataSource.connection.use { it.createStatement().execute(sql) }
    }

    protected fun query(sql: String): List<Map<String, Any?>> {
        dataSource.connection.use { connection ->
            connection.createStatement().executeQuery(sql).use { rs ->
                val meta = rs.metaData
                val results = mutableListOf<Map<String, Any?>>()
                while (rs.next()) {
                    results.add((1..meta.columnCount).associate { i ->
                        meta.getColumnName(i).lowercase() to rs.getObject(i)
                    })
                }
                return results
            }
        }
    }

    protected fun parse(csv: String) =
        CsvInstructionParser().parse(csv.trimIndent().byteInputStream())

    @Test
    fun `INSERT inserts new rows`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT NOT NULL, age INT)")
        execute("TRUNCATE person")

        val result = executor.execute(
            parse(
                """
                INSERT person ; name ; age
                ; Ada ; 42
                ; John ; 30
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(2, result.totalInserted)
        val rows = query("SELECT name, age FROM person ORDER BY name")
        assertEquals(2, rows.size)
        assertEquals("Ada", rows[0]["name"])
        assertEquals("John", rows[1]["name"])
    }

    @Test
    fun `UPDATE updates existing rows`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT NOT NULL, age INT)")
        execute("TRUNCATE person")
        execute("INSERT INTO person (name, age) VALUES ('Ada', 42)")

        val result = executor.execute(
            parse(
                """
                UPDATE person ; name[unique=true] ; age
                ; Ada ; 99
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(1, result.totalUpdated)
        assertEquals(99, query("SELECT age FROM person WHERE name = 'Ada'").single()["age"])
    }

    @Test
    fun `INSERT_UPDATE inserts when row does not exist`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT UNIQUE NOT NULL, age INT)")
        execute("TRUNCATE person")

        val result = executor.execute(
            parse(
                """
                INSERT_UPDATE person ; name[unique=true] ; age
                ; Ada ; 42
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(1, result.totalInserted)
        assertEquals(42, query("SELECT age FROM person WHERE name = 'Ada'").single()["age"])
    }

    @Test
    fun `INSERT_UPDATE updates when row already exists`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT UNIQUE NOT NULL, age INT)")
        execute("TRUNCATE person")
        execute("INSERT INTO person (name, age) VALUES ('Ada', 42)")

        executor.execute(
            parse(
                """
                INSERT_UPDATE person ; name[unique=true] ; age
                ; Ada ; 99
                """,
            ),
        )

        assertEquals(99, query("SELECT age FROM person WHERE name = 'Ada'").single()["age"])
    }

    @Test
    fun `DELETE removes matching rows`() {
        execute("CREATE TABLE IF NOT EXISTS person (id SERIAL PRIMARY KEY, name TEXT NOT NULL, age INT)")
        execute("TRUNCATE person")
        execute("INSERT INTO person (name, age) VALUES ('Ada', 42)")
        execute("INSERT INTO person (name, age) VALUES ('John', 30)")

        val result = executor.execute(
            parse(
                """
                DELETE person ; name[unique=true]
                ; Ada
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(1, result.totalDeleted)
        val remaining = query("SELECT name FROM person")
        assertEquals(1, remaining.size)
        assertEquals("John", remaining.single()["name"])
    }

    @Test
    fun `resolves foreign key to primary key`() {
        execute("CREATE TABLE IF NOT EXISTS customer (id SERIAL PRIMARY KEY, email TEXT UNIQUE NOT NULL)")
        execute("CREATE TABLE IF NOT EXISTS order_item (id SERIAL PRIMARY KEY, customer_id INT REFERENCES customer(id), quantity INT)")
        execute("TRUNCATE order_item")
        execute("TRUNCATE customer CASCADE")
        execute("INSERT INTO customer (email) VALUES ('ada@example.com')")

        val result = executor.execute(
            parse(
                """
                ${'$'}customer=customer_id(customer.email)
                INSERT order_item ; ${'$'}customer ; quantity
                ; ada@example.com ; 3
                """,
            ),
        )

        assertTrue(result.successful)
        assertEquals(1, result.totalInserted)
        assertEquals(3, query("SELECT quantity FROM order_item").single()["quantity"])
    }

    @Test
    fun `skips row when foreign key cannot be resolved`() {
        execute("CREATE TABLE IF NOT EXISTS customer (id SERIAL PRIMARY KEY, email TEXT UNIQUE NOT NULL)")
        execute("CREATE TABLE IF NOT EXISTS order_item (id SERIAL PRIMARY KEY, customer_id INT REFERENCES customer(id), quantity INT)")
        execute("TRUNCATE order_item")
        execute("TRUNCATE customer CASCADE")

        val result = executor.execute(
            parse(
                """
                ${'$'}customer=customer_id(customer.email)
                INSERT order_item ; ${'$'}customer ; quantity
                ; unknown@example.com ; 3
                """,
            ),
        )

        assertEquals(1, result.skippedRows.size)
        assertEquals(0, result.totalInserted)
    }
}
