# xx.hocon Fixture Conventions

This document describes the conventions for test fixtures in xx.hocon. It is
NORMATIVE for downstream cluster implementations (3b, 3c, 3e, 3f and future
clusters that add error-expected fixtures).

---

## Fixture groups

Fixtures live under `testdata/hocon/<group>/` and their expected outputs under
`expected/hocon/<group>/`.

### SUCCESS_CONFS

Fixtures that must parse and resolve without error. The generator runs each
through Lightbend (`ConfigFactory.parseFile().resolve()`) and writes
`expected/hocon/<group>/<name>-expected.json`.

Conformance test assertion: parse/resolve succeeds AND the resulting JSON
matches `<name>-expected.json`.

### ERROR_CONFS (legacy — JSON sidecar)

Fixtures that are expected to error. The generator captures the exception and
writes a JSON sidecar:

```
expected/hocon/<group>/<name>-expected-error.json
```

Format:

```json
{
  "error": true,
  "type": "<SimpleClassName>",
  "message": "<exception message>"
}
```

Used by legacy fixture groups (`cycle.conf`, `test13-reference-bad-substitutions.conf`,
`subst-tokenize/st-err*.conf`). New groups should use the `.error` sidecar
convention below.

### ENV_VAR_LIST_SIDECAR — `.env` sidecar for `${X[]}` fixtures (S13c, cluster 3a)

Fixtures under `testdata/hocon/env-var-list/` exercise the spec S13c env-var list
expansion syntax (`${X[]}` and `${?X[]}`). Lightbend typesafe-config 1.4.3 does
NOT implement `[]`, so the generator pre-expands these patterns using a
per-fixture `.env` sidecar before invoking Lightbend.

#### `.env` sidecar format

Each success fixture (and ev03 error fixture) has a paired
`testdata/hocon/env-var-list/<name>.env` file:

```text
# Comments and blank lines are skipped.
S13C_EV01_MY_LIST_0=a
S13C_EV01_MY_LIST_1=b
# Lines without `=` are treated as keys with empty-string values.
```

- Keys follow the spec convention `<NAME>_0`, `<NAME>_1`, ...; lookup stops at
  the first absent index.
- Empty string IS a valid value (see ev10 fixture) — `containsKey` (not
  truthy) determines presence.
- Fixture key prefix is namespaced (e.g. `S13C_EV01_…`) so concurrent
  generator runs and host env do not collide.

#### Hermeticity

`EnvVarListExpander.generateJson` calls `setUseSystemEnvironment(false)` on
`ConfigResolveOptions`, so generator output is invariant under the host's
environment. The required-empty and optional-empty placeholders use a
distinctive sentinel path (`__hocon_gen_NEVER_DEFINED_DO_NOT_SET__`) for
defense in depth. Do not export that name as a shell var or system property in
CI.

#### Multi-file include semantics

`generateJson` pre-expands every `.conf` in the fixture's directory into a
temp dir, then loads the target from the temp dir. This makes
`include "sibling.conf"` work (see ev11-include-context + ev11-inner). For
fixtures that need a sibling include, keep all files in the same directory —
cross-directory includes are NOT pre-expanded.

`ev11-inner.conf` is include-only: it is not in `SUCCESS_CONFS` and has no
expected JSON of its own.

### ENV_VAR_LIST_ERROR_CONFS — `.error` sidecar with pre-expansion (S13c errors)

Distinct from `SIDECAR_ERROR_CONFS` only in the pre-processing step: source
text is run through `EnvVarListExpander.expandListSubstitutions` (with
`setUseSystemEnvironment(false)`) before Lightbend evaluation. Output format
matches `SIDECAR_ERROR_CONFS` (`<name>.error` plain text). Same UNEXPECTED
SUCCESS safety-net throw.

Currently: `ev03-required-no-elements.conf`,
`ev12a-list-suffix-suppresses-scalar-fallback-required.conf`. Add new
entries to the `ENV_VAR_LIST_ERROR_CONFS` array in `GenerateExpected.java`.

### SIDECAR_ERROR_CONFS — `.error` sidecar (NORMATIVE for clusters 3b, 3c, 3e, 3f)

Fixtures that must cause a parse/resolve error. The generator writes a
plain-text sidecar:

```text
expected/hocon/<group>/<name>.error
```

#### Sidecar format

```text
Exception class: <fully-qualified Java exception class name>
Message: <exception message verbatim>
```

Example (`ce01-array-plus-object.error`):

