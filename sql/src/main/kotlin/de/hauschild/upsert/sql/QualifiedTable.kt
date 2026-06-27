package de.hauschild.upsert.sql

/**
 * A database table name optionally qualified with a schema.
 *
 * Constructed from the raw table string in the instruction header. A dot in the name
 * is interpreted as a schema separator: `public.person` yields schema `public` and
 * table `person`. Without a dot the [defaultSchema] is used if provided.
 *
 * ```kotlin
 * QualifiedTable.of("public.person")          // schema=public, table=person
 * QualifiedTable.of("person", "public")       // schema=public, table=person
 * QualifiedTable.of("person")                 // schema=null,   table=person
 * ```
 *
 * @param schema the schema name, or `null` if unqualified
 * @param name the bare table name
 */
data class QualifiedTable(val schema: String?, val name: String) {

    /**
     * Returns the fully qualified name used in SQL statements.
     */
    override fun toString(): String {
        return if (schema != null) { "$schema.$name" } else { name }
    }

    companion object {

        /**
         * Parses [rawName] into a [QualifiedTable], falling back to [defaultSchema] when
         * no dot is present in [rawName].
         *
         * @param rawName the table name as it appears in the instruction header
         * @param defaultSchema the schema to use when [rawName] is not dot-qualified
         */
        fun of(rawName: String, defaultSchema: String? = null): QualifiedTable {
            val dotIndex = rawName.indexOf('.')
            return if (dotIndex != -1) {
                QualifiedTable(
                    schema = rawName.substring(0, dotIndex).trim(),
                    name = rawName.substring(dotIndex + 1).trim(),
                )
            } else {
                QualifiedTable(schema = defaultSchema, name = rawName.trim())
            }
        }
    }
}
