package de.hauschild.upsert.parser.exception

/**
 * Thrown when a column modifier key is not recognized.
 *
 * @param modifier the unrecognized modifier key
 */
class UnknownModifierException(modifier: String) :
    UpsertParseException("Unknown modifier: '$modifier'")
