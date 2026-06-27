package de.hauschild.upsert.sql.resolver

import de.hauschild.upsert.model.Column
import de.hauschild.upsert.sql.QualifiedTable
import de.hauschild.upsert.sql.exception.ForeignKeyResolutionException
import de.hauschild.upsert.sql.timed
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import java.sql.Connection

/**
 * Resolves foreign key natural key values to primary key values via JDBC SELECT queries.
 *
 * Call [prime] once per FK column before processing rows to batch-load all required
 * values in a single `SELECT … WHERE naturalKey IN (…)` query. Subsequent [resolve]
 * calls are served from the in-memory cache without further round-trips.
 *
 * Values not found during priming are not an error — [resolve] falls back to an
 * individual lookup for those and throws [ForeignKeyResolutionException] if still absent.
 *
 * The referenced table and natural key column are derived from [Column.ForeignKey]:
 * - If [Column.ForeignKey.attribute] contains a dot (e.g. `customer.email`), the part
 *   before the dot is the referenced table name and the part after is the natural key column.
 * - Otherwise [Column.ForeignKey.name] doubles as the referenced table name.
 *
 * @param connection the JDBC connection used for lookup queries
 * @param defaultSchema the schema applied to unqualified table references
 * @param observationRegistry registry for `upsert.fk.prime` observations
 */
class ForeignKeyResolver(
    private val connection: Connection,
    private val defaultSchema: String?,
    private val observationRegistry: ObservationRegistry = ObservationRegistry.NOOP,
) {

    private val valueCache: MutableMap<Triple<String, String, String>, Any> = mutableMapOf()
    private val pkCache: MutableMap<String, String?> = mutableMapOf()

    /**
     * Batch-loads primary key values for all [values] of [column] in a single query.
     *
     * Already-cached values are skipped. Values absent from the database are silently
     * ignored here; [resolve] will throw [ForeignKeyResolutionException] for them.
     *
     * @param column the foreign key column descriptor
     * @param values the set of natural key values to pre-load
     */
    fun prime(column: Column.ForeignKey, values: Set<String>) {
        if (values.isEmpty()) {
            return
        }
        val (table, naturalKeyColumn) = parseReference(column)
        val uncached = values.filter { Triple(table.toString(), naturalKeyColumn, it) !in valueCache }
        if (uncached.isEmpty()) {
            return
        }
        val pkColumn = primaryKeyColumn(table) ?: naturalKeyColumn
        val placeholders = uncached.joinToString { "?" }
        val sql = "SELECT $pkColumn, $naturalKeyColumn FROM $table WHERE $naturalKeyColumn IN ($placeholders)"
        Observation.createNotStarted("upsert.fk.prime", observationRegistry)
            .lowCardinalityKeyValue("table", table.toString())
            .lowCardinalityKeyValue("column", naturalKeyColumn)
            .highCardinalityKeyValue("values", uncached.size.toString())
            .timed {
                connection.prepareStatement(sql).use { stmt ->
                    uncached.forEachIndexed { index, value -> stmt.setString(index + 1, value) }
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val pk = requireNotNull(rs.getObject(1)) {
                                "Primary key is null in '$table' for $naturalKeyColumn = '${rs.getString(2)}'"
                            }
                            val naturalKey = rs.getString(2)
                            valueCache[Triple(table.toString(), naturalKeyColumn, naturalKey)] = pk
                        }
                    }
                }
            }
    }

    /**
     * Resolves [naturalKeyValue] for [column] to its primary key value.
     *
     * Returns the cached value if available (after a prior [prime] call), otherwise
     * performs an individual lookup.
     *
     * @param column the foreign key column descriptor
     * @param naturalKeyValue the raw string value from the data row
     * @return the primary key value of the referenced row
     * @throws ForeignKeyResolutionException if no matching row is found
     */
    fun resolve(column: Column.ForeignKey, naturalKeyValue: String): Any {
        val (table, naturalKeyColumn) = parseReference(column)
        val cacheKey = Triple(table.toString(), naturalKeyColumn, naturalKeyValue)
        return valueCache.getOrPut(cacheKey) { lookup(table, naturalKeyColumn, naturalKeyValue) }
    }

    private fun parseReference(column: Column.ForeignKey): Pair<QualifiedTable, String> {
        val dotIndex = column.attribute.indexOf('.')
        return if (dotIndex != -1) {
            val tableName = column.attribute.substring(0, dotIndex).trim()
            val naturalKeyColumn = column.attribute.substring(dotIndex + 1).trim()
            QualifiedTable.of(tableName, defaultSchema) to naturalKeyColumn
        } else {
            QualifiedTable.of(column.name, defaultSchema) to column.attribute
        }
    }

    private fun primaryKeyColumn(table: QualifiedTable): String? {
        return pkCache.getOrPut(table.toString()) {
            connection.metaData.getPrimaryKeys(null, table.schema, table.name).use { rs ->
                if (rs.next()) { rs.getString("COLUMN_NAME").lowercase() } else { null }
            }
        }
    }

    private fun lookup(table: QualifiedTable, naturalKeyColumn: String, value: String): Any {
        val pkColumn = primaryKeyColumn(table) ?: naturalKeyColumn
        val sql = "SELECT $pkColumn FROM $table WHERE $naturalKeyColumn = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, value)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) { throw ForeignKeyResolutionException(table.toString(), naturalKeyColumn, value) }
                return requireNotNull(rs.getObject(1)) {
                    "Primary key is null in '$table' for $naturalKeyColumn = '$value'"
                }
            }
        }
    }
}
