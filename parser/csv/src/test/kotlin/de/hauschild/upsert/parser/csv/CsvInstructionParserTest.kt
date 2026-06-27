package de.hauschild.upsert.parser.csv

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.model.Operation
import de.hauschild.upsert.parser.csv.exception.CsvParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CsvInstructionParserTest {

    private val parser = CsvInstructionParser()

    private fun parse(csv: String) =
        parser.parse(csv.trimIndent().byteInputStream()).toList()

    @Test
    fun `parses single instruction with one data row`() {
        val instructions = parse(
            """
            INSERT person ; name[unique=true] ; age
            ; Ada ; 19
            """,
        )
        assertEquals(1, instructions.size)
        val instruction = instructions.single()
        assertEquals(Operation.INSERT, instruction.header.operation)
        assertEquals("person", instruction.header.table)
        val row = instruction.rows.single()
        assertEquals(listOf("Ada", "19"), row.values)
    }

    @Test
    fun `parses multiple data rows`() {
        val rows = parse(
            """
            INSERT person ; name
            ; Ada
            ; John
            ; Alan
            """,
        ).single().rows.toList()
        assertEquals(3, rows.size)
        assertEquals("Ada", rows[0].values.single())
        assertEquals("John", rows[1].values.single())
        assertEquals("Alan", rows[2].values.single())
    }

    @Test
    fun `parses multiple instructions`() {
        val instructions = parse(
            """
            INSERT person ; name
            ; Ada
            INSERT_UPDATE address ; street
            ; Main St
            """,
        )
        assertEquals(2, instructions.size)
        assertEquals("person", instructions[0].header.table)
        assertEquals("address", instructions[1].header.table)
    }

    @Test
    fun `parses foreign key column`() {
        val header = parse(
            """
            INSERT order_item ; order_code[unique=true] ; customer(email) ; quantity
            ; ORD-001 ; ada@example.com ; 3
            """,
        ).single().header
        assertIs<Column.ForeignKey>(header.columns[1])
        assertEquals("customer", (header.columns[1] as Column.ForeignKey).name)
        assertEquals("email", (header.columns[1] as Column.ForeignKey).attribute)
    }

    @Test
    fun `resolves macro references`() {
        val header = parse(
            """
            ${'$'}customer=customer_id(customer.email)
            INSERT order_item ; ${'$'}customer ; quantity
            ; ada@example.com ; 3
            """,
        ).single().header
        assertIs<Column.ForeignKey>(header.columns[0])
        assertEquals("customer_id", (header.columns[0] as Column.ForeignKey).name)
    }

    @Test
    fun `skips blank lines`() {
        val instructions = parse(
            """
            INSERT person ; name

            ; Ada

            """,
        )
        assertEquals(1, instructions.size)
        assertEquals(1, instructions.single().rows.toList().size)
    }

    @Test
    fun `parses all four operations`() {
        listOf("INSERT", "UPDATE", "INSERT_UPDATE", "DELETE").forEach { op ->
            val instruction = parse("$op person ; name\n; Ada").single()
            assertEquals(Operation.valueOf(op), instruction.header.operation)
        }
    }

    @Test
    fun `fails on malformed CSV`() {
        assertFailsWith<CsvParseException> {
            parse("INSERT person ; name\n; \"unclosed quote").single().rows.toList()
        }
    }
}
