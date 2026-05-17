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

---

## Fixture naming convention

| Prefix | Group | Coverage |
| --- | --- | --- |
| `ce01`–`ce15` | `concat-errors/` | S10.4 / S10.13 / S10.19 concat type-check (cluster 3b) |
| `st01`–`st20` | `subst-tokenize/` | Substitution tokenization (Phase 4) |
| `st-err01`–`st-err11` | `subst-tokenize/` | Substitution tokenization errors (Phase 4) |
| `na01`–`na12` | `numeric-obj-array/` | Numeric-object-to-array conversion (Phase 6 #2, S15) |

Future clusters (3c, 3e, 3f) should use a new prefix and group directory, and
add their error fixtures to `CONCAT_ERROR_CONFS`.
