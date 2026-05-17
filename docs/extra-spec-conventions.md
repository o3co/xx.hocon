# Extra-Spec Conventions

Cross-implementation behaviors **not enumerated by the [Lightbend HOCON spec](https://github.com/lightbend/config/blob/main/HOCON.md)** but agreed across the three implementations:

- [o3co/ts.hocon](https://github.com/o3co/ts.hocon)
- [o3co/rs.hocon](https://github.com/o3co/rs.hocon)
- [o3co/go.hocon](https://github.com/o3co/go.hocon)

## Role of this file

The HOCON spec (`refs/lightbend-config/HOCON.md`) defines a finite set of normative behaviors. This file captures *behaviors that fall outside the spec's explicit scope* but that the three implementations have nevertheless aligned on. Typical sources:

- **Implicit-by-absence**: the spec enumerates a set (e.g. whitespace characters) without saying anything about non-members. The non-membership has observable consequences in some impls (typically when a stdlib helper is broader than the spec's enumeration).
- **Cross-impl conventions agreed by project policy** that go beyond what HOCON.md requires (e.g. API shape, error message format, error-recovery behavior).

Items here use an **E-prefix** namespace (`E1`, `E2`, …) to keep them clearly distinct from canonical spec items (`S1.1`, `S6.5`, etc.) tracked in [`spec-checklist.md`](spec-checklist.md). E-items do **not** count toward the compliance-matrix denominator; they are a separate axis of cross-impl convergence.

## Status legend

Same glyphs as `spec-checklist.md`:

| Glyph | Meaning |
| --- | --- |
| ✅ | Test exists and passes |
| ⚠️ | Test exists, partial pass / known-incorrect-but-pinned |
| ❌ | Test exists and fails (or known violation) |
| 🤷 | No test — implementation claim only, unverified |

## Items

### E1 — NEL (U+0085) is ordinary char in unquoted strings (not whitespace, not newline)

**Source**: implicit-by-absence. HOCON.md §Whitespace (L165–184) enumerates the whitespace set but does not list U+0085 (NEL). The "newline" definition at L182–184 restricts newline to U+000A specifically. Therefore NEL is a non-whitespace, non-newline character — it falls into ordinary unquoted-string content per S8.8 ("control characters not in the forbidden set are allowed").

**Why an E-item rather than an S-item**: the spec asserts what *is* in HOCON_WS, not what is *not* in HOCON_WS. Treating "X is not in HOCON_WS" as a canonical spec item would force a row for every Unicode codepoint not enumerated — infeasible and not the spec's intent. NEL is called out separately because **Go's `unicode.IsSpace()` and Rust's `char::is_whitespace()` both include NEL**, making it a real footgun for stdlib-using implementations. ECMAScript regex `\s` also includes NEL.

| Impl | Status | Test | Notes |
|---|---|---|---|
| ts.hocon | 🤷 | (followup — to be added) | Hardcoded ASCII whitespace set; JS regex `\s` is not used. NEL has always been an ordinary char. |
| rs.hocon | 🤷 | (followup — to be added) | Hardcoded match arm; Rust `char::is_whitespace()` is not used. NEL has always been an ordinary char. |
| go.hocon | ✅ | `internal/lexer/lexer_test.go` (`TestSpecS8_8_NELAllowedInUnquoted`) | Reached convergence on 2026-05-15 via [#78](https://github.com/o3co/go.hocon/pull/78); `isUnquotedForbidden` was previously routed through `unicode.IsSpace()` and silently treated NEL as whitespace. The S6 work replaced that with the spec-faithful `isHoconWhitespace()` predicate. |

The two `🤷` are due to absent test coverage, not divergent behavior — followup PRs will add explicit NEL regression tests in ts.hocon and rs.hocon to flip those to ✅.

### E2 — Numeric-key array conversion: leading-zero key forms rejected (`"00"` ≠ `"0"`)

**Source**: implicit-by-absence + cross-impl convention. HOCON.md §Conversion of numerically-indexed objects to arrays (L1184–L1219) requires conversion of objects whose keys "parse as positive integers" but does not specify whether `"00"` and `"0"` are the same key (they parse to the same `int` in every common stdlib parser). Lightbend's reference impl uses `Integer.parseInt(key, 10)` and `HashMap<Integer, ConfigValue>`, so `"00"` and `"0"` collide at the HashMap level — the surviving value depends on object key iteration order, which Java does not guarantee.

**o3co convention**: each integer N is canonicalised to exactly one textual form (`Integer.toString(N)`). Keys with leading zeros (`"00"`, `"01"`, `"007"`) are pre-filtered out by the regex `^(0|[1-9][0-9]*)$` before integer parsing. This guarantees deterministic conversion across the three impls and avoids the Lightbend collision race.

**Why an E-item rather than an S-item**: the spec is silent on whether `"00"` is a "valid" integer key. We are tightening — Lightbend accepts the form, we reject it.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Pre-filter regex applied in `numericObjectToArray` helper before `parseInt`. |
| rs.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Pre-filter applied in `numeric_object_to_array` helper before `from_str`. |
| go.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Pre-filter applied in `numericObjectToArray` helper before `strconv.ParseInt`. |

**Fixture**: `testdata/hocon/numeric-obj-array/na08-leading-zero.conf` + sibling `expected/.../na08-leading-zero.divergence.md` (records Lightbend's non-deterministic `["a"]`-or-`["b"]` behaviour).

### E3 — Numeric-key array conversion: leading `+` key forms rejected (`"+1"` ≠ `"1"`)

**Source**: implicit-by-absence + cross-impl convention. Same spec section as E2. Lightbend's `Integer.parseInt("+1", 10)` returns 1, so Lightbend treats `"+1"` and `"1"` as the same integer key. JS `Number.parseInt("+1", 10)`, Rust `i32::from_str("+1")`, and Go `strconv.ParseInt("+1", 10, 32)` all also accept the leading `+` — relying on the native parser to reject this form would silently break canonical-text guarantee.

**o3co convention**: same canonicalisation as E2. The pre-filter regex `^(0|[1-9][0-9]*)$` rejects any leading sign character. `"+1"` is ineligible; only `"1"` is.

**Why an E-item rather than an S-item**: the spec is silent on this form. We are tightening — Lightbend accepts the form (and produces a deterministic, but non-canonical, result), we reject it.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Same pre-filter as E2. |
| rs.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Same pre-filter as E2. |
| go.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Same pre-filter as E2. |

**Fixture**: `testdata/hocon/numeric-obj-array/na10a-plus-sign.conf` + sibling `expected/.../na10a-plus-sign.divergence.md`.

### E4 — Numeric-key array conversion: leading `-` key forms rejected, including `"-0"` (`"-0"` ≠ `"0"`)

**Source**: implicit-by-absence + cross-impl convention. Same spec section as E2. Lightbend's `Integer.parseInt("-0", 10)` returns 0; the negative-filter `if (i < 0) continue;` does NOT skip `-0` because the parsed int is 0, not less than 0. So `"-0"` and `"0"` collide at the HashMap level the same way as E2 (`"00"` ↔ `"0"`).

**o3co convention**: same canonicalisation as E2. The pre-filter regex `^(0|[1-9][0-9]*)$` rejects any leading sign character — for negatives, this is the *only* mechanism (no separate post-parse `i < 0` filter is needed since the regex already excludes `-`).

**Why an E-item rather than an S-item**: the spec says "positive integers" but does not specify the textual form for zero or for negatives. We are tightening uniformly — leading `-` is rejected for *any* value, not just `< 0`.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Same pre-filter as E2/E3. |
| rs.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Same pre-filter as E2/E3. |
| go.hocon | 🤷 | (test coverage to be added in fix/s15-numeric-obj-array PR) | Same pre-filter as E2/E3. |

**Fixture**: `testdata/hocon/numeric-obj-array/na10b-minus-zero.conf` + sibling `expected/.../na10b-minus-zero.divergence.md` (records Lightbend's non-deterministic `["a"]`-or-`["b"]` behaviour, identical mechanism to E2's na08).

<a id="e5"></a>

### E5 — Trailing scalar after object in concat is a type error (not silently discarded)

**Source**: cross-impl convention. HOCON.md §Value concatenation (L373) requires: "it is invalid for arrays or objects to appear in a string value concatenation." Lightbend 1.4.3 violates this: given `a = { b: 1 } x`, the parser silently accepts the input, the object wins, and the trailing unquoted scalar `x` is discarded → `a = {"b": 1}`. No error is raised. This was discovered empirically during Phase 6 #3b xx.hocon fixture generation (cluster 3b) — Lightbend's generator did NOT throw on the `ce05-object-plus-scalar.conf` fixture, contradicting the spec.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST raise a type error (`ResolveError` or per-impl resolver-layer equivalent) for `{object} scalar` concatenation. The error fires in the pairwise-fold `joinPair` helper introduced by Phase 6 #2 (S15 work). The same rule extends to `[array] scalar`, `scalar {object}`, `scalar [array]` (covered by ce03–ce06 in the `concat-errors/` fixture group); the asymmetry — where Lightbend errors on some but not others — is documented in `fixture-conventions.md` "Lightbend quirks".

**Why an E-item rather than an S-item**: HOCON.md L373 is unambiguous, but Lightbend's reference implementation does not enforce it for this specific cell. Tracking this as an E-item makes the deliberate Lightbend divergence explicit (parallel to the strict-spec posture for S8.6 cluster 3c and S12.5 cluster 3e). The S10.13 row in `compliance-matrix.md` already represents the canonical spec rule; E5 documents the specific Lightbend divergence in narrative form so implementers know what they are tightening beyond Lightbend.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ❌ | `concat-errors/ce05-object-plus-scalar.conf` loaded by per-impl test (no xx.hocon `.error` sidecar; Lightbend doesn't throw) | Will flip to ✅ after Phase 6 #3b impl PR merges; impl rejects via `joinPair` type-mismatch branch |
| rs.hocon | ❌ | same fixture | Will flip to ✅ after Phase 6 #3b impl PR merges |
| go.hocon | ❌ | same fixture | Will flip to ✅ after Phase 6 #3b impl PR merges |

**Fixture**: `testdata/hocon/concat-errors/ce05-object-plus-scalar.conf`. No `expected/.../ce05-object-plus-scalar.error` sidecar (Lightbend silent-accept; see `fixture-conventions.md` "Lightbend quirks").

<a id="e6"></a>

### E6 — `${X[]}` config-defined wins, `[]` suffix is a no-op when X is a config key

**Source**: implicit-by-absence + cross-impl convention. HOCON.md §Environment variable list expansion (L902–L920) introduces the `${X[]}` syntax for "expanding an environment variable into a list of values". The spec says the syntax is "only supported for environment variables and not system properties or path expressions", but does not specify the behaviour when `X` is *also* defined as a config key in the same source. Two plausible readings:

- **(a) Config wins, suffix is no-op** — `${X[]}` resolves to whatever `X` is bound to in config (string, list, etc.); the env vars `X_0`, `X_1`, ... are ignored.
- **(b) Error** — `[]` on a non-env-var path is unsupported syntax; should error.

**o3co convention**: reading (a). When NAME has a config-level assignment in the same source, the `[]` suffix is stripped and the resolution proceeds as a normal `${NAME}` substitution. Env vars `NAME_0`, `NAME_1`, ... are not consulted. This matches the principle "config provided by the user always wins" that runs through HOCON (e.g. the `${?...}` optional-substitution semantics).

**Why an E-item rather than an S-item**: the spec text is silent on the config-key conflict case. We are establishing a convention to keep the three impls aligned and to make user expectations predictable; tightening to reading (b) is a defensible alternative that the spec also allows.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | 🤷 | `env-var-list/ev05-config-defined-wins.conf` (to be loaded in Phase 2/3 impl PR) | Per-impl substitution resolver applies same precedence as `${X}`. |
| rs.hocon | 🤷 | same fixture | Same convention as ts. |
| go.hocon | 🤷 | same fixture | Same convention as ts. |

**Fixture**: `testdata/hocon/env-var-list/ev05-config-defined-wins.conf` + `expected/hocon/env-var-list/ev05-config-defined-wins-expected.json`. Generator pre-expansion uses `EnvVarListExpander.isDefinedInConfig` to detect the config-defined case before falling through to env-var lookup.

<a id="e7"></a>

### E7 — Whitespace between path expression and `[]` is allowed in `${X []}` / `${?X []}`

**Source**: implicit-by-absence + cross-impl convention. HOCON.md L902–L920 introduces `${X[]}` without specifying tokenizer behaviour for whitespace between the path expression and the `[]` suffix. Substitution body tokenization (HOCON.md L1410–L1450) generally allows whitespace inside `${...}` body (e.g. `${ X }` is equivalent to `${X}`), so allowing `${X []}` matches that general permissiveness. Per-impl tokenizers can each decide independently, but uniform handling avoids cross-impl friction.

**o3co convention**: any number (including zero) of horizontal whitespace characters between the path expression and `[]` is accepted and treated identically to no-whitespace. The substitution body up to and including the `[]` is a single token sequence at the tokenizer level.

**Why an E-item rather than an S-item**: HOCON.md does not enumerate this tokenizer cell. We are not tightening or loosening — we are picking the lenient side of an unspecified degree of freedom.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | 🤷 | `env-var-list/ev09-whitespace-before-suffix.conf` (Phase 2/3 impl PR) | Tokenizer skips inner whitespace before `[]` consumption. |
| rs.hocon | 🤷 | same fixture | Same as ts. |
| go.hocon | 🤷 | same fixture | Same as ts. |

**Fixture**: `testdata/hocon/env-var-list/ev09-whitespace-before-suffix.conf` + `expected/hocon/env-var-list/ev09-whitespace-before-suffix-expected.json`. Generator regex permits `\s*` between path expression and `[]`.

## How this file is maintained

1. Add a new item when a cross-impl convergence (or divergence worth documenting) is observed that does not map to a row in [`spec-checklist.md`](spec-checklist.md).
2. Use the next available `E<n>` ID; never reuse retired IDs.
3. Each entry records: source / rationale, per-impl status with test refs, and any cross-impl divergence notes.
4. E-items do not affect compliance rates in [`compliance-matrix.md`](compliance-matrix.md), but the matrix should link here so readers can find these conventions when scanning compliance state.

## Last verified

2026-05-16 — file created; E1 (NEL) added.
2026-05-16 — E2 (leading-zero key), E3 (leading `+` key), E4 (leading `-` key incl. `-0`) added as part of S15 numerically-indexed-object → array work (Phase 6 #2).
2026-05-17 — E5 (trailing scalar in object/array concat) added as part of S10 concat type-check tightening work (Phase 6 #3b).
2026-05-17 — E6 (`${X[]}` config-defined wins), E7 (whitespace before `[]` allowed) added as part of S13c env-var-list fixture work (Phase 6 #3a).
