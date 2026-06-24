package de.hauschild.upsert.parser

import de.hauschild.upsert.model.DataRow
import de.hauschild.upsert.model.Header
import de.hauschild.upsert.model.Instruction
import java.io.InputStream

/**
 * Skeleton implementation of [InstructionParser] that handles the full parsing orchestration.
 *
 * Subclasses provide only the format-specific part: splitting the input stream into token lists
 * via [tokenize]. The abstract class takes care of macro collection and resolution,
 * header parsing, data row assembly, and instruction streaming.
 *
 * Each token list represents one logical line from the source. The first token determines
 * the line type:
 * - starts with `$` — macro definition, forwarded to [MacroProcessor]
 * - empty — data row (the leading empty field mirrors the ImpEx leading semicolon convention)
 * - otherwise — header line, parsed by [HeaderParser]
 *
 * Blank and empty lines are skipped. Macro references are resolved in header tokens and
 * data values before further processing.
 */
abstract class AbstractInstructionParser : InstructionParser {

    /**
     * Parses the given [input] by delegating to [tokenize] and orchestrating the result.
     *
     * This method is final — format-specific behavior belongs in [tokenize] only.
     */
    final override fun parse(input: InputStream): Sequence<Instruction> = sequence {
        val macroProcessor = MacroProcessor()
        val headerParser = HeaderParser()
        var currentHeader: Header? = null
        val currentRows = mutableListOf<DataRow>()
        var lineNumber = 0

        tokenize(input).forEach { tokens ->
            lineNumber++
            if (tokens.isEmpty() || tokens.all { it.isBlank() }) { return@forEach }
            val firstToken = tokens.first().trim()
            when {
                firstToken.startsWith("$") -> macroProcessor.collect(firstToken)
                firstToken.isEmpty() -> currentHeader?.let {
                    val values = tokens.drop(1).map { token -> macroProcessor.resolve(token.trim()) }
                    currentRows.add(DataRow(lineNumber, values))
                }
                else -> {
                    currentHeader?.let {
                        yield(Instruction(it, currentRows.toList().asSequence()))
                        currentRows.clear()
                    }
                    currentHeader = headerParser.parse(tokens.map { macroProcessor.resolve(it) })
                }
            }
        }

        currentHeader?.let { yield(Instruction(it, currentRows.toList().asSequence())) }
    }

    /**
     * Splits the given [input] stream into a sequence of token lists.
     *
     * Each element represents one logical line from the source. The first token of each list
     * determines the line type (macro, header, or data row) as described in [AbstractInstructionParser].
     *
     * @param input the source stream to read from
     * @return a lazy sequence of token lists in source order
     */
    protected abstract fun tokenize(input: InputStream): Sequence<List<String>>
}
