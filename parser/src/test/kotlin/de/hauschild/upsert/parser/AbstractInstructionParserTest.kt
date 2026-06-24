package de.hauschild.upsert.parser

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Operation
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AbstractInstructionParserTest {

    private fun parserFor(vararg lines: List<String>): AbstractInstructionParser =
        object : AbstractInstructionParser() {
            override fun tokenize(input: InputStream) = lines.asSequence()
        }

    @Test
    fun `parses single instruction with one data row`() {
        val parser = parserFor(
            listOf("INSERT person", "name[unique=true]", "age"),
            listOf("", "Ada", "19"),
        )
        val instructions = parser.parse(InputStream.nullInputStream()).toList()
        assertEquals(1, instructions.size)
        val instruction = instructions.single()
        assertEquals(Operation.INSERT, instruction.header.operation)
        assertEquals("person", instruction.header.table)
        val row = instruction.rows.single()
        assertEquals(listOf("Ada", "19"), row.values)
    }

    @Test
    fun `parses multiple data rows`() {
        val parser = parserFor(
            listOf("INSERT person", "name"),
            listOf("", "Ada"),
            listOf("", "John"),
            listOf("", "Alan"),
        )
        val rows = parser.parse(InputStream.nullInputStream()).single().rows.toList()
        assertEquals(3, rows.size)
        assertEquals("Ada", rows[0].values.single())
        assertEquals("John", rows[1].values.single())
        assertEquals("Alan", rows[2].values.single())
    }

    @Test
    fun `parses multiple instructions`() {
        val parser = parserFor(
            listOf("INSERT person", "name"),
            listOf("", "Ada"),
            listOf("INSERT_UPDATE address", "street"),
            listOf("", "Main St"),
        )
        val instructions = parser.parse(InputStream.nullInputStream()).toList()
        assertEquals(2, instructions.size)
        assertEquals("person", instructions[0].header.table)
        assertEquals("address", instructions[1].header.table)
    }

    @Test
    fun `skips blank lines`() {
        val parser = parserFor(
            emptyList(),
            listOf("INSERT person", "name"),
            listOf(""),
            listOf("", "Ada"),
            emptyList(),
        )
        val instructions = parser.parse(InputStream.nullInputStream()).toList()
        assertEquals(1, instructions.size)
        assertEquals(1, instructions.single().rows.toList().size)
    }

    @Test
    fun `collects and resolves macros`() {
        val parser = parserFor(
            listOf("\$customer=customer_id(customer.email)"),
            listOf("INSERT order_item", "\$customer", "quantity"),
            listOf("", "ada@example.com", "3"),
        )
        val header = parser.parse(InputStream.nullInputStream()).single().header
        assertIs<Column.ForeignKey>(header.columns[0])
        assertEquals("customer_id", header.columns[0].let { (it as Column.ForeignKey).name })
    }

    @Test
    fun `assigns line numbers to data rows`() {
        val parser = parserFor(
            listOf("INSERT person", "name"),
            listOf("", "Ada"),
            listOf("", "John"),
        )
        val rows = parser.parse(InputStream.nullInputStream()).single().rows.toList()
        assertEquals(2, rows[0].lineNumber)
        assertEquals(3, rows[1].lineNumber)
    }

    @Test
    fun `data rows before any header are ignored`() {
        val parser = parserFor(
            listOf("", "orphan", "row"),
            listOf("INSERT person", "name"),
            listOf("", "Ada"),
        )
        val rows = parser.parse(InputStream.nullInputStream()).single().rows.toList()
        assertEquals(1, rows.size)
    }

    @Test
    fun `produces empty sequence for empty input`() {
        val parser = parserFor()
        val instructions = parser.parse(InputStream.nullInputStream()).toList()
        assertEquals(0, instructions.size)
    }
}
