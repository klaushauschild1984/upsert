package de.hauschild.upsert.sql

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QualifiedTableTest {

    @Test
    fun `parses schema and table from dot-qualified name`() {
        val table = QualifiedTable.of("public.person")
        assertEquals("public", table.schema)
        assertEquals("person", table.name)
    }

    @Test
    fun `uses default schema for unqualified name`() {
        val table = QualifiedTable.of("person", "public")
        assertEquals("public", table.schema)
        assertEquals("person", table.name)
    }

    @Test
    fun `schema is null when name is unqualified and no default given`() {
        val table = QualifiedTable.of("person")
        assertNull(table.schema)
        assertEquals("person", table.name)
    }

    @Test
    fun `explicit dot qualification takes precedence over default schema`() {
        val table = QualifiedTable.of("app.person", "public")
        assertEquals("app", table.schema)
        assertEquals("person", table.name)
    }

    @Test
    fun `toString returns schema-qualified name`() {
        val table = QualifiedTable(schema = "public", name = "person")
        assertEquals("public.person", table.toString())
    }

    @Test
    fun `toString returns bare name when schema is null`() {
        val table = QualifiedTable(schema = null, name = "person")
        assertEquals("person", table.toString())
    }

    @Test
    fun `trims whitespace from parsed parts`() {
        val table = QualifiedTable.of("  public . person  ")
        assertEquals("public", table.schema)
        assertEquals("person", table.name)
    }
}