```text
Exception class: com.typesafe.config.ConfigException$WrongType
Message: ../testdata/hocon/concat-errors/ce01-array-plus-object.conf: 1: Cannot concatenate object or list with a non-object-or-list, SimpleConfigList([1]) and SimpleConfigObject({"b":2}) are not compatible
```

> **Note**: the `../testdata/...` prefix in `Message` is an artifact of the generator's working directory (`generate/` is the gradle cwd; `testdataDir = Path.of("../testdata/hocon")` is relative to that). Conformance tests MUST NOT depend on this prefix — per §Semantics below, message content is not asserted at all.

#### Semantics for conformance test runners

- **Existence** of `<name>.error` is the signal: the fixture is expected to
  fail.
- Conformance tests MUST assert that parse/resolve raises any `HoconError` (or
  per-impl equivalent). No exact message matching is required or expected.
- The sidecar content is for traceability (human reference to Lightbend's
  actual error), not for cross-impl assertion.

#### Generator behaviour

1. For each entry in `SIDECAR_ERROR_CONFS`, the generator attempts
   `ConfigFactory.parseFile(path).resolve()`.
2. If Lightbend throws `ConfigException` (or subtype), the generator writes the
   sidecar. The generator prints `OK (.error sidecar): <name> -> <name>.error`.
3. If Lightbend does NOT throw, the generator FAILS the build with
   `RuntimeException("Expected error fixture <name> did NOT throw — verify the fixture input.")`. This is the safety net for "fixture was meant to error but Lightbend started accepting it" (regression or typo); without this, a misconfigured fixture is invisible. The only way to add a Lightbend-silent-accept fixture is to exclude it from `SIDECAR_ERROR_CONFS` and document it under §Lightbend quirks (e.g., ce05).

#### Adding a new error-expected fixture group

1. Create fixtures under `testdata/hocon/<new-group>/`.
2. Add fixture paths to `SIDECAR_ERROR_CONFS` in
   `generate/src/main/java/GenerateExpected.java`.
3. Run `./gradlew run` from `generate/`.
4. Verify all entries produced `.error` sidecars (the generator throws on
   any UNEXPECTED SUCCESS — passing run = all expected-errors actually
   errored).
5. Commit `testdata/hocon/<new-group>/` and `expected/hocon/<new-group>/`
   together.

---

## Lightbend quirks

Some fixtures document spec-required errors that Lightbend 1.4.3 does not
enforce. These cannot have `.error` sidecars because the generator oracle
(Lightbend) does not throw. They are documented here for implementers.

### ce05-object-plus-scalar (`a = { b: 1 } x`)

Spec S10.13 says this should be a type error (object in string concat). However,
Lightbend 1.4.3 silently accepts it: the object wins and the trailing unquoted
scalar `x` is discarded, yielding `{"b": 1}`.

