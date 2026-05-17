# HOCON Spec Compliance Matrix

Cross-implementation roll-up of [`spec-checklist.md`](spec-checklist.md) for the three sibling implementations. This file is the public-facing one-page summary; per-item detail lives in each implementation's own `docs/spec-compliance.md`.

## Top-line compliance rate

| Implementation | Spec-total | In-scope | ✅ | ⚠️ | ❌ | 🤷 | ➖ |
|---|---:|---:|---:|---:|---:|---:|---:|
| [ts.hocon](https://github.com/o3co/ts.hocon/blob/develop/docs/spec-compliance.md) | **78.5%** | **88.2%** | 162 | 4 | 20 | 0 | 23 |
| [rs.hocon](https://github.com/o3co/rs.hocon/blob/develop/docs/spec-compliance.md) | **79.9%** | **88.8%** | 164 | 6 | 18 | 0 | 21 |
| [go.hocon](https://github.com/o3co/go.hocon/blob/develop/docs/spec-compliance.md) | **77.0%** | **86.1%** | 158 | 6 | 23 | 0 | 22 |

Where:

- **Spec-total** = `(✅ + ⚠️·0.5) / 209`. Denominator includes ALL items, including out-of-scope. Out-of-scope items intentionally lower this number — it is the answer to "how much of HOCON.md does this implementation handle?".
- **In-scope** = `(✅ + ⚠️·0.5) / (209 − ➖_per_impl)`. The denominator is **per-impl** because each implementation can additionally mark items ➖ for language-natural reasons that don't apply to its siblings (e.g. ts marks S1.1 ➖ because JS strings are pre-decoded Unicode at the I/O boundary, but go cannot — Go `string` permits arbitrary bytes). Globally shared ➖ count is 21; per-impl: ts=23 (+ S1.1, S13a.10), rs=21, go=22 (+ S13a.10). This is the answer to "of what the implementation chooses to support, how much is covered?".
- `❌` and `🤷` contribute 0. `🤷` is treated as 0 because an unverified claim is, by policy, not a pass — pinning it as ✅/❌ requires a test. After Phase 5, all three impls reached `🤷 = 0`.

Both numbers are shown side by side so neither over-claims nor under-claims. See [`spec-checklist.md`](spec-checklist.md) for the convention rationale.

## Status legend

| Glyph | Meaning |
| --- | --- |
| ✅ | Test exists and passes |
| ⚠️ | Test exists, partial pass / pinning a spec-violating behavior |
| ❌ | Test exists and fails, OR known spec violation documented in source |
| 🤷 | No test — implementation claim only, unverified |
| ➖ | Out of scope (rationale required). May be **globally out of scope** (excluded by all three impls — see [Globally out-of-scope items](#globally-out-of-scope-items-21)) or **per-impl out of scope** (one impl excludes for language-natural reasons that don't apply to siblings, e.g. ts S1.1 because JS strings are pre-decoded Unicode at the I/O boundary). |

## Globally out-of-scope items (21)

These 21 items are marked `➖` in **all three** implementations, by policy. Some impls also mark additional items `➖` for language-natural reasons (e.g. ts S1.1, ts/go S13a.10) — those are noted in the impl's own `spec-compliance.md`, not here.

| Items | Rationale class |
|---|---|
| S14a.4, S14f.5 | classpath resources are a JVM-only concept |
| S16.1 | MIME Type is set by HTTP servers, not parsers |
| S17.5 | `"null"` → null when null requested — none of the three implementations has a `getNull()`-equivalent typed accessor; spec L1244 is structurally inapplicable to their API models (added in Phase 4) |
| S20.1–S20.4 | Period Format mirrors `java.time.Period`, a Java-specific type |
| S23.5, S23.6 | `.properties` multi-line + Unicode escapes; documented simplification in each README |
| S24.1, S24.2 | reference.conf / application.conf are JVM conventions |
| S25.1 | System properties override is a JVM mechanism |
| S26.3 | `SecurityException` is a JVM-specific exception type |
| S1.2.6 | Unpaired surrogate codepoint — intentional language-natural divergence (Java accepts, Rust/Go reject) |
| S14a.2, S14e.4, S14e.5, S14f.1, S14f.6, S14f.8 | URL include unsupported by design across all three READMEs |

See each item's `out-of-scope:` line in [`spec-checklist.md`](spec-checklist.md) for the full rationale.

## Top spec violations (verified)

Items where the test or implementation behavior contradicts the spec:

| Item | Impl | Status | Description |
|---|---|---|---|
| S1.1 | go | ❌ | Invalid UTF-8 (e.g. `string([]byte{0xff})` via `ParseString`) is silently substituted with U+FFFD instead of rejected; spec L117 requires rejection. Go `string` is `[]byte` and is not language-guaranteed UTF-8. ts ➖ (JS string is pre-decoded Unicode at the I/O boundary; the parser cannot observe raw bytes — see ts.hocon S1.1 entry). rs ✅ (Rust `&str` is language-guaranteed valid UTF-8; verified positively via `tests/testdata/hocon/bom.conf` fixture). |
| S3.1 | ts, rs, go | ❌ / ⚠️ / ❌ | Empty file accepted. rs returns empty object; ts/go return `nil` error. Spec L130 says empty is invalid. |
| S8.2 | go | ❌ | `//` inside an unquoted run without preceding whitespace is treated as literal content; spec L248 says `//` starts a comment anywhere outside a quoted string. ts/rs ✅. |
| S3.4 | ts | ❌ | Unbraced root + stray `}` accepted ([#55](https://github.com/o3co/ts.hocon/issues/55)) |
| S8.1 | ts | ⚠️ | Lexer allows backtick in unquoted strings, contrary to spec L245 forbidden set |
| S8.6 | ts, rs, go | ⚠️ | `-` not followed by a digit is now rejected at lex/parse time in all 3 impls (Phase 6 #3c Phase 2 — [ts.hocon#96](https://github.com/o3co/ts.hocon/pull/96)+[#97](https://github.com/o3co/ts.hocon/pull/97), [rs.hocon#86](https://github.com/o3co/rs.hocon/pull/86), [go.hocon#82](https://github.com/o3co/go.hocon/pull/82)). go.hocon's parser numeric-key support (us08 `123abc = 1`, us09 `3.14 = "v"`, plus keyword-tail `123true = 1`) landed in [go.hocon#84](https://github.com/o3co/go.hocon/pull/84) (#81-followup); signed-numeric multi-tail (`123-456 = 1`) deferred to [go.hocon#83](https://github.com/o3co/go.hocon/issues/83). Digit-leading unquoted strings (us13 `01`, us15 `1e+x`) remain accepted as a known gap across all 3 impls. See the "Cleared in Phase 6 #3c-followup" and "Partially cleared in Phase 6 #3c" sections below for details. ([ts#73](https://github.com/o3co/ts.hocon/issues/73), [rs#63](https://github.com/o3co/rs.hocon/issues/63), [go#60](https://github.com/o3co/go.hocon/issues/60)) (fixtures: `testdata/hocon/unquoted-starts/` us01-us16 — Phase 6 #3c; Lightbend-quirk subset us02/us03/us13 documented under [E8](extra-spec-conventions.md#e8)) |
| S10.4 | ts, rs, go | ❌ | Mixing arrays + objects in concat silently allowed; spec L385 requires error ([ts#75](https://github.com/o3co/ts.hocon/issues/75), [rs#65](https://github.com/o3co/rs.hocon/issues/65), [go#63](https://github.com/o3co/go.hocon/issues/63)) |
| S10.8 | ts, rs, go | ❌ / ⚠️ | Unquoted concat in field keys (`a b = 1`) rejected; spec L317/L556 requires acceptance as key "a b". rs partial pass: quoted variant works ([ts#76](https://github.com/o3co/ts.hocon/issues/76), [rs#66](https://github.com/o3co/rs.hocon/issues/66), [go#65](https://github.com/o3co/go.hocon/issues/65)) |
| S10.13 | ts, rs, go | ❌ / ❌ / ⚠️ | Array/object in string concat silently accepted; spec L373 requires error. go ⚠️ permissive: `a = [1, 2] 3` → `[1, 2, 3]` ([ts#77](https://github.com/o3co/ts.hocon/issues/77), [rs#67](https://github.com/o3co/rs.hocon/issues/67)) |
| S10.15 | rs, go | ❌ | Quoted whitespace between obj/array substitutions (e.g. `c = ${a} " " ${b}`) is silently accepted and the arrays merged to `[1, 2]`; spec L442 requires this to be an error. ts ✅. |
| S10.19 | ts, rs, go | ❌ | Mixing substitution-resolved object with literal array silently accepted; spec L385-389 requires error ([ts#79](https://github.com/o3co/ts.hocon/issues/79), [rs#68](https://github.com/o3co/rs.hocon/issues/68), [go#63](https://github.com/o3co/go.hocon/issues/63)) |
| S11.8 | go | ❌ | Parser rejects TokenBool in key position; spec L504 requires stringification to `"true"` / `"false"`. Impl is stricter than spec ([go#66](https://github.com/o3co/go.hocon/issues/66)) |
| S12.5 | ts, rs, go | ❌ | `include.foo = 1` silently accepted as key `["include", "foo"]`; spec L570 reserves `include` from beginning a path expression ([ts#80](https://github.com/o3co/ts.hocon/issues/80), [rs#71](https://github.com/o3co/rs.hocon/issues/71), [go#67](https://github.com/o3co/go.hocon/issues/67)) (fixtures: `testdata/hocon/include-reservation/` ir01-ir14 — Phase 6 #3e; Lightbend-quirk subset ir03/ir04 documented under [E9](extra-spec-conventions.md#e9)) |
| S13b.2 | ts, rs | ❌ | `+=` on non-array prior value silently allowed; spec L732 requires error. go ✅ correctly rejects ([ts#81](https://github.com/o3co/ts.hocon/issues/81), [rs#72](https://github.com/o3co/rs.hocon/issues/72)) |
| S13.9 | rs | ❌ | `HOME = null; result = ${?HOME}` resolves `result` to a present null scalar instead of erasing the field per L618 "null treated same as missing"; env value is correctly blocked ([rs#74](https://github.com/o3co/rs.hocon/issues/74)). ts ✅, go ✅. |
| S13.15 | rs, go | ❌ | `foo : ${?bar}${?baz}` skip semantics: spec L658 says the field is skipped only when **both** substitutions are undefined. rs/go differ from this; go in particular creates the field with an empty-string value when both are undefined. ts ✅. |
| S13.11 | go | ⚠️ | Lenient mode drops optional substitutions in nested-include scope ([#45](https://github.com/o3co/go.hocon/issues/45)) |
| S13a.3 | ts | ⚠️ | Self-reference before any prior value (`a = ${a}`) raises a cycle error, but the error type / message classifies this as a generic substitution error rather than the "undefined" path the spec describes at L795. rs/go ✅ (correct error class). |
| S13a.12 | go | ❌ | Self-ref in a path expression (`${foo.a}` where `foo.a` is being defined) does not resolve to the "below" value per L831; the looked-up sub-object is discarded in the merge. ts/rs ✅. |
| S13a.13 | ts, rs, go | ❌ | `a = ${?a}foo` with no prior `a` resolves to `"foofoo"` not `"foo"` — the self-ref look-back picks up the trailing literal as its prior value per L841 ([ts#84](https://github.com/o3co/ts.hocon/issues/84), [rs#76](https://github.com/o3co/rs.hocon/issues/76), [go#68](https://github.com/o3co/go.hocon/issues/68)) (fixtures: `testdata/hocon/self-ref-lookback/` sr01-sr11 — Phase 6 #3f; Lightbend-spec-conformant, per-impl bug only) |
| S13c.1–S13c.5 | ts, rs, go | ❌ | `${X[]}` env-var list not implemented; each implementation's lexer rejects `[` / `]` inside `${...}` body |
| S14a.10 | go | ❌ | Unquoted include argument (e.g. `include foo.conf`) is silently accepted instead of rejected with a parse error per L958. ts/rs ✅. |
| S14c.2 | rs | ❌ | Non-relativized substitution path fallback not implemented ([#44](https://github.com/o3co/rs.hocon/issues/44)) |
| S17.6 | ts | ⚠️ | `getString()` on null silently returns the string `"null"` instead of throwing per L1252; other typed accessors throw, but *incidentally* (no explicit `valueType==='null'` guard in `requireScalar`) ([ts#88](https://github.com/o3co/ts.hocon/issues/88)). rs/go ✅. |
| S18.1 | ts | ❌ | Number value taken as default unit not implemented — bare-number duration values are not interpreted with the impl's default unit per L1280. rs/go ✅. |
| S18.4 | ts, rs, go | ❌ / ⚠️ / ❌ | String value with no unit should be interpreted with the default unit per L1294. ts/go: `getDuration("500")` errors instead of producing 500 ms. rs ⚠️: some forms work, others error (partial). (fixtures: `testdata/hocon/units-default/` ud01–ud08, up01–up05, ub01–ub06, un01–un03 — Phase 6 #3d) |
| S17.7, S17.8 | go | ⚠️ | Non-Option accessors panic correctly per L1254-1255; Option accessors return `None` instead of error — partial violation ([go#72](https://github.com/o3co/go.hocon/issues/72)). ts/rs ✅. |
| S19.1 | go | ⚠️ | Nanosecond units: `ns` / `nanosecond` / `nanoseconds` work; `nano` / `nanos` aliases missing per L1310. ts/rs ✅. |
| S19.2 | go | ❌ | Microsecond units (`us` / `micro` / `micros` / `microsecond` / `microseconds`) all missing from `parseDuration`; `getDurationOption` returns `None`. ts/rs ✅. |
| S19.8 | ts, rs | ❌ | Duration unit names should be case-sensitive (lowercase only) per L1304; both impls accept `MS`, `Seconds`, `NS`, etc. (rs `parse_duration` calls `.to_lowercase()` before matching at `src/config.rs:417`; ts has the same shape). go ✅. |
| S22.2 | ts | ❌ | Intermediate non-object hides earlier object across files per L1430; ts merges across the non-object barrier. rs/go ✅. |
| S23.4 | ts, go | ❌ | When a `.properties` key conflicts with an object path (leaf-vs-parent), the object should win per L1462; ts/go keep the leaf string instead. rs ✅. |
| S21.4 | ts, go | ❌ | Single-letter byte abbreviations (`K`/`M`/`G`/…) not recognized — spec L1385 / java `-Xmx` convention. rs ✅ ([ts#89](https://github.com/o3co/ts.hocon/issues/89), [go#73](https://github.com/o3co/go.hocon/issues/73)). |
| S21.5 | go | ❌ | Fractional byte values (`0.5KB`, `1.5MiB`, …) rejected — `parseBytes` uses `ParseInt`. ts/rs ✅ ([go#74](https://github.com/o3co/go.hocon/issues/74)). |

## Shared test debt

Spec items with no test coverage in **any** of the three implementations. These are the natural targets for future test-debt PRs:

- (Empty — Phase 4 cleared the last shared `🤷` cluster around S15/S17/S21; Phase 5 cleared per-impl `🤷` in all three impls. The `🤷` column in the top table is now `0` everywhere.)

The next phase of compliance work shifts from "verify what we don't know" to "fix what we now know is broken" — see [Top spec violations](#top-spec-violations-verified) for the candidate list.

For behaviors that fall **outside** HOCON.md but should converge across the three impls (e.g. NEL handling), see [`extra-spec-conventions.md`](extra-spec-conventions.md) — separate E-prefix namespace, not counted in the matrix denominator.

### Cleared in Phase 6 #3c-followup (2026-05-18)

go-only follow-up to Phase 6 #3c, landed in [go.hocon#84](https://github.com/o3co/go.hocon/pull/84) (closes [go.hocon#81](https://github.com/o3co/go.hocon/issues/81)). The Phase 6 #3c PR (go.hocon#82) deferred parser-level numeric-key support to keep the lex-time S8.6 fix small and aligned with ts/rs Option B. PR #84 finishes the Option A picture for go.hocon:

- **S11.3** (go ❌ → ✅) — `1.2.3 = x` now creates path `["1","2","3"]` per spec L489 (previously rejected). ts/rs were already ✅.
- **S11.4** (go ❌ → ✅) — `10.0foo = x` now creates path `["10","0foo"]` per spec L496 (previously rejected). ts/rs were already ✅.
- **S8.6 narrative tightened** — us08 `123abc = 1` → `{"123abc": 1}` (TokenInt + unquoted concat as key) and us09 `3.14 = "v"` → `{"3":{"14":"v"}}` (TokenFloat dot-split as key) now pass. Status remains ⚠️ in all 3 impls because us13/us15 strict lex-time rejection still requires the Number-token-aware path that's out of scope.

What's enabled in `parseKey`:

- TokenFloat accepted as key start; value dot-split into nested path segments
- TokenInt or TokenFloat followed by an adjacent stringifiable unquoted token (`TokenString` unquoted, `TokenBool`, `TokenNull`, `TokenInclude`) with no preceding whitespace concatenates into the last key segment; the merged value is re-split on `.` and each segment re-validated against S8.6
- Concat gated on `prevKeyTokenIsNumeric` so a quoted key like `"a.b"c = 1` is NOT silently re-split (the literal `.` inside the quoted segment must not be reinterpreted as a path separator — round-1 Codex review caught this regression)

What remains deferred to [go.hocon#83](https://github.com/o3co/go.hocon/issues/83):

- Signed-numeric multi-tail concat: `123-456 = 1` requires consuming a chain of adjacent TokenInt/TokenFloat tails rather than a single tail. The current concat is single-shot. Identified by Codex round-3 review, out of #81 scope. ts/rs handle this naturally via their unquoted-only Option B token model (single TokenString `"123-456"`).

Rate change (in-scope) — go only: 85.0% → 86.1% (+1.1pp; +2 cells = S11.3 + S11.4 flipped ❌ → ✅).

Cross-impl review feedback loop continued to pay off in #3c-followup: Codex (via the mandatory file-pipe `codex exec < INPUT > OUTPUT` invocation) caught the Critical quoted-key gating bug in round 1, the keyword-tail asymmetry in round 2, and the signed-numeric multi-tail gap in round 3 (deferred to #83 to keep scope tight). Each finding was a real correctness issue that single-reviewer review would have missed.

### Partially cleared in Phase 6 #3c (2026-05-18)

The following item was **partially** cleared from "Top spec violations" by [ts.hocon#96](https://github.com/o3co/ts.hocon/pull/96) + [ts.hocon#97](https://github.com/o3co/ts.hocon/pull/97), [rs.hocon#86](https://github.com/o3co/rs.hocon/pull/86), and [go.hocon#82](https://github.com/o3co/go.hocon/pull/82) — third Phase 6 wave: cross-impl convergent fix for HOCON.md §Unquoted strings (L270-276) rejecting `-` not followed by a digit. xx.hocon ground truth pinned by 16 fixtures in [xx.hocon#16](https://github.com/o3co/xx.hocon/pull/16) (us01-us16).

- **S8.6** — unquoted strings cannot begin with `-` (unless followed by a digit) (⚠️ partial in all 3, was ❌ in all 3)

What's enforced now (all 3 impls):
- `a = -foo`, `a = -bar`, `a = -` → `ParseError`
- `a.-foo = 1` (dotted key segment) → `ParseError`
- `${-foo}` (substitution path segment) → `ParseError` at lex/parse time, gated on `!curStarted` so `${"a"-foo}` (quoted+unquoted concat → key `"a-foo"`) remains accepted

What remains as ⚠️ partial:
- Digit-leading unquoted strings that resolve to value-concat strings (e.g. `123abc`, `1ex`, `1.x`, `0xff`, `1.0.0`, `-1foo`) are not lex-rejected — they continue to tokenize and resolve to the value-concat string matching Lightbend output. The strict lex-time rejection (us13 `01`, us15 `1e+x`) requires introducing a `Number` token kind in ts/rs (the unquoted-only token model has no number kind) or a stricter `lex_number` validity check in go (already has number tokens). Tracked as `it.fails` / `#[should_panic]` / `t.Skip` tripwires in each impl's conformance file.
- go.hocon defers us08 (`123abc = 1` → `{"123abc": 1}`) and us09 (`3.14 = "v"` → `{"3":{"14":"v"}}`) to [go.hocon#81](https://github.com/o3co/go.hocon/issues/81) (parser numeric-key support).

Cross-impl architectural divergence (intentional):
- **ts.hocon and rs.hocon** take **Option B**: the lexer has no separate Number token kind (per `tokenizes_numbers_as_unquoted` test in rs.hocon and `unquoted: bare word, number, true/false/null` comment in ts.hocon `token.ts:17`). Fix is a single `isDecimalDigit` peek-ahead check at three sites — main tokenize loop's unquoted-start branch, `parseSubstBody` (gated on `!curStarted`), and `parseKey` after dot-split.
- **go.hocon** takes **Option A** (plan-shaped, since the lexer already has `TokenInt`/`TokenFloat`): `readNumber` rewritten as greedy-with-backtrack per the HOCON.md number grammar (fractional/exponent productions backtrack to the last valid number end if not followed by a digit); leading `-` no-digit returns `TokenError`. The same `parseSubstBody` `!curStarted` gate and `parseKey` segment check are added.

Multi-reviewer convergence observed during Phase 6 #3c: the **`parseSubstBody` segment-start gate** was independently flagged by Claude general-purpose Opus and Codex on rs.hocon PR #86 round-1 review — the initial check fired for every unquoted fragment, breaking `${"a"-foo}` quoted+unquoted concat. The same bug was then found in ts.hocon PR #96 (already merged) and fixed via follow-up PR #97; go.hocon implemented the gate correctly from the start in PR #82 based on the rs/ts convergent learning. Cross-impl review feedback loop confirmed as a useful pattern for catching architectural mistakes that pass single-impl tests because there's nothing to gate.

Side-effect fixes (single-impl, landed alongside Phase 6 #3c):
- **go.hocon CI** — `.github/workflows/test.yml` updated to use `-coverpkg=./...` so cross-package coverage (tests in `package hocon_test` exercising `internal/lexer`/`internal/parser`) is measured correctly for codecov. Previously the per-package default under-reported and falsely failed `codecov/patch` on PRs adding cross-package integration tests.

Two strict-spec gaps (us13/us15) are documented but deferred:
- us13 (`01` leading-zero): Lightbend silent-accept quirk excluded from generator. xx.hocon E8 in `extra-spec-conventions.md`.
- us15 (`1e+x` incomplete-exp + `+` in unquoted): Lightbend value-parser error. Tripwires in each impl's conformance test fire when behavior changes.

### Cleared in Phase 6 #2 (2026-05-17)

The following items were cleared from "Top spec violations" by [ts.hocon#95](https://github.com/o3co/ts.hocon/pull/95), [rs.hocon#85](https://github.com/o3co/rs.hocon/pull/85), and [go.hocon#80](https://github.com/o3co/go.hocon/pull/80) — second Phase 6 wave: cross-impl convergent fix for HOCON.md §Conversion of numerically-indexed objects to arrays (L1184–L1219), via a new `numericObjectToArray` helper wired into accessors (`getList` / `get_list` / `getArray`) and the resolver pairwise-fold concat join. xx.hocon ground truth pinned by 17 fixtures in [xx.hocon#10](https://github.com/o3co/xx.hocon/pull/10) and [xx.hocon#11](https://github.com/o3co/xx.hocon/pull/11) (na03e-overlap added after multi-agent review).

- **S15.1** — numerically-keyed object → array when array context (✅ in all 3, was ❌ in all 3)
- **S15.2** — conversion is lazy (only on type-required access) (✅ in all 3 — now explicit guard, no longer incidental)
- **S15.3** — conversion in concatenation when list expected (✅ in all 3, was ❌ in all 3)
- **S15.4** — empty object NOT converted (✅ in all 3 — now explicit empty-guard, no longer incidental)
- **S15.5** — non-integer keys ignored during conversion (✅ in all 3, was ❌ in all 3)
- **S15.6** — missing indices compacted (✅ in all 3, was ❌ in all 3)
- **S15.7** — sorted by integer key value (✅ in all 3, was ❌ in all 3)

Three new entries (**E2 / E3 / E4**) in [`extra-spec-conventions.md`](extra-spec-conventions.md) document the canonical-text guarantee — leading-zero (`"00"` ≠ `"0"`), leading `+` (`"+1"` ≠ `"1"`), and leading sign char on zero (`"-0"` ≠ `"0"`) are all rejected via a pre-filter regex `^(0|[1-9][0-9]*)$`. JS / Rust / Go native int parsers all accept these forms, so the pre-filter is required in every impl; relying on the native parser would silently break canonical-text guarantee.

Multi-reviewer convergence observed during Phase 6 #2: 5 of 6 reviewers (3 Codex + 2 Claude general-purpose Opus) independently flagged that the initial single-pass loop variant of the multi-piece concat produced wrong results for overlapping numeric keys (`obj1={"0":"x"}, obj2={"0":"z"}, arr=${obj1} ${obj2} [a]` → single-pass `["x","z","a"]`, spec-correct pairwise-fold `["z","a"]`). The fixture `na03d-concat-multi-piece.conf` originally shipped used disjoint keys and could not detect the divergence. A follow-up fixture `na03e-multi-piece-overlap.conf` (xx.hocon#11) pins Lightbend ground truth `["z","y","a"]` for overlapping keys, and all 3 impls refactored to true left-to-right pairwise fold via a `joinPair` helper.

Side-effect fixes (separator-skip in array-concat path, required for S15.3 NORMATIVE behaviour, simultaneously resolved adjacent pinned bugs):

- **S10.2** (rs only) — `[1,2] [3,4]` array-array concat ✅ (was ❌, see ts.hocon/go.hocon already ✅)
- **S10.14** (ts only) — whitespace around array substitutions stripped ✅ (was ⚠️)
- **S10.17** (rs only) — substitution-resolved array participates in array concat ✅ (was ❌)
- **S13.14** (ts, rs) — optional missing in array concat → clean array ✅ (was ⚠️ in both; go already ✅)

rs.hocon additionally extended serde to thread the same conversion through `deserialize_seq` and `deserialize_enum` in the new `OwnedHoconDeserializer`, so `#[derive(Deserialize)] struct Cfg { items: Vec<String> }` against `items = {"0":"a","1":"b"}` works under the `serde` feature.

### Cleared in Phase 6 #1 (2026-05-15)

The following items were cleared from "Top spec violations" by [ts.hocon#94](https://github.com/o3co/ts.hocon/pull/94), [rs.hocon#84](https://github.com/o3co/rs.hocon/pull/84), and [go.hocon#78](https://github.com/o3co/go.hocon/pull/78) — first Phase 6 wave: cross-impl convergent fix for HOCON.md §Whitespace L165–184:

- **S6.1** — Unicode Zs/Zl/Zp category whitespace (✅ in all 3, was ❌ in all 3)
- **S6.2** — non-breaking spaces U+00A0/U+2007/U+202F (✅ in all 3, was ❌ in all 3)
- **S6.4** — ASCII control whitespace (vtab/FF/FS–US) (✅ in all 3, was ⚠️ in all 3 — only tab + CR previously recognized)
- **S6.3** — BOM (U+FEFF) broadened from "stripped only at start-of-input" to "whitespace anywhere" (already ✅; coverage broadened with mid-stream regression test)

Each impl introduced a single `isHoconWhitespace` / `is_hocon_whitespace` predicate covering the full spec set, routed through main lexer + substitution body + unquoted terminator. The newline-vs-whitespace ordering invariant (LF still emits the newline token) was preserved.

Cross-impl convergent intentional behavior changes worth noting (3-way):

- **BOM mid-stream** is now whitespace (was: stripped only at start-of-input). Pinned by `S6.3 BOM mid-stream` regression test in each impl.
- **CR (`\r`) inside `${...}`** is now inter-segment whitespace (was: `"unterminated substitution"` error alongside LF). Spec L182–184 restricts newline to U+000A specifically; CR is whitespace, not newline. LF still terminates substitution body as error.

Go-specific incidental fix during the convergence: `isUnquotedForbidden` previously routed through `unicode.IsSpace()` and silently treated NEL (U+0085) as whitespace; replaced with `isHoconWhitespace()`. The NEL handling is now cross-impl convergent and tracked separately in [`extra-spec-conventions.md`](extra-spec-conventions.md) as **E1** (E-namespace because NEL non-membership in HOCON_WS is implicit-by-absence in the spec, not enumerated).

### Cleared in Phase 1 (2026-05-12)

The following items were cleared from shared test debt by [ts.hocon#74](https://github.com/o3co/ts.hocon/pull/74), [rs.hocon#64](https://github.com/o3co/rs.hocon/pull/64), and [go.hocon#61](https://github.com/o3co/go.hocon/pull/61):

- **S2.3** — comment markers literal inside quoted strings (✅ in all 3)
- **S5.2–S5.6** — comma rules (✅ in all 3)
- **S6.1, S6.2** — Unicode/non-breaking whitespace (now verified ❌ in all 3 — see Top spec violations above)
- **S6.4** — ASCII control whitespace (⚠️ in all 3 — partial pass)
- **S8.6** — digit/hyphen unquoted starts (verified ❌ across rs/go too, was only ts before)
- **S8.7, S8.8** — escape rejection + control-char allowance in unquoted strings (✅ in all 3)

### Cleared in Phase 3 (2026-05-12)

The following items were cleared from shared test debt by [ts.hocon#85](https://github.com/o3co/ts.hocon/pull/85), [rs.hocon#77](https://github.com/o3co/rs.hocon/pull/77), and [go.hocon#69](https://github.com/o3co/go.hocon/pull/69):

- **S13.3** — `${?` is exactly 3 chars; whitespace before `?` is not optional marker (✅ in all 3)
- **S13.5** — substitutions are NOT parsed inside quoted strings (✅ in all 3)
- **S13.9** — `null` in config blocks env var lookup (✅ in ts/go; ❌ in rs — see Top spec violations above)
- **S13.13** — optional undefined in string concat → empty string (✅ in all 3)
- **S13.14** — optional undefined in obj/array concat (✅ in go; ⚠️ in ts/rs — array variant broken, see Top spec violations above)
- **S13.16** — substitutions only in field values / array elements (✅ in all 3)
- **S13a.13** — `a = ${?a}foo` resolves to `"foo"` (now verified ❌ in all 3 — see Top spec violations above)
- **S14a.6** — unquoted `include` at non-start-of-key is literal (✅ in all 3)
- **S14a.8** — no value concatenation on include argument (✅ in all 3)
- **S14a.9** — no substitutions in include argument (✅ in all 3)
- **S14b.1** — included root must be an object; array → error (✅ in all 3)

Deferred (not externally observable): **S13a.10** — substitution memoization-by-instance is an internal resolver invariant; black-box parse/resolve APIs cannot distinguish it.

### Cleared in Phase 2 (2026-05-12)

The following items were cleared from shared test debt by [ts.hocon#82](https://github.com/o3co/ts.hocon/pull/82), [rs.hocon#73](https://github.com/o3co/rs.hocon/pull/73), and [go.hocon#64](https://github.com/o3co/go.hocon/pull/64):

- **S3.2** — root non-object/non-array is invalid (✅ in all 3)
- **S10.4** — mixing arrays + objects in concat is an error (now verified ❌ in all 3 — see Top spec violations above)
- **S10.7** — concatenation does not span a newline (✅ in all 3)
- **S10.8** — string concat in field keys (verified ❌ in ts/go, ⚠️ in rs — see Top spec violations above)
- **S10.13** — array/object in string concat → error (verified ❌ across ts/rs, was only go ⚠️ before)
- **S10.14** — whitespace around obj/array substitutions (✅ in rs/go; ⚠️ in ts — partial pass)
- **S10.19** — substitution-resolved object + literal array → error (now verified ❌ in all 3)
- **S11.4** — `10.0foo` → path `[10, 0foo]` (✅ in ts/rs; ❌ in go — divergence verified)
- **S11.5** — `foo10.0` → path `[foo10, 0]` (✅ in all 3)
- **S11.8** — path expression always stringifies (✅ in ts/rs; ❌ in go — impl stricter than spec)
- **S11.9** — substitutions not allowed inside path expressions (✅ in all 3)
- **S12.5** — `include` may NOT begin a path expression (now verified ❌ in all 3)
- **S13b.2** — `+=` on non-array prior value → error (✅ in go; ❌ in ts/rs — go is the only spec-compliant impl)

### Cleared in Phase 4 (2026-05-13)

The following items were cleared from shared test debt by [ts.hocon#90](https://github.com/o3co/ts.hocon/pull/90), [rs.hocon#81](https://github.com/o3co/rs.hocon/pull/81), and [go.hocon#75](https://github.com/o3co/go.hocon/pull/75):

- **S15.1–S15.3, S15.5–S15.7** — numerically-indexed object → array conversion (verified ❌ in all 3 — see Top spec violations above)
- **S15.4** — empty object NOT converted (✅ in all 3 — *incidental*: passes today because no conversion runs at all; must be re-validated when the S15 conversion path lands)
- **S17.5** — `"null"` → null when null requested (now ➖ in all 3 — no `getNull()`-equivalent accessor in any impl; spec L1244 structurally inapplicable. Added to globally OOS list.)
- **S17.6** — null → other type: error (✅ in rs/go; ⚠️ in ts — see Top spec violations above)
- **S17.7** — object → other type: error (✅ in ts/rs; ⚠️ in go — Option accessors return None instead of error)
- **S17.8** — array → other type: error (✅ in ts/rs; ⚠️ in go — same shape as S17.7)
- **S21.4** — single-letter byte abbreviations (verified ❌ in ts/go; ✅ in rs — see Top spec violations above)
- **S21.5** — fractional byte values (✅ in ts/rs; ❌ in go — see Top spec violations above)

Multi-reviewer convergence observed during Phase 4: the S15.3 test in all three impls was initially written against a non-concatenation scenario (plain substitution / path-expression / array literal). Reviewers (Copilot on rs+go, Claude on ts) independently flagged this, leading to a uniform rewrite using the real adjacent-list-concat context `[a] ${obj}` across all three impls.

### Cleared in Phase 5 (2026-05-13)

The remaining per-impl `🤷` items (ts: 20, rs: 17, go: 28) were cleared by [ts.hocon#91](https://github.com/o3co/ts.hocon/pull/91), [rs.hocon#82](https://github.com/o3co/rs.hocon/pull/82), and [go.hocon#76](https://github.com/o3co/go.hocon/pull/76). All three impls now have `🤷 = 0`. Phase 5 was per-impl mop-up (no shared-cluster work — that ended at Phase 4).

Cross-impl convergent verifications (items where the same status now lands in 2+ impls):

- **S3.1** — empty-file rule: ❌ in ts/go (nil error), ⚠️ in rs (empty object) → **3-way violation** (existing row updated)
- **S18.4** — bare-number duration default unit: ❌ in ts/go, ⚠️ in rs → **3-way violation** (new row)
- **S10.15** — quoted whitespace between obj/array substitutions: ❌ in rs/go (silently merged) → 2-way violation (new row)
- **S13.15** — `${?bar}${?baz}` skip when both undefined: ❌ in rs/go → 2-way violation (new row)
- **S19.8** — duration unit case sensitivity: ❌ in ts/rs (`.to_lowercase()` before matching) → 2-way violation (new row)
- **S23.4** — `.properties` object-vs-leaf conflict: ❌ in ts/go (leaf wins) → 2-way violation (new row)

Per-impl ❌ / ⚠️ verifications added to Top spec violations:

- ts-only: S13a.3 ⚠️ (cycle error class), S18.1 ❌ (number value default unit), S22.2 ❌ (intermediate non-object across files)
- rs-only: S10.2 ❌ (array concat whitespace scalar), S10.17 ❌ (substituted-array concat)
- go-only: S1.1 ❌ (invalid UTF-8 → U+FFFD), S8.2 ❌ (`//` in unquoted), S11.3 ❌ (numeric path expressions), S13a.12 ❌ (self-ref path), S14a.10 ❌ (unquoted include arg), S19.1 ⚠️ (`nano`/`nanos` aliases missing), S19.2 ❌ (microsecond units missing)

Per-impl ➖ added (NOT promoted to globally OOS — language-natural divergence):

- ts: S1.1 (JS `string` is pre-decoded Unicode at the I/O boundary; the parser cannot observe raw byte sequences. Note: Node's default `fs.readFileSync('utf-8')` is non-fatal — invalid bytes are silently replaced with U+FFFD — strict rejection requires a custom decoder. Documented at the impl level.)
- ts, go: S13a.10 (substitution memoization-by-instance is an internal resolver invariant; black-box parse/resolve APIs cannot distinguish it. Same reasoning as the Phase 3 deferral note.)

Multi-reviewer convergence observed during Phase 5: Copilot review on go.hocon #76 caught a misclassification — S1.1 had been initially marked ➖ on the (incorrect) rationale that Go strings are guaranteed valid UTF-8. They are not (Go `string` is `[]byte`). The probe revealed that go silently substitutes invalid UTF-8 with U+FFFD instead of rejecting per spec L117 → reclassified ❌ with a Pin/Spec test pair. ts marks ➖ (JS string is pre-decoded Unicode; parser cannot observe raw bytes); rs marks ✅ (Rust `&str` is language-guaranteed valid UTF-8, verified positively via `bom.conf` fixture). The three impls' divergent classifications all reflect their language's actual string-type guarantees.

## How this file is maintained

1. The canonical item definitions live in [`spec-checklist.md`](spec-checklist.md). Adding or removing items there is the only way to change the denominator.
2. Each implementation maintains its own `docs/spec-compliance.md` with `tests:` and `status:` cells per item.
3. This matrix is rebuilt by counting statuses in each per-repo file:

```bash
for repo in ts.hocon rs.hocon go.hocon; do
  for g in ✅ ⚠️ ❌ 🤷 ➖; do
    n=$(grep -c "^  status: $g" /path/to/$repo/docs/spec-compliance.md)
    echo "$repo $g $n"
  done
done
```

4. Rates are computed as `(✅ + ⚠️·0.5) / N` with `N = 209` (spec-total) or `N = 209 − ➖_per_impl` (in-scope). After Phase 5 the per-impl ➖ counts diverge — globally shared ➖ is 21, but ts has 23 (+ S1.1, S13a.10) and go has 22 (+ S13a.10), so denominators are: ts=186, rs=188, go=187. When recomputing, count each repo's ➖ from its own `docs/spec-compliance.md`.
5. When the template gains or loses an item, **all three per-repo files must be synced** before this matrix is rebuilt; otherwise the totals will be inconsistent.

## Last verified

2026-05-18 (Phase 6 #3c-followup) — re-rolled-up after [go.hocon#84](https://github.com/o3co/go.hocon/pull/84) landed (closes [go.hocon#81](https://github.com/o3co/go.hocon/issues/81)). go-only follow-up to Phase 6 #3c that adds parser-level numeric-key support (TokenFloat as key start, TokenInt/TokenFloat + adjacent unquoted/keyword tail concat, gated to prevent quoted-key re-split). S11.3 (`1.2.3 = x` → `["1","2","3"]`) and S11.4 (`10.0foo = x` → `["10","0foo"]`) closed as side effects — both rows removed from "Top spec violations" since all 3 impls are now ✅ on those items. S8.6 narrative tightened: us08/us09 now pass; status remains ⚠️ in all 3 because us13/us15 strict lex-time rejection is still a known gap. Rate change (in-scope) — go only: 85.0% → 86.1% (+1.1pp; +2 cells flipped ❌ → ✅). ts/rs unchanged. Multi-reviewer cycle (3 rounds Claude + Codex via mandatory `codex exec < INPUT > OUTPUT` file pipe) caught one Critical-class regression (quoted-key concat re-split — `"a.b"c = 1` was silently accepted as path `["a","bc"]` in round 1, fixed via `prevKeyTokenIsNumeric` gate) and two Important-class asymmetries (keyword-tail tokens not in concat predicate — round 2, fixed; signed-numeric multi-tail `123-456 = 1` — round 3, deferred to [go.hocon#83](https://github.com/o3co/go.hocon/issues/83)). Each finding was a real correctness issue that single-reviewer review would have missed.

2026-05-18 (Phase 6 #3c) — re-rolled-up after the third Phase 6 impl-gap wave landed in all three impls ([ts.hocon#96](https://github.com/o3co/ts.hocon/pull/96) + [ts.hocon#97](https://github.com/o3co/ts.hocon/pull/97), [rs.hocon#86](https://github.com/o3co/rs.hocon/pull/86), [go.hocon#82](https://github.com/o3co/go.hocon/pull/82)). S8.6 partially cleared in all 3 (1 item × 3 impls = 3 cells flipped from ❌ to ⚠️). Rate change per impl (in-scope): ts 88.4% → 88.2% (−0.2pp), rs 89.9% → 88.8% (−1.1pp), go 84.8% → 85.0% (+0.2pp). The rate movement is intentionally mixed because ❌ → ⚠️ flips contribute +0.5/N to the numerator but the develop-branch state since the previous Phase 6 #2 roll-up also included unrelated `✅` → `⚠️` shifts in ts/rs (other tracked items reclassified), so the net per-impl delta varies. Architecture divergence (intentional): ts/rs took Option B (single peek-ahead in unquoted-only lexer); go took Option A (greedy-with-backtrack `lex_number` per HOCON.md number grammar, since the lexer already has TokenInt/TokenFloat). Multi-reviewer convergence on the `parseSubstBody` segment-start gate: Claude+Codex flagged the missing `!curStarted` gate on rs.hocon PR #86, which then surfaced the same bug already merged in ts.hocon PR #96 (fixed via follow-up PR #97); go.hocon PR #82 implemented the gate correctly from the start based on rs/ts learning. Side-effect fix (go-only): CI `.github/workflows/test.yml` now uses `-coverpkg=./...` so cross-package coverage is measured correctly for codecov (was falsely failing patch-coverage on PRs with cross-package integration tests). Two strict-spec gaps remain `it.fails`/`#[should_panic]`/`t.Skip` tripwires (us13 `01`, us15 `1e+x`); go.hocon additionally defers parser numeric-key support for us08/us09 to [go.hocon#81](https://github.com/o3co/go.hocon/issues/81).

2026-05-17 (Phase 6 #2) — re-rolled-up after the second Phase 6 impl-gap wave landed in all three impls ([ts.hocon#95](https://github.com/o3co/ts.hocon/pull/95), [rs.hocon#85](https://github.com/o3co/rs.hocon/pull/85), [go.hocon#80](https://github.com/o3co/go.hocon/pull/80)). S15.1–S15.7 cleared from "Top spec violations" in all 3 (6 cells × 3 impls = 18 cells flipped from ❌ to ✅, plus S15.4 promoted from incidental-pass to explicit-guard ✅). Rate lift per impl (in-scope): ts 84.7% → 88.4% (+3.7pp), rs 85.4% → 89.9% (+4.5pp), go 81.6% → 84.8% (+3.2pp). Three new entries E2/E3/E4 in `extra-spec-conventions.md` document canonical-text-strict integer-key parse rule (`"00"` / `"+1"` / `"-0"` rejected via pre-filter regex despite native JS/Rust/Go parsers accepting them). Side-effect fixes from the separator-skip required by S15.3 simultaneously cleared S10.2 (rs), S10.14 (ts), S10.17 (rs), and S13.14 (ts, rs) — 5 additional cells flipped ❌/⚠️ → ✅. Multi-reviewer convergence on the multi-piece concat: 5/6 reviewers (3 Codex + 2 Claude general-purpose Opus) independently flagged that the initial single-pass loop variant produced wrong results for overlapping numeric keys; follow-up fixture [xx.hocon#11](https://github.com/o3co/xx.hocon/pull/11) (na03e-multi-piece-overlap) pinned Lightbend ground truth and all 3 impls refactored to true left-to-right pairwise fold via `joinPair`. rs.hocon additionally extended serde `deserialize_seq` + `deserialize_enum` (`OwnedHoconDeserializer`) to thread the conversion through typed `Vec<T>` deserialization.

2026-05-16 (Phase 6 #1) — re-rolled-up after the first Phase 6 impl-gap wave landed in all three impls ([ts.hocon#94](https://github.com/o3co/ts.hocon/pull/94), [rs.hocon#84](https://github.com/o3co/rs.hocon/pull/84), [go.hocon#78](https://github.com/o3co/go.hocon/pull/78)). S6.1/6.2/6.4 cleared from "Top spec violations" in all 3 (3 items × 3 impls = 9 cells flipped from ❌/⚠️ to ✅); S6.3 coverage broadened (BOM anywhere, not just start-of-input). Rate lift per impl: ts 83.3% → 84.7%, rs 84.0% → 85.4%, go 80.2% → 81.6% (in-scope). 3-way convergent intentional behavior changes recorded in PR descriptions and the "Cleared in Phase 6 #1" section above: BOM mid-stream is now whitespace; CR inside `${...}` is now inter-segment whitespace (was error). New `extra-spec-conventions.md` doc added (E-namespace, separate from S-namespace) capturing NEL handling (E1) as the first cross-impl extra-spec convention; S6.6 row from go.hocon's per-impl file (added in #78) was reclassified out of canonical scope and removed via [go.hocon#79](https://github.com/o3co/go.hocon/pull/79).

2026-05-13 (Phase 5) — re-rolled-up after Phase 5 per-impl `🤷` mop-up landed in all three impls ([ts.hocon#91](https://github.com/o3co/ts.hocon/pull/91), [rs.hocon#82](https://github.com/o3co/rs.hocon/pull/82), [go.hocon#76](https://github.com/o3co/go.hocon/pull/76)). 65 items total (ts 20 / rs 17 / go 28) cleared from `🤷` to verified ✅ / ⚠️ / ❌ / ➖. All three impls now have `🤷 = 0`. New per-impl ➖ added (NOT globally OOS): ts S1.1 + S13a.10, go S13a.10 — denominators diverge to ts=186, rs=188, go=187. New cross-impl violation rows: S3.1 expanded to 3-way (ts/go ❌, rs ⚠️), S18.4 3-way (ts/go ❌, rs ⚠️), S10.15 + S13.15 (rs+go ❌), S19.8 (ts+rs ❌), S23.4 (ts+go ❌), plus 12 single-impl rows (ts: S13a.3/S18.1/S22.2; rs: S10.2/S10.17; go: S1.1/S8.2/S11.3/S13a.12/S14a.10/S19.1/S19.2). Multi-reviewer convergence: Copilot caught S1.1 misclassification on go.hocon — initially marked ➖ on incorrect "Go strings are UTF-8" rationale; reclassified ❌ after probe showed silent U+FFFD substitution.

2026-05-13 (Phase 4) — re-rolled-up after Phase 4 type-conversion / values test-debt PRs landed in all three impls ([ts.hocon#90](https://github.com/o3co/ts.hocon/pull/90), [rs.hocon#81](https://github.com/o3co/rs.hocon/pull/81), [go.hocon#75](https://github.com/o3co/go.hocon/pull/75)). 12–13 items × 3 impls promoted from 🤷 to verified ✅ / ⚠️ / ❌ / ➖. S17.5 reclassified globally OOS (denominator: 21 OOS, in-scope N = 188). New cross-impl violation rows: S15.1–S15.3/S15.5–S15.7 (3-way ❌), S17.6 (ts ⚠️), S17.7/S17.8 (go ⚠️), S21.4 (ts+go ❌), S21.5 (go ❌). Multi-reviewer convergence on S15.3 across ts/rs/go: initial tests didn't exercise concatenation context; uniform rewrite to `[a] ${obj}` adopted.

2026-05-12 — re-rolled-up after Phase 3 substitution/include test-debt PRs landed in all three impls ([ts.hocon#85](https://github.com/o3co/ts.hocon/pull/85), [rs.hocon#77](https://github.com/o3co/rs.hocon/pull/77), [go.hocon#69](https://github.com/o3co/go.hocon/pull/69)). 11 items × 3 impls promoted from 🤷 to verified ✅ / ⚠️ / ❌; S13a.10 explicitly deferred as not externally observable. 3 new cross-impl violation rows added to Top spec violations (S13.9, S13.14, S13a.13); S13a.13 is convergent ❌ across all three impls; S13.14 has go as the lone compliant impl.
