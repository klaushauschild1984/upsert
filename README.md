# upsert

A Kotlin library for describing SQL INSERT, UPDATE, UPSERT, and DELETE operations via CSV, Excel, or JSON files.

Inspired by [SAP Hybris ImpEx](https://help.sap.com/docs/SAP_COMMERCE) — the idea of Import/Export of data through a simple tabular syntax that non-technicians can write — brought to plain JDBC without any dependency on SAP Commerce or JPA.

This project is a reimplementation of [jpa-upsert](https://github.com/klaushauschild1984/jpa-upsert) in Kotlin.

## The problem

Loading or migrating data in a JPA or SQL project usually means writing raw SQL fixtures. Those bypass every constraint, validation, and ID-generation mechanism you have in place. You either duplicate that logic in your fixtures or ignore it.

## The solution

`upsert` lets you describe data operations in a tabular format — oriented closely on the original ImpEx syntax — and executes them via plain JDBC. Each instruction identifies the target table and operation in a header line; data rows follow below.

## Data format

An upsert file contains one or more instruction blocks. The header line names the table and its columns; each data row starts with an empty leading field (the ImpEx convention).

**JSON:**
```json
[
  {
    "header": "INSERT_UPDATE person ; name[unique=true] ; age ; gender[default=DIVERSE]",
    "data": [
      "; Ada  ; 19 ; FEMALE",
      "; John ; 38 ; MALE",
      "; Alan ; 26 ; "
    ]
  }
]
```

**CSV / Excel:** one block per sheet or section — header line followed by data lines, no extra metadata needed.

```
INSERT_UPDATE person ; name[unique=true] ; age ; gender[default=DIVERSE]
; Ada  ; 19 ; FEMALE
; John ; 38 ; MALE
; Alan ; 26 ;
```

Fields are separated by `;`. Leading and trailing whitespace is trimmed per cell.

## Operations

Specified as the first token of the header line.

| Operation       | Behaviour |
|-----------------|-----------|
| `INSERT`        | Inserts the row. Skips silently if a row matching all `[unique=true]` columns already exists. |
| `UPDATE`        | Finds the row by its `[unique=true]` columns and updates all other fields. Skips if not found. |
| `INSERT_UPDATE` | Inserts or updates — whichever applies. |
| `DELETE`        | Deletes the row identified by its `[unique=true]` columns. |

## Column modifiers

Modifiers follow the column name in square brackets: `column[modifier=value]`. Multiple modifiers are comma-separated: `column[unique=true,default=0]`.

| Modifier | Example | Effect |
|----------|---------|--------|
| `unique` | `code[unique=true]` | Marks the column as part of the natural key used to detect existing rows. Multiple `unique` columns form a composite key. |
| `default` | `status[default=ACTIVE]` | Uses the given value when the data cell is blank. |
| `dateformat` | `created[dateformat=dd.MM.yyyy]` | Date parsing pattern for this column. |
| `lang` | `name[lang=de]` | Targets the language-specific variant of a column (e.g. for i18n tables). |
| `translator` | `price[translator=EuroCentTranslator]` | Fully qualified class name of a custom `ValueTranslator` for non-trivial conversions. |

## Foreign key resolution

A column can resolve a foreign key by looking up a related row via one of its attributes rather than its primary key. The referenced attribute is given in parentheses after the column name:

```
INSERT_UPDATE order ; code[unique=true] ; customer(email)[unique=true]
; ORD-001 ; ada@example.com
```

Here `customer(email)` means: find the `customer` row whose `email` equals `ada@example.com` and write its primary key into the `order.customer` column.

Nested resolution with multiple attributes is supported:

```
; ORD-001 ; Staged:Default
```
```
catalogVersion(catalog(id),version)[unique=true]
```

## Macros

Reusable values can be defined with `$name=value` and referenced anywhere in the file as `$name`:

```
$catalog=Default
$version=Staged

INSERT_UPDATE product ; code[unique=true] ; catalogVersion(catalog(id),version)[unique=true]
; p001 ; $catalog:$version
```

## Comments

Lines starting with `#` are ignored.

```
# This block loads initial product data
INSERT product ; code[unique=true] ; name
; p001 ; Widget
```

## Supported input formats

| Format | Notes |
|--------|-------|
| CSV    | One instruction block per file, or multiple blocks separated by a blank line. |
| Excel (`.xlsx`) | One instruction block per sheet. Sheet name is ignored. |
| JSON   | Array of instruction objects with `header` and `data` fields. |

## Requirements

- JVM 21+
- A JDBC-compatible datasource
