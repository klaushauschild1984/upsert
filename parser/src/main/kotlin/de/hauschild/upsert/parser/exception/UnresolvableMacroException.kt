package de.hauschild.upsert.parser.exception

/**
 * Thrown when a macro reference cannot be resolved because no matching definition was collected.
 *
 * Macro definitions must appear before their first use in the source file.
 *
 * @param name the macro name that could not be resolved
 */
class UnresolvableMacroException(name: String) :
    UpsertParseException("Unresolvable macro: '\$$name'")
