package de.hauschild.upsert.sql.strategy

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.model.Operation
import de.hauschild.upsert.sql.QualifiedTable
import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresOnConflictStrategyTest {

    private val strategy = PostgresOnConflictStrategy()

    private fun field(name: String, vararg modifiers: Modifier) =
        Column.Field(name, modifiers.toList())

    private fun unique(name: String) = field(name, Modifier.Unique)

    @Test
    fun `builds INSERT sql`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "person"),
            listOf(field("name"), field("age")),
            Operation.INSERT,
        )
        assertEquals("INSERT INTO person (name, age) VALUES (?, ?)", sql)
    }

    @Test
    fun `builds INSERT sql with schema`() {
        val sql = strategy.buildSql(
            QualifiedTable("public", "person"),
            listOf(field("name"), field("age")),
            Operation.INSERT,
        )
        assertEquals("INSERT INTO public.person (name, age) VALUES (?, ?)", sql)
    }

    @Test
    fun `builds UPDATE sql with unique column in WHERE`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "person"),
            listOf(unique("email"), field("name"), field("age")),
            Operation.UPDATE,
        )
        assertEquals("UPDATE person SET name = ?, age = ? WHERE email = ?", sql)
    }

    @Test
    fun `builds UPDATE sql with multiple unique columns in WHERE`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "order_item"),
            listOf(unique("order_code"), unique("product_code"), field("quantity")),
            Operation.UPDATE,
        )
        assertEquals("UPDATE order_item SET quantity = ? WHERE order_code = ? AND product_code = ?", sql)
    }

    @Test
    fun `builds INSERT_UPDATE sql with ON CONFLICT DO UPDATE`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "person"),
            listOf(unique("email"), field("name"), field("age")),
            Operation.INSERT_UPDATE,
        )
        assertEquals(
            "INSERT INTO person (email, name, age) VALUES (?, ?, ?) ON CONFLICT (email) " +
                "DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age",
            sql,
        )
    }

    @Test
    fun `builds INSERT_UPDATE sql with multiple unique columns`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "order_item"),
            listOf(unique("order_code"), unique("product_code"), field("quantity")),
            Operation.INSERT_UPDATE,
        )
        assertEquals(
            "INSERT INTO order_item (order_code, product_code, quantity) VALUES (?, ?, ?) " +
                "ON CONFLICT (order_code, product_code) DO UPDATE SET quantity = EXCLUDED.quantity",
            sql,
        )
    }

    @Test
    fun `builds DELETE sql with unique column in WHERE`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "person"),
            listOf(unique("email"), field("name")),
            Operation.DELETE,
        )
        assertEquals("DELETE FROM person WHERE email = ?", sql)
    }

    @Test
    fun `builds DELETE sql with multiple unique columns`() {
        val sql = strategy.buildSql(
            QualifiedTable(null, "order_item"),
            listOf(unique("order_code"), unique("product_code")),
            Operation.DELETE,
        )
        assertEquals("DELETE FROM order_item WHERE order_code = ? AND product_code = ?", sql)
    }
}
