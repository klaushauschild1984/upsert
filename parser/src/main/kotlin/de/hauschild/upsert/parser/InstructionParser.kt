package de.hauschild.upsert.parser

import de.hauschild.upsert.model.Instruction
import java.io.InputStream

/**
 * Reads a source stream and produces a sequence of [Instruction]s.
 *
 * Implementations are format-specific (CSV, Excel, JSON, etc.) but share the same contract:
 * the returned sequence is lazy and should be consumed only once. The caller is responsible
 * for closing the [InputStream] after the sequence has been fully consumed.
 *
 * Using [InputStream] as the source type keeps the interface format- and resource-agnostic.
 * Any resource that can provide a stream is a valid source:
 * ```kotlin
 * // inline test data
 * parser.parse("INSERT person ; name\n; Ada".byteInputStream())
 *
 * // file
 * parser.parse(File("persons.csv").inputStream())
 *
 * // classpath resource
 * parser.parse(checkNotNull(javaClass.getResourceAsStream("/persons.csv")))
 * ```
 */
fun interface InstructionParser {

    /**
     * Parses the given [input] stream and returns a lazy sequence of [Instruction]s.
     *
     * @param input the source stream to read from
     * @return a lazy sequence of instructions in the order they appear in the source
     */
    fun parse(input: InputStream): Sequence<Instruction>
}
