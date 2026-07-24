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

#### Hybrid mode: `ev12c` routes through native Lightbend 1.4.6 (no sidecar)

Most env-var-list fixtures use the `.env` sidecar + `EnvVarListExpander`
pipeline above. **`ev12c-include-config-defined-wins.conf` is an exception**:
it has NO `.env` sidecar and is processed via the native Lightbend 1.4.6 path
(the `else` branch of the SUCCESS_CONFS loop in `GenerateExpected.java`,
equivalent to plain `ConfigFactory.parseFile(...).resolve()`).

**Why the exception**: ev12c exercises the cross-source case — `${X[]}` in an
included file when `X` is defined as a config key in the *including* file at
root. `EnvVarListExpander.generateJson` pre-expands every `.conf` in
`confDir` against its own source via line-level `isDefinedInConfig`. When
processing `ev12c-inner.conf` (which contains `mylist = ${S13C_EV12C_X[]}`),
the expander would not see that `S13C_EV12C_X` is defined in the *outer*
file, so the regex would replace `${X[]}` with the
`${NEVER_DEFINED_PLACEHOLDER}` sentinel and required-resolution would fail.

The native Lightbend 1.4.6 path has full resolver semantics including
[S14c.2 original-path fallback](spec-checklist.md), which is exactly what
the spec posture under [E6 cross-source](extra-spec-conventions.md#e6)
relies on. See [xx.hocon#22](https://github.com/o3co/xx.hocon/issues/22).

**Hermeticity note**: the native path uses `ConfigResolveOptions.defaults()`,
which leaves `useSystemEnvironment=true`. For ev12c specifically this is
harmless — even if a host exports `S13C_EV12C_X_0`, Lightbend's resolver
hits the original-path config (`S13C_EV12C_X = ["root-val"]`) first and
never consults env. The test's expected JSON pins this behavior, so any
host-env-induced deviation would surface as a fixture failure.

**When to use this hybrid mode**: when the behavior under test cannot be
modeled by per-file textual pre-expansion (cross-source semantics, multi-pass
resolution interactions, etc.). For ordinary `${X[]}` same-source cases, stay
with the `.env` sidecar pipeline.

### HERMETIC_NO_ENV_GROUPS — `setUseSystemEnvironment(false)` for env-unset fixtures

A small array of group-prefix strings in `GenerateExpected.java`
(`HERMETIC_NO_ENV_GROUPS`) selects fixtures that route through the native
Lightbend `SUCCESS_CONFS` path **with `useSystemEnvironment` forced to
false**. Currently: `include-env-fallback/`. Used when the case under test
is the env-UNSET branch of a `${?ENV}` fallback — if a host happened to
define the same env name, the generator would silently bake the host value
into the expected JSON, defeating the regression. Forcing
`useSystemEnvironment=false` at resolve time forces the optional
substitution to its undefined branch, making the expected JSON
bit-exact-stable across machines.

Distinct from `.env` sidecar / `EnvVarListExpander`:

- `.env` sidecar is used when the fixture's `${X[]}` content needs
  *pre-expansion* against a known env (S13c list expansion).
- `HERMETIC_NO_ENV_GROUPS` is for plain `${?ENV}` fallback where the
  expected behaviour is "env undefined, prior assignment wins". No
  pre-expansion; just disable env lookup at resolve time.

The fixture is registered in `SUCCESS_CONFS` normally. The native loop
checks `confName.startsWith(prefix)` against each entry in
`HERMETIC_NO_ENV_GROUPS` and applies the no-env `ConfigResolveOptions`
when a match is found.

#### Adding a new hermetic-no-env group

1. Create fixtures under `testdata/hocon/<group>/`. Use namespaced env-var
   names (e.g., `GH128_IEV01_VAR_UNSET`) for defense-in-depth.
2. Add the fixture paths to `SUCCESS_CONFS` in `GenerateExpected.java`
   *and* add `"<group>/"` (with trailing slash) to `HERMETIC_NO_ENV_GROUPS`.
3. Run `./gradlew run` from `generate/`.
4. Inspect the generated `expected/hocon/<group>/*-expected.json` — the
   optional fallback should resolve as undefined (prior assignment wins).
5. If the fixture also models an env-SET companion case, that companion
   should be tested at the per-impl layer with an env-injection API
   (`parse(input, { env })` etc.), NOT at the shared-fixture layer where
   hermeticity is the priority.

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

### us02 / us03 / us13 (cluster 3c, formerly strict-spec divergence — re-aligned with Lightbend 2026-05-20)

**Historical context:** these three S8.6 fixtures previously encoded a strict
xx.hocon reading of HOCON.md L270-276 ("an unquoted string may not begin with
`-` or digits") that diverged from Lightbend's pragmatic interpretation. They
were excluded from `SUCCESS_CONFS` and from `SIDECAR_ERROR_CONFS` (Lightbend
accepts them silently), with per-impl override lists pinning them to error.

**Re-alignment:** xx.hocon issue [#31](https://github.com/o3co/xx.hocon/issues/31)
(2026-05-20, @cgordon) surfaced `b = ${a}-bar` rejected by the same strict
reading extended into concat-continuation. Investigation determined this was a
spec-interpretation difference, not a Lightbend divergence to preserve. E8 was
rewritten to adopt Lightbend's reading (see
[extra-spec-conventions.md E8](extra-spec-conventions.md#e8)), and us02/us03/us13
moved into `SUCCESS_CONFS` with their Lightbend-produced `-expected.json`
sidecars:

- `us02-hyphen-no-digit` (`a = -foo`) → `{"a":"-foo"}` — `-` not followed by a
  digit is treated as the start of an unquoted run.
- `us03-hyphen-alone` (`a = -`) → `{"a":"-"}` — same rule.
- `us13-leading-zero` (`a = 01`) → `{"a":1}` — value-start digit-led runs use
  Java numeric semantics (`Long.parseLong` accepts the leading zero).

Per-impl override entries (`IMPL_OVERRIDE_ERRORS` in ts, `KNOWN_LIGHTBEND_QUIRKS`
in rs, `implErrors` in go) are removed as part of the per-impl E8 amendment PRs.

**New concat-continuation fixtures (us17–us30)** were added alongside the E8
rewrite to pin the broader rule across the probe matrix (groups A/B/D/E):
`${a}-bar`, `${a}-`, `${a}--bar`, `${a}-1`, `${a}1bar`, `${a}.bar`, `${a}_bar`,
`"foo"-bar`, `"foo".bar`, `"foo"1bar`, `${a}-${a}`, `${a}-${b}`, `foo-${a}`,
`"foo"-${a}`. All ship with `-expected.json` sidecars from Lightbend output.

Other S8.6 coverage: us01/us04-us12/us14/us16 (Lightbend value-layer-equivalent;
in `SUCCESS_CONFS`), us15 (`a = 1e+x`) — `+` reservation, error in both Lightbend
and impl (`.error` sidecar in `SIDECAR_ERROR_CONFS`).

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

### ef01–ef06 (cluster 3h, S3.1 — empty document parses to `{}`)

Six fixtures pin the S3.1 rule that an empty document is valid HOCON parsing
to the empty object:

- `ef01-empty.conf` — fully empty file
- `ef02-whitespace-only.conf` — three spaces
- `ef03-newlines-only.conf` — newlines only
- `ef04-comment-only.conf` — only `# only a comment\n`
- `ef05-bom-only.conf` — only the UTF-8 BOM (`ef bb bf`)
- `ef06-mixed-ws-comment.conf` — whitespace + comment + blank lines

HOCON.md L130-132 describes the *JSON baseline* ("Empty files are invalid
documents"); the L134 HOCON relaxation parses any file not beginning with `[`
or `{` as if enclosed in `{}`, so an empty document is `{}`. Lightbend's
`ConfigFactory.parseString("")` returns `SimpleConfigObject({})` — verified
across all 6 variants.

**Conformance test treatment:** all 6 are processed as `SUCCESS_CONFS` so the
generator emits `expected/hocon/empty-file/<name>-expected.json = {}` matching
Lightbend's output, and **the sidecars are normative as-is** — implementations
must parse each fixture to `{}`; no per-impl override list applies.

*(History: cluster 3h originally shipped these under a strict-spec divergence
posture — impls rejected via per-impl override lists, treating Lightbend's
accept as a quirk. That posture rested on misreading the L130-132 JSON
baseline as HOCON-normative and was revoked 2026-07-23; see
[extra-spec-conventions.md E10](extra-spec-conventions.md#e10).)*

### properties-conflict/pc01–pc04 (cluster 3h, S23.4)

Four `.properties` fixtures encode the L1485 "object wins" rule for dotted
keys that conflict with scalar keys:

- `pc01-forward.properties` — `a=hello\na.b=world` → `{a:{b:"world"}}` (object wins)
- `pc02-reverse.properties` — same content, reverse order → same result (order-independent)
- `pc03-deep-forward.properties` — `a.b.c=v1\na.b=v2` → `{a:{b:{c:"v1"}}}` (object wins at deeper level)
- `pc04-deep-reverse.properties` — `a.b=v1\na.b.c=v2` → `{a:{b:{c:"v2"}}}`

**Generator note**: these are processed via `PROPERTIES_CONFS` (a new
fixture class), running through Lightbend's direct `ConfigFactory.parseFile(.properties)`
path — NOT via a wrapping `.conf` with `include "...properties"`. Lightbend's
include path is order-dependent (insertion order leaks through) and silently
violates the L1485 rule for pc02/pc03. The direct `parseFile(.properties)`
path is spec-compliant; per-impl conformance tests should match that contract
by calling each impl's `parseProperties()` / `propsToObjectVal()` / etc.
function directly on the `.properties` file, NOT via `include`.

---

## Test-package registry fixtures (E11 — `include package(...)`)

The `include-package/` fixture group is structurally different from all other
groups because Lightbend has no concept of `package(...)`. This group follows
the **per-impl registry population model**:

1. No `expected/hocon/include-package/` directory. The Java generator is NOT
   run for these fixtures and MUST NOT be added to `SIDECAR_ERROR_CONFS`,
   `SUCCESS_CONFS`, or any other generator array.
2. Each fixture requires per-impl test code to **pre-populate a registry**
   (impl-specific) with test-package content before calling `parse()`.
3. Expected outputs are described in `testdata/hocon/include-package/README.md`
   — a human-readable description of the merged output (for success fixtures)
   or the error category (for error fixtures).

### `_packages/` convention

Test-package content lives in `testdata/hocon/include-package/_packages/`. The
leading `_` marks this directory as non-fixture: per-impl test runners that
iterate a directory for `.conf` files MUST skip `_packages/`. The authoritative
registry key for each file is the `# Registry key:` comment inside the file —
the filesystem path encodes `/` as `_` for filesystem compatibility but is NOT
the registry key.

### Per-impl override: ipk03 (collision test)

ipk03 exercises go.hocon / rs.hocon explicit-registry collision policy. ts.hocon
has no explicit registry, so ipk03 is not applicable for ts.hocon. Per-impl
override lists must skip ipk03 for ts.hocon:

- ts.hocon: `IMPL_OVERRIDE_ERRORS` (or equivalent skip list)
- rs.hocon: `KNOWN_LIGHTBEND_QUIRKS` — mark as go/rs only
- go.hocon: `implErrors` — mark as go/rs only

### Why no `.error` sidecars for this group

The generator oracle (Lightbend) does not throw on `package(...)` syntax —
it would parse it as an unknown qualifier or a token error with different
semantics than E11 intends. Lightbend error messages cannot be used as
conformance targets here. Instead, per-impl conformance tests assert error
category directly (any `HoconError` for parse-error fixtures; registration
error before parse for ipk03; cycle error for ipk13/ipk14).

This follows the same spirit as ce05 / ir03 / ir04 / ef01–ef06, but with the
additional twist that the expected outcome is never "Lightbend says X"; it is
always "E11 spec says X".

---

## Scenario YAML fixtures (E12 — deferred substitution resolution)

The `deferred-resolution/` fixture group is structurally different from all other
groups because each scenario is **multi-step** (parse layer A, FromMap layer B,
`withFallback` chain, then `resolve()` with options). A single `.conf` file
cannot encode this structural information. Instead, each fixture is a YAML
scenario file with explicit step sequencing.

This group is normative for E12 cross-impl conformance. The Java generator
**does** run these scenarios through Lightbend (with the exception of two
fixtures marked `lightbendSkip: true`).

### File layout

```text
testdata/hocon/deferred-resolution/
  README.md                      — schema spec + per-impl test runner contract
  dr01-basic-fallback.yaml       — scenario fixtures (dr01..dr30)
  dr02-frommap-only-fallback.yaml
  ...
expected/hocon/deferred-resolution/
  dr01-basic-fallback-expected.json  — Lightbend-resolved JSON (success cases)
  dr01-basic-fallback-expected.txt   — getter assertions + isResolved (success cases)
  dr06-required-self-ref-no-fallback-expected.error  — error category + message (error cases)
  dr11b-resolve-with-unresolved-source-expected.skip — lightbendSkip rationale
```

### Schema overview

Each scenario YAML has four top-level sections:

1. `sources:` — input atoms keyed by id; each is either `parseString` (with optional `parseOptions`) or `fromMap`.
2. `build:` — ordered list of operations (`take`, `extract`, `withFallback`, `resolve`, `resolveWith`) that produce intermediate named artifacts.
3. `expect:` — expected outcome of the final artifact (`success` with `json` / `isResolved` / `getter`, or `error` with `errorCategory` and optional `errorAt` / `errorContains`).
4. `lightbendSkip:` (optional) — when `true`, the Java generator skips the scenario and emits only a `.skip` file. Per-impl tests still run the scenario.

Full schema reference: [`testdata/hocon/deferred-resolution/README.md`](../testdata/hocon/deferred-resolution/README.md).

### Per-impl test runner contract

Per-impl conformance tests:

1. **Read scenario YAML**.
2. **Materialise sources** in declaration order:
   - `parseString` → impl's `ParseStringWithOptions` (or equivalent) with `ResolveSubstitutions: false` UNLESS `parseOptions.resolveSubstitutions = true` is set.
   - `fromMap` → impl's `FromMap`.
3. **Execute `build` steps** in order, maintaining a name→artifact map.
4. **Validate `expect`**:
   - `outcome: success`: the final artifact must succeed all `getter` assertions and (if `expected/.../<name>-expected.json` exists) JSON-equal it. The Lightbend-generated `.json` is the cross-impl ground truth.
   - `outcome: error`: the build step at index `errorAt` (or any step if `errorAt` omitted) must raise an error mapped to `errorCategory`. `errorContains` is substring-matched against the error message.

### Generator behaviour (DeferredResolutionRunner)

`generate/src/main/java/DeferredResolutionRunner.java` runs each `.yaml` through
Lightbend `com.typesafe.config` and emits:

- `<name>-expected.json` — resolved JSON (success scenarios). For `isResolved=false` configs (AllowUnresolved=true), Lightbend's raw render is preserved as-is because unresolved substitutions are not valid JSON.
- `<name>-expected.txt` — human-readable record of `isResolved` and per-getter assertion results (`<path>: <value>` or `<path>: ERROR: <category>`).
- `<name>-expected.error` — `Category: <cat>\nAt: <step>\nMessage: <msg>` for error scenarios. WARN lines are appended when the fixture's expected category or `errorContains` does not match Lightbend's output — these indicate intentional Lightbend divergence (decision 10) or fixture-author mismatches that need review.
- `<name>-expected.skip` — `lightbendSkip` rationale for skipped scenarios.

The generator wires into the existing `GenerateExpected.main` pipeline; running
`./gradlew run` produces all `dr*` outputs alongside the existing groups.

### lightbendSkip mechanism

Two fixtures are currently marked `lightbendSkip: true`:

- **dr11b** (`ResolveWith with unresolved source`): intentional Lightbend divergence per E12 decision 10. Lightbend does NOT precondition-check the source; E12 deliberately strengthens this to `NotResolved`. Lightbend cannot produce ground truth here.
- **dr17** (`E11 package include + deferred resolve`): Lightbend has no `package(...)` qualifier (E11 is non-JVM extension). Per-impl tests run with their registered package content; Lightbend skips.

Marking a scenario `lightbendSkip: true` ALSO requires the runner to write a
`.skip` file documenting the rationale. The fixture YAML's `description` field
is used verbatim — fixture authors must explain WHY Lightbend cannot verify the
scenario in the description.

### Why YAML and not `.conf`?

Multi-step scenarios cannot be encoded in a single `.conf`. Earlier fixture
groups (subst-tokenize, env-var-list) encode multi-aspect behavior via paired
sidecars (`.env`, `.error`); for E12 the structural complexity (parse with
options → fromMap → multiple withFallback → resolve with options) exceeds what
sidecars can express cleanly. YAML's named-key + structured-list shape maps
directly to the step sequence.

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
| `ef01`–`ef06` | `empty-file/` | S3.1 empty document parses to `{}` (cluster 3h; reject-posture revoked 2026-07-23 per [E10](extra-spec-conventions.md#e10)) — `{}` sidecars normative, no override |
| `bsl01`–`bsl09` | `byte-single-letter/` | S21.4 binary single-letter K/M/G/T/P/E byte abbreviations (cluster 3h) — per-impl `getBytes()` assertion |
| `pc01`–`pc04` | `properties-conflict/` | S23.4 `.properties` object-wins conflict (cluster 3h) — direct `.properties` parse, no `.conf` wrapper |
| `ipk01`–`ipk14` | `include-package/` | E11 `include package(...)` qualifier — service-locator pattern; no Lightbend sidecars; per-impl registry-population model (see "Test-package registry fixtures" section above) |
| `dr01`–`dr30` (with `dr11a`/`dr11b`) | `deferred-resolution/` | E12 deferred substitution resolution lifecycle — multi-step scenario YAML format (parse / withFallback / resolve); Lightbend ground truth via `DeferredResolutionRunner`; 2 `lightbendSkip` (dr11b, dr17) per "Scenario YAML fixtures (E12)" section above |
| `kh01`–`kh08` | `key-hyphen-position/` | S8.6 in key position (xx.hocon [#42](https://github.com/o3co/xx.hocon/issues/42), v1.5.3) — hyphen-start segments accepted in field keys per Lightbend; previously over-strict in ts/rs/go. kh08 pins the hyphen-then-digit branch (`-1bar` accepted verbatim, not number-lexed) |
| `pw01`–`pw07` | `path-expr-whitespace/` | Path-expression literal whitespace preservation (xx.hocon [#42](https://github.com/o3co/xx.hocon/issues/42), v1.5.3) — whitespace adjacent to dots in path expressions taken verbatim per Lightbend; `pw04` is a combined-regression guard (no whitespace at dot); `pw06` pins trailing-dot rule still errors (BadPath); `pw07` pins HOCON_WS tab coverage adjacent to dot |
| `ar01`–`ar03` | `array-root/` | S3.5 array-root document rejected with a **type** error at the Config boundary after a successful syntax parse (Lightbend `WrongType` "has type LIST rather than object at file root"); `ar03` pins S14b.1 — an included file with array root is invalid (L993-994), error names the *included* file. `.error` sidecars; per-impl tests assert the impl's type-mismatch error class, not a syntax error |
| `pe01`–`pe08` | `path-empty-segment/` | S11.7 empty path segments in **key** position (xx.hocon [#68](https://github.com/o3co/xx.hocon/issues/68)) — the spec's own invalid examples `a..b` (L517) and paths starting/ending with `.` (L518-519). `pe03` (trailing dot) and `pe08` (substitution position) were already rejected by all four impls and act as regression guards; `pe01`/`pe02`/`pe04`/`pe05`/`pe06` were the 4-way gap. `pe07` is the S11.6 SUCCESS guard: a **quoted** empty segment (`a."".b`) is legal |
| `uf01`–`uf04` | `unquoted-forbidden/` | S8.1 backtick — the one forbidden character (L245-247) still accepted by unquoted-string lexers in all four impls (xx.hocon [#68](https://github.com/o3co/xx.hocon/issues/68)); the other forbidden characters were already rejected. `uf01` value position, `uf02` key position, `uf03` mid-token; `uf04` is the SUCCESS guard (backtick inside a quoted string is ordinary content) |

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
