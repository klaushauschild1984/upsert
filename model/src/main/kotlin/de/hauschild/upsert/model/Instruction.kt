package de.hauschild.upsert.model

/**
 * A single import block parsed from a source file, combining a [Header] with its data rows.
 *
 * An instruction is the primary unit of work passed from the parser layer to the SQL execution layer.
 * The [rows] sequence is lazy and should be consumed only once, enabling streaming processing
 * of arbitrarily large files without loading all rows into memory.
 *
 * Source representation:
 * ```
 * INSERT_UPDATE person ; name[unique=true] ; age ; gender[default=DIVERSE]
 * ; Ada  ; 19 ; FEMALE
 * ; John ; 38 ; MALE
 * ```
 *
 * In tests or simple scenarios, any [List] of [DataRow]s can be wrapped with [Iterable.asSequence].
 *
 * @property header the parsed header describing the operation, table, and column layout
 * @property rows the data rows to process, consumed lazily to support large files
 */
class Instruction(
    val header: Header,
    val rows: Sequence<DataRow>,
)
