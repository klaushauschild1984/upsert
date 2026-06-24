package de.hauschild.upsert.model

/**
 * Describes a single column in a [Header].
 *
 * A column is either a plain [Field] that maps directly to a column in the target table,
 * or a [ForeignKey] that resolves its value by looking up a related table.
 *
 * Column syntax: `columnName[modifier=value]` or `tableName(attribute)[modifier=value]`
 */
sealed class Column {

    /**
     * The modifiers controlling import behavior for this column.
     */
    abstract val modifiers: List<Modifier>

    /**
     * A plain column that maps a raw cell value directly to a column in the target table.
     *
     * Examples:
     * - `age` — no modifiers
     * - `code[unique=true]` — unique key column
     * - `status[default=ACTIVE]` — column with default value
     * - `created[unique=true,dateformat=dd.MM.yyyy]` — composite key column with date parsing
     *
     * @property name the target column name
     * @property modifiers the list of modifiers controlling import behavior for this column
     */
    data class Field(
        val name: String,
        override val modifiers: List<Modifier> = emptyList(),
    ) : Column()

    /**
     * A column resolved by looking up a related table via one of its attributes.
     *
     * The [name] serves as both the column name in the target table and the name of the
     * table to query for the primary key. The [attribute] identifies which column in the
     * referenced table is matched against the raw cell value.
     *
     * Example — `customer(email)` with cell value `ada@example.com`:
     * ```
     * SELECT <pk> FROM customer WHERE email = 'ada@example.com'
     * ```
     * The returned primary key is written into the `customer` column of the target table.
     *
     * @property name the target column name and the referenced table name
     * @property attribute the column in the referenced table to match against the raw cell value
     * @property modifiers the list of modifiers controlling import behavior for this column
     */
    data class ForeignKey(
        val name: String,
        val attribute: String,
        override val modifiers: List<Modifier> = emptyList(),
    ) : Column()
}
