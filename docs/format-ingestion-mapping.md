# Format Ingestion Mapping (F-items)

How foreign config formats map onto the HOCON value model when ingested at runtime, agreed across the four implementations:

- [o3co/ts.hocon](https://github.com/o3co/ts.hocon)
- [o3co/rs.hocon](https://github.com/o3co/rs.hocon)
- [o3co/go.hocon](https://github.com/o3co/go.hocon)
- [o3co/py.hocon](https://github.com/o3co/py.hocon)

Formats: env (bulk mount + `.env` files) / JSON (conformance only) / Properties / JSONC・JSON5 / TOML / YAML.

## Role of this file

Ingestion happens **at the value level** — a foreign document is decoded by its own library into a tree, and that tree is handed to a `FromMap`-style constructor. It is never rendered to HOCON text and re-parsed, so nothing in a foreign document can become a substitution, a concatenation, a `+=` or an include (F0.1, F0.2).

Items here use an **F-prefix** namespace, distinct from the canonical spec items (`S1.1`, `S6.5`, …) in [`spec-checklist.md`](spec-checklist.md) and the extra-spec conventions (`E1`, `E2`, …) in [`extra-spec-conventions.md`](extra-spec-conventions.md). F-items do **not** count toward the compliance-matrix denominator, for the same reason E-items do not: the [Lightbend HOCON spec](https://github.com/lightbend/config/blob/main/HOCON.md) does not define these adapters. Only `.properties` has a reference implementation to be pinned against (Lightbend's `parseProperties` / `PropertiesParser`, cited by F2.5); for env, JSONC, TOML and YAML **there is no oracle at all**, so an F-item is a decision this project makes and then holds the four implementations to.

That is why the fixtures for these items live in [`testdata/format-ingestion/`](../testdata/format-ingestion/) rather than under `expected/`: they are not generated from Lightbend, and their whole value is differential. See [fixture-conventions.md](fixture-conventions.md#format-ingestion-fixtures-f-items--the-format-adapters).

**Errors raised by the adapters cite these item numbers** (`(spec F0.5)`, `(spec F1.6)`, …). If you arrived here from one, the item is in the table below.

Implementations: `go.hocon/adapters` (a nested Go module), `ts.hocon/src/adapters`, `py.hocon/src/hocon/adapters`, `rs.hocon/src/adapters`. All four have shipped adapter sets since v1.10.0.

## Status legend

The glyph records **how the item is verified**, not how settled the decision is — every item without a `❓` is decided and implemented.

| Glyph | Meaning |
| --- | --- |
| ✅ | Pinned by a differential fixture in `testdata/format-ingestion/` — the four implementations are mechanically held to the same answer |
| 🤷 | Decided and implemented, but with per-implementation tests only. Cross-impl agreement rests on review, so a divergence here would not be caught by the harness |
| ❓ | Open — needs a decision before the affected format can claim parity |

The `✅` set is derived from the `note` fields of [`testdata/format-ingestion/manifest.json`](../testdata/format-ingestion/manifest.json); the mapping is listed under [Fixture coverage](#fixture-coverage) below.

## F0 — Common principles (all formats)

| Item | Status | Rule |
| --- | --- | --- |
| F0.1 | 🤷 | Ingestion produces the **data subset** only: object / array / string / number / boolean / null. Never substitutions, concatenations, `+=`, or includes. Ingested trees are always fully resolved; they can be substitution *targets* but never contain unresolved nodes. |
| F0.2 | ✅ | `${...}` occurring in foreign string values stays **literal**. No substitution parsing of foreign data (foreign files are owned by other programs). |
| F0.3 | ✅ | The document root must map to an **object**. Non-object roots (scalar or array) are errors. (Matches Lightbend: `parseString("[1]")` rejects non-object root.) |
| F0.4 | 🤷 | Origin is file-level (`FromMap` originDescription granularity). Per-node line origins are deferred until core exposes a richer construction API. |
| F0.5 | 🤷 | Numbers preserve the source's integer/float distinction: integers → int64 (overflow = error), floats → float64. No silent precision loss via float-only decoding (JSON family must decode via `json.Number` or equivalent; in JS, `Number`-only decoding is exactly the forbidden case — integer literals must reach the value model via source text / BigInt so anything above 2^53 stays lossless or errors at the int64 bound).<br><br>**This governs ingestion only, and deliberately differs from HOCON's own number lexing.** A foreign format declares its integer type, so a value that will not survive as an int64 is a defect in the mapping and an error is the honest answer. HOCON source text has no such declaration: per the S-items it follows Lightbend, trying `Long` and then falling back to `Double`, so `a = 9223372036854775808` in a `.conf` file is a double, not an error. An implementation is therefore expected to bound the adapter paths while leaving the core parser alone; the two answers are not an inconsistency to fix. |
| F0.6 | ✅ | Values unrepresentable in the HOCON/JSON number model (`NaN`, `±Infinity` where a source allows them) are **errors** by default. No silent stringification. |
| F0.7 | 🤷 | Duplicate keys within one foreign document follow HOCON duplicate-key semantics: objects merge, otherwise last-wins. |
| F0.8 | 🤷 | The host HOCON document must be parsed with **resolution deferred**, then merged, then resolved. Eager-resolving parse entry points fail on `${...}` pointing into the foreign layer before the fallback is attached. Every implementation therefore needs a deferred-resolution parse option for adapters to be usable (Go: `ParseFileWithOptions(..., DefaultParseOptions().WithResolveSubstitutions(false))`). |
| F0.9 | ✅ | A leading **UTF-8 BOM** (U+FEFF) is stripped before parsing, in every format and at every file-reading entry point. Windows editors emit one, and a BOM left in place becomes part of the first key — `a: 1` yields the key `"﻿a"`, so a lookup of `a` misses and the value is silently unreachable. That is plausible-but-wrong output, the failure mode this spec most wants to avoid. Erroring instead is acceptable but weaker; what is not acceptable is admitting the BOM into a key. (The core HOCON parser already ignores U+FEFF; adapters must match.) |
| F0.10 | 🤷 | **A path rendered in an error message is a HOCON path expression**: segments joined with `.`, each written bare only if it matches `[A-Za-z0-9_-]+`, otherwise as a **JSON string literal**. JSON syntax because it *is* HOCON's quoted-string syntax, and because the implementations that render paths must produce the same text for the cross-language fixtures to compare it. Two departures from a language's own quoting, both deliberate: **NUL is `\u0000`**, never Go's `\x00` or Rust's `\u{0}`; and **U+2028 / U+2029 are escaped** although JSON permits them raw, because they are line separators to enough log viewers that a key could otherwise break the message reporting it. Hex escapes are **lowercase**. Printable non-ASCII stays itself (`é`, `İ`) rather than being escaped — F1.3 leaves such segments unfolded, so they occur in normal use. A byte that is not valid UTF-8 renders as `\xNN` — the rendering's **one departure from JSON**, because JSON cannot express such a byte at all. It must not render as `\ufffd`, which is what decoding it yields and which a key really holding U+FFFD would also produce; U+FFFD itself is ordinary printable non-ASCII and prints as itself. The case is **Go-only**: a Python `str` and a Rust `&str` cannot hold such a byte, so no sibling has it to diverge on. **The rendered path is not guaranteed to paste into a getter** — measured: no implementation's path parser decodes escapes inside a quoted segment, so `"a\nb"` does not address the key `a<LF>b`. The property this item does guarantee, and the one an error message needs, is that two different paths never render alike. |

## F1 — env (bulk mount + .env files)

Per-key access (`${?VAR}`) is an existing spec feature and is **out of scope** here.

| Item | Status | Rule |
| --- | --- | --- |
| F1.1 | ✅ | Bulk mount requires an explicit prefix filter (e.g. `APP_`); the prefix is stripped from resulting paths. No prefixless "mount everything" mode. |
| F1.2 | ✅ | `__` (double underscore) maps to the path separator `.`; single `_` stays literal in the key. (`APP_DB__MAX_CONN` → `db.max_conn`) A literal `.` in the variable name is key text, not a boundary — only `__` creates hierarchy. Adapters must carry the mapped path as a **segment list** end-to-end (no join-on-`.` / re-split, which manufactures boundaries): `APP_FOO.BAR` yields the single top-level key `"foo.bar"` (quoted-path addressable) and does **not** collide with `APP_FOO__BAR` under F1.6. |
| F1.3 | ✅ | Keys are lowercased after mapping, using **ASCII-only** case folding (`A`–`Z` → `a`–`z`; every other codepoint is left alone). Full Unicode lowercasing is not portable: Go's simple case mapping turns `İ` (U+0130) into `i`, while Python, JS and Rust apply the full mapping and produce `i` + U+0307. That difference decides whether `APP_İ` collides with `APP_I` under F1.6, so the case mapping must be pinned rather than inherited from each stdlib. Environment variable names are ASCII in every practical setting, so this costs nothing real. |
| F1.4 | 🤷 | All values are strings. Type coercion happens at HOCON getter level, as with `.properties` in Lightbend. |
| F1.5 | 🤷 | Empty value → empty string, never null. |
| F1.6 | ✅ | Post-mapping key collisions (e.g. `APP_A__B` vs `APP_a__b` after lowercasing) are **errors** — environment iteration order is not deterministic, so last-wins would be nondeterministic. Applies to the **process environment only**; a `.env` file has a definite line order and follows F0.7 last-wins. |
| F1.7 | ✅ | `.env` dialect, pinned minimal: `NAME=value`, optional `export` prefix, whole-line `#` comments, single quotes literal, double quotes with `\n \r \t \\ \"` (unknown escapes are errors). **Multi-line values and trailing comments are unsupported**; an unquoted value containing ` #` is an **error** naming the fix (quote it) rather than a guess about the author's intent. No `${...}` expansion (F0.2).<br><br>**The prefix filter runs first, and only what survives it is validated.** A `.env` shared with tools that support trailing comments must stay loadable when the caller mounts only their own slice of it — and `load`'s own contract already says so ("entries outside the prefix are never inspected", the F1.1 principle), so `parse_dotenv` diverging from its sibling function in the same module was the actual inconsistency. Before this was pinned, all four implementations parsed the value *before* filtering and mapped the path *after*, so one check landed on each side of the filter by accident.<br><br>**`export` may be followed by a run of spaces and/or tabs**, not a single space: matching `"export "` alone turned `export\tFOO=bar` into the key `export\tfoo`, silently. Space and tab specifically, because that is what this dialect already trims on the value side — widening it to every Unicode space would make `export\fFOO=bar` a keyword line, where leaving it out makes `export\fFOO` a *name*, which the rule below then refuses. An error beats a silently odd key.<br><br>**A name containing whitespace or `#` is an error.** F1.7's stated principle for values — an error naming the fix rather than a guess about the author's intent — applies to names too, and both characters indicate a mis-parse rather than an intended key. The rule is deliberately narrower than a POSIX name grammar (`[A-Za-z_][A-Za-z0-9_]*`), which would reject `APP_FOO.BAR` — a name F1.2 documents as valid and which the fixtures exercise. |
| F1.8 | 🤷 | Scalar-vs-scope conflict (`APP_DB` + `APP_DB__HOST`) follows F2.5: objects win. Order-independent, so it stays deterministic under nondeterministic environment iteration. |
| F1.9 | 🤷 | **A process-environment entry that is not valid UTF-8 must not abort the program, and must not silently disappear from a namespace the caller asked for.** Reading the environment is unavoidable on ordinary parse paths, so an entry the config never mentions must never be fatal — implementations iterate with an OS-string API (`vars_os`, `environ` bytes) rather than one that fails on the whole iteration. What happens to an undecodable entry then depends on whether the caller asked for it: (a) **substitution lookup** (`${VAR}` / `${?VAR}`) treats it as absent — `${?VAR}` falls through to the default and `${VAR}` raises the ordinary unresolved error; never lossy or replacement-character text. (b) **Bulk mount** (F1.1) is an explicit request for a whole namespace, so an entry matching the mount prefix whose name or value is undecodable is an **error** — silently omitting it yields a subtree that looks complete while the operator's setting is missing, and a stale config default then wins invisibly. The prefix filter bounds this to variables the caller named, so it cannot resurrect the abort-on-unrelated-entry bug. (c) **F1.6 collision detection still sees skipped entries**: a name that decodes but whose value does not still occupies its mapped path, so two names mapping to one path remain an error. Otherwise which value survives would depend on an encoding property of the value — exactly the nondeterminism F1.6 exists to forbid. |

## F2 — Properties (java.util.Properties)

| Item | Status | Rule |
| --- | --- | --- |
| F2.1 | ✅ | Dotted keys map to paths (`a.b.c` → nested objects), as in Lightbend `parseProperties`. |
| F2.2 | ✅ | All values are strings. |
| F2.3 | 🤷 | Encoding is UTF-8; `\uXXXX` escapes honored. (Java ≥9 load semantics, not ISO-8859-1.) |
| F2.4 | ✅ | Syntax per `java.util.Properties`: `=` / `:` / whitespace separators, `\` line continuation, `#` / `!` comments, key escapes (`\:` `\=` `\ `). |
| F2.5 | 🤷 | Scalar-vs-object path conflict (`a=1` + `a.b=2` in one file): the scalar is dropped — **objects win**. Pinned to Lightbend `PropertiesParser.fromPathMap`: `valuePaths.removeAll(scopePaths)` under `convertedFromProperties`; deterministic, order-independent. |
| F2.6 | 🤷 | The adapter emits objects only — no array production. Numerically-indexed keys stay object keys; downstream numeric-key→array conversion (S15, [E2](extra-spec-conventions.md#e2--numeric-key-array-conversion-leading-zero-key-forms-rejected-00--0)) applies at getter level as usual. |
| F2.7 | ❓ | Quoted path segments (`foo."bar.baz"=1`) are **rejected with an error** for now. Lightbend routes the key through its full path-expression parser and accepts them; erroring is a deliberate documented gap rather than a silent mis-split. Needs a fixture-backed decision before any implementation claims parity. |
| F2.8 | 🤷 | An unpaired `\uXXXX` surrogate is an **error**. Java strings are UTF-16 and can hold one; Go/Rust/Python strings cannot, and writing one silently yields U+FFFD. Valid surrogate **pairs** are combined into the astral codepoint. |
| F2.9 | ✅ | No key denylist: `__proto__`, `constructor`, `prototype` are ordinary keys and must be preserved — silently dropping them is data loss. JS implementations achieve prototype-pollution safety by construction (null-prototype carriers / own-property definition), never by dropping keys. Applies to every consumer of the shared key-nesting path (properties **and** env). |

## F3 — JSON / JSONC / JSON5

| Item | Status | Rule |
| --- | --- | --- |
| F3.1 | 🤷 | Plain JSON needs **no adapter**: HOCON is a JSON superset and the parser accepts all JSON (covered by existing S-items). Verified by a conformance test rather than merely asserted (go.hocon: `adapters/json_conformance_test.go`). |
| F3.2 | ✅ | JSONC = JSON + `//` + `/* */` comments + trailing commas. Implementation: string-aware comment/trailing-comma strip → stdlib JSON decode. Zero dependencies. (Note: HOCON already accepts `//` and trailing commas; `/* */` is the part HOCON lacks.) Comment stripping must be **token-separating**: a comment is replaced by whitespace (at least one space; newlines inside it are preserved for line origins), never the empty string — `1/*x*/2` must remain two tokens and fail the JSON decode. Trailing content after the top-level value is an error via a strict EOF check; a one-token peek (`Decoder.More()`-style) wrongly accepts stray closers. Reject it **without materializing** the trailing content — decoding it into a discarded value amplifies untrusted input roughly tenfold (a 90 MB tail measured at ~600 MB of live heap), and a single token peek gives an identical verdict.<br><br>A `//` comment ends at **LF or CR**, not LF alone. Scanning only for `\n` makes a CR-terminated comment swallow the following line, and because the trailing-comma pass then tidies up the dangling comma the document still parses — silently losing a key rather than erroring. U+2028/U+2029 deliberately do **not** terminate a comment: `node-jsonc-parser`, which defines the dialect this item tracks, treats only LF and CR as line breaks, and ending a comment early here would make a document mean one thing in the editor that owns the format and another in these libraries. |
| F3.3 | ❓ | JSON5 feature mapping: unquoted identifier keys ok, single-quoted strings ok, hex ints → int64, leading/trailing-dot floats → float64, explicit `+` ok, string line continuations ok, `Infinity`/`NaN` → error (F0.6). Still open, and JSON5 did indeed fall behind JSONC: the Go JSON5 libraries are unmaintained, so this needs a hand-rolled tokenizer. Not started. |
| F3.4 | 🤷 | Duplicate keys: F0.7 applies. |
| F3.5 | 🤷 | **An unpaired `\uXXXX` surrogate in a JSON/JSONC document is an error**, mirroring F2.8 for `.properties` — the reasoning transfers verbatim, and the stdlib decoders do not do this for us. Measured before pinning: go.hocon **silently substituted U+FFFD**, which is the plausible-but-wrong output this spec ranks as the worst failure mode; py.hocon kept the lone surrogate, deferring the failure to encode time, far from the parse that admitted it; rs.hocon already refused (serde_json does). Valid surrogate **pairs** combine into the astral codepoint, unchanged. Applies to keys as well as values. **ts.hocon accepts a lone surrogate**, the same S1.2.6-class divergence F2.8 already records: JavaScript strings are UTF-16 like Java's and can hold one, so refusing would be this spec overriding the host language rather than protecting the user. Whether the **core parser** follows is deliberately out of scope here — that is HOCON source text, so an S-item, and the four currently disagree there too. |

## F4 — TOML

| Item | Status | Rule |
| --- | --- | --- |
| F4.1 | 🤷 | Library selection (Go): **pelletier/go-toml/v2 v2.4.3**, confirmed 2026-07-24. Other languages pick their own; only the mapping below is normative. |
| F4.2 | ✅ | All four date-time types (offset date-time, local date-time, local date, local time) map to their RFC 3339 / ISO 8601 **string** forms. HOCON has no datetime type; strings are the honest representation. Two spellings had to be pinned after the fixtures caught implementations disagreeing while all being valid RFC 3339: **UTC is written `Z`**, not `+00:00` (Python's `isoformat` produces the latter), and **a fractional part that is all zeros is omitted**, so `07:32:00` stays itself rather than becoming `07:32:00.000` (smol-toml always writes milliseconds). The string therefore carries the source's own precision. |
| F4.3 | 🤷 | `inf` / `nan` floats → error (F0.6). |
| F4.4 | ✅ | Arrays of tables → arrays of objects. Integers are int64, floats float64 (TOML spec types). TOML has no null — no rule needed for ingestion. |

## F5 — YAML

### Scope: what this spec does and does not define

This is a HOCON library, not a YAML implementation. The boundary follows from that:

- **Defined here**: what happens *after* a YAML library has produced a mapping/sequence/scalar
  tree. Root must be a mapping, `${...}` stays literal, NaN and infinity are refused, integers
  that do not fit are refused, binary becomes base64 text, a multi-document stream is refused.
  These are portable, testable, and ours.
- **Not defined here**: how YAML *text* becomes that tree. Whether `010` is `8` or `10`, whether
  `no` is a boolean, whether `2002-12-14` is a string or a date — **that is the YAML library's
  answer, and the caller's choice of library.**

Earlier drafts of this section declared "the YAML 1.2 core schema" as a normative baseline. That
was wrong, and the reason is worth keeping: YAML 1.2 defines several schemas and permits an
implementation to add its own resolution, so "1.2 compliant" does not name one behaviour. On top
of that, libraries have bugs with no guarantee of a fix, and change defaults between major
versions (js-yaml moved timestamps, binary and merge handling between 4 and 5). Promising
cross-language equality of scalar resolution would commit this project to tracking the spec's
ambiguity *plus* four libraries' bugs *plus* their version drift, forever — and to reimplementing
YAML by accretion when they disagree. That is not this project's job.

**So the honest contract is: YAML scalar resolution conforms to the library in use.** Each
implementation names its library and pins what it can (the TS adapter passes `version: '1.2'`
explicitly rather than trusting a default), and the measured behaviour below is documentation
for users, not a conformance target.

**And the user-facing consequence: the library must be swappable.** Each adapter exposes a
tree-level entry point (`yaml.FromValue` in Go, `fromYamlValue` in TS) that accepts an
already-decoded value tree from whatever library and settings the caller chose; the packaged
parse function is only a convenience front on the named default. This — not a `{parser,
version}` option — is how a user routes around a library bug or picks a schema: an enum of
blessed parsers would make the dependency a public contract with a language-specific value set,
while the injection point makes the *tree shape* the contract, which is exactly the part this
spec owns. Tree rules apply to injected trees identically, and leaf normalization accepts the
shapes the ecosystem's libraries commonly produce (string-keyed and any-keyed maps, timestamps
as native date types → their ISO text, byte arrays → base64).

**Consequence for the differential harness**: cross-impl YAML fixtures must not assert
equality on ambiguous plain scalars, or the harness will report the libraries' differences as
our divergences. Fixtures should quote any scalar whose resolution is not portable — quoting is
unambiguous everywhere — and exercise the tree-level rules that this spec actually owns.

### What we define

| Item | Status | Rule |
| --- | --- | --- |
| F5.1 | 🤷 | Scalar resolution is **delegated to the YAML library**; this spec states no baseline schema. Implementations should pin what the library allows to be pinned, and name the library and version in their docs. |
| F5.2 | ✅ | Anchors, aliases and merge keys are resolved before the tree reaches the adapter, so an ingested config contains no aliases (consistent with F0.1). Where the library makes merging optional it must be enabled — otherwise `<<` leaks through as an ordinary field, which is a *structural* difference, not a scalar one, and therefore ours to prevent. |
| F5.3 | ✅ | Non-string scalar keys map to their string forms; a collection key is an error. Two sibling keys whose string forms coincide (`1` and `"1"`) are an **error** — map-iteration order is not deterministic, same rationale as F1.6; applies to injected trees (the `FromValue` entry points) as well. **An integer key and a boolean key cannot be siblings in a portable document**: Python's `bool` subclasses `int`, so `1` and `true` collide as dict keys and the mapping is not representable there at all — a language-natural divergence in the shape of S1.2.6, not something an adapter can fix. Fixtures keep them in separate mappings. **Which forms coincide is not aligned across implementations and deliberately so** (2026-07-30 cross-check): `1.0` gives the key `"1.0"` in rs.hocon and py.hocon, `"1"` in go.hocon and ts.hocon, because that is each library's scalar resolution, which F5.1 delegates. What is common is that a coincidence errors. |
| F5.5 | ✅ | `!!binary` becomes its base64 string — HOCON has no binary type, the same reasoning as F4.2 for TOML dates. A library that refuses the tag instead is acceptable. |
| F5.6 | 🤷 | NaN and infinity are errors per F0.6, whatever the library resolved them from. |
| F5.7 | ✅ | **Multi-document streams are an error.** Some libraries return the first document and drop the rest silently, which is data loss the caller cannot see; the adapter must detect it. A **trailing `---`** is a second (empty) document and is refused too. Detection cannot always be delegated: goccy (go.hocon) collapses a stream whose *first* document is empty into one empty document at both its decoder and its AST, so a leading `---` used to discard the whole file — the count has to come from the token stream ([go.hocon#166](https://github.com/o3co/go.hocon/issues/166)). A directive line (`%YAML 1.2`) belongs to the document after it and is not one itself. |
| F5.8 | 🤷 | Duplicate keys in one mapping are an error where the library reports them. |
| F5.9 | ✅ | An empty document maps to the empty object, matching HOCON's own S3.1. |

**F5.4 is withdrawn, not vacant.** It read "timestamps stay strings", and the scalar-resolution
contract above retired it: whether `2002-12-14` reaches the adapter as a string or as a date is
the library's answer under F5.1, and leaf normalization converts a native date to its ISO text
either way. The number is left unused rather than recycled so that older commit messages and
issues still resolve.

### Measured library behaviour (documentation, not a contract)

Recorded so users know what their choice of library gives them, and so a change is noticed.

The same library returns both answers depending on the YAML version it is told to use — the
variance is a property of the schema, not of the language:

| Input | `yaml` @ 1.1 | `yaml` @ 1.2 |
| --- | --- | --- |
| `010` | `8` | `10` |
| `1_000` | `1000` | `"1_000"` |
| `y` `n` `yes` `no` `on` `off` | booleans | strings |
| `1:30` | `90` | `"1:30"` |
| `2002-12-14` | `Date` | `"2002-12-14"` |

Thirteen plain-scalar forms differ between the two versions, in four families: integer
bases/separators, sexagesimal, the boolean words, and timestamps. Quoting resolves every one of
them identically, which is the portable advice for anyone who cares.

Across the libraries actually shipped:

| Library | `010` | `1_000` | `no` | timestamp | duplicate key | multi-document |
| --- | --- | --- | --- | --- | --- | --- |
| goccy/go-yaml 1.19.2 (Go) | `8` | `1000` | string | string | error | **silently first** |
| yaml 2.9.0 @ 1.2 (TS) | `10` | `"1_000"` | string | string | error | throws |
| go.yaml.in/yaml v3.0.4 | `8` | `1000` | string | `time.Time` | error | silently first |
| sigs.k8s.io/yaml 1.6.0 | `8` | `1000` | **`false`** | string | **silently last-wins** | silently first |

Go and TS therefore disagree on `010`. Under the contract above that is not a defect to fix — it
is the libraries' answer, and a config that depends on it was never portable. What the adapters
*do* guarantee is identical treatment of everything in "What we define": both refuse a
multi-document stream, both refuse NaN, both keep `${...}` literal.

Library selection notes, on measured evidence rather than popularity:

- Go: `goccy` over `go.yaml.in/yaml` (which resolves timestamps to `time.Time`) and over
  `sigs.k8s.io/yaml` (which has the Norway problem and accepts duplicate keys silently).
  `gopkg.in/yaml.v3` is archived, though the project continues as `go.yaml.in/yaml`.
- TS: `yaml` (eemeli) over `js-yaml`, despite js-yaml's larger download and star counts:
  js-yaml 5 throws on `!!binary` and does not merge `<<`, so F5.5 and F5.2 cannot be met with it.

## Fixture coverage

Which items are held by the differential harness, from the `note` fields of
[`testdata/format-ingestion/manifest.json`](../testdata/format-ingestion/manifest.json). All 26
cases are listed, so an item's absence here is a real gap rather than an unlabelled fixture.

| Item | Fixtures |
| --- | --- |
| F0.2 | `fi12-dotenv`, `fi21-markers-in-strings`, `fi31-tables`, `fi52-dotted-keys` |
| F0.3 | `fi22-array-root` |
| F0.6 | `fi32-infinity`, `fi45-nan` |
| F0.9 | `fi17-bom`, `fi51-bom` |
| F1.1 | `fi10-bulk` |
| F1.2 | `fi10-bulk`, `fi14-dot-in-name` |
| F1.3 | `fi10-bulk`, `fi15-ascii-fold` |
| F1.6 | `fi11-collision`, `fi14-dot-in-name`, `fi15-ascii-fold` |
| F1.7 | `fi12-dotenv`, `fi13-ambiguous-hash` |
| F2.1 | `fi52-dotted-keys` |
| F2.2 | `fi52-dotted-keys` |
| F2.4 | `fi52-dotted-keys` |
| F2.9 | `fi16-proto-keys`, `fi50-proto-keys` |
| F3.2 | `fi20-comments`, `fi23-comment-splices-tokens`, `fi24-cr-terminated-comment`, `fi25-line-separator-in-comment` |
| F4.2 | `fi30-datetimes` |
| F4.4 | `fi31-tables` |
| F5.2 | `fi40-merge-and-alias` |
| F5.3 | `fi41-keys-and-literals` |
| F5.5 | `fi44-binary` |
| F5.7 | `fi42-multidoc` |
| F5.9 | `fi43-empty` |

21 of the 44 live items are covered (F5.4 is withdrawn; F2.7 and F3.3 are open by design). The
uncovered ones are not equally exposed: F0.1 and F0.4 describe a construction path that has no
alternative implementation to diverge into, while F0.5, F0.7, F0.10, F1.9, F2.8, F3.5 and F5.6
each pin an answer the four could plausibly disagree on and are worth fixtures.
