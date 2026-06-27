package de.hauschild.upsert.sql.strategy

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.sql.QualifiedTable
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresCopyStrategyTest {

    private val strategy = PostgresCopyStrategy()

    private fun field(name: String, vararg modifiers: Modifier) =
        Column.Field(name, modifiers.toList())

    private fun unique(name: String) = field(name, Modifier.Unique)

    @Test
    fun `buildCreateStagingSql selects columns from target`() {
        val sql = strategy.buildCreateStagingSql(
            QualifiedTable(null, "person"),
            listOf("name", "age"),
        )
        assertEquals(
            "CREATE TEMPORARY TABLE upsert_staging ON COMMIT DROP AS SELECT name, age FROM person WHERE FALSE",
            sql,
        )
    }

    @Test
    fun `buildCreateStagingSql uses schema-qualified table`() {
        val sql = strategy.buildCreateStagingSql(
            QualifiedTable("public", "person"),
            listOf("name", "age"),
        )
        assertEquals(
            "CREATE TEMPORARY TABLE upsert_staging ON COMMIT DROP AS SELECT name, age FROM public.person WHERE FALSE",
            sql,
        )
    }

    @Test
    fun `buildCopySql references staging table with CSV format`() {
        val sql = strategy.buildCopySql(listOf("name", "age"))
        assertEquals(
            "COPY upsert_staging (name, age) FROM STDIN WITH (FORMAT CSV, NULL '')",
            sql,
        )
    }

    @Test
    fun `buildInsertFromStagingSql inserts all columns`() {
        val sql = strategy.buildInsertFromStagingSql(
            QualifiedTable(null, "person"),
            listOf("name", "age"),
        )
        assertEquals("INSERT INTO person (name, age) SELECT name, age FROM upsert_staging", sql)
    }

    @Test
    fun `buildUpdateFromStagingSql sets non-unique columns and filters on unique columns`() {
        val sql = strategy.buildUpdateFromStagingSql(
            QualifiedTable(null, "person"),
            setCols = listOf("name", "age"),
            whereCols = listOf("email"),
        )
        assertEquals(
            "UPDATE person SET name = s.name, age = s.age FROM upsert_staging s WHERE person.email = s.email",
            sql,
        )
    }

    @Test
    fun `buildUpdateFromStagingSql uses schema-qualified table in WHERE`() {
        val sql = strategy.buildUpdateFromStagingSql(
            QualifiedTable("public", "person"),
            setCols = listOf("age"),
            whereCols = listOf("email"),
        )
        assertEquals(
            "UPDATE public.person SET age = s.age FROM upsert_staging s WHERE public.person.email = s.email",
            sql,
        )
    }

    @Test
    fun `buildUpsertFromStagingSql uses ON CONFLICT with xmax counting`() {
        val sql = strategy.buildUpsertFromStagingSql(
            QualifiedTable(null, "person"),
            allCols = listOf("email", "name", "age"),
            uniqueCols = listOf("email"),
            updateCols = listOf("name", "age"),
        )
        assertEquals(
            """
            WITH upsert AS (
                INSERT INTO person (email, name, age)
                SELECT email, name, age FROM upsert_staging
                ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age
                RETURNING (xmax = 0) AS is_insert
            )
            SELECT
                count(*) FILTER (WHERE is_insert) AS inserted,
                count(*) FILTER (WHERE NOT is_insert) AS updated
            FROM upsert
            """.trimIndent(),
            sql,
        )
    }

    @Test
    fun `buildDeleteFromStagingSql uses USING clause`() {
        val sql = strategy.buildDeleteFromStagingSql(
            QualifiedTable(null, "person"),
            whereCols = listOf("email"),
        )
        assertEquals(
            "DELETE FROM person USING upsert_staging s WHERE person.email = s.email",
            sql,
        )
    }

    @Test
    fun `buildDeleteFromStagingSql with multiple unique columns`() {
        val sql = strategy.buildDeleteFromStagingSql(
            QualifiedTable(null, "order_item"),
            whereCols = listOf("order_code", "product_code"),
        )
        assertEquals(
            "DELETE FROM order_item USING upsert_staging s WHERE order_item.order_code = s.order_code AND order_item.product_code = s.product_code",
            sql,
        )
    }
}
