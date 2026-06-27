package de.hauschild.upsert.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import de.hauschild.upsert.parser.csv.CsvInstructionParser
import de.hauschild.upsert.sql.ExecutionConfig
import de.hauschild.upsert.sql.SqlExecutor
import de.hauschild.upsert.sql.result.InstructionResult
import de.hauschild.upsert.sql.strategy.PostgresCopyStrategy
import de.hauschild.upsert.sql.strategy.PostgresOnConflictStrategy
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.postgresql.ds.PGSimpleDataSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap

fun main(args: Array<String>) = Upsert().main(args)

class Upsert : CliktCommand(
    name = "upsert",
    help = "Execute SQL upsert instructions from CSV files.",
) {
    init {
        subcommands(RunCommand())
    }

    override fun run() = Unit
}

class RunCommand : CliktCommand(
    name = "run",
    help = "Execute upsert instructions from CSV files against a database.",
) {

    private val url by option("--url", "-u", envvar = "UPSERT_URL", help = "JDBC URL").required()
    private val user by option("--user", envvar = "UPSERT_USER", help = "Database user").required()
    private val password by option(
        "--password",
        envvar = "UPSERT_PASSWORD",
        help = "Database password (prefer UPSERT_PASSWORD env variable over flag)",
    ).required()
    private val schema by option(
        "--schema",
        envvar = "UPSERT_SCHEMA",
        help = "Default schema for unqualified table names",
    )
    private val strategy by option("--strategy", help = "copy (default) or tolerant (row-level error tolerance)")
        .choice("copy", "tolerant")
        .default("copy")
    private val trace by option("--trace", help = "Print live performance observations to stdout").flag()
    private val files by argument("files", help = "CSV files to execute").multiple(required = true)

    override fun run() {
        val dataSource = PGSimpleDataSource().apply {
            setUrl(this@RunCommand.url)
            this.user = this@RunCommand.user
            this.password = this@RunCommand.password
        }
        val upsertStrategy = when (strategy) {
            "tolerant" -> PostgresOnConflictStrategy()
            else -> PostgresCopyStrategy()
        }
        val observationRegistry = if (trace) { buildTracingRegistry() } else { ObservationRegistry.NOOP }
        val executor = SqlExecutor(
            ExecutionConfig(
                dataSource = dataSource,
                defaultSchema = schema,
                strategy = upsertStrategy,
                observationRegistry = observationRegistry,
            ),
        )
        val parser = CsvInstructionParser()
        var failed = false

        for (path in files) {
            val file = File(path)
            if (!file.exists()) {
                System.err.println("error: file not found: $path")
                failed = true
                continue
            }
            val result = executor.execute(parser.parse(file.inputStream()))
            for (instruction in result.instructions) {
                printInstructionResult(file.name, instruction)
            }
            if (!result.successful) {
                failed = true
            }
        }

        if (failed) {
            throw ProgramResult(1)
        }
    }

    private fun printInstructionResult(filename: String, result: InstructionResult) {
        val prefix = "$filename  ${result.header.operation}  ${result.header.table}"
        val cause = result.cause
        if (cause != null) {
            System.err.println("$prefix  FAILED: ${cause.message}")
            return
        }
        println("$prefix  → ${formatCounts(result)}")
        for (skip in result.skippedRows) {
            System.err.println("  ! line ${skip.lineNumber}: ${skip.reason}")
        }
    }

    private fun formatCounts(result: InstructionResult): String {
        val parts = mutableListOf<String>()
        if (result.rowsInserted > 0) { parts.add("${result.rowsInserted} inserted") }
        if (result.rowsUpdated > 0) { parts.add("${result.rowsUpdated} updated") }
        if (result.rowsDeleted > 0) { parts.add("${result.rowsDeleted} deleted") }
        if (result.skippedRows.isNotEmpty()) { parts.add("${result.skippedRows.size} skipped") }
        if (parts.isEmpty()) { parts.add("0 affected") }
        return parts.joinToString(", ")
    }

    private fun buildTracingRegistry(): ObservationRegistry {
        val registry = ObservationRegistry.create()
        val startNanos = ConcurrentHashMap<Int, Long>()
        registry.observationConfig().observationHandler(
            object : ObservationHandler<Observation.Context> {
                override fun onStart(context: Observation.Context) {
                    startNanos[System.identityHashCode(context)] = System.nanoTime()
                    val tags = formatKeyValues(context)
                    println("  ↳ ${context.name}  $tags")
                }

                override fun onStop(context: Observation.Context) {
                    val start = startNanos.remove(System.identityHashCode(context)) ?: return
                    val ms = (System.nanoTime() - start) / 1_000_000
                    println("  ↳ ${context.name}  done (${ms}ms)")
                }

                override fun supportsContext(context: Observation.Context) = true
            },
        )
        return registry
    }

    private fun formatKeyValues(context: Observation.Context): String {
        val low = context.lowCardinalityKeyValues.map { "${it.key}=${it.value}" }
        val high = context.highCardinalityKeyValues.map { "${it.key}=${it.value}" }
        return (low + high).joinToString(", ")
    }
}
