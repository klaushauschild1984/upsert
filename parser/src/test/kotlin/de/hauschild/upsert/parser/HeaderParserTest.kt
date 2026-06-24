package de.hauschild.upsert.parser

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Modifier
import de.hauschild.upsert.model.Operation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HeaderParserTest {

    private val parser = HeaderParser()

    @Test
    fun `parses operation and table from first token`() {
        val header = parser.parse(listOf("INSERT_UPDATE person", "name"))
        assertEquals(Operation.INSERT_UPDATE, header.operation)
        assertEquals("person", header.table)
    }

    @Test
    fun `parses all four operations`() {
        listOf("INSERT", "UPDATE", "INSERT_UPDATE", "DELETE").forEach { op ->
            val header = parser.parse(listOf("$op t", "col"))
            assertEquals(Operation.valueOf(op), header.operation)
        }
    }

    @Test
    fun `parses plain field column`() {
        val header = parser.parse(listOf("INSERT t", "age"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertEquals("age", column.name)
        assertTrue(column.modifiers.isEmpty())
    }

    @Test
    fun `parses foreign key column`() {
        val header = parser.parse(listOf("INSERT t", "customer(email)"))
        val column = assertIs<Column.ForeignKey>(header.columns.single())
        assertEquals("customer", column.name)
        assertEquals("email", column.attribute)
        assertTrue(column.modifiers.isEmpty())
    }

    @Test
    fun `parses unique modifier`() {
        val header = parser.parse(listOf("INSERT t", "code[unique=true]"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertIs<Modifier.Unique>(column.modifiers.single())
    }

    @Test
    fun `parses default modifier`() {
        val header = parser.parse(listOf("INSERT t", "status[default=ACTIVE]"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertEquals(Modifier.Default("ACTIVE"), column.modifiers.single())
    }

    @Test
    fun `parses dateformat modifier`() {
        val header = parser.parse(listOf("INSERT t", "created[dateformat=dd.MM.yyyy]"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertEquals(Modifier.DateFormat("dd.MM.yyyy"), column.modifiers.single())
    }

    @Test
    fun `parses translator modifier`() {
        val header = parser.parse(listOf("INSERT t", "price[translator=com.example.EuroCentTranslator]"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertEquals(Modifier.Translator("com.example.EuroCentTranslator"), column.modifiers.single())
    }

    @Test
    fun `parses multiple modifiers in one bracket pair`() {
        val header = parser.parse(listOf("INSERT t", "code[unique=true,default=N/A]"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertEquals(2, column.modifiers.size)
        assertIs<Modifier.Unique>(column.modifiers[0])
        assertEquals(Modifier.Default("N/A"), column.modifiers[1])
    }

    @Test
    fun `parses multiple modifiers in separate bracket pairs`() {
        val header = parser.parse(listOf("INSERT t", "code[unique=true][default=N/A]"))
        val column = assertIs<Column.Field>(header.columns.single())
        assertEquals(2, column.modifiers.size)
        assertIs<Modifier.Unique>(column.modifiers[0])
        assertEquals(Modifier.Default("N/A"), column.modifiers[1])
    }

    @Test
    fun `parses foreign key with modifier`() {
        val header = parser.parse(listOf("INSERT t", "customer(email)[unique=true]"))
        val column = assertIs<Column.ForeignKey>(header.columns.single())
        assertEquals("customer", column.name)
        assertEquals("email", column.attribute)
        assertIs<Modifier.Unique>(column.modifiers.single())
    }

    @Test
    fun `parses multiple columns`() {
        val header = parser.parse(listOf("INSERT_UPDATE person", "name[unique=true]", "age", "gender[default=DIVERSE]"))
        assertEquals(3, header.columns.size)
        assertIs<Column.Field>(header.columns[0])
        assertIs<Column.Field>(header.columns[1])
        assertIs<Column.Field>(header.columns[2])
    }

    @Test
    fun `trims whitespace from tokens`() {
        val header = parser.parse(listOf("  INSERT_UPDATE   person  ", "  name  ", "  age  "))
        assertEquals("person", header.table)
        assertEquals("name", (header.columns[0] as Column.Field).name)
    }

    @Test
    fun `fails on empty token list`() {
        assertFailsWith<IllegalArgumentException> { parser.parse(emptyList()) }
    }

    @Test
    fun `fails on missing table name`() {
        assertFailsWith<IllegalArgumentException> { parser.parse(listOf("INSERT")) }
    }

    @Test
    fun `fails on unknown modifier`() {
        assertFailsWith<IllegalArgumentException> {
            parser.parse(listOf("INSERT t", "col[unknown=true]"))
        }
    }
}
