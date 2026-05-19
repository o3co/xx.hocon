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
| ts.hocon | ✅ | `concat-errors/ce05-object-plus-scalar.conf` loaded by `tests/concat-errors.test.ts` with ce05 in the per-impl override list (no xx.hocon `.error` sidecar; Lightbend doesn't throw) | Flipped ✅ via [ts.hocon#101](https://github.com/o3co/ts.hocon/pull/101) (Phase 6 #3b); impl rejects via `joinPair` type-mismatch branch |
| rs.hocon | ✅ | `concat-errors/ce05-object-plus-scalar.conf` loaded by `tests/concat_errors_test.rs` with ce05 in the per-impl override list | Flipped ✅ via [rs.hocon#89](https://github.com/o3co/rs.hocon/pull/89) (Phase 6 #3b); `join_pair` discriminator on the catch-all arm returns `Err(ResolveError::concat_type_mismatch(...))` when either side is `HoconValue::Object(_)` before string-concat |
| go.hocon | ✅ | `concat-errors/ce05-object-plus-scalar.conf` loaded by `internal/resolver/concat_errors_test.go` with ce05 in the per-impl override list | Flipped ✅ via [go.hocon#87](https://github.com/o3co/go.hocon/pull/87) (Phase 6 #3b, BREAKING); `joinPair` `default:` case rewritten to discriminate object operands before falling through to `concatStrings` |

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
| ts.hocon | ✅ | `tests/env-var-list.test.ts` (ev05) loaded in [ts.hocon#100](https://github.com/o3co/ts.hocon/pull/100) | Resolver checks config presence before delegating to `resolveEnvList`; matches `${X}` precedence. |
| rs.hocon | ✅ | `tests/env_var_list_test.rs` (ev05) loaded in [rs.hocon#88](https://github.com/o3co/rs.hocon/pull/88) | Same: config lookup wins; `resolve_env_list` consulted only when path is not config-defined. |
| go.hocon | ✅ | `s13c_env_var_list_test.go` (ev05) loaded in [go.hocon#86](https://github.com/o3co/go.hocon/pull/86) | Same; `resolveEnvList` runs only when relativized-path config lookup fails. **Known limitation** (tracked in [xx.hocon#22](https://github.com/o3co/xx.hocon/issues/22)): include-scope `${X[]}` does NOT currently fall back to original-path config when root has `X` — `${X}` non-list does. Cross-impl scope, fix planned. |

**Fixture**: `testdata/hocon/env-var-list/ev05-config-defined-wins.conf` + `expected/hocon/env-var-list/ev05-config-defined-wins-expected.json`. Generator pre-expansion uses `EnvVarListExpander.isDefinedInConfig` to detect the config-defined case before falling through to env-var lookup.

<a id="e7"></a>

### E7 — Whitespace between path expression and `[]` is allowed in `${X []}` / `${?X []}`

**Source**: implicit-by-absence + cross-impl convention. HOCON.md L902–L920 introduces `${X[]}` without specifying tokenizer behaviour for whitespace between the path expression and the `[]` suffix. Substitution body tokenization (HOCON.md L1410–L1450) generally allows whitespace inside `${...}` body (e.g. `${ X }` is equivalent to `${X}`), so allowing `${X []}` matches that general permissiveness. Per-impl tokenizers can each decide independently, but uniform handling avoids cross-impl friction.

**o3co convention**: any number (including zero) of ASCII horizontal whitespace characters (`0x20` SPACE or `0x09` TAB) between the path expression and `[]` is accepted and treated identically to no-whitespace. The substitution body up to and including the `[]` is a single token sequence at the tokenizer level. Other Unicode horizontal whitespace (NBSP, etc.) is out of scope for this convention.

**Why an E-item rather than an S-item**: HOCON.md does not enumerate this tokenizer cell. We are not tightening or loosening — we are picking the lenient side of an unspecified degree of freedom.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ✅ | `tests/env-var-list.test.ts` (ev09) + `tests/lexer.test.ts` (NBSP/CR rejection regressions) — [ts.hocon#100](https://github.com/o3co/ts.hocon/pull/100) | Tokenizer accumulates pending whitespace; at `[` arm validates ASCII space/tab only (I2 multi-agent-review fix). Other Unicode whitespace (NBSP, CR, Zs) errors with `HOCON extra-spec E7`. |
| rs.hocon | ✅ | `tests/env_var_list_test.rs` (ev09) + `tests/lexer_test.rs` (NBSP/CR rejection regressions) — [rs.hocon#88](https://github.com/o3co/rs.hocon/pull/88) | Same: `pending_ws` allow-list restricted to `0x20`/`0x09` (I2 fix). |
| go.hocon | ✅ | `s13c_env_var_list_test.go` (ev09) + `internal/lexer/lexer_test.go` sub-tests — [go.hocon#86](https://github.com/o3co/go.hocon/pull/86) | Same: ASCII strict check on `pendingWs` at `[` arm (I2 fix). |

**Fixture**: `testdata/hocon/env-var-list/ev09-whitespace-before-suffix.conf` + `expected/hocon/env-var-list/ev09-whitespace-before-suffix-expected.json`. Generator regex permits `[ \t]*` (ASCII space or tab) between path expression and `[]`.

<a id="e8"></a>

### E8 — Unquoted-string-starts are strict per HOCON.md L270-276 (Lightbend tolerates fallback)

**Source**: cross-impl convention. HOCON.md §Unquoted strings L270-276 states:

> An unquoted string may not *begin* with the digits 0-9 or with a hyphen (`-`, 0x002D) because those are valid characters to begin a JSON number. The initial number character, plus any valid-in-JSON number characters that follow it, must be parsed as a number value.

Lightbend 1.4.3's `Tokenizer.pullNumber` does NOT enforce this strictly. Its actual behaviour (verified empirically against fixture group `unquoted-starts/`):

1. Consumes a maximal run of number-like chars (`0123456789eE+-.`).
2. Tries `Long.parseLong` then `Double.parseDouble`.
3. **Falls back to unquoted-text on parse failure** (provided no reserved chars are present).

This produces three observable divergences from the strict spec:

- **`a = -foo`** (us02): Lightbend → `unquoted("-") + unquoted("foo")` → `{"a":"-foo"}`. Spec → lex error (`-` not followed by digit).
- **`a = -`** (us03): Lightbend → `unquoted("-")` → `{"a":"-"}`. Spec → lex error.
- **`a = 01`** (us13): Lightbend → `Long.parseLong("01") = 1` → `{"a":1}`. Spec → `number(0) + unquoted("1")` → `{"a":"01"}` (strict JSON-number grammar forbids leading zeros on non-zero ints; only `0` alone is a valid leading-zero number).

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST implement the strict spec algorithm — leading `0-9` triggers number-lex (always succeeds with ≥1 digit); leading `-` triggers number-lex with required digit (lex error if absent, NO fallback to unquoted). Number lex uses greedy-with-backtrack-to-last-valid-prefix per the algorithm in the design spec (`docs/superpowers/specs/2026-05-17-s8-unquoted-starts-design.md` §HOCON number grammar).

**Why an E-item rather than an S-item**: HOCON.md L270-276 IS the canonical spec rule; the matrix already marks S8.6 as ❌ in all 3 impls. E8 tracks the *specific Lightbend divergence pattern* and the per-fixture treatment, parallel to E5 for the S10.13 ce05 quirk. The S8.6 matrix row will flip ❌→✅ as each impl PR merges.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ❌ | `unquoted-starts/us02`, `us03`, `us13` loaded by per-impl test (no xx.hocon `.error` sidecar; Lightbend doesn't throw) | Will flip to ✅ after Phase 6 #3c impl PR merges; lexer removes the unquoted-fallback for leading `-` and emits strict `number(0) + unquoted("1")` for `01` |
| rs.hocon | ❌ | same fixtures | Will flip to ✅ after Phase 6 #3c impl PR merges |
| go.hocon | ❌ | same fixtures | Will flip to ✅ after Phase 6 #3c impl PR merges |

**Fixtures**: `testdata/hocon/unquoted-starts/us02-hyphen-no-digit.conf`, `us03-hyphen-alone.conf`, `us13-leading-zero.conf`. No `.error` sidecars (Lightbend silent-accept; see `fixture-conventions.md` "Lightbend quirks"). The remaining S8.6 fixtures (us01, us04-us12, us14, us16) are Lightbend-value-layer-equivalent and live in `SUCCESS_CONFS`; us15 (`a = 1e+x`) errors in both Lightbend and strict spec and lives in `SIDECAR_ERROR_CONFS`.

<a id="e9"></a>

### E9 — `include` reserved at the start of a key path (Lightbend tolerates dotted-include silently)

**Source**: cross-impl convention. HOCON.md §Path expressions L570-572 states:

> As a special rule, the unquoted string `include` may not begin a path expression in a key, because it has a special interpretation (see below).

Lightbend 1.4.3 enforces this rule only for the bare form (`include = 1`, `include : 1`, `include += [1]`, `include { x = 1 }`, and forms where text after `include` is not a valid include argument such as `include\nfoo.conf`). For the dotted form, the tokenizer joins `include.foo` into a single unquoted token before the parser can apply the reservation rule, then PathParser splits it later — by which time `isIncludeKeyword` (which matches only the bare 7-char `include`) has already passed. Result: `include.foo = 1` and `a = { include.bar = 1 }` are silently accepted as regular nested keys.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST reject ALL forms where the first parsed path element equals the bare unquoted string `include`, including the dotted form. Detection runs post-PathParser on the constructed element list — uniform for both `include = 1` (one token) and `include.foo = 1` (one joined token, post-split into `["include", "foo"]`). Quoted-form `"include"` bypasses the reservation. Substitution paths (`${include}`, `${include.foo}`) are NOT subject to the reservation (Codex-verified; the L570 rule says "begin a path expression in a key", which substitutions are not).

**Why an E-item rather than an S-item**: HOCON.md L570 IS the canonical spec rule; the matrix already marks S12.5 as ❌ in all 3 impls. E9 documents the specific Lightbend divergence pattern (silent accept for dotted-include) and the per-fixture treatment, parallel to E5 and E8.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ✅ | `include-reservation/ir03-include-dot-foo-equals.conf` + `ir04-include-nested-object.conf` loaded by `tests/include-reservation.test.ts` with both in the per-impl `IMPL_OVERRIDE_ERRORS` list (no xx.hocon `.error` sidecar; Lightbend doesn't throw) | Flipped ✅ via [ts.hocon#102](https://github.com/o3co/ts.hocon/pull/102) (Phase 6 #3e); `parseObject()` checks first parsed path element post-PathParser with `firstWasQuoted` provenance from `parseKey()` private return-type refactor |
| rs.hocon | ✅ | `include-reservation/ir03-include-dot-foo-equals.conf` + `ir04-include-nested-object.conf` loaded by `tests/include_reservation_test.rs` with both in `KNOWN_LIGHTBEND_QUIRKS` + dedicated `_per_impl` override tests | Flipped ✅ via [rs.hocon#90](https://github.com/o3co/rs.hocon/pull/90) (Phase 6 #3e); `parse_object()` snaps `first_key_is_quoted` before `parse_key()` advances, then checks `!first_key_is_quoted && key[0] == "include"` post-return |
| go.hocon | ✅ | `include-reservation/ir03-include-dot-foo-equals.conf` + `ir04-include-nested-object.conf` loaded by `internal/parser/include_reservation_test.go` with `implErrors` per-impl override map | Flipped ✅ via [go.hocon#88](https://github.com/o3co/go.hocon/pull/88) (Phase 6 #3e); `parseField` captures `firstTokenIsQuoted` + `firstTokenType` before `parseKey`, checks `key[0] == "include" && !firstTokenIsQuoted && firstTokenType == lexer.TokenString` post-return |

**Fixtures**: `testdata/hocon/include-reservation/ir03-include-dot-foo-equals.conf`, `ir04-include-nested-object.conf`. No `.error` sidecars (Lightbend silent-accept; see `fixture-conventions.md` "Lightbend quirks"). The remaining S12.5 negative fixtures (ir01, ir02, ir10, ir12, ir13) live in `SIDECAR_ERROR_CONFS` with generator-produced `.error` sidecars (Lightbend's include-statement parser rejects each).

### E10 — Empty file is invalid (Lightbend silently accepts as `{}`)

**Source**: cross-impl convention. HOCON.md §Omit root braces L130-132 states:

> Empty files are invalid documents, as are files containing only a non-array non-object value such as a string.

Lightbend 1.4.3 silently accepts empty input as `SimpleConfigObject({})`. Verified: `ConfigFactory.parseString("")`, `parseString("   ")`, `parseString("\n\n")`, `parseString("# only comment\n")`, `parseString("﻿")` (BOM only), `parseString("  # x \n  \n")` — all return empty config, no exception.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST reject empty documents at the parser entry. "Empty" includes any combination of whitespace, newlines, BOM, and comments with no other content. Detection runs post-tokenize on the token stream, excluding skip-tokens (EOF, Newline, and any whitespace/comment tokens the lexer emits) — uniform across all "comments-only" / "whitespace-only" / "BOM-only" variants.

**Why an E-item rather than purely S12.5-style**: HOCON.md L130 IS the canonical spec rule; the matrix marks S3.1 as ❌/⚠️/❌ in ts/rs/go. E10 documents the specific Lightbend divergence pattern (silent accept) and the per-fixture treatment, parallel to E5/E8/E9.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ❌ → ✅ | `empty-file/ef01–ef06.conf` loaded by `tests/conformance/empty-file.test.ts` with per-impl `IMPL_OVERRIDE_ERRORS` (no xx.hocon `.error` sidecar; Lightbend doesn't throw) | (cluster 3h target) Empty-check inside `buildResolveContext()` after `tokenize()`, before `parseTokens()` |
| rs.hocon | ⚠️ → ✅ | `empty-file/ef01–ef06.conf` loaded by `tests/conformance_empty_file.rs` with per-impl override | (cluster 3h target) Empty-token-stream check at `lib.rs::parse_with_env` after `tokenize()` |
| go.hocon | ❌ → ✅ | `empty-file/ef01–ef06.conf` loaded by `s3_1_empty_file_test.go` with per-impl override | (cluster 3h target) Empty-content check inside `internal/parser/parser.go:Parse()` |

**Fixtures**: `testdata/hocon/empty-file/ef01-empty.conf`, `ef02-whitespace-only.conf`, `ef03-newlines-only.conf`, `ef04-comment-only.conf`, `ef05-bom-only.conf`, `ef06-mixed-ws-comment.conf`. All 6 ship `-expected.json` sidecars containing `{}` (Lightbend's silent-accept output). Per-impl conformance tests apply the override list to assert error.

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
2026-05-17 — E8 (S8.6 strict unquoted-starts; Lightbend fallback divergences for us02/us03/us13) added as part of Phase 6 #3c.
2026-05-17 — E9 (`include` reserved at start of key path; Lightbend silent-accept for dotted form ir03/ir04) added as part of Phase 6 #3e.
2026-05-18 — ev12a/ev12b/ev13 follow-up fixtures landed (Phase 6 #3g). ev12a/ev12b are the canonical pins for S13c.5 (list suffix suppresses scalar-env fallback — the resolver must NOT consult bare scalar `X` when `listSuffix=true` and no `X_0` is present). ev13 isolates the optional-list-with-elements path (`${?X[]}` as entire value, not inside concat). These complement the ev01-ev11 set; no new E-items introduced (ev12 covers a spec-normative behavior, not an extra-spec convention).
2026-05-18 — E9 flipped ❌ → ✅ in all 3 impls after Phase 6 #3e impl PRs landed ([ts.hocon#102](https://github.com/o3co/ts.hocon/pull/102), [rs.hocon#90](https://github.com/o3co/rs.hocon/pull/90), [go.hocon#88](https://github.com/o3co/go.hocon/pull/88)). Each impl rejects `include.foo = 1` (and `a = { include.bar = 1 }`) via its post-PathParser path-element check on the constructed key list, with quoted-vs-unquoted provenance captured locally in the field-parser before `parseKey` / `parse_key` advances the cursor. The ir03/ir04 fixtures remain without xx.hocon `.error` sidecars — Lightbend's tokenizer joins `include.foo` into a single unquoted token before `isIncludeKeyword` runs, so it silently accepts. Each impl's conformance test maintains a per-impl override list (`IMPL_OVERRIDE_ERRORS` in ts, `KNOWN_LIGHTBEND_QUIRKS` in rs, `implErrors` in go) pinning ir03/ir04 to error. **Side-clear**: go.hocon S14a.10 (#80) also flipped ❌ → ✅ via the same PR — `parseInclude` now requires the include argument to be a quoted TokenString; unquoted `include foo.conf` raises `*parser.Error`. Spec-aligned error-message split applied in go.hocon (S14a.10 case → "include argument must be a quoted string"; S12.5 case → "'include' is reserved as a key name") per Claude multi-agent-review Important finding fixed in-PR (commit 7b5c80a). ts/rs were already ✅ on S14a.10 pre-fix so their flips are S12.5-only.
2026-05-18 — E5 flipped ❌ → ✅ in all 3 impls after Phase 6 #3b impl PRs landed ([ts.hocon#101](https://github.com/o3co/ts.hocon/pull/101), [rs.hocon#89](https://github.com/o3co/rs.hocon/pull/89), [go.hocon#87](https://github.com/o3co/go.hocon/pull/87)). Each impl rejects the `{object} scalar`, `scalar {object}`, `[array] scalar`, `scalar [array]` constructs via its pairwise-fold `joinPair` / `join_pair` type-mismatch branch — same code path that closes S10.4/S10.13/S10.19 in the canonical matrix. go.hocon merge is **BREAKING** (prior permissive `[1, 2] 3 → [1, 2, 3]` removed); ts and rs were already error on the construct pre-fix so their flips are non-breaking. The ce05 fixture remains without an xx.hocon `.error` sidecar — Lightbend silently accepts the construct, so the generator omits the sidecar; each impl's conformance test maintains a per-impl override list pinning ce05 (and other E-marked Lightbend-quirk fixtures) to error.
2026-05-18 — E6 and E7 flipped 🤷 → ✅ in all 3 impls after Phase 6 #3g impl PRs landed ([ts.hocon#100](https://github.com/o3co/ts.hocon/pull/100), [rs.hocon#88](https://github.com/o3co/rs.hocon/pull/88), [go.hocon#86](https://github.com/o3co/go.hocon/pull/86)). E7 enforcement narrowed via multi-agent-review (I2 fix): only ASCII space (0x20) / tab (0x09) accepted between path and `[]`; broader Unicode whitespace (NBSP, CR, Zs) errors. E6 known limitation (cross-impl: include-scope `${X[]}` does not fall back to original-path config) tracked at [xx.hocon#22](https://github.com/o3co/xx.hocon/issues/22) — Copilot review on go.hocon#86 surfaced the issue; cross-impl scope (also affects ts + rs).
