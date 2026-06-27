package de.hauschild.upsert.sql

import de.hauschild.upsert.sql.strategy.PostgresCopyStrategy
import de.hauschild.upsert.sql.strategy.UpsertStrategy
import io.micrometer.observation.ObservationRegistry
import javax.sql.DataSource

/**
 * Configuration for a [SqlExecutor] run.
 *
 * ```kotlin
 * val config = ExecutionConfig(
 *     dataSource = myDataSource,
 *     defaultSchema = "public",
 *     strategy = PostgresOnConflictStrategy(),
 * )
 * ```
 *
 * @param dataSource the data source used to obtain JDBC connections
 * @param defaultSchema the schema applied to unqualified table names; `null` uses the
 *   connection's default schema
 * @param strategy the SQL execution strategy; defaults to [PostgresCopyStrategy]
 * @param observationRegistry Micrometer registry for live performance observations;
 *   defaults to [ObservationRegistry.NOOP] (zero overhead)
 */
data class ExecutionConfig(
    val dataSource: DataSource,
    val defaultSchema: String? = null,
    val strategy: UpsertStrategy = PostgresCopyStrategy(),
    val observationRegistry: ObservationRegistry = ObservationRegistry.NOOP,
)
