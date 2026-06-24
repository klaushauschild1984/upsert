package de.hauschild.upsert.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroProcessorTest {

    private val processor = MacroProcessor()

    @Test
    fun `collects macro definition`() {
        processor.collect("\$customer=customer_id(customer.email)")
        assertEquals("customer_id(customer.email)", processor.macros["customer"])
    }

    @Test
    fun `collects macro with simple value`() {
        processor.collect("\$defaultStatus=ACTIVE")
        assertEquals("ACTIVE", processor.macros["defaultStatus"])
    }

    @Test
    fun `value may contain equals sign`() {
        processor.collect("\$expr=a=b")
        assertEquals("a=b", processor.macros["expr"])
    }

    @Test
    fun `trims whitespace from name and value`() {
        processor.collect("  \$  customer  =  customer_id(customer.email)  ")
        assertEquals("customer_id(customer.email)", processor.macros["customer"])
    }

    @Test
    fun `ignores non-macro lines`() {
        processor.collect("INSERT_UPDATE person ; name")
        processor.collect("; Ada ; 19")
        processor.collect("")
        assertTrue(processor.macros.isEmpty())
    }

    @Test
    fun `ignores dollar sign without equals`() {
        processor.collect("\$orphan")
        assertTrue(processor.macros.isEmpty())
    }

    @Test
    fun `collects multiple macros in order`() {
        processor.collect("\$customer=customer_id(customer.email)")
        processor.collect("\$product=product_id(product.code)")
        assertEquals("customer_id(customer.email)", processor.macros["customer"])
        assertEquals("product_id(product.code)", processor.macros["product"])
    }

    @Test
    fun `later definition overwrites earlier one`() {
        processor.collect("\$customer=customer_id(customer.email)")
        processor.collect("\$customer=customer_id(customer.username)")
        assertEquals("customer_id(customer.username)", processor.macros["customer"])
    }

    @Test
    fun `resolve substitutes macro reference in string`() {
        processor.collect("\$customer=customer_id(customer.email)")
        assertEquals(
            "INSERT_UPDATE order_item ; order_code ; customer_id(customer.email) ; quantity",
            processor.resolve("INSERT_UPDATE order_item ; order_code ; \$customer ; quantity"),
        )
    }

    @Test
    fun `resolve leaves unknown references unchanged`() {
        assertEquals("order \$unknown reference", processor.resolve("order \$unknown reference"))
    }

    @Test
    fun `resolve substitutes multiple references in one string`() {
        processor.collect("\$customer=customer_id(customer.email)")
        processor.collect("\$product=product_id(product.code)")
        assertEquals(
            "; customer_id(customer.email) ; product_id(product.code) ; 3",
            processor.resolve("; \$customer ; \$product ; 3"),
        )
    }

    @Test
    fun `resolve prefers longer macro name over shorter prefix`() {
        processor.collect("\$cat=WRONG")
        processor.collect("\$catalog=CORRECT")
        assertEquals("CORRECT", processor.resolve("\$catalog"))
    }

    @Test
    fun `resolve handles nested macro references`() {
        processor.collect("\$product_column=product_id")
        processor.collect("\$product_table=product")
        processor.collect("\$product=\$product_column(\$product_table.code)")
        assertEquals("product_id(product.code)", processor.resolve("\$product"))
    }

    @Test
    fun `resolve returns input unchanged when no macros collected`() {
        val input = "INSERT_UPDATE person ; name ; age"
        assertEquals(input, processor.resolve(input))
    }
}
