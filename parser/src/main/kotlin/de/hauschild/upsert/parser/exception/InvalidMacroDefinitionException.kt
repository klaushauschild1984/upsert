package de.hauschild.upsert.parser.exception

/**
 * Thrown when a macro definition line is syntactically invalid.
 *
 * @param name the invalid macro name (e.g. empty string for `$=value`)
 */
class InvalidMacroDefinitionException(name: String) :
    UpsertParseException("Invalid macro definition: name must not be empty, got '$name'")
