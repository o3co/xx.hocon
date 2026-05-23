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

### E8 — Leading `-` / digit handling: strict at value-start, pragmatic at concat-continuation (aligned with Lightbend)

**Source**: cross-impl convention, aligned with the spec-author reference implementation (Lightbend 1.4.3). HOCON.md §Unquoted strings L270-276 states:

> An unquoted string may not *begin* with the digits 0-9 or with a hyphen (`-`, 0x002D) because those are valid characters to begin a JSON number. The initial number character, plus any valid-in-JSON number characters that follow it, must be parsed as a number value.

**Reading of L270-276 "begin"**: read as *value-position begin* (the first component of a concatenation), not as *token-begin at any lexer position*. Justifications:

1. **Reference-implementation disambiguation rule**: Lightbend at value-start enters number-lex only when `-` is followed by a digit; otherwise `-` is treated as the start of an unquoted run. The quoted HOCON text names `-` as "a valid character to begin a JSON number" (consistent with RFC 8259's `number = [minus] int [frac] [exp]` production), but a bare `-` or `-foo` is not a *complete* JSON-number prefix, so Lightbend's disambiguator does not classify it as a number-lex entry point. This is the empirically-observed behavior across the probe matrix (F1/F2).
2. **Concatenation continuation**: when a value-token (`${...}`, `"..."`, a prior unquoted run, etc.) has already been emitted, the next unquoted run is a *continuation of the same value*, not the *begin* of a new unquoted string. L270's "begin" applies to the start of a value-position unquoted run, not to every lexer offset within a concatenation.
3. **Reference-implementation authority**: HOCON.md and Lightbend's reference implementation are maintained by the same project. Where xx.hocon's prior strict reading and Lightbend's pragmatic reading both derive from the spec text, we adopt the reference-implementation reading.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST implement the following behavior, which matches Lightbend 1.4.3 empirically (probe matrix at [`generate/src/main/java/ProbeIssue31.java`](../generate/src/main/java/ProbeIssue31.java), groups A–F):

| Position | Leading character | Behavior |
| --- | --- | --- |
| **value-start** | digit `0-9` | greedy number-lex via Java numeric semantics: `Long.parseLong` first, `Double.parseDouble` second; fall back to unquoted text on parse failure. `01` → `1` (number, not `"01"` string). |
| **value-start** | `-` followed by digit | greedy number-lex as above (e.g. `-1`, `-0.5`). |
| **value-start** | `-` not followed by digit (`-foo`, lone `-`) | emit as unquoted text (no number-lex attempt, since not a valid JSON-number prefix). |
| **value-start** | `+` | **reject** — HOCON `+=` operator reservation, distinct from number-lex. |
| **concat-continuation** (after `${...}`, `"..."`, prior unquoted, etc.) | any unquoted-permissible character except `+` | continue the unquoted run; no number-lex attempted. `${a}-bar` → `"foo-bar"`. Characters that would terminate an unquoted token (`}`, `]`, `,`, `=`, `:`, whitespace, comment markers, etc.) still terminate it as usual. |
| **concat-continuation** | `+` | **reject** (same operator-reservation reason as value-start). |

**Notes on characters not listed above**: characters outside L270's `0-9` / `-` / `+` set (e.g. `.`, `_`, letters) follow the spec's normal unquoted-string rules in both positions. Example: `a = .5` → `".5"` (string, since `.` is not in L270's disallow list); `${a}.bar` → `"foo.bar"` (concat-continuation extends with `.bar`).

**Value-start number-lex algorithm** (informative; matches Lightbend `Tokenizer.pullNumber`):

1. **Eligibility**: attempt number-lex only if the first character is `0-9` or `-` followed by a digit. `+` is rejected up front (operator reservation).
2. **Greedy consume**: consume the maximal run of characters in the class `[0-9.eE+-]` (Lightbend's `pullNumber` character set; adopted for compatibility).
3. **Parse**: try `Long.parseLong` first, then `Double.parseDouble`, on the consumed run. On success, emit a number token.
4. **Parse-failure handling**:
    - If the consumed run contains `+`, emit a reserved-character error (matches `us15` `a = 1e+x`: consumes `1e+x`, parsing fails, `+` triggers reserved-character error — does *not* fall back to unquoted).
    - Otherwise, emit the consumed characters as the start of an unquoted run and continue lexing in the unquoted state (matches `a = 1.2.3` → unquoted `"1.2.3"`).

The side effect: `+` in number-lex position is always an error (either rejected by eligibility, or by parse-failure handling). The same `+` rejection applies in concat-continuation position per the behavior table.

**History**: E8 was originally added 2026-05-17 with a strict reading — leading `-` always errored when no digit followed, and `01` lexed as `number(0) + unquoted("1")` → `"01"` string. External issue [#31](https://github.com/o3co/xx.hocon/issues/31) (2026-05-20, reported by @cgordon) surfaced the `b = ${a}-bar` case, which the strict reading rejected by extending the same `-` rule into concat-continuation positions. Investigation (recorded in the issue reply and the probe matrix) determined this is a spec-interpretation difference, not a Lightbend divergence to preserve. E8 was rewritten 2026-05-20 to adopt the reference-implementation reading; us02/us03/us13 fixtures moved from per-impl error overrides to `SUCCESS_CONFS` with `-expected.json`.

**Why an E-item rather than (just) an S-item**: HOCON.md L270-276 is the canonical spec rule (S8.6 in [`spec-checklist.md`](spec-checklist.md)). E8 records (a) the project's chosen reading of "begin" (value-position vs token-position), (b) the concat-continuation handling rule which HOCON.md does not explicitly enumerate, and (c) the `+` exception (HOCON operator reservation, not number-lex). The S8.6 matrix row will flip ❌→✅ as each impl PR merges this amendment.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ❌ → ✅ | `unquoted-starts/us02`, `us03`, `us13` move to `SUCCESS_CONFS` with `-expected.json`; new concat-continuation fixtures `us17`–`us30` (covering probe matrix groups A/B/D/E) | Lexer removes strict-no-fallback for leading `-`, switches leading-zero handling to Java numeric semantics, retains `+` rejection. Allows concat-continuation runs after value-tokens. |
| rs.hocon | ❌ → ✅ | same fixtures | Same lexer changes. |
| go.hocon | ❌ → ✅ | same fixtures | Same lexer changes. |

**Fixtures**:

- **Existing (move to `SUCCESS_CONFS`)**: `testdata/hocon/unquoted-starts/us02-hyphen-no-digit.conf` → `{"a":"-foo"}`, `us03-hyphen-alone.conf` → `{"a":"-"}`, `us13-leading-zero.conf` → `{"a":1}`. All gain `-expected.json` sidecars reflecting Lightbend output.
- **New (concat-continuation, added in this amendment as `us17`–`us30`)**: cases from probe groups A/B/D/E — `${a}-bar`, `${a}-`, `${a}--bar`, `${a}-1`, `${a}1bar`, `${a}.bar`, `${a}_bar`, `"foo"-bar`, `"foo".bar`, `"foo"1bar`, `${a}-${a}`, `${a}-${b}`, `foo-${a}`, `"foo"-${a}`. All in `SUCCESS_CONFS` with `-expected.json` matching Lightbend output.
- **`+` rejection (unchanged)**: any `+` in a value position outside `+=` (e.g. probe cases A8 `${a}+bar`, F5 `a = +foo`) remains an error; new error fixtures may be added if not already covered by existing `+=`-related tests.

**Note on cross-file consistency**: the migration of us02/us03/us13 to `SUCCESS_CONFS` requires synchronized updates to [`fixture-conventions.md`](fixture-conventions.md) (the "us02 / us03 / us13 (cluster 3c)" section under "Lightbend quirks") and [`generate/src/main/java/GenerateExpected.java`](../generate/src/main/java/GenerateExpected.java) (the `SUCCESS_CONFS` array + the related code comments). Both currently document the prior strict-reject treatment. These updates ship in the same PR as this E8 rewrite to avoid a divergent intermediate state.

**Downstream BREAKING note**: the F3 change (`a = 01` → `1` number, was `"01"` string) is a value-type-change for the specific leading-zero literal case. All other changes are additive (expand the set of inputs that parse successfully). Each impl PR should call this out in its CHANGELOG / release notes.

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

<a id="e10"></a>

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

<a id="e11"></a>

### E11 — `include package(...)` qualifier: package-scoped include resolver (service-locator pattern)

**Source**: spec extension agreed by cross-impl convention. HOCON.md does not enumerate include qualifiers beyond `file(...)`, `url(...)`, and `classpath(...)`. `classpath(...)` is a JVM-host-specific qualifier (it relies on JVM classpath resource scanning), with no defined behavior in non-JVM HOCON parsers. E11 introduces a new host-neutral qualifier `package("<identifier>", "<file>")` for the non-JVM HOCON ecosystem, where compile-time embedding (Go `//go:embed`, Rust `include_str!`) or runtime module resolution (Node `require.resolve`) is the only way to ship and locate `.conf` files alongside library packages.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST recognize a new include qualifier `package("<identifier>", "<file>")` with the following abstract semantics:

- A `package(...)` include resolves to a HOCON document body via an **impl-provided lookup**. The lookup maps the pair `(identifier, file)` to a string of HOCON source text (the "registered content").
- The resolved string is then parsed as a HOCON document and merged into the including object per existing include semantics (HOCON.md §Includes).
- The **concrete lookup mechanism is impl-defined** (see per-impl table below). Some impls maintain an explicit registry populated by the package's own code; others delegate to host-provided module resolution. The abstract `(identifier, file) -> content` contract is what's normative; the surface API and lifetime are per-impl.

Closest existing analogy: JVM `ServiceLoader` / JNDI, **not** classpath. The qualifier does NOT auto-discover packages, does NOT auto-merge `reference.conf` files (no `ConfigFactory.load()` equivalent), and does NOT auto-resolve transitive packages.

**Why an E-item rather than an S-item**: this is a project-introduced extension to HOCON syntax, not present in HOCON.md. By policy these live in the E-prefix namespace. Conceptually parallel to `classpath(...)` but with host-neutral, service-locator semantics in place of JVM resource scanning.

#### Spec decisions

The following decisions are normative for all three impls:

1. **Identifier shape — interoperability convention**: identifiers used in *portable* `.conf` files (intended to work across ts.hocon / go.hocon / rs.hocon) SHOULD be Go-module-path-style strings `<host>/<org>/<name>` (e.g., `github.com/o3co/auth`). Parsers MUST NOT validate identifier shape beyond requiring it to be a non-empty HOCON string — identifiers are opaque registry keys at the parser layer. Rationale: a parser-level validator is hard to specify exactly across language ecosystems (npm scopes, internal naming, private registries), and would block legitimate impl-local use cases; cross-impl portability of `.conf` files is a *.conf-author* responsibility enforced by convention. TS-only `.conf` files MAY use npm-style names (`@scope/foo`) when the application is not intended to be cross-impl portable.

2. **Two-arg form mandatory**: only `package("identifier", "file")` (two string arguments) is recognized. The one-arg form `package("identifier/file")` MUST be rejected as a parse error. Rationale: avoids longest-prefix-match ambiguity at the registry layer (e.g., distinguishing `github.com/o3co/auth` from `github.com/o3co/auth.policy`); the two-arg signature matches the multi-arg style HOCON already uses for `include required(...)` constructs and is unambiguous to tokenize.

3. **Collision policy** (impl-scoped): for impls that maintain an explicit registry (go.hocon, rs.hocon), registering two **different** contents under the same `(identifier, file)` key MUST raise an error at registration time. Re-registering **byte-identical** content under the same key is allowed (idempotent — common during test re-init or hot-reload). For impls that delegate to host module resolution (ts.hocon via `require.resolve`), collision is resolved by the host's module resolution algorithm — ts.hocon does NOT detect collisions and app authors MUST NOT install conflicting packages. Rationale: silent last-wins in explicit registries creates debugging nightmares when package load order affects effective config; explicit failure surfaces the conflict at the source.

4. **Lookup failure**: hard error at parse time. Encountering `include package("X", "Y")` where `("X", "Y")` cannot be resolved (registry miss in go.hocon / rs.hocon; `require.resolve` throws in ts.hocon) MUST raise a clear error indicating the missing entry (and, in Go's case, hinting at the likely-missing `_ "..."` import). No silent empty-include fallback. Rationale: forgotten registrations / missing dependencies should surface at the include site, not silently produce broken downstream config — this is also the lesson taken from `gurkankaymak/hocon` parsing `classpath(...)` as a silent filesystem fallback.

   **Note on empty content**: registered-but-empty content (zero bytes, or content that parses to an empty HOCON document) is NOT a lookup failure. It succeeds and contributes an empty object (`{}`) to the merge, consistent with HOCON's existing handling of empty include targets.

5. **Identifier and file equality**: both `<identifier>` and `<file>` arguments are compared **byte-exact, case-sensitive**, after HOCON string unescaping. Implementations MUST NOT apply Unicode normalization (NFC/NFD/NFKC/NFKD), case-folding, or path-separator canonicalization. Rationale: prevents cross-host divergence (Node, Cargo, and Go modules each have different default normalization stances); makes registry behavior predictable; matches how HOCON itself treats string keys elsewhere.

6. **File argument constraints**: the `<file>` argument MUST satisfy the following at the parser layer (any violation is a parse error):
   - non-empty string
   - forward-slash separators only (backslash `\` rejected)
   - no leading `/` (absolute paths rejected)
   - no `.` or `..` segments (path traversal rejected)
   - no consecutive `/` (e.g., `a//b.conf` rejected)
   - no implicit percent-decoding at the parser layer

   Allowed examples: `reference.conf`, `conf/reference.conf`, `subdir/nested/x.conf`.
   Rejected examples: `""`, `/abs/path.conf`, `../escape.conf`, `./x.conf`, `conf\\x.conf`, `conf//x.conf`, `conf/./x.conf`.

   Rationale: prevents host-asymmetric behavior between ts.hocon (which concatenates `identifier + "/" + file` into a module-resolution path) and go.hocon / rs.hocon (which use the pair as opaque registry keys). Forbids traversal patterns that could escape package boundaries in the TS host-resolution case.

7. **Required form**: `include required(package("X", "Y"))` is supported and follows existing `required(...)` semantics — a lookup failure in this position is always an error regardless of any future "optional include" toggle.

8. **Cycle detection**: `include package(...)` participates in the same include-cycle detection as `include file(...)` / `include url(...)`. The cycle-detection key for a `package(...)` include MUST include the qualifier kind plus the byte-exact pair, i.e., `("package", <identifier>, <file>)`. Self-includes and mutual-includes via `package(...)` MUST raise an include-cycle error consistent with existing include semantics.

#### Non-goals (explicit)

The following are out of scope for E11 and are NOT to be inferred from the qualifier's name:

- **Auto-discovery of packages**: no implementation scans the installed package set on its own initiative. Resolution requires either (a) explicit registration (go.hocon, rs.hocon) or (b) the host's existing module-resolution surface (ts.hocon via Node). In all three cases, only packages reachable by these mechanisms can be resolved; the parser does not enumerate.
- **Auto `reference.conf` merge**: no `ConfigFactory.load()` equivalent. Each `include package(...)` is its own explicit include statement in the consuming `.conf`.
- **Transitive auto-resolution**: packages that include other packages' configs MUST cascade-register manually. A `register()` in Rust calling `dep_pkg::register()` is the recommended convention; in Go, transitive `_ "..."` imports propagate naturally via Go's import side-effects.
- **Wildcard / glob lookups**: `include package("X", "*.conf")` or `include package("X", "conf/*")` is not supported. Each include statement targets exactly one registered file.
- **Identifier shape validation at parse time**: see decision 1 — parser does not enforce Go-module-path syntax.

| Impl | Status | Lookup mechanism | Registration / population | Notes |
| --- | --- | --- | --- | --- |
| ts.hocon | 🤷 | Host module resolution via Node `require.resolve(identifier + "/" + file, { paths: [path.dirname(includingConfFile)] })` by default | Implicit: any package whose tree contains the file at the resolved path is reachable. No explicit HOCON-level registration. App may override starting `paths` via parser option. | Collision semantics deferred to Node's module resolution (decision 3). Bundler/edge runtimes where `require.resolve` does not work raise a clear runtime error. |
| rs.hocon | 🤷 | Explicit per-parser registry (`HashMap<(Identifier, File), String>`) populated by builder methods | `Parser::register_package(identifier, file, content)` called explicitly with `include_str!`-loaded content. Cascade convention for transitive deps: `pkg_a::register(p)` internally calls `pkg_b::register(p)`. | Registry lifetime = parser instance. Multiple parsers do not share registry. Test isolation trivial. |
| go.hocon | 🤷 | Global registry (process-wide), parallel to `database/sql.Register` | `hocon.RegisterPackage(identifier, file, content)` called from the providing package's `init()` after `//go:embed`. App side: `_ "<module-path>"` import triggers `init()`. Test helper `hocon.ResetPackageRegistry()` provided for test isolation. | Registry lifetime = process. App authors must include `_ "..."` imports for each config-providing dep. Idempotent re-registration of byte-equal content allowed (decision 3). |

**Fixtures** (planned, to be added under `testdata/hocon/include-package/`):

- `ipk01-basic.conf` — happy-path: `include package("github.com/example/lib", "reference.conf")` with registry populated.
- `ipk02-one-arg-rejected.conf` — `include package("github.com/example/lib/reference.conf")` (one-arg) → parse error (decision 2).
- `ipk03-collision.conf` — sibling test setup registers same `(id, file)` key twice with different content → registration-time error (decision 3; go.hocon + rs.hocon only — n/a for ts.hocon per per-impl override).
- `ipk04-lookup-miss.conf` — `include package("github.com/example/missing", "x.conf")` with empty registry → parse error (decision 4).
- `ipk05-required-miss.conf` — `include required(package("github.com/example/missing", "x.conf"))` → parse error (decision 7).
- `ipk06-byte-exact-id-case.conf` — register `Foo/Bar` then `include package("foo/bar", "x.conf")` → lookup miss (decision 5: case-sensitive identifier).
- `ipk07-byte-exact-file-case.conf` — register `Reference.conf` then `include package("github.com/example/lib", "reference.conf")` → lookup miss (decision 5: case-sensitive file).
- `ipk08-empty-content.conf` — register `("github.com/example/lib", "empty.conf")` with empty string content, `include package(...)` succeeds and merges `{}` (decision 4 note).
- `ipk09-file-empty.conf` — `include package("foo", "")` → parse error (decision 6).
- `ipk10-file-absolute.conf` — `include package("foo", "/etc/passwd")` → parse error (decision 6).
- `ipk11-file-traversal.conf` — `include package("foo", "../escape.conf")` → parse error (decision 6).
- `ipk12-file-backslash.conf` — `include package("foo", "x\\y.conf")` → parse error (decision 6).
- `ipk13-cycle-self.conf` — register `("foo", "x.conf")` whose content body is `include package("foo", "x.conf")` → include-cycle error (decision 8).
- `ipk14-cycle-mutual.conf` — register `("foo", "a.conf")` including `("foo", "b.conf")` and vice versa → include-cycle error (decision 8).

No Lightbend `.error` sidecars (Lightbend has no concept of `package(...)`) — per-impl conformance tests use per-impl override lists, following the `IMPL_OVERRIDE_ERRORS` / `KNOWN_LIGHTBEND_QUIRKS` / `implErrors` pattern established by E5 / E9 / E10. ts.hocon overrides exempt `ipk03` (collision not detected — see decision 3).

**Prior art** (non-JVM HOCON parsers, surveyed 2026-05-20):

- `go-akka/configuration` exposes an `IncludeCallback(filename string) -> *HoconRoot` parser option — the closest existing hook, but at a lower abstraction level (raw callback rather than typed package registry). The tokenizer accepts only bare `include "..."` (no qualifier syntax), so `package(...)` cannot be layered on top without a tokenizer-level addition.
- `gurkankaymak/hocon` parses `classpath(...)` syntactically but silently treats the inner path as a filesystem path. This is the anti-pattern E11 spec decision 4 explicitly rejects: accepting a qualifier syntax without delivering its semantics produces runtime surprises.
- `mockersf/hocon.rs` (Rust) has no pluggable include resolution; resolution is fully internal. Repository is archived/unmaintained.
- No existing non-JVM HOCON parser implements a `package(...)`-equivalent qualifier. E11 establishes the first such convention in the cross-impl o3co stack.

**Tracking issue**: [#33](https://github.com/o3co/xx.hocon/issues/33).

<a id="e12"></a>

### E12 — Deferred substitution resolution: Lightbend-aligned `parse / withFallback / resolve()` lifecycle

**Source**: external request + Lightbend-parity convention. HOCON.md L130–L240 (object merge) and L660–L720 (substitutions) describe substitution and merge semantics but do NOT prescribe the public API lifecycle through which callers invoke them. Lightbend Java exposes `ConfigFactory.parseString(s)` returning an **unresolved** `Config`, followed by explicit `Config.withFallback(...)` composition and `Config.resolve(...)` resolution as distinct operations (with `ConfigFactory.defaultReferenceUnresolved()` existing specifically for callers that want to defer resolution). The three impls deviated by fusing parse-and-resolve into a single call (`ParseString` / `parseString` / `parse`), which prevents callers from composing programmatic fallback layers between parse and resolve. External request [o3co/go.hocon#99](https://github.com/o3co/go.hocon/issues/99) (cgordon) surfaced this with a concrete CI use case where `${CI_RUN_NUMBER}` cannot be parsed because the value is only available from a programmatic fallback layer.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST expose a public API that allows the canonical Lightbend lifecycle:

1. **Parse without resolving** — `ParseStringWithOptions(input, ParseOptions{ResolveSubstitutions: false})` (or the language-idiomatic equivalent) returns a `Config` whose `IsResolved()` is `false` if the input contains any unresolved `${...}` substitutions.
2. **Compose fallback layers** — `c.WithFallback(other)` accepts both resolved and unresolved operands. Substitution placeholders survive merge unchanged.
3. **Resolve explicitly** — `c.Resolve(ResolveOptions{...})` performs a single top-level resolve operation over the entire fallback stack, with options for hermetic resolution (`UseSystemEnvironment=false`) and partial resolution (`AllowUnresolved=true`).
4. **Construct configs from in-memory data** — `FromMap(values, originDescription)` produces a `Config` from a plain-key map for use as a fallback layer.

The existing `ParseString` / `parseString` / `parse` (no options) entry points remain as parse-and-resolve convenience wrappers and produce identical results to `ParseStringWithOptions(..., {ResolveSubstitutions: true})` — backward compatibility is preserved.

**Why an E-item rather than an S-item**: HOCON.md is silent on API surface. The lifecycle separation is an *interface* convention, not a behavioral spec item — there is no S-item it clarifies. Conceptually parallel to E11: a project-introduced cross-impl convention. Cross-spec *behavioral* interactions (s13a, s10) ARE clarified by E12 — see § "Cross-spec interactions" below — but those clarifications are anchored in E12 because they describe the spec items' behavior *under the new lifecycle*, not at parse time alone.

#### Spec decisions

The following decisions are normative for all three impls:

1. **Existing `ParseString` / `ParseFile` behavior preserved** for backward compatibility. They continue to parse and resolve in one shot. A future major version MAY flip this default to align with Lightbend's parse-only-by-default semantics; out of scope for v1. Rationale: cross-impl release cadence requires minor (not major) bump for E12; preserving existing parse-and-resolve avoids forcing downstream code changes.

2. **`ParseOptions` v1 fields**: `ResolveSubstitutions: bool` (default `true`) and `OriginDescription: string` (default empty). Other Lightbend `ConfigParseOptions` fields (`setSyntax`, `setAllowMissing`, `setIncluder`, `setClassLoader`) are deferred to follow-on. Rationale: minimal v1 scope; defaults match Lightbend semantics for the included fields.

3. **`ResolveOptions` v1 fields**: `UseSystemEnvironment: bool` (default `true`, Lightbend default) and `AllowUnresolved: bool` (default `false`, Lightbend default). Custom resolver chain (Lightbend `ConfigResolveOptions.appendResolver`) is deferred. Rationale: covers issue #99's hermetic-resolve and partial-resolution needs; custom-resolver is a Lightbend 1.3.2+ feature with no current cross-impl demand.

4. **Options encoding per language**: each impl uses its idiomatic pattern. **Go**: builder pattern with `DefaultParseOptions()` / `DefaultResolveOptions()` factory functions + chainable `WithX()` setter methods. **`ParseOptions{}` zero-value literal is NOT valid invocation** (would mean `ResolveSubstitutions=false` and `UseSystemEnvironment=false`, contradicting Lightbend defaults). **TypeScript**: `Partial<ParseOptions>` interface — omitted fields take Lightbend defaults. **Rust**: `Default` impl returning Lightbend defaults + chainable builder methods (`.with_resolve_substitutions(false)`, etc.). The hard cross-impl constraint: an invocation equivalent to "use all defaults" MUST produce Lightbend default behavior without requiring the caller to set anything.

5. **`WithFallback` accepts unresolved operands**. Existing semantic for resolved operands is preserved (deep merge with receiver-wins precedence). New behavior: when either operand is unresolved, the merge operates at the unresolved-tree level — substitution placeholders survive into the merged tree. The result is unresolved iff either operand is unresolved. Non-object values do not merge: `obj.WithFallback(nonObj).WithFallback(otherObj)` ignores `otherObj` per Lightbend `ConfigMergeable` semantics (HOCON.md §Merge L191-L207). Rationale: required for the issue #99 use case; preserves Lightbend parity.

6. **Single-pass, transitively-recursive resolution over the fallback stack**: `Resolve()` performs **one top-level resolve operation** over the entire merged fallback stack, with **transitive recursive substitution evaluation**. A literal "single tree walk" is not required; what matters is that `a = ${b}; b = ${c}; c = 1` resolves `a` to `1` (not `${b}`), regardless of whether the chain crosses fallback layers. Rationale: Lightbend documents this as "ideally resolve once on the full stack"; transitive resolution is implicit in HOCON.md §Substitutions but explicit here to prevent impl divergence.

7. **Hidden substitutions are not evaluated**. Substitutions in *overridden* values (HOCON.md §Substitutions L670–L703) MUST be discarded before resolution. `foo = ${nonexist}\nfoo = 42` resolves to `{foo: 42}` without error. Across fallback layers, the receiver's keys win — fallback substitutions hidden by the receiver are dropped. Rationale: HOCON-spec compliance; an impl that evaluates hidden substitutions would fail valid Lightbend-produced configs.

8. **Cross-layer cycle detection**: substitution cycles that emerge only after `WithFallback` (e.g., receiver `a=${b}`, fallback `b=${a}`) MUST be detected at resolve time. The algorithm is impl-internal; the conformance requirement is the outcome. Rationale: single-source cycles are already covered by S13a.6-9; this clarifies that cycle detection extends to merged trees.

9. **`ResolveWith(source)` is distinct from `WithFallback(source).Resolve()`**: substitutions in the receiver are looked up in `source`, but `source`'s keys are NOT merged into the result. Conformance: MUST in go.hocon (where issue #99 was filed); MAY in ts.hocon / rs.hocon v1 (follow-on PR acceptable). Rationale: Lightbend parity; the two operations have distinct use cases (template-with-lookup vs. composition).

10. **`ResolveWith` precondition** (intentional Lightbend divergence): the `source` argument MUST be resolved (or substitution-free). If unresolved, `ResolveWith` MUST raise `NotResolved` BEFORE attempting to resolve the receiver. Lightbend does NOT precondition-check (it surfaces the source's resolution failure as `ConfigException.UnresolvedSubstitution`); E12 deliberately strengthens this to surface the *caller's mistake* clearly. Rationale: passing an unresolved source is virtually always a programmer error; an early-precondition error is more actionable than a downstream resolution failure.

11. **`IsResolved()`** is whole-config granularity: returns `false` if any substitution placeholder remains in the value tree. No per-value `isResolved` — matches Lightbend.

12. **Getters on unresolved paths raise `NotResolved`**. Reading any path whose value (or any transitive parent) contains an unresolved substitution returns the language-idiomatic `NotResolved` error. `AllowUnresolved=true` does NOT make getters lenient — it only makes `Resolve()` itself non-erroring. Paths that resolve cleanly are returned; paths that don't error at getter call. Matches Lightbend.

13. **`FromMap(values, originDescription)`**: constructs a `Config` from a plain-key map (keys are NOT path expressions). Type coercion follows Lightbend `ConfigValueFactory.fromMap` semantics: booleans, numbers (int/float), strings, lists (homogeneously typed recursively), nested maps, and null. The `originDescription` argument provides source-location info for error messages; an empty string means "use default origin". Rationale: covers issue #99's programmatic-fallback use case. `FromAnyRef` (Lightbend scalar/list/null roots) is deferred — requires publishing a `ConfigValue` public type.

14. **Include resolution stays at parse phase**, regardless of `ResolveSubstitutions`. `ParseStringWithOptions(..., {ResolveSubstitutions: false})` returns a `Config` with all `include` directives (including E11 `include package(...)`) already expanded; only `${...}` substitutions are deferred. Rationale: matches Lightbend (includes are parse-time, substitutions are resolve-time); avoids surprising interaction with E11 by keeping include resolution timing invariant.

#### Cross-spec interactions

E12 clarifies the behavior of two existing S-items when WithFallback / Resolve are invoked as separate operations.

##### S13a × WithFallback — Self-reference lookback across fallback layers

S13a.2 ("self-ref to overridden field works in merge") implicitly assumes single-source merge. With multi-source fallback composition, the lookback algorithm extends as follows:

Within the receiver's definition of `a`, `${?a}` (optional) and `${a}` (required) look back at the value of `a` from the immediately-prior layer in the fallback stack. The lookback chain walks **across fallback layers** (receiver → fallback₁ → fallback₂ → ...), not just within the receiver's own source.

Concrete cases (covered by fixtures dr04–dr06):

1. Receiver `a = ${?a} extra`, fallback `a = base`. After merge + resolve: `a = "base extra"` (optional self-ref pulls from fallback prior).
2. Receiver `a = ${?a} extra`, fallback has no `a` OR no fallback at all. After merge + resolve: `a = " extra"` (optional self-ref to undefined → empty contribution per S13a.4). Empty fallback and absent fallback are observably equivalent per Lightbend.
3. Receiver `a = ${a} extra` (required), fallback `a = base`. After merge + resolve: `a = "base extra"`.
4. Receiver `a = ${a} extra`, fallback has no `a` OR no fallback at all. After merge + resolve: **CycleError** (the substitution looks at the very value it is defining; Lightbend reports this as `ConfigException.UnresolvedSubstitution` with a cycle message). Both sub-cases (fallback present but without `a`, and no fallback layer) MUST produce the same outcome — there is no observable distinction in the merged-tree lookback chain when `a` is absent from all non-receiver layers. dr06 pins this with no fallback; impls without a distinct cycle category MAY surface as `ResolveError`.

##### S10 × AllowUnresolved — Type-check behavior under partial resolution

S10.4 / S10.13 / S10.19 specify concat type errors fire during resolution. Under `AllowUnresolved=true`:

- **Type-incompatible concat with at least one resolved operand of incompatible type**: the type error fires regardless of `AllowUnresolved`. A type error is structurally invalid; deferring it would mask programmer mistakes. Example: `a = "p" [1,2]` raises `TypeError` even with `AllowUnresolved=true` (covered by dr13).
- **Concat with all operands unresolved**: the concat survives as a concat-placeholder. Getter on its path raises `NotResolved` (covered by dr14).
- **Concat with mixed resolved + unresolved operands of compatible type**: the resolved portion is computed; the placeholder retains a reference to unresolved operands. Getter raises `NotResolved`.

Rationale: `AllowUnresolved` is about deferring *missing-value* errors, not *type* errors. The distinction prevents a class of silent miscompilations under partial-resolve workflows.

##### Optional substitution materialisation in concat contexts

Per HOCON.md §Substitutions L626–L645 and §Concatenation L387–L441, undefined `${?foo}` materialises differently depending on surrounding concat context. The materialisation rules are normative (covered by fixtures dr24–dr28):

| Context | Undefined `${?foo}` materialises as | Example | Result |
| --- | --- | --- | --- |
| Standalone field value | Field is **omitted** from parent object | `a = ${?x}` (x undef) | `{}` (no `a` key) |
| String concat | Empty string | `a = ${?x} "tail"` | `a = " tail"` (leading space preserved per HOCON whitespace rules) |
| String concat (multiple optional, all undef) | Empty string; if entire value collapses to empty, field is omitted | `a = ${?x}${?y}` (both undef) | `{}` (no `a` key) |
| Array concat | Empty array (no elements contributed) | `a = ${?x} [1,2]` (x undef) | `a = [1,2]` |
| Object merge | Empty object (no keys contributed) | `a = ${?x} { k = 1 }` (x undef) | `a = { k: 1 }` |
| Type-mixed concat | Type-determined per s10; `${?x}` does NOT bridge incompatible types | `a = "p" ${?x} [1]` | s10 type error |

#### Non-goals (explicit)

The following are out of scope for E12 v1 and are NOT to be inferred from the convention:

- **`FromAnyRef` + public `ConfigValue` type**: requires defining the public surface for non-object Config roots (scalar / list / null). Deferred to a follow-on E-item.
- **Custom resolver chain** (Lightbend `ConfigResolveOptions.appendResolver`): no current cross-impl demand. Future enhancement.
- **Path-expression `parseMap`** (Lightbend `ConfigFactory.parseMap` interpreting keys as path expressions): v1 has `FromMap` (plain keys) only.
- **Other `ConfigParseOptions` fields** (`setSyntax`, `setAllowMissing`, `setIncluder`, `setClassLoader`): each is a separate follow-on.
- **`Config.atPath(p)` / `Config.atKey(k)`**: Lightbend convenience wrappers, not in scope for issue #99.
- **Default-loading-chain replication** (Lightbend `ConfigFactory.load()` with reference.conf / application.conf auto-merge): platform-specific. Out of scope.

| Impl | Status | Encoding | API surface | Notes |
| --- | --- | --- | --- | --- |
| ts.hocon | 🤷 | `Partial<ParseOptions>` / `Partial<ResolveOptions>` interfaces | `parseString(s, opts?)`, `Config.withFallback(c)`, `Config.resolve(opts?)`, `Config.resolveWith(s, opts?)`, `Config.isResolved()`, `fromMap(m, origin?)`, `empty(origin?)` | `resolveWith` MAY be follow-on per decision 9. |
| rs.hocon | 🤷 | `ParseOptions::default()` + builder methods; `ResolveOptions::default()` + builders | `hocon::parse_with_options(s, opts)`, `Config::with_fallback(&self, c)`, `Config::resolve(&self, opts)`, `Config::resolve_with(&self, s, opts)`, `Config::is_resolved(&self)`, `hocon::from_map(m, origin)`, `hocon::empty(origin)` | `resolve_with` MAY be follow-on per decision 9. `StructureBuilder` / `SubstitutionResolver` remain `pub(crate)`. |
| go.hocon | 🤷 | Builder pattern: `DefaultParseOptions()` + `WithX()` chainable setters | `hocon.ParseStringWithOptions(s, opts)`, `(*Config).WithFallback(c)`, `(*Config).Resolve(opts)`, `(*Config).ResolveWith(s, opts)`, `(*Config).IsResolved()`, `hocon.FromMap(m, origin)`, `hocon.Empty(origin)` | `ResolveWith` MUST in v1 (issue origin per decision 9). v1.4.0 release closes go.hocon#99. |

**Fixtures**: 31 scenario YAML files (30 scenario IDs, with dr11 split into dr11a/dr11b) under `testdata/hocon/deferred-resolution/` (`dr01.yaml` – `dr30.yaml`) covering: basic fallback (dr01), FromMap-only (dr02), multi-layer (dr03), self-reference × fallback (dr04–dr06), AllowUnresolved partial (dr07), hermetic resolve (dr08), getter-on-unresolved (dr09), composition barrier (dr10), ResolveWith semantics (dr11a/dr11b), origin preservation (dr12), type-check under AllowUnresolved (dr13/dr14), include + deferred (dr15), FromMap nested coercion (dr16), E11 package include + deferred (dr17, Lightbend-skip), cross-layer cycle (dr18), Resolve idempotency (dr19), transitive substitution (dr20/dr21), hidden substitution (dr22/dr23), optional substitution materialisation (dr24–dr28), empty config edges (dr29), object-merge barrier (dr30).

**Fixture format**: YAML scenarios (NOT `.conf`) — encoded as multi-step build sequences with explicit `parseString` / `fromMap` / `withFallback` / `resolve` ops. See [`testdata/hocon/deferred-resolution/README.md`](../testdata/hocon/deferred-resolution/README.md) and [`docs/fixture-conventions.md` § "Scenario YAML fixtures (E12)"](fixture-conventions.md#scenario-yaml-fixtures-e12) for schema.

**Java generator**: `generate/src/main/java/DeferredResolutionRunner.java` runs each YAML scenario through Lightbend `com.typesafe.config` and emits expected outputs (`.json` / `.error` / `.skip` / `.txt`). dr11b and dr17 are marked `lightbendSkip: true` (dr11b: intentional Lightbend divergence per decision 10; dr17: E11 not applicable to Lightbend).

**Prior art**: Lightbend Java HOCON (`com.typesafe.config`) is the reference. No non-JVM HOCON parser surveyed (`go-akka/configuration`, `gurkankaymak/hocon`, `mockersf/hocon.rs`) exposes the lifecycle separation; all fuse parse-and-resolve. E12 establishes this convention in the cross-impl o3co stack.

**Tracking issue**: [#37](https://github.com/o3co/xx.hocon/issues/37). **External origin**: [o3co/go.hocon#99](https://github.com/o3co/go.hocon/issues/99) (cgordon).

<a id="e13"></a>

### E13 — Key-position parsing: S8.6 not enforced, path-expression whitespace preserved (aligned with Lightbend)

**Source**: cross-impl convention, aligned with the spec-author reference implementation (Lightbend 1.4.3). HOCON.md §Unquoted strings L270-276 (S8.6) and §Path expressions (path-element parsing) interact ambiguously when applied to field-key position. Lightbend's path parser does NOT enforce S8.6 in key position and DOES preserve literal whitespace adjacent to dots; ts/rs/go previously over-enforced S8.6 on each key segment and stripped leading whitespace from post-dot segments. This E-item documents the Lightbend-aligned reading.

**Reading**: S8.6 is a *value-position lexer rule* — its purpose (disambiguating unquoted runs from JSON numbers) does not apply in field-key position, where the parser is consuming a path expression, not classifying a value. Path-expression parsing tokenizes on `.` and field separators (`=` / `:` / `{`) but otherwise takes characters verbatim, including hyphens at any segment offset and whitespace adjacent to dots.

**o3co convention**: each of ts.hocon / rs.hocon / go.hocon MUST implement the following behavior in key position, matching Lightbend 1.4.3 empirically (probe matrix at [`generate/src/main/java/ProbeKeyHyphenAndPathWS.java`](../generate/src/main/java/ProbeKeyHyphenAndPathWS.java)):

| Aspect | Behavior |
| --- | --- |
| **Hyphen-start in any path segment** | Accept verbatim. `foo -bar = 1` → `{"foo -bar":1}`; `a.b -bar = 1` → `{"a":{"b -bar":1}}`; `foo.-bar = 1` → `{"foo":{"-bar":1}}`; `-foo bar = 1` → `{"-foo bar":1}`. S8.6's "begin with `-` requires digit" rule does NOT fire on key path elements. |
| **Trailing hyphen-only segment** | Accept verbatim. `foo - = 1` → `{"foo -":1}`. |
| **Whitespace adjacent to dot** | Preserve literally as part of the adjacent segment(s). `a b. c = 1` → `{"a b":{" c":1}}` (leading space on `" c"` preserved); `a . b = 1` → `{"a ":{" b":1}}` (both segments preserve their adjacent whitespace); `a .b = 1` → `{"a ":{"b":1}}`; `a b . c = 1` → `{"a b ":{" c":1}}`. |
| **Leading / trailing / double dot in path** | Reject (`BadPath`). `.foo = 1`, `foo. = 1`, `a b. = 1` all error — whitespace adjacency does NOT relax the empty-segment rule. |
| **Plus-sign in key position** | (Unchanged from prior behavior) reject — `+foo = 1` is rejected by the `+= operator` reservation, distinct from this E-item's scope. |

**Notes on cross-impl consistency**:

- For `key-hyphen-position/kh01–kh08`, this is a pure *loosening*: existing valid inputs continue to parse identically; previously-rejected inputs (hyphen-start key segments) now parse. From a downstream-consumer perspective the change is **backward-compatible** — no input that succeeded before fails now, and no key string changes.
- For path-WS preservation (`path-expr-whitespace/pw01–pw03, pw05, pw07`), some inputs that previously parsed with a stripped-leading-whitespace key now produce a key with the whitespace preserved verbatim. The input continues to succeed (no parse error gained), but the *key string* changes — a narrow behavior change worth calling out in per-impl release notes for users who were relying on the prior trimming.
- `pw04` (`a b.c d = 1`) is a combined-regression guard: it has no whitespace adjacent to a dot, so the path-WS loosening is a no-op for it. Included to verify the loosening does not regress the S10.8-only space-concat-in-segment case.
- `pw06` (`a b. = 1` → BadPath) is a boundary guard: loosening S8.6-in-key and preserving path-WS does NOT cascade into accepting empty path segments. The trailing-dot rule still fires.
- `kh08` (`foo -1bar = 1`) pins the hyphen-then-digit branch: in key position, `-1bar` is NOT number-lexed; it joins as verbatim text. Distinguishes "S8.6 not enforced at all in key" from "S8.6 still applies when the next char is a digit".
- `pw07` (`a b.\tc = 1`) extends the path-WS preservation to HOCON_WS tab (U+0009). HOCON_WS includes tab per the spec; the path-WS rule applies uniformly to space and tab.

**Why an E-item rather than (just) an S-item**: HOCON.md L270-276 (S8.6) is value-position; HOCON.md §Path expressions does not enumerate the whitespace-preservation or hyphen-acceptance rules explicitly. E13 records the project's chosen reading (Lightbend-aligned: S8.6 is value-only, path-WS preserved) and is parallel to E8 (which clarifies S8.6 in value position). The S8.6 matrix row stays ✅ under E8; E13 covers key-position behavior that S8.6 was being over-applied to. There is no compliance-matrix row to flip for E13 since the spec did not enumerate these rules — only the affected per-impl fixture results change. The follow-up notes at [`compliance-matrix.md`](compliance-matrix.md) L176-177 (S8.6-in-key + trailing-dot whitespace from Phase 6 #4) will be retired when each per-impl PR merges this E-item.

**Out of scope**:

- **Value-position S8.6** (the canonical `a = -foo` etc. lexer rule) is unchanged — still governed by E8.
- **`+` reservation** in key position (e.g. `+foo = 1`) is unchanged — still rejected, distinct from this E-item's scope.
- **Substitution-body path expressions** (e.g. `${-foo}`, `${a b}`) are unchanged — out of E8 scope per its narrative; same applies to E13.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | ❌ → ✅ | `key-hyphen-position/kh01–kh07.conf` + `path-expr-whitespace/pw01–pw06.conf` via conformance test | Path-parser loosening: remove S8.6-style validation on key path segments; preserve verbatim whitespace in path expressions. |
| rs.hocon | ❌ → ✅ | same fixtures | Same path-parser changes. |
| go.hocon | ❌ → ✅ | same fixtures | Same path-parser changes. |

**Fixtures**:

- `key-hyphen-position/kh01-space-concat-hyphen-tail.conf` — `foo -bar = 1` → `{"foo -bar":1}`
- `key-hyphen-position/kh02-dotted-then-space-hyphen-tail.conf` — `a.b -bar = 1` → `{"a":{"b -bar":1}}`
- `key-hyphen-position/kh03-quoted-then-space-hyphen-tail.conf` — `"foo" -bar = 1` → `{"foo -bar":1}`
- `key-hyphen-position/kh04-space-concat-dot-hyphen-start.conf` — `foo bar.-baz = 1` → `{"foo bar":{"-baz":1}}`
- `key-hyphen-position/kh05-first-token-hyphen-start.conf` — `-foo bar = 1` → `{"-foo bar":1}`
- `key-hyphen-position/kh06-trailing-hyphen-only.conf` — `foo - = 1` → `{"foo -":1}`
- `key-hyphen-position/kh07-dot-hyphen-start-segment.conf` — `foo.-bar = 1` → `{"foo":{"-bar":1}}`
- `key-hyphen-position/kh08-space-concat-hyphen-digit-tail.conf` — `foo -1bar = 1` → `{"foo -1bar":1}` (hyphen-then-digit branch — pins that key position does NOT number-lex even when next char is a digit)
- `path-expr-whitespace/pw01-space-after-dot.conf` — `a b. c = 1` → `{"a b":{" c":1}}`
- `path-expr-whitespace/pw02-space-both-sides-of-dot.conf` — `a . b = 1` → `{"a ":{" b":1}}`
- `path-expr-whitespace/pw03-space-before-dot.conf` — `a .b = 1` → `{"a ":{"b":1}}`
- `path-expr-whitespace/pw04-space-concat-both-segments.conf` — `a b.c d = 1` → `{"a b":{"c d":1}}` (combined-regression guard — no whitespace adjacent to dot)
- `path-expr-whitespace/pw05-multi-whitespace-both-sides.conf` — `a b . c = 1` → `{"a b ":{" c":1}}`
- `path-expr-whitespace/pw06-trailing-dot-before-separator.conf` — `a b. = 1` → BadPath (`.error` sidecar; boundary guard — loosening does not relax trailing-dot rule)
- `path-expr-whitespace/pw07-tab-after-dot.conf` — `a b.\tc = 1` → `{"a b":{"\tc":1}}` (HOCON_WS tab variant — preservation rule applies uniformly to space and tab)

**Tracking issue**: [#42](https://github.com/o3co/xx.hocon/issues/42). **Companion to E8** (value-position).

<a id="e14"></a>

### E14 — S13a.13 self-ref detection mechanism: narrower-than-path-equality variants accepted

**Source**: cross-impl convergence + spec amendment of the S13a.13 cluster 3f design (per `agentscope/.claude/superpowers/specs/2026-05-17-s13a-self-ref-lookback-design.md` ★1 decision #1). The original normative decision read:

> Self-ref detection via path-equality is preserved: each impl already correctly identifies self-ref by matching the substitution path against the path being resolved. The existing detection mechanism is correct; the fix is downstream.

Round-2 multi-agent-review of the per-impl cluster 3f PRs found this **too coarse**: path-equality misfires when an external field references a self-ref'd field's value (`a = ${?a}foo; b = ${a}` — when `b = ${a}` triggers resolution of `a`, the `${?a}` inside `a`'s value matches path-equality even though `b`'s lookup is NOT the self-ref site). All 3 impls independently arrived at strictly-narrower mechanisms during the cluster 3f fix work; this E-item records the cross-impl convention that *any* mechanism strictly narrower than path-equality is acceptable, and pins five regression fixtures (sr12–sr16) covering the four resolver bugs that path-equality permitted.

**Reading**: S13a.13 normative behavior (sr01–sr11) is unchanged. What this E-item amends is the *implementation flexibility* clause of decision #1 — impls SHOULD use a self-ref detection mechanism narrower than naive path-equality. The three observed mechanisms (each strictly narrower than path-equality, each correct enough to handle one or more of the bug cases below) are all acceptable variants:

| Impl | Mechanism | Source pointer |
| --- | --- | --- |
| ts.hocon | `resolvingConcats` WeakSet of `ConcatPlaceholder` nodes currently mid-resolution + `resolvingFieldPath` stack | `src/internal/resolver/substitution-resolver.ts` (`resolvingConcats`, `resolvingFieldPath`) |
| rs.hocon | `resolving_field_path: Vec<String>` + `is_owner` guard (current field-path equals subst path) | `src/resolver/substitution_resolver.rs` (`resolving_field_path`, `is_owner`) |
| go.hocon | AST-node pointer identity (`sp == s` between resolving substitution pointer and found substitution pointer) | `internal/resolver/resolver.go` (`sp == s` comparison) |

Each impl's mechanism is **strictly narrower** than path-equality — it correctly excludes the "external lookup of a self-ref'd field" case that path-equality misfired on. The choice between mechanisms is a per-impl architectural fit decision (WeakSet suits a closure-heavy fold; pointer-identity suits a single-root AST; `is_owner` suits a structurally-shared resolver). No mechanism is mandated; the E-item only mandates "narrower than path-equality" plus the regression fixtures below.

**o3co convention**: each impl MUST:

1. Use a self-ref detection mechanism that is strictly narrower than naive path-equality between the `${...}` subst path and the path-being-resolved. The three mechanisms above are reference implementations; novel mechanisms are acceptable if they pass the sr12–sr16 fixture set.
2. Pass sr12 + sr14 + sr15 + sr16 (Lightbend ground truth shown in expected JSON sidecars).
3. Pass sr13 (nested-external-ref + prior — the 2-axis combo of Bug #1 and Bug #2).

**The 4 bugs pinned by sr12–sr16** (cross-impl pre-fix matrix as of E14 introduction):

| Fixture | Input pattern | Expected | ts pre-fix | rs pre-fix | go pre-fix |
| --- | --- | --- | --- | --- | --- |
| sr12 | `foo.a = ${?foo.a}bar; foo.b = ${foo.a}` | `{"foo":{"a":"bar","b":"bar"}}` | ✅ | ❌ stack overflow | ✅ |
| sr13 | `foo.a = "x"; foo.a = ${?foo.a}bar; foo.b = ${foo.a}` | `{"foo":{"a":"xbar","b":"xbar"}}` | (un-verified) | (un-verified) | (un-verified) |
| sr14 | `a = "x"; a = ${?a}foo; b = ${a}` | `{"a":"xfoo","b":"xfoo"}` | ❌ `b="x"` (cache poll) | ❌ `b="x"` (cache poll) | ✅ |
| sr15 | `a = ${?a}1; a = ${?a}2` | `{"a":"12"}` | ❌ `a="2"` | ❌ `a="2"` | ❌ `a="2"` |
| sr16 | `b = ${a}; a = ${?a}foo` | `{"a":"foo","b":"foo"}` | ❌ `a="foofoo"` | ❌ `a="foofoo"` | ✅ |

**Notes on cross-impl behavior**:

- **Bug #1 (sr12)** caused a hard stack overflow in rs.hocon (and originally in ts.hocon, fixed as a side-effect of the chained-self-ref work in [ts.hocon#131](https://github.com/o3co/ts.hocon/issues/131) v1.5.2). go.hocon's pointer-identity mechanism is structurally immune. Per-impl fix scope: rs.hocon needs the most invasive change (look-back walker recursion guard); ts.hocon is already green.
- **Bug #2 (sr14)** affects ts and rs identically — the `b = ${a}` lookup resolves `a` against a cached prior-merge value (`"x"`) rather than the final post-concat value (`"xfoo"`). go.hocon's resolver structurally separates the prior-merge cache from the final-resolved cache, so this bug does not appear. Per-impl fix scope: ts/rs need cache-invalidation-on-concat-completion; go.hocon is green.
- **Bug #3 (sr15)** is the only universal failure — all 3 impls return `"2"` instead of `"12"`. The second assignment's `${?a}` does not see the first assignment's resolved `"1"`. This is likely a `prior_values` map population issue independent of the detection mechanism, so the cross-impl fix is expected to share a root cause and may be tractable as a single 3-PR cluster.
- **Bug #4 (sr16)** affects ts and rs identically — when `b = ${a}` resolves first (declaration order swap), `a` ends up over-expanded to `"foofoo"`. go.hocon's pointer-identity prevents the double-fold. Per-impl fix scope: same as Bug #2 (cache invalidation timing); go.hocon is green.

**Why an E-item rather than amending the spec inline**: The S13a.13 design spec at `.claude/superpowers/specs/2026-05-17-s13a-self-ref-lookback-design.md` is an internal-only artifact (not a normative spec exported to consumers); its decision #1 wording is preserved with an amendment-note appended. The cross-impl consumer-facing convention is recorded here as E14, parallel to how E8 records the value-position S8.6 reading. The S13a.13 spec-checklist.md row stays at its current state (it represents the canonical "no prior + self-ref → undefined" rule, which is unchanged); E14 adds the orthogonal detection-mechanism-flexibility convention plus the new fixture set.

**Out of scope**:

- **The "no prior + self-ref → UNDEFINED" core rule** of decision #2 is unchanged. E14 only amends decision #1 (the detection mechanism flexibility).
- **The sr01–sr11 fixture set** is unchanged; no behavior change to those cases.
- **Caching of short-circuited substitution results** (decision-track open question #2 in the original spec) is unchanged.

| Impl | Status | Test | Notes |
| --- | --- | --- | --- |
| ts.hocon | 🤷 → planned | sr12 ✅ / sr13 (verify) / sr14 ❌ / sr15 ❌ / sr16 ❌ | sr14/sr16 fix touches cache invalidation; sr15 fix shares root cause cross-impl. |
| rs.hocon | 🤷 → planned | sr12 ❌-CRASH / sr13 (verify) / sr14 ❌ / sr15 ❌ / sr16 ❌ | sr12 is hardest (look-back walker recursion guard); sr14/sr16/sr15 same as ts. |
| go.hocon | 🤷 → planned (partial-green) | sr12 ✅ / sr13 (verify) / sr14 ✅ / sr15 ❌ / sr16 ✅ | Only sr15 fails — pointer-identity mechanism is structurally robust on the other three. |

**Fixtures**:

- `self-ref-lookback/sr12-nested-external-ref-no-prior.conf` — Bug #1 minimal
- `self-ref-lookback/sr13-nested-external-ref-with-prior.conf` — Bug #1 + prior combo (2-axis)
- `self-ref-lookback/sr14-cache-prior-external.conf` — Bug #2
- `self-ref-lookback/sr15-double-self-ref.conf` — Bug #3 (universal failure)
- `self-ref-lookback/sr16-external-before-self-ref.conf` — Bug #4 (order-dependent)

**Tracking issue**: [#27](https://github.com/o3co/xx.hocon/issues/27). **Companion to S13a.13** (decision #1 amendment); sr01–sr11 in the same fixture directory cover the unchanged S13a.13 normative cases.

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
2026-05-21 — E12 (deferred substitution resolution — Lightbend-aligned `parse / withFallback / resolve()` lifecycle) added as a project-introduced cross-impl API convention. Fourteen normative spec decisions established covering: parse-with-options entry point, options encoding per language (Go builder / TS Partial / Rust Default), WithFallback semantics for unresolved operands, single-pass transitive resolution, hidden-substitution discard, cross-layer cycle detection, ResolveWith semantics + precondition (intentional Lightbend divergence per decision 10), IsResolved granularity, getter behavior, FromMap type coercion, include-resolution timing invariance. Cross-spec interactions clarified for S13a (self-reference lookback across fallback layers) and S10 (concat type-check behavior under AllowUnresolved=true) inline in E12 section. 31 scenario YAML fixtures (30 scenario IDs, with dr11 split into dr11a/dr11b) landed under `testdata/hocon/deferred-resolution/`, runnable via new `DeferredResolutionRunner.java` (Lightbend ground truth: 29 success or expected-error, 2 lightbendSkip — dr11b [decision 10 divergence] + dr17 [E11 not applicable to Lightbend]). External origin [go.hocon#99](https://github.com/o3co/go.hocon/issues/99); tracking issue [#37](https://github.com/o3co/xx.hocon/issues/37). Status 🤷 in all 3 impls — impls to follow in per-impl PRs targeting v1.4.0 bundle release with E11.

2026-05-21 — E11 (`include package(...)` qualifier — service-locator pattern for non-JVM HOCON) added as a project-introduced extension to HOCON syntax. Five normative spec decisions established: (1) identifier MUST be Go-module-path-style canonical form `<host>/<org>/<name>`; (2) two-arg form `package("id", "file")` mandatory, one-arg rejected; (3) collision on `(id, file)` is hard error (idempotent byte-equal re-registration allowed); (4) registry-miss is hard error at parse time (no silent empty-include fallback); (5) `include required(package(...))` follows existing required semantics. Per-impl registration mechanism: ts.hocon via runtime `require.resolve`, go.hocon via `init()`+`embed.FS`+`RegisterPackage`, rs.hocon via explicit `Parser::register_package` + `include_str!`. Driven by cross-impl design discussion 2026-05-20 (tracking issue [#33](https://github.com/o3co/xx.hocon/issues/33)); supersedes earlier ts-only proposal [ts.hocon#109](https://github.com/o3co/ts.hocon/issues/109) (closed). Status 🤷 in all 3 impls — fixtures and impls to follow in per-impl PRs.

2026-05-23 — E14 (S13a.13 self-ref detection mechanism — narrower-than-path-equality variants accepted) added as amendment to the S13a.13 cluster 3f spec ★1 decision #1. Round-2 multi-agent-review found the original "path-equality detection is correct" claim too coarse — it misfires when an external field references a self-ref'd field's value. All 3 impls independently arrived at strictly-narrower mechanisms (ts: `resolvingConcats` WeakSet; rs: `is_owner` guard; go: AST-node pointer-identity). E14 records the convention that any mechanism narrower than path-equality is acceptable, and pins 5 regression fixtures (sr12–sr16) covering the 4 newly-surfaced resolver bugs: sr12/sr13 nested external ref (Bug #1); sr14 cache pollution (Bug #2); sr15 same-field double-self-ref (Bug #3, universal failure); sr16 order-dependent ref-before-self-ref (Bug #4). Cross-impl pre-fix matrix: go.hocon green on 3/4 (only sr15 fails); ts.hocon fails sr14/sr15/sr16; rs.hocon fails sr12 (hard stack overflow) + sr14/sr15/sr16. Per-impl PRs to follow in cluster 3h. Tracking issue [#27](https://github.com/o3co/xx.hocon/issues/27).

2026-05-23 — E13 (key-position parsing — S8.6 not enforced on key path segments, path-expression whitespace preserved verbatim) added, aligned with Lightbend 1.4.3. Companion to E8 (value-position): E8 documents value-start S8.6 reading; E13 documents key-position scope. Driven by issue [#42](https://github.com/o3co/xx.hocon/issues/42), surfaced during S10.8 cross-impl work (Codex review on rs.hocon#115 / ts.hocon#128 flagged `foo -bar = 1` rejected by S8.6 in newly-reachable space-concat path; FCoT probe revealed Lightbend does not enforce S8.6 in key position at all). 8 `kh*` (`key-hyphen-position/`) + 6 success `pw*` + 1 error `pw06` (`path-expr-whitespace/`) fixtures land alongside the E-item: kh08 covers the hyphen-then-digit branch (distinguishes "S8.6 not enforced at all" from "S8.6 still triggers on hyphen-then-digit"); pw07 covers HOCON_WS tab adjacent to dot. Probe matrix at `generate/src/main/java/ProbeKeyHyphenAndPathWS.java`. Status 🤷 in all 3 impls — fixtures land first; per-impl PRs to follow targeting v1.5.3.

2026-05-20 — E8 **rewritten** to adopt Lightbend's pragmatic reading of HOCON.md L270-276 "begin" as value-position (not token-position at any lexer offset). Driven by external issue [xx.hocon#31](https://github.com/o3co/xx.hocon/issues/31) (@cgordon, first external issue), which surfaced `b = ${a}-bar` rejected under the strict reading. Concat-continuation cases (`${a}-bar`, `${a}--bar`, `${a}-1`, `${a}1bar`, `${a}.bar`, `"foo"-bar`, etc.) now accepted; us02 (`a = -foo`) / us03 (`a = -`) / us13 (`a = 01`) moved from per-impl error overrides to `SUCCESS_CONFS`. **BREAKING for downstream**: F3 (`a = 01` → `1` number, was `"01"` string) is a value-type change; other changes are additive. `+` rejection retained in both value-start and concat-continuation positions (HOCON `+=` operator reservation, distinct from number-lex). Lightbend probe matrix recorded at [`generate/src/main/java/ProbeIssue31.java`](../generate/src/main/java/ProbeIssue31.java) (groups A–F). Project principle established alongside this amendment: where xx.hocon and Lightbend differ and both behaviors derive from a reasonable reading of the spec, xx.hocon is the side that needs correcting (E5/E9/E10 to be re-audited under this principle as separate follow-ups; E10 may be a genuine Lightbend spec violation rather than an interpretation difference and will be raised upstream).