**Conformance test treatment:** ce05 has no `.error` sidecar (Lightbend does not throw, so the generator cannot produce one). It is excluded from `SIDECAR_ERROR_CONFS`. However per the o3co strict-HOCON-spec posture (see [extra-spec-conventions.md E5](extra-spec-conventions.md#e5)), **implementations MUST reject `ce05`-style input** as a type error per HOCON.md L373 — this is a deliberate Lightbend divergence. Per-impl conformance tests assert the error directly (the `.conf` is loaded by test code, not by the generator).

Other S10.13 coverage runs through ce03 (`[1, 2] 3`), ce04 (`3 [1, 2]`), ce06 (`x { b: 1 }`), and ce12/ce13 for resolved-substitution variants — Lightbend errors on all of these (sidecars produced).

### us02 / us03 / us13 (cluster 3c, S8.6 strict-spec divergence)

Three S8.6 fixtures encode strict-HOCON-spec behaviour where Lightbend silently
accepts spec-violating input:

- `us02-hyphen-no-digit` (`a = -foo`) — spec says lex error (`-` must be followed
  by a digit per HOCON.md L270-276); Lightbend tokenizes as
  `unquoted("-") + unquoted("foo")` and produces `{"a":"-foo"}`.
- `us03-hyphen-alone` (`a = -`) — spec says lex error; Lightbend produces `{"a":"-"}`.
- `us13-leading-zero` (`a = 01`) — strict JSON-number grammar (HOCON.md L270-276)
  forbids leading zeros on non-zero ints, so the spec tokenizes as
  `number(0) + unquoted("1")` → string `"01"`. Lightbend calls
  `Long.parseLong("01")` → number `1` and produces `{"a":1}`.

**Conformance test treatment:** all three are EXCLUDED from `SUCCESS_CONFS` and
`SIDECAR_ERROR_CONFS` (the safety net would fire on Lightbend's silent accept).
The `.conf` files are kept on disk for per-impl test loading. Per the o3co
strict-HOCON-spec posture (see [extra-spec-conventions.md E8](extra-spec-conventions.md#e8)),
**implementations MUST reject `us02`/`us03` and produce string `"01"` for `us13`**
per HOCON.md L270-276 — a deliberate Lightbend divergence.

Other S8.6 coverage runs through us01/us04-us12/us14/us16 (Lightbend
value-layer-equivalent to strict spec; safe in `SUCCESS_CONFS`) and us15
(`a = 1e+x`) which both Lightbend and strict-spec reject — Lightbend on reserved
`+`, strict-spec lex on the same (`.error` sidecar produced).

### ir03 / ir04 (cluster 3e, S12.5 strict-spec divergence)

Two S12.5 fixtures encode strict-HOCON-spec behaviour where Lightbend silently
accepts spec-violating dotted-include paths:

- `ir03-include-dot-foo-equals` (`include.foo = 1`) — spec says parse error
  (HOCON.md L570 reserves `include` from beginning a key path expression);
  Lightbend's tokenizer joins `include.foo` into a single unquoted token, then
  PathParser splits it later; `isIncludeKeyword` matches only the bare 7-char
  `include` token, so the joined form silently parses as a regular nested key:
  `{"include":{"foo":1}}`.
- `ir04-include-nested-object` (`a = { include.bar = 1 }`) — same mechanism
  inside a nested object literal. Lightbend produces `{"a":{"include":{"bar":1}}}`.

**Conformance test treatment:** both are EXCLUDED from `SIDECAR_ERROR_CONFS`
(the safety net would fire on Lightbend's silent accept). The `.conf` files are
kept on disk for per-impl test loading. Per the o3co strict-HOCON-spec posture
(see [extra-spec-conventions.md E9](extra-spec-conventions.md#e9)),
**implementations MUST reject `ir03`/`ir04`** per HOCON.md L570 — a deliberate
Lightbend divergence.

The remaining S12.5 negative fixtures (ir01 `include = 1`, ir02 `include : 1`,
ir10 `include += [1]`, ir12 `include` + newline + `foo.conf`, ir13
`include { x = 1 }`) all cause Lightbend to throw via the include-statement
parser (text after `include` is not a valid include argument); these live in
`SIDECAR_ERROR_CONFS` with `.error` sidecars produced.

---

## Fixture naming convention

| Prefix | Group | Coverage |
| --- | --- | --- |
| `ce01`–`ce15` | `concat-errors/` | S10.4 / S10.13 / S10.19 concat type-check (cluster 3b) |
| `st01`–`st20` | `subst-tokenize/` | Substitution tokenization (Phase 4) |
| `st-err01`–`st-err11` | `subst-tokenize/` | Substitution tokenization errors (Phase 4) |
| `na01`–`na12` | `numeric-obj-array/` | Numeric-object-to-array conversion (Phase 6 #2, S15) |
| `ev01`–`ev11` | `env-var-list/` | S13c env-var list expansion `${X[]}` / `${?X[]}` (cluster 3a) |
| `ev12a`–`ev12b` | `env-var-list/` | S13c.5 — list suffix suppresses scalar env-var fallback (cluster 3g follow-up) |
| `ev13` | `env-var-list/` | S13c — optional list expansion in isolation, not inside concat (cluster 3g follow-up) |
| `us01`–`us16` | `unquoted-starts/` | S8.6 strict-spec unquoted-string-starts (cluster 3c) |
| `ir01`–`ir14` | `include-reservation/` | S12.5 strict-spec `include` reservation at key-path start (cluster 3e) |
| `sr01`–`sr11` | `self-ref-lookback/` | S13a.13 optional self-ref in value concatenation look-back (cluster 3f) |

### Sibling include-target files

Some fixtures use `include "<sibling>.conf"` directives that load a sibling
`.conf` in the same group directory. The sibling target files are named
`<base>-inner.conf` (e.g. `ir05-inner.conf`, `ir09-inner.conf`) and are NOT
standalone fixtures — they are not listed in `SUCCESS_CONFS` /
`SIDECAR_ERROR_CONFS` and no `*-expected.json` is generated for them. Per-impl
test runners that iterate a fixture directory directly (rather than reading
the canonical `_CONFS` arrays from `GenerateExpected.java`) MUST skip
`*-inner.conf` files.

Future clusters should use a new prefix and group directory, and add their
error fixtures to `SIDECAR_ERROR_CONFS`.
