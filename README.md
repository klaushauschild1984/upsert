# upsert

`upsert` is a Kotlin library for describing SQL `INSERT`, `UPDATE`, `INSERT_UPDATE`, and `DELETE` operations via CSV
files — executed via plain JDBC, no JPA, no Spring required.

Inspired by [SAP Hybris ImpEx](https://help.sap.com/docs/SAP_COMMERCE), which lets non-technicians express data import
and export in a simple tabular syntax.

## CI Status

|                                                                            Build                                                                             |                                   Last Commit                                    |                               Open Issues                                |                                   Repo Size                                    |                                  Coverage                                   |
|:------------------------------------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------:|:------------------------------------------------------------------------:|:------------------------------------------------------------------------------:|:---------------------------------------------------------------------------:|
| [![CI](https://github.com/klaushauschild1984/upsert/actions/workflows/ci.yml/badge.svg)](https://github.com/klaushauschild1984/upsert/actions/workflows/ci.yml) | ![Last Commit](https://img.shields.io/github/last-commit/klaushauschild1984/upsert) | ![Issues](https://img.shields.io/github/issues/klaushauschild1984/upsert) | ![Repo Size](https://img.shields.io/github/repo-size/klaushauschild1984/upsert) | [![Coverage](.github/badges/coverage.svg)](https://github.com/klaushauschild1984/upsert/actions/workflows/ci.yml) |

## Requirements

| Requirement | Version                                                                              |
|-------------|--------------------------------------------------------------------------------------|
| JDK         | ![JDK](https://img.shields.io/badge/JDK-21+-orange)                                  |
| Kotlin      | ![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple?logo=kotlin&logoColor=white) |
| Database    | ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-12+-blue?logo=postgresql)       |

## Getting Started

| JitPack                                                                                                         | License                                                                         |
|-----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| [![Release](https://jitpack.io/v/klaushauschild1984/upsert.svg)](https://jitpack.io/#klaushauschild1984/upsert) | [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) |

Add the JitPack repository and the `sql` module (execution engine) plus a parser module of your choice.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.klaushauschild1984.upsert:sql:x.y.z")
    implementation("com.github.klaushauschild1984.upsert:parser-csv:x.y.z")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.klaushauschild1984.upsert</groupId>
        <artifactId>sql</artifactId>
        <version>x.y.z</version>
    </dependency>
    <dependency>
        <groupId>com.github.klaushauschild1984.upsert</groupId>
        <artifactId>parser-csv</artifactId>
        <version>x.y.z</version>
    </dependency>
</dependencies>
```

## Input Format

An upsert file contains one or more instruction blocks. The header line names the operation, the target table, and its
columns. Each data row follows the ImpEx convention of a leading empty field separated by `;`.

```
INSERT_UPDATE person ; name[unique] ; age ; gender[default=DIVERSE]
; Ada  ; 19 ; FEMALE
; John ; 38 ; MALE
; Alan ; 26 ;
```

Fields are separated by `;`. Leading and trailing whitespace is trimmed per cell. Lines starting with `#` are ignored.

### Operations

| Operation       | Behaviour                                                                          |
|-----------------|------------------------------------------------------------------------------------|
| `INSERT`        | Inserts the row. Skips if a row with matching `[unique]` columns exists.      |
| `UPDATE`        | Updates the row identified by `[unique]` columns. Skips if not found.         |
| `INSERT_UPDATE` | Inserts or updates — whichever applies.                                            |
| `DELETE`        | Deletes the row identified by `[unique]` columns.                             |

### Column Modifiers

Modifiers appear in square brackets after the column name: `column[modifier=value]`. Multiple modifiers are
comma-separated.

| Modifier  | Example                    | Effect                                                              |
|-----------|----------------------------|---------------------------------------------------------------------|
| `unique`  | `code[unique]`        | Part of the natural key used to detect existing rows.               |
| `default` | `status[default=ACTIVE]`   | Fallback value when the data cell is blank.                         |

### Foreign Key Resolution

A column can resolve a foreign key by looking up a related row via a natural attribute rather than its primary key:

```
INSERT_UPDATE order ; code[unique] ; customer(email)
; ORD-001 ; ada@example.com
```

Here `customer(email)` finds the `customer` row whose `email` equals `ada@example.com` and writes its primary key into
the `order.customer` column.

### Macros

Reusable values can be defined once and referenced anywhere in the file:

```
$catalog=Default

INSERT_UPDATE product ; code[unique] ; catalog(id)
; p001 ; $catalog
```

## Usage

```kotlin
val dataSource: DataSource = /* your JDBC DataSource */

val executor = SqlExecutor(
    ExecutionConfig(
        dataSource = dataSource,
        defaultSchema = "public",
    ),
)

val parser = CsvInstructionParser()
val result = executor.execute(parser.parse(File("data.csv").inputStream()))

println("successful: ${result.successful}")
```

## Execution Strategies

Two strategies are available via `ExecutionConfig.strategy`:

| Strategy                   | How it works                                           | Error tolerance        |
|----------------------------|--------------------------------------------------------|------------------------|
| `PostgresCopyStrategy`     | PostgreSQL `COPY` into a temp table, then bulk merge   | Instruction-level only |
| `PostgresOnConflictStrategy` | `INSERT … ON CONFLICT` row by row                    | Per-row                |

`PostgresCopyStrategy` is the default and the fastest option. Use `PostgresOnConflictStrategy` when per-row error
tolerance is required — individual row failures are recorded as skipped rows while the rest of the instruction
continues.

## Observability

`upsert` integrates with [Micrometer Observation API](https://micrometer.io/docs/observation). Pass a configured
`ObservationRegistry` to `ExecutionConfig` to receive timing data for the four built-in observation points:

| Observation name    | What it covers                                   |
|---------------------|--------------------------------------------------|
| `upsert.instruction` | Full instruction execution (FK resolution + SQL) |
| `upsert.fk.prime`   | Batch pre-loading of foreign key caches          |
| `upsert.copy`       | PostgreSQL COPY phase (bulk data transfer)       |
| `upsert.merge`      | SQL merge phase (INSERT/UPDATE/DELETE)           |

The default `ObservationRegistry.NOOP` has zero overhead.

## CLI

A ready-to-run fat JAR is attached to each [GitHub release](https://github.com/klaushauschild1984/upsert/releases):

```
java -jar upsert-cli-x.y.z.jar run \
  --url jdbc:postgresql://localhost:5432/mydb \
  --user myuser \
  --password secret \
  data.csv
```

Credentials can also be supplied via environment variables `UPSERT_URL`, `UPSERT_USER`, and `UPSERT_PASSWORD`.

| Option        | Env variable       | Description                                      |
|---------------|--------------------|--------------------------------------------------|
| `--url`       | `UPSERT_URL`       | JDBC URL                                         |
| `--user`      | `UPSERT_USER`      | Database user                                    |
| `--password`  | `UPSERT_PASSWORD`  | Database password                                |
| `--schema`    | `UPSERT_SCHEMA`    | Default schema for unqualified table names       |
| `--strategy`  | —                  | `copy` (default) or `tolerant`                   |
| `--trace`     | —                  | Print live Micrometer observations to stdout     |

## Contributing

Contributions are welcome. If you face a bug or see the need for an enhancement, feel free to open an issue. For
changes, please open an issue first to discuss what you would like to change.
