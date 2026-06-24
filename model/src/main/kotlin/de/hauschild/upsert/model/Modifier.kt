package de.hauschild.upsert.model

/**
 * A column modifier that controls how a [Header] is interpreted during import.
 *
 * Modifiers are declared in square brackets after the column name in the header line,
 * e.g. `name[unique=true]` or `created[dateformat=dd.MM.yyyy]`.
 * Multiple modifiers are comma-separated: `code[unique=true,default=N/A]`.
 */
sealed class Modifier {

    /**
     * Marks the column as part of the natural key used to detect existing rows.
     *
     * Multiple unique columns form a composite key. At least one unique column is required
     * for [Operation.UPDATE], [Operation.INSERT_UPDATE], and [Operation.DELETE].
     */
    data object Unique : Modifier()

    /**
     * Supplies a fallback value when the corresponding data cell is blank.
     *
     * @property value the value to use when the cell is empty after trimming
     */
    data class Default(val value: String) : Modifier()

    /**
     * Specifies the date parsing pattern for this column.
     *
     * The pattern follows [java.time.format.DateTimeFormatter] conventions,
     * e.g. `dd.MM.yyyy` or `yyyy-MM-dd'T'HH:mm:ss`.
     *
     * @property pattern the date/time format pattern
     */
    data class DateFormat(val pattern: String) : Modifier()

    /**
     * Designates a custom translator responsible for converting the raw string value
     * into the target column type.
     *
     * The class name is resolved and instantiated at import time.
     * The translator interface will be defined in the [de.hauschild.upsert.parser] module.
     *
     * @property className fully qualified class name of the translator implementation
     */
    data class Translator(val className: String) : Modifier()
}
