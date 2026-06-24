package de.hauschild.upsert.parser

/**
 * Collects macro definitions from raw input lines and resolves macro references in strings.
 *
 * Macros are defined as `$name=value` lines anywhere in the source file, conventionally
 * at the top. Once collected, macro references of the form `$name` are substituted by
 * their defined value wherever they appear in subsequent lines.
 *
 * ```
 * $customer=customer_id(customer.email)
 *
 * INSERT_UPDATE order_item ; order_code[unique=true] ; $customer ; quantity
 * ; ORD-001 ; ada@example.com ; 3
 * ```
 *
 * A [MacroProcessor] instance is stateful: macros accumulate across [collect] calls in the
 * order they are encountered. Each [InstructionParser] instance owns one [MacroProcessor].
 */
class MacroProcessor {

    internal val macros: MutableMap<String, String> = mutableMapOf()

    /**
     * Inspects the given [line] and stores it as a macro definition if it matches the
     * `$name=value` pattern. Non-macro lines are silently ignored.
     *
     * The `$` prefix and surrounding whitespace are stripped before storing.
     * The value is everything after the first `=`, trimmed.
     *
     * @param line a raw line from the source file
     */
    fun collect(line: String) {

        val trimmed = line.trim()
        if (!trimmed.startsWith("$")) return
        val equalsIndex = trimmed.indexOf('=')
        if (equalsIndex == -1) return
        val name = trimmed.substring(1, equalsIndex).trim()
        val value = trimmed.substring(equalsIndex + 1).trim()
        macros[name] = resolve(value)
    }

    /**
     * Replaces all macro references of the form `$name` in [input] with their collected values.
     *
     * Macros are substituted in descending order of name length to prevent shorter names from
     * being matched as prefixes of longer ones (e.g. `$cat` must not consume the start of `$catalog`).
     * Unknown references are left unchanged.
     *
     * @param input the string in which to substitute macro references
     * @return the input with all known macro references replaced
     */
    fun resolve(input: String): String {
        var result = input
        macros.entries
            .sortedByDescending { it.key.length }
            .forEach { (name, value) -> result = result.replace("\$$name", value) }
        return result
    }
}
