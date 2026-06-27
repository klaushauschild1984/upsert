package de.hauschild.upsert.parser.csv

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import de.hauschild.upsert.parser.AbstractInstructionParser
import de.hauschild.upsert.parser.csv.exception.CsvParseException
import java.io.InputStream

/**
 * Parses upsert instructions from a semicolon-delimited CSV input stream.
 *
 * The expected format follows the ImpEx convention: each header row starts with an operation
 * and table name, each data row starts with an empty field (the leading semicolon):
 *
 * ```
 * INSERT_UPDATE person ; name[unique=true] ; age
 * ; Ada ; 19
 * ; John ; 30
 * ```
 *
 * Macro definitions may appear anywhere before the header that references them:
 *
 * ```
 * $customer=customer_id(customer.email)
 *
 * INSERT_UPDATE order_item ; order_code[unique=true] ; $customer ; quantity
 * ; ORD-001 ; ada@example.com ; 3
 * ```
 *
 * @throws CsvParseException if the input is structurally malformed CSV
 */
class CsvInstructionParser : AbstractInstructionParser() {

    /**
     * Reads [input] line by line using a semicolon delimiter and returns the rows as a lazy sequence.
     *
     * Each line is parsed independently so that macro lines and data rows with differing field
     * counts do not trigger a field-count validation error. The underlying stream is kept open
     * for the lifetime of the returned sequence and closed once it is fully consumed.
     *
     * @param input the CSV input stream
     * @return a lazy sequence of token lists, one per line
     * @throws CsvParseException if a line contains malformed CSV (e.g. an unclosed quote)
     */
    override fun tokenize(input: InputStream): Sequence<List<String>> = sequence {
        val lineReader = csvReader { delimiter = ';' }
        input.bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null) {
                try {
                    yield(lineReader.readAll(line.byteInputStream()).firstOrNull() ?: emptyList())
                } catch (exception: MalformedCSVException) {
                    throw CsvParseException(exception)
                }
                line = reader.readLine()
            }
        }
    }
}
