# HOCON Spec Compliance Matrix

Cross-implementation roll-up of [`spec-checklist.md`](spec-checklist.md) for the four sibling implementations. This file is the public-facing one-page summary; per-item detail lives in each implementation's own `docs/spec-compliance.md`.

## Top-line compliance rate

| Implementation | Spec-total | In-scope | ✅ | ⚠️ | ❌ | 🤷 | ➖ |
|---|---:|---:|---:|---:|---:|---:|---:|
| [ts.hocon](https://github.com/o3co/ts.hocon/blob/develop/docs/spec-compliance.md) | **88.0%** | **98.9%** | 183 | 2 | 1 | 0 | 23 |
| [rs.hocon](https://github.com/o3co/rs.hocon/blob/develop/docs/spec-compliance.md) | **91.9%** | **100.0%** | 192 | 0 | 0 | 0 | 17 |
| [go.hocon](https://github.com/o3co/go.hocon/blob/develop/docs/spec-compliance.md) | **88.0%** | **98.4%** | 184 | 0 | 3 | 0 | 22 |
| [py.hocon](https://github.com/o3co/py.hocon/blob/main/docs/spec-compliance.md) | **53.1%** | **58.1%** | 103 | 16 | 0 | 72 | 18 |

Where:

- **Spec-total** = `(✅ + ⚠️·0.5) / 209`. Denominator includes ALL items, including out-of-scope. Out-of-scope items intentionally lower this number — it is the answer to "how much of HOCON.md does this implementation handle?".
- **In-scope** = `(✅ + ⚠️·0.5) / (209 − ➖_per_impl)`. The denominator is **per-impl** because each implementation can additionally mark items ➖ for language-natural reasons that don't apply to its siblings (e.g. ts marks S1.1 ➖ because JS strings are pre-decoded Unicode at the I/O boundary, but go cannot — Go `string` permits arbitrary bytes). Globally shared ➖ count is 17; per-impl: ts=23 (+ S1.1, S13a.10, S20.1–S20.4), rs=17, go=22 (+ S13a.10, S20.1–S20.4), py=18 (+ S1.1). This is the answer to "of what the implementation chooses to support, how much is covered?".
- `❌` and `🤷` contribute 0. `🤷` is treated as 0 because an unverified claim is, by policy, not a pass — pinning it as ✅/❌ requires a test. After Phase 5, all three impls reached `🤷 = 0`.

Both numbers are shown side by side so neither over-claims nor under-claims. See [`spec-checklist.md`](spec-checklist.md) for the convention rationale.

## Status legend

| Glyph | Meaning |
| --- | --- |
| ✅ | Test exists and passes |
| ⚠️ | Test exists, partial pass / pinning a spec-violating behavior |
| ❌ | Test exists and fails, OR known spec violation documented in source |
| 🤷 | No test — implementation claim only, unverified |
| ➖ | Out of scope (rationale required). May be **globally out of scope** (excluded by all four impls — see [Globally out-of-scope items](#globally-out-of-scope-items-17)) or **per-impl out of scope** (one impl excludes for language-natural reasons that don't apply to siblings, e.g. ts S1.1 because JS strings are pre-decoded Unicode at the I/O boundary). |

## Globally out-of-scope items (17)

These 17 items are marked `➖` in **all four** implementations, by policy. Some impls also mark additional items `➖` for language-natural reasons (e.g. ts S1.1, ts/go S13a.10, **ts/go S20.1–S20.4 since Phase 6 #3d**) — those are noted in the impl's own `spec-compliance.md`, not here.

| Items | Rationale class |
|---|---|
| S14a.4, S14f.5 | classpath resources are a JVM-only concept |
| S16.1 | MIME Type is set by HTTP servers, not parsers |
| S17.5 | `"null"` → null when null requested — none of the four implementations has a `getNull()`-equivalent typed accessor; spec L1244 is structurally inapplicable to their API models (added in Phase 4) |
| S23.5, S23.6 | `.properties` multi-line + Unicode escapes; documented simplification in each README |
| S24.1, S24.2 | reference.conf / application.conf are JVM conventions |
| S25.1 | System properties override is a JVM mechanism |
| S26.3 | `SecurityException` is a JVM-specific exception type |
| S1.2.6 | Unpaired surrogate codepoint — intentional language-natural divergence (Java accepts, Rust/Go reject) |
| S14a.2, S14e.4, S14e.5, S14f.1, S14f.6, S14f.8 | URL include unsupported by design across all four READMEs |

**Note: S20.1–S20.4 (Period Format)** moved from globally-OOS to **per-impl OOS** in Phase 6 #3d: rs.hocon implemented a `Period { years: i32, months: i32, days: i32 }` struct + `get_period` / `get_period_option` accessors via [rs.hocon#91](https://github.com/o3co/rs.hocon/pull/91), so rs is ✅ on S20.1–S20.4. ts and go still mark these ➖ per-impl (no `getPeriod` / `GetPeriod` API); py.hocon also implements Period (`get_period` / `Period`) at rs parity, so py is ✅ on S20.1–S20.4 like rs. This drops globally-OOS count from 21 to 17 and reduces rs's per-impl ➖ count from 21 to 17 (denominator 188 → 192).

See each item's `out-of-scope:` line in [`spec-checklist.md`](spec-checklist.md) for the full rationale.

## Top spec violations (verified)

Items where the test or implementation behavior contradicts the spec:

| Item | Impl | Status | Description |
|---|---|---|---|
| S1.1 | go | ❌ | Invalid UTF-8 (e.g. `string([]byte{0xff})` via `ParseString`) is silently substituted with U+FFFD instead of rejected; spec L117 requires rejection. Go `string` is `[]byte` and is not language-guaranteed UTF-8. ts ➖ (JS string is pre-decoded Unicode at the I/O boundary; the parser cannot observe raw bytes — see ts.hocon S1.1 entry). rs ✅ (Rust `&str` is language-guaranteed valid UTF-8; verified positively via `tests/testdata/hocon/bom.conf` fixture). |
| S3.4 | ts | ❌ | Unbraced root + stray `}` accepted ([#55](https://github.com/o3co/ts.hocon/issues/55)) |
| S8.1 | ts | ⚠️ | Lexer allows backtick in unquoted strings, contrary to spec L245 forbidden set |
| S8.2 | go | ❌ | `//` inside an unquoted run without preceding whitespace is treated as literal content; spec L248 says `//` starts a comment anywhere outside a quoted string. ts/rs ✅. |
| S13a.3 | ts | ⚠️ | Self-reference before any prior value (`a = ${a}`) raises a cycle error, but the error type / message classifies this as a generic substitution error rather than the "undefined" path the spec describes at L795. rs/go ✅ (correct error class). |
| S13a.12 | go | ❌ | Self-ref in a path expression (`${foo.a}` where `foo.a` is being defined) does not resolve to the "below" value per L831; the looked-up sub-object is discarded in the merge. ts/rs ✅. |

## Shared test debt

Spec items with no test coverage in **any** of the four implementations. These are the natural targets for future test-debt PRs:

- (Empty — Phase 4 cleared the last shared `🤷` cluster around S15/S17/S21; Phase 5 cleared per-impl `🤷` in all three impls, so the `🤷` column is `0` for ts/rs/go. py.hocon carries 72 `🤷` (ported-but-not-yet-pinned items, its verification surface still expanding), but none are *shared* debt — every item is already verified by at least one sibling, so this list stays empty.)

The next phase of compliance work shifts from "verify what we don't know" to "fix what we now know is broken" — see [Top spec violations](#top-spec-violations-verified) for the candidate list.

For behaviors that fall **outside** HOCON.md but should converge across the four impls (e.g. NEL handling), see [`extra-spec-conventions.md`](extra-spec-conventions.md) — separate E-prefix namespace, not counted in the matrix denominator.

### 2026-07-23 — S3.1 correction shipped in all four impls (same-day roll-up)

The four impl PRs implementing the corrected S3.1 (see the correction entry
below) merged the same day: [ts.hocon#153](https://github.com/o3co/ts.hocon/pull/153)
(squash `5fd8ade`), [go.hocon#155](https://github.com/o3co/go.hocon/pull/155)
(squash `0540e45`), [rs.hocon#146](https://github.com/o3co/rs.hocon/pull/146)
(squash `2def39b`), [py.hocon#11](https://github.com/o3co/py.hocon/pull/11)
(squash `d5a4aae`). Each removes the parser-entry reject guard, the include-path
special-cases, and the per-impl test overrides; ef01–ef06 now assert the
normative `{}` sidecars directly (py additionally folds the group into its
conformance and adapter corpora). All four flipped S3.1 ❌ → ✅ under the
corrected definition — a **behavior fix**, not a doc flip: empty / whitespace-only /
comment-only / BOM-only documents parse to `{}` uniformly at top level and on
every include path.

- **Rate change (restores pre-correction numbers, now with compliant behavior)**:
  ts 87.6% → **88.0%** spec-total / 98.4% → **98.9%** in-scope; rs 91.4% →
  **91.9%** / 99.5% → **100.0%** (in-scope board fully green again); go 87.6% →
  **88.0%** / 97.9% → **98.4%**; py 52.6% → **53.1%** / 57.6% → **58.1%**.
- S3.1 row removed from [Top spec violations](#top-spec-violations-verified).
- The ecosystem-conformance / bench empty-file holdout remains until the fixed
  **releases** ship (the bench measures released versions); it folds into the
  corpora at the next release cycle.
- Review notes: multi-agent review (Claude + Codex) per impl PR; Copilot rounds
  produced 7 minor fixes (assertion strengthening, wording, gofmt). A
  pre-existing gap surfaced during py review — array-root documents
  (`parse("[1,2]")`) are rejected by py.hocon (`_Parser.parse()` special-cases
  only `{`) — adjacent to S3.2/S3.3, tracked as a follow-up candidate.

### 2026-07-23 — S3.1 corrected: empty document is valid HOCON (`{}`); all four impls regress

The S3.1 checklist item previously read "Empty file is invalid — L130". That was a **misreading of the spec**: HOCON.md L130-132 ("JSON documents must have an array or object at the root. Empty files are invalid documents…") describes the *JSON baseline* that §Omit root braces then relaxes — the HOCON-normative sentence is L134-136, which parses any file not beginning with `[` or `{` (an empty file vacuously qualifies) as if enclosed in `{}`. The reference implementation confirms decisively: `ConfigDocumentParser.parse()` throws `"Empty document"` **only** in the `ConfigSyntax.JSON` branch, Lightbend's own test suite pins the rule as JSON-scoped (`// JSON does not support empty documents`), and `ConfigFactory.parseString("")` is used as a valid empty config throughout its tests. Full correction record: [E10](extra-spec-conventions.md#e10).

Consequences:

- **S3.1 redefined** in [`spec-checklist.md`](spec-checklist.md): empty / whitespace-only / comment-only / BOM-only documents parse to `{}`.
- **All four impls flip S3.1 ✅ → ❌** — each rejects at the parser entry via the cluster 3h (2026-05-19) guard, a behavior regression vs. their pre-3h releases (verified on ts.hocon v1.2.0: all 6 ef variants returned `{}`). The [cluster 3h entry](#cleared-in-phase-6-3h-2026-05-19) below records the original (now-revoked) rationale; it is retained as history.
- **E10 revoked as a divergence** — rewritten as the correction record. The go.hocon#105 include-path "carve-out" is now simply the rule; the remaining asymmetries (top-level parse rejects; ts package-include rejects whitespace-only but accepts zero-byte) are part of the S3.1 violation.
- **ef01–ef06 `{}` sidecars are normative as-is**; per-impl override lists (`IMPL_OVERRIDE_ERRORS` and equivalents) must be dropped. `GenerateExpected.java` already emits `{}` — no fixture regeneration needed.
- **Rate change**: ts 88.0% → **87.6%** spec-total / 98.9% → **98.4%** in-scope; rs 91.9% → **91.4%** / 100.0% → **99.5%**; go 88.0% → **87.6%** / 98.4% → **97.9%**; py 53.1% → **52.6%** / 58.1% → **57.6%**. Per-impl `spec-compliance.md` rows flip in each impl's fix PR.
- **Triage rule alignment**: this is the first application of the lightbend-as-spec-interpretation-authority rule (`differential/known-divergences.json`) to an existing E-item — Lightbend's behaviour is not "indefensible under any reasonable reading of the spec" here; it is the plain reading.

Fix tracking: per-impl PRs remove the guards + overrides (ts.hocon, go.hocon, rs.hocon, py.hocon), then the ecosystem-conformance empty-file exclusion is lifted once fixed releases ship.

### 2026-07-14 re-roll-up — S19.8 cleared (ts/rs) + 11 stale cells synced to test ground truth

S19.8 (duration unit names must be lowercase, L1304) cleared in ts.hocon ([#151](https://github.com/o3co/ts.hocon/pull/151)) and rs.hocon ([#144](https://github.com/o3co/rs.hocon/pull/144)) — both impls removed the unit lowercasing before matching (BREAKING: `"5 MS"` / `"100 Seconds"` now error), aligning with go.hocon which was already compliant. rs additionally became consistent with its own `parse_period`, which matched case-sensitively all along.

The same audit found the matrix and several per-impl rows stale relative to test ground truth (fixes had landed without the doc/matrix sync). Verified by running the cited tests and runtime probes per impl:

- **ts**: S13b.2 ❌→✅ (ts#81 fixed — `+=` on non-array errors), S17.6 ⚠️→✅ (ts#88 fixed — all typed accessors throw on null), S22.2 ❌→✅ (non-object fallback barrier respected).
- **rs**: S13.14 ⚠️→✅ (rs#75), S13.15 ❌→✅, S13b.2 ❌→✅ (rs#72), S17.6 ⚠️→✅ (rs#80 / `a7d7aea`), S14c.2 ❌→✅ (rs#44 — was already noted fixed in the per-impl file), and S13.9 per-impl cleanup (the matrix had already ruled it ✅ in the v1.5.0 audit below; the per-impl row and obsolete `#[ignore]` test are now aligned with that canon).
- **go**: S10.15 ❌→✅ (go#83), S11.8 ❌→✅ (go#66 — parseKey accepts TokenBool/TokenNull), S13.11 ⚠️→✅ (go#45, verified by nested-include runtime probe), S13.15 ❌→✅ (go#78), S17.7/S17.8 ⚠️→✅ (go#72 closed by-design: panic accessors satisfy the conversion-error requirement; Option accessors are a soft try-get matching rs).

Rate change: ts 86.4% → **88.0%** spec-total / 97.0% → **98.9%** in-scope; rs 88.5% → **91.9%** / 96.4% → **100.0%** (zero in-scope ❌/⚠️ — first impl to reach a fully green in-scope board); go 86.4% → **88.0%** / 96.5% → **98.4%**. Remaining violations: 6 cells across 3 impls (ts S3.4/S8.1/S13a.3, go S1.1/S8.2/S13a.12).

### v1.5.0 work — S13.9 rs mis-classification corrected (Lightbend ground-truth verification, 2026-05-22)

During v1.5.0 PR review cycle, [rs.hocon PR #111](https://github.com/o3co/rs.hocon/pull/111) (the proposed fix for [rs.hocon#74](https://github.com/o3co/rs.hocon/issues/74)) was closed without merge after multi-agent review (Claude + Codex) raised a Critical spec-interpretation concern. Lightbend reference verification via `ProbeS13_9.java` (committed in this PR's `generate/`):

```java
ConfigFactory.parseString("HOME = null\nresult = ${?HOME}").resolve();
// rendered:   {"HOME":null, "result":null}      // field PRESENT in tree as null
// hasPath:    false                              // getter API filters null as absent
// entrySet:   []                                 // typed-view filter null
```

Same result for the required form `result = ${HOME}`. The conclusion:

- **HOCON.md L630 "null treated same as missing" is a *getter-level* statement**, not a tree-level one. Lightbend keeps the field in the resolved tree as a null scalar; the typed-getter API (`hasPath`, `entrySet`, typed accessors) filters null out as "absent".
- **rs.hocon's pre-fix behavior** (field present as null scalar, env value blocked) matches Lightbend tree-level semantics.
- **The matrix's prior S13.9 row** (rs ❌, "should erase the field") was a mis-classification that conflated tree-level and getter-level semantics. ts.hocon and go.hocon were already ✅; rs.hocon is also actually ✅.
- **Getter-level null-rejection** is covered separately by S17.6 (`get_string` on null → error). rs.hocon had a S17.6 violation, fixed in [rs.hocon PR #109](https://github.com/o3co/rs.hocon/pull/109) (merged `6c1b95f` in v1.5.0 work).

Matrix changes:

- S13.9 row removed from [Top spec violations](#top-spec-violations-verified).
- rs.hocon top-table row: 182 ✅ → 183 ✅, 7 ❌ → 6 ❌. Spec-total **87.8% → 88.3%** (+0.5pp); in-scope **95.6% → 96.1%** (+0.5pp).
- Phase 3 historical entry annotated with audit-correction.

Companion cleanup:

- [rs.hocon#111](https://github.com/o3co/rs.hocon/pull/111) closed with full Lightbend probe explanation.
- [rs.hocon#74](https://github.com/o3co/rs.hocon/issues/74) closed as "not a spec violation."
- `generate/src/main/java/ProbeS13_9.java` + `generate/build.gradle.kts probeS13_9` task added to xx.hocon for future spec-verification audits.

### v1.4.0 retroactive matrix audit — S13.15 cleared, S17.6 rs mis-class corrected (2026-05-22)

After cutting v1.4.1 narrative roll-up, a full-test audit on all 3 impls at the v1.4.1 tag (go.hocon `84e73f4`, ts.hocon `8639cab`, rs.hocon `6346a5a`) surfaced two pre-existing drift items that the v1.4.0 roll-up missed (the v1.4.0 entry in "Last verified" was scoped to E12 addition only, not the bundled S13.15 cell flips). Both corrected here as the v1.5.0 work baseline:

- **S13.15 (rs ❌ → ✅, go ❌ → ✅, ts unchanged ✅)** — `foo = ${?bar}${?baz}` with both substitutions undefined now correctly omits the field (was returning `null` / empty-string scalar). All 3 v1.4.0 CHANGELOGs explicitly list this fix:
  - **go.hocon v1.4.0**: S13.15 multi-optional-undef concat materialisation: now correctly omits the field (was producing an empty-string value). Per HOCON.md L640. Bundled with E12 since the dr28 fixture surfaced the spec divergence.
  - **ts.hocon v1.4.0**: Optional substitution materialisation: `a = ${?x}${?y}` with both undefined now correctly omits field `a` (was incorrectly returning null).
  - **rs.hocon v1.4.0**: Optional `${?x}${?y}` where all operands are undefined → field omitted from result (HOCON.md §Substitutions L626–L645 concat materialisation rule).

  Audit verified post-fix state by running the existing `s13_15_spec_both_optional_undefined_field_absent` / `TestSpec_S13_15_BothUndefined_Spec` / E12 dr28 scenario tests in each impl at v1.4.1 — all pass (the rs `_spec` variant is no longer `#[ignore]`'d, the go `_Spec` variant no longer carries `t.Skipf`, the ts `it()` for the field-absent case is no longer `it.fails`). The S13.15 row is removed from [Top spec violations](#top-spec-violations-verified).

- **S17.6 rs ⚠️/❌ — pre-existing matrix mis-classification corrected** — matrix previously read "rs/go ✅" on S17.6 (`getString` / `get_string` on null should throw per L1252). rs reality post-v1.4.1 audit: `get_string` returns `Ok("null")` on null values (pre-existing `#[ignore = "spec violation ... see #80"]` test in `tests/integration_test.rs` documents the violation; pending fix in [rs#109](https://github.com/o3co/rs.hocon/pull/109)). The matrix entry now reads "ts ⚠️, rs ❌"; go remains ✅. **No net count/rate change for rs** — two cells flip (S13.15 ❌→✅ via the v1.4.0 fix; S17.6 ✅→❌ via the mis-class correction) and exactly cancel in both ✅ and ❌ tallies. rs spec-total / in-scope: 87.8% / 95.6% (unchanged).

Rate change from this audit:

- **go.hocon**: 85.4% → **85.9%** spec-total (+0.5pp; +1 ✅, −1 ❌ from S13.15). In-scope: 95.5% → **96.0%** (+0.5pp).
- **rs.hocon**: 87.8% / 95.6% **unchanged** (S13.15 +1 ✅ and S17.6 −1 ✅ mis-class correction cancel; ❌ count likewise stable since S13.15 −1 and S17.6 +1 cancel).
- **ts.hocon**: 85.9% / 96.5% **unchanged** (S13.15 was already classified ✅ pre-v1.4.0; the ts CHANGELOG suggests pre-v1.4.0 ts may itself have been silently broken — either it would have been 🤷 if no test pinned the case, or it was outright mis-classified ✅ if a test passed for the wrong reason. The matrix did not previously flag this; cannot be retroactively corrected since the v1.4.0 fix made the question moot).

Methodology note: this audit pattern (run full test suite at the release tag, cross-reference `#[ignore]` / `t.Skip` / `it.fails` against violations table, also against each impl's docs/spec-compliance.md) should run **after every release roll-up**, not just when triggered by a missed entry. The v1.4.0 "Last verified" entry below was scoped to "E12 added" because E12 was the headline; the bundled S-cell flips slipped through. For v1.5.0 work onward, each release's "Last verified" entry should include the full enumeration of touched S-cells via a `git log --oneline -- "**/docs/spec-compliance.md"` style grep, not just the headline feature.

### Released in v1.4.1 — Lightbend include-path divergences (2026-05-22)

Two cgordon-reported Lightbend Config divergences on the `include` code path landed simultaneously in all 3 impls. Both are pure include-path behaviour; no public API surface additions or signature changes (one E12 semantics refinement noted below); safe drop-in upgrade from v1.4.0. **No matrix-cell flips** — neither behaviour was in the previous [Top spec violations](#top-spec-violations-verified) table (they were silent footguns rather than tracked items). The work is recorded here to pin the cross-impl ground truth and the regression-test set so the merge logic does not silently regress when touched.

- **Empty / comment-only / whitespace-only included files → `{}` (Lightbend compatibility)** ([go.hocon#105](https://github.com/o3co/go.hocon/issues/105), reported by [@cgordon](https://github.com/cgordon)). `include "empty.conf"` (or comment-only / whitespace-only / BOM-only content) previously errored with `empty file is not a valid HOCON document (HOCON.md L130)`; Lightbend silently produces `{}` in this position, which unblocks the common optional-override-file pattern. The carve-out is **narrow**: applies only to the file-include code path, NOT to top-level empty parses (`parse("")` / `parseFile` on an empty top-level file still errors per S3.1 — see [E10](extra-spec-conventions.md#e10) for the divergence rationale). This narrows the [Phase 6 #3h S3.1 fix](#cleared-in-phase-6-3h-2026-05-19) so the include code path no longer enforces empty-file rejection; the top-level enforcement from #3h is preserved. E11 `include package(...)` already had its own zero-byte carve-out and is unchanged.

- **Include-as-if-written-inline ordering + self-referential append through include** ([go.hocon#106](https://github.com/o3co/go.hocon/issues/106), reported by [@cgordon](https://github.com/cgordon)). When an `include` directive appeared after an existing key in the parent file, go.hocon was incorrectly keeping the parent's value instead of overriding it from the included file. Lightbend treats included content as if it had been written inline at the include position. The companion fix: self-referential appends inside an included file (e.g. `steps = ${steps} [{ name = child }]`) now resolve against the parent's prior value, matching Lightbend. **go-only impl fix** — ts.hocon's `deepMergeResObjInto` and rs.hocon's `deep_merge_res_obj_into` already implemented src-wins + prior-capture correctly; ts.hocon and rs.hocon shipped cross-impl regression tests (7 scenarios each: scalar override, parent-after-include, self-referential append through include, same-file self-ref control, both-object deep-merge, nested-include scope isolation, sequential includes) without production-code changes, so the existing correct behaviour stays pinned.

- **E12 `AllowUnresolved` lenient self-ref defer semantics refinement (go-only)** — under `Resolve(opts.WithAllowUnresolved(true))`, a required self-referential `${k}` with no prior value used to error; it now defers the placeholder so a subsequent merge supplying a prior can complete resolution. This is **required by the include-ordering fix above** because the include child resolver runs in lenient mode and must defer rather than error on prior-less self-refs that the parent will later resolve. User-visible behaviour change is limited to `AllowUnresolved=true`; `Resolve()` default (`AllowUnresolved=false`) is unchanged. ts.hocon's and rs.hocon's deferred-resolution lifecycle already conformed to this semantics; go.hocon is now aligned. **E12 spec implication**: dr-series fixtures clarify that lenient mode defers, not errors, on prior-less self-references — see [E12](extra-spec-conventions.md#e12).

Per-impl placement:

- **go.hocon** ([PR #109](https://github.com/o3co/go.hocon/pull/109) `80f6d11` for #106, [PR #110](https://github.com/o3co/go.hocon/pull/110) `3dfb515` for #105, release [PR #112](https://github.com/o3co/go.hocon/pull/112) `84e73f4`):
  - **#106 resolver rewrite** — `internal/resolver/resolver.go` rewrites the `include` apply-loop to (a) detect scalar-vs-scalar and array-vs-anything overrides as src-wins, (b) deep-merge when both sides are objects, (c) capture the displaced prior into `obj.priorValues` (and the top-level `r.priorValues` only when `len(pathPrefix) == 0`, gating bare-leaf-key collisions caught by multi-agent review on the S13a.13 fix's neighborhood).
  - **#105 empty-include short-circuit** — `isEmptyOrCommentOnlyHocon` helper added in `internal/resolver/resolver.go` short-circuits the include-loader to `ObjectVal{}` when the included file body is empty after stripping comments/whitespace via the newly-exported `lexer.IsHoconWhitespace` (from `internal/lexer/lexer.go`).
  - **E12 `AllowUnresolved` semantics refinement** — see top-level bullet above.
- **ts.hocon** ([PR #121](https://github.com/o3co/ts.hocon/pull/121) `373392c` regression tests for #106, [PR #122](https://github.com/o3co/ts.hocon/pull/122) `4dbf00d` for #105, release [PR #125](https://github.com/o3co/ts.hocon/pull/125) `8639cab`): `src/internal/resolver/include-loader.ts` replaces `assertNonEmptyDocument` with `!hasContentTokens(tokens)` early-return in `loadSingle` / `loadSingleAsync`. Existing `deepMergeResObjInto` already does src-wins + prior-capture, so #106 needed no production-code change — 7 conformance tests added in `tests/issue106-include-ordering.test.ts` (6 include-path scenarios + 1 same-file self-ref control case proving the prior-capture path is unchanged by the include-path work).
- **rs.hocon** ([PR #107](https://github.com/o3co/rs.hocon/pull/107) `840be2e` regression tests for #106, [PR #108](https://github.com/o3co/rs.hocon/pull/108) `1836860` for #105, release [PR #112](https://github.com/o3co/rs.hocon/pull/112) `6346a5a`): `src/resolver/include_loader.rs` short-circuits to `Ok(ResObj::new())` when the included token stream has no content tokens. Existing `deep_merge_res_obj_into` already src-wins + prior-captures, so #106 needed no production-code change — 7 conformance tests added in `tests/issue106_include_ordering.rs` (6 include-path scenarios + 1 same-file self-ref control case).

Lightbend ground-truth verification: both behaviours cross-checked against typesafe-config 1.4.3 (the cross-impl reference per [`AGENTS.md`](../AGENTS.md)). The "Lightbend silently accepts empty include = empty config" path goes through `Parseable.parse` → `Tokenizer.tokenize` returning an EOF-only stream → `Parser` producing `SimpleConfigObject.empty()`. The "include is as if written inline" semantics are encoded in `SimpleIncluder.includeWithoutFallback` → field-level override propagation through `AbstractConfigObject.peekAssumingResolved`. Neither is novel — both are documented as Lightbend behaviour; the v1.4.1 fixes close the divergence in the o3co cross-impl stack.

Multi-agent-review cross-impl convergent learnings during the v1.4.1 rollout:

- **S13a13-neighborhood priorValues collision** — Claude + Codex independently flagged on go.hocon PR #109 (the #106 fix). The initial patch wrote to `r.priorValues[segmentsToKey(segments)]` unconditionally during nested `set_path` base-case, which silently collided with bare-leaf-keys defined at the top level (e.g. `foo` in `foo { a = "x" }` got prior-stamped at key `"a"`, clobbering the actual top-level `a`'s prior). Convergence-promoted to must-fix per the multi-reviewer convergence rule; fix is the `len(pathPrefix) == 0` gate so root-namespace writes only land at the actual root scope. Regression-pinned by 4 cross-namespace tests in go.hocon's issue106 suite (`top_level_unaffected_by_nested_prior_write`).
- **HOCON whitespace coverage** — Codex flagged the initial #105 patch using ASCII-subset whitespace stripping (space, tab, CR, LF, FF, VT) instead of HOCON's full whitespace set (Java `Character.isWhitespace` + Unicode separators per HOCON.md L184). Promoted to must-fix; fix exports `lexer.IsHoconWhitespace` and routes the include-empty-check through it. Cross-impl rs.hocon's tokenizer already uses Lightbend-equivalent whitespace classes via `unicode_xid` + explicit BOM/NEL/U+2028/U+2029 carve-outs; ts.hocon uses `WHITESPACE_REGEX` covering the same set. Single-impl gap (go) closed.
- **Block-comment carve-out** — go.hocon's initial #105 patch added a special-case for `/* ... */` block comments inside the empty-check. Codex flagged that this duplicated lexer logic and could mask malformed includes (unterminated block comment silently treated as empty). Promoted to must-fix; the special-case was removed — the lexer's existing tokenization handles block comments uniformly (terminated ones lex to nothing, unterminated ones raise a parse error before the empty-check runs).

Rate change: **none on any cell.** This release is recorded for ground-truth pinning, not for matrix movement. Both behaviours were silent divergences not represented in the previous violation table; the regression-test sets (`issue105_*` + `issue106_*` across the 3 impls — 7 scenarios per impl for #106 ordering including a same-file self-ref control case, and per-impl coverage of empty/comment/whitespace/BOM for #105) are the durable artifact.

Released: [go.hocon v1.4.1](https://github.com/o3co/go.hocon/releases/tag/v1.4.1), [ts.hocon v1.4.1](https://github.com/o3co/ts.hocon/releases/tag/v1.4.1), [rs.hocon v1.4.1](https://github.com/o3co/rs.hocon/releases/tag/v1.4.1) (2026-05-22).

### Cleared in Phase 6 #4 — S10.8 unquoted space-concat in field keys (2026-05-22)

S10.8 fully cleared across all 3 impls (`ts ❌ / rs ⚠️ / go ❌ → all ✅`) via the unquoted space-concat key fix ([ts.hocon#128](https://github.com/o3co/ts.hocon/pull/128) rebase `1fc0582`, [rs.hocon#115](https://github.com/o3co/rs.hocon/pull/115) rebase `ebb06f4`, [go.hocon#114](https://github.com/o3co/go.hocon/pull/114) squash `f238996`). Closes [ts.hocon#76](https://github.com/o3co/ts.hocon/issues/76), [rs.hocon#66](https://github.com/o3co/rs.hocon/issues/66), [go.hocon#65](https://github.com/o3co/go.hocon/issues/65). HOCON.md L317 ("string value concatenation is allowed in field keys") + L553-560 (`a b c : 42` ≡ `"a b c" : 42`) require unquoted whitespace-separated tokens in key position to merge into a single path element. Previously all 3 impls rejected `foo bar = 1` at parse time (`unexpected token after key` / `expected ':', '=' or '{' after key`); rs partially passed because quoted-key variant `"foo bar" = 1` worked.

- **S10.8** (ts ❌ → ✅, rs ⚠️ → ✅, go ❌ → ✅) — `parseKey` / `parse_key` gain a `spaceConcat` flag: when the next key token has `precedingSpace`/`PrecedingSpace`, the first dot-split piece merges into the LAST existing segment with a literal space; remaining pieces become new path segments. Behaviour pinned by 12–13 regression tests per impl (basic 2-token, 3-token spec L556 example, dotted-prefix + dotted-tail concat, quoted+unquoted concat, inline-object shorthand, four leading-dot edge cases, tab whitespace).

Leading-dot interaction (S10.8 + S11.1): if the spaced-in token starts with `.`, the leading `.` is a path separator that survives the space — not a literal char to fold into the previous segment. `a .b = 1` → `["a", "b"]`, `a.b .c = 1` → `["a", "b", "c"]`. This edge case was flagged on the ts.hocon companion PR by Claude + Codex + Copilot **independent review (3-way convergence)** — rs.hocon and go.hocon ports both pre-emptively handle it. ts implements via a `raw.startsWith('.')` guard inside the spaceConcat merge; rs same; go via the pre-existing leading-dot continuation branch which fires before space-concat (incidental correctness preserved by ordering).

Multi-reviewer convergent findings during the rollout:

- **Leading-dot priority** (Claude internal + Codex internal + Copilot on PR — 3 independent reviewers on the ts.hocon companion PR; auto-promoted to must-fix per CLAUDE.md). Cross-ported to rs/go before initial PR submission.
- **S8.6-in-key over-strict vs Lightbend** (Codex flagged the partial case on rs.hocon #115; FCoT-driven Lightbend probe via direct typesafe-config 1.4.3 JVM call revealed the broader gap). All 3 impls currently enforce S8.6 leading-`-` rule in key position; Lightbend's typesafe-config accepts `foo -bar = 1`, `foo bar.-baz = 1`, and `-foo bar = 1` without enforcing S8.6 at the path-element level. **Out of scope for the S10.8 v1.5.0 work**; tracked for follow-up as [xx.hocon#42](https://github.com/o3co/xx.hocon/issues/42).
- **Trailing-dot-then-whitespace divergence** (uncovered while addressing codecov gap on go.hocon #114). Lightbend preserves leading whitespace on the post-dot segment (`a b. c = 1` → `{"a b":{" c":1}}` with leading-space `" c"`); none of the 3 impls preserve it (`{"a b":{"c":1}}`). Also tracked in [xx.hocon#42](https://github.com/o3co/xx.hocon/issues/42).

Rate change (in-scope): ts 96.5% → 97.0% (+0.5pp), rs 96.1% → 96.4% (+0.3pp), go 96.0% → 96.5% (+0.5pp). +1 ✅ across all 3 impls; ts: −1 ❌, rs: −1 ⚠️, go: −1 ❌. Top-line spec-total: ts 85.9% → 86.4%, rs 88.3% → 88.5%, go 85.9% → 86.4%.

Released: [ts.hocon v1.5.0](https://github.com/o3co/ts.hocon/releases/tag/v1.5.0), [rs.hocon v1.5.0](https://github.com/o3co/rs.hocon/releases/tag/v1.5.0), [go.hocon v1.5.0](https://github.com/o3co/go.hocon/releases/tag/v1.5.0) (TBD).

### Cleared in Phase 6 #3i — paren-in-unquoted-string (2026-05-21)

S8.1 fully cleared (go ❌ → ✅) via the paren-in-unquoted-string fix ([xx.hocon#34](https://github.com/o3co/xx.hocon/issues/34) external report by @cgordon — second issue from outside o3co, [xx.hocon#35](https://github.com/o3co/xx.hocon/pull/35) spec PR commit `5b9c1ba`, [ts.hocon#111](https://github.com/o3co/ts.hocon/pull/111) wire-up `4116e98`, [rs.hocon#102](https://github.com/o3co/rs.hocon/pull/102) wire-up `6f6f0f0`, [go.hocon#101](https://github.com/o3co/go.hocon/pull/101) impl squash `b57ff25`). HOCON.md L274 forbidden set does NOT include `(` or `)`; ts.hocon and rs.hocon already matched the spec — go.hocon was the only outlier (lexer emitted standalone `TokenLParen`/`TokenRParen` unconditionally + `unquotedForbidden` const included `()`).

- **S8.1** (go ❌ → ✅) — Lexer's standalone paren tokenization removed at the value-position dispatch; `unquotedForbidden` const no longer includes `()`. `parseInclude` rewritten to string-match on the unquoted token value for the include resource forms (`file(`, `required(`, `classpath(`, `url(`), mirroring ts.hocon's `parseInclude` structure. `TokenLParen` / `TokenRParen` constants removed from `internal/lexer/lexer.go` (no longer emitted; previously only consumed inside `parseInclude` — `internal/`-scoped per Go path rule, non-breaking).

Architecture divergence (intentional — go.hocon is **stricter** than the pure ts.hocon mirror to close silent-data-loss footguns surfaced by multi-agent review):

- **go.hocon** (`b57ff25`): parens are unquoted-continue chars; `skipToIncludePath` helper restricts pre-path advance to genuine include-syntax noise tokens (bare `(`, `file`/`url`/`classpath` with optional `(`-prefix); post-path advance restricted to `)`-only tokens via `onlyClosingParens`. Bare `file`/`required` (whitespace before `(`) requires `(` to follow. Exact `file(` / `url(` / `classpath(` prefix checks inside `required(...)` (replacing `strings.HasPrefix("file")` which false-matched `fileX(`). Pinned by 6 conformance fixtures `unquoted-parens/up01-up06` + 10 regression tests for the multi-agent hardening (`TestIncludeFile_DoesNotSilentlyMaskMalformedIncludes` + `TestIncludeFile_DoesNotConsumeNextFieldOnSameLine`).
- **ts.hocon** + **rs.hocon**: already spec-compliant — both impls' lexers allow `(` `)` in unquoted runs. Wire-up PRs added the new conformance fixtures with no impl change and no version bump (will bundle into the next normal release).

Cross-impl convergent learnings flagged during the rollout:

- Multi-agent review surfaced the same root issue across rounds: Codex caught the pre-path skip silent-data-loss in round 1; Copilot caught the symmetric post-path skip bug in round 2 plus the bare-resource-word `(` enforcement gap. Convergence-promoted fix shape: `isIncludeSkipToken` (gate pre-path) + `onlyClosingParens` (gate post-path) helpers, plus exact-prefix resource-name detection.
- ts.hocon mirror is NOT always strict — `parseInclude` in ts.hocon has the same skip-loop silent-data-loss and `startsWith` false-match bugs that go.hocon's fix closed. Filed upstream as [ts.hocon#113](https://github.com/o3co/ts.hocon/issues/113).
- Phase 1 spec PR (xx.hocon#35) added a transient ❌ row + lowered go.hocon's top-line to 84.9%/94.9% to truthfully reflect the newly-documented gap before the fix landed. This #3i clear restores 85.4%/95.5%.

Rate change (go.hocon, in-scope): 94.9% → 95.5% (+0.6pp restoration). +1 ✅ −1 ❌ on go (1 cell total). ts/rs unchanged.

Released: [go.hocon v1.3.1](https://github.com/o3co/go.hocon/releases/tag/v1.3.1) (2026-05-21).

### Cleared in Phase 6 #3c Phase 3 — E8 amendment (2026-05-20)

S8.6 fully cleared (⚠️ → ✅) in all 3 impls via the E8 amendment ([xx.hocon#31](https://github.com/o3co/xx.hocon/issues/31) external report by @cgordon — first issue from outside o3co, [xx.hocon#32](https://github.com/o3co/xx.hocon/pull/32) spec rewrite commit `dd102e8`, [ts.hocon#108](https://github.com/o3co/ts.hocon/pull/108) squash `569a7d6`, [rs.hocon#98](https://github.com/o3co/rs.hocon/pull/98) squash `e56d70f`, [go.hocon#97](https://github.com/o3co/go.hocon/pull/97) squash `9aa9238`). xx.hocon E8 was rewritten to adopt Lightbend's pragmatic reading of HOCON.md L270-276: **"begin" = value-position begin** (first component of a concatenation), not token-position begin at any lexer offset. Phase 6 #3c Phase 2 (the original strict-spec posture from cluster #3c) is retracted.

- **S8.6** (ts ⚠️ → ✅, rs ⚠️ → ✅, go ⚠️ → ✅) — Value-position `-` not followed by a digit now lexes as unquoted text (`a = -foo` → `{"a":"-foo"}`, `a = -` → `{"a":"-"}`). Concat-continuation after value-tokens accepted (`b = ${a}-bar` → `"foo-bar"`; symmetric for `"foo"-bar`, `${a}1bar`, `${a}.bar`). Value-position digit-leading runs normalized via the impl's int parser (`a = 01` → number 1 with canonical raw text `"1"`; `a = -0` → `"0"`). `+` reservation enforced at value-start (`+foo`) and concat-continuation (`${a}+bar`). us15 `a = 1e+x` no longer a known gap in any impl (closed by the `+` reservation enforcement). Subst-body path expressions (`${-foo}`) and key-path segments (`a.-foo = 1`) **out of E8 scope** — strict reject preserved. Issue trackers closed/superseded: [ts#73](https://github.com/o3co/ts.hocon/issues/73), [rs#63](https://github.com/o3co/rs.hocon/issues/63), [go#60](https://github.com/o3co/go.hocon/issues/60).

Architecture divergence (intentional, by per-impl token-model differences):

- **ts.hocon** (Option B): single Unquoted token kind; lexer's strict `-` reject removed at the unquoted-start branch. Numeric coercion stays at the resolve layer (`Number()` on the unquoted token). F3 `a = 01` BREAKING — value `01` now resolves to number `1` (was unquoted string `"01"`).
- **rs.hocon** (Option B): same shape as ts; lexer's strict `-` reject removed. `parse_scalar_value` (`src/parser.rs`) i64-first normalization → `ScalarValue::number("1")` from raw `"01"`. Multi-agent-review caught a Codex-discovered correctness bug: Rust's `f64::parse("-inf")` was admitting `-inf` / `-nan` as numbers; added a JSON-number-shaped entry gate (`-` requires next digit) to keep these on the string path.
- **go.hocon** (Option A): separate `TokenInt` / `TokenFloat` token kinds. Dispatch peek-ahead at `nextToken` routes `-` to `readNumber` only when followed by a digit. The pre-existing `+` reservation gap (go.hocon accepted `+foo` as unquoted; ts/rs/Lightbend rejected) closed in the same PR by emitting `TokenError` from the bare-`+` dispatch. Normalization is **value-position only** in `parser.parseSingleValue` — multi-agent-review caught a Codex-discovered bug where lexer-level normalization was silently renaming keys (`01 = x` → `"1"`). F3 BREAKING for `GetString` callers (`a = 01` → `"1"`, was `"01"`); typed getters and JSON serialization unaffected.

Cross-impl convergent learnings flagged during the rollout:

- Sidecar pre-check pattern (`fp.exists()` / `jp.exists()` with "run `make testdata`" guidance) — Copilot raised on rs.hocon#98; proactively applied in go.hocon#97 before review.
- Lex-test flips with name change (Pre-E8 `..._Rejected` → `..._E8_Accepted`) over `it.fails` / inverted-skip tripwires keep the post-E8 contract documented in the test name.
- `Refs #N` over `Closes #N` for downstream impl PRs to avoid GitHub's auto-close firing before the cross-impl rollout completes — xx.hocon#32 auto-closed #31 on merge, requiring a manual reopen + status comment until all 3 impl PRs landed. Convention update applied for future E-item PRs.

Rate change (in-scope): ts 96.2% → 96.5% (+0.3pp), rs 95.3% → 95.6% (+0.3pp), go 95.2% → 95.5% (+0.3pp). +1 ✅ −1 ⚠️ per impl (3 cells total).

### Cleared in Phase 6 #3h (2026-05-19)

S3.1 (empty file rejection, HOCON.md L130), S21.4 (single-letter byte abbreviations binary, HOCON.md L1385), and S23.4 (`.properties` object-wins, HOCON.md L1485) landed in all 3 impls via [ts.hocon#106](https://github.com/o3co/ts.hocon/pull/106), [rs.hocon#94](https://github.com/o3co/rs.hocon/pull/94), and [go.hocon#93](https://github.com/o3co/go.hocon/pull/93). xx.hocon ground truth pinned by 19 fixtures across 3 new groups in [xx.hocon#29](https://github.com/o3co/xx.hocon/pull/29): `empty-file/ef01–ef06`, `byte-single-letter/bsl01–bsl09`, `properties-conflict/pc01–pc04`. **Two pre-existing mis-classifications corrected in rs.hocon** (see Mis-classification audit below).

- **S3.1** (ts ❌ → ✅, rs ⚠️ → ✅, go ❌ → ✅) — Empty document (`""`, whitespace-only, comments-only, BOM-only, mixed) now raises `ParseError` per HOCON.md L130. Detection runs post-tokenize on the token stream excluding skip-tokens (EOF, Newline, BOM, comments — all consumed by each impl's lexer). Each impl additionally applies the guard inside its include-loader path (caught by multi-agent review as cross-impl convergent gap).
- **S21.4** (ts ❌ → ✅, **rs BREAKING** ✅-misclassified → ✅-correct, go ❌ → ✅) — Single-letter `K/k/M/m/G/g/T/t/P/p/E/e` byte abbreviations as powers of two per spec L1385 (java `-Xmx` convention). Lightbend typesafe-config 1.4.3 verified ground truth: `1K=1024`, `1M=2^20`, ..., `1E=2^60`. Multi-letter forms (`KB`/`MB`/`GB`/`TB`) remain SI decimal. `Z`/`Y` deferred (spec L1383 lists them but byte counts overflow i64 — separate cluster). Overflow-checked multiplication added on all 3 impls; fractional path uses precision-safe boundary (`math.Exp2(63)` in go, `2f64.powi(63)` in rs, `Number.MAX_SAFE_INTEGER` in ts).
- **S23.4** (ts ❌ → ✅, **rs ✅-misclassified → ✅-correct**, go ❌ → ✅) — `.properties` dotted-key conflict applies object-wins per spec L1485. Each impl's properties loader now: (a) sorts keys before iteration (for input-order independence), (b) replaces scalar with empty Object on non-leaf conflicts (scalar discarded per L1487), (c) skips overwrite at leaf when existing value is already an Object. Cross-impl convergent fix shape.

Architecture (uniform across 3 impls):

- **S3.1**: shared empty-token-stream helper called from both top-level parse entry AND include-loader path. Each impl's helper takes a source descriptor so include-path errors mention the included file.
- **S21.4**: single-letter entries added to the byte multiplier map as powers of two. Overflow guards: i64 / int64 `checked_mul` (rs) or `result/mult != n` (go) or `MAX_SAFE_INTEGER` boundary (ts) on integer path; precision-safe float64 boundary (`2^63` exact value) on fractional path.
- **S23.4**: `parseProperties` / `properties_to_hocon` / `propsToObjectVal` rewritten with sorted-key iteration + object-wins at both leaf and non-leaf segments. Conflict resolution is deterministic regardless of input line order.

Per-impl placement:

- **ts.hocon**: `src/internal/parser/empty-check.ts` (new shared helper `assertNonEmptyDocument`); `src/parse.ts buildResolveContext()` and `src/internal/resolver/include-loader.ts` both call it. `src/coerce.ts BYTE_UNITS` map gains K/k/M/m/G/g/T/t/P/p/E/e (powers of two); MAX_SAFE_INTEGER overflow guard on BOTH unit-less and with-unit paths. `src/internal/properties/properties.ts parseProperties` sorts collected pairs by key before calling `setNested`; `setNested` last-segment write guards on existing-object presence.
- **rs.hocon**: `src/lib.rs assert_non_empty_document` private helper called from `parse_with_env`, `parse_file_with_env`, AND `resolver/include_loader.rs`. `src/config.rs parse_bytes` multiplier match: `"K" | "k" => 1_024`, `"M" | "m" => 1_048_576`, ..., `"E" | "e" => 1_152_921_504_606_846_976`; multi-letter `"KB"`, `"MB"`, etc. remain in separate match arms (SI decimal). `tests/units_default_test.rs ub05_bytes_with_unit` updated `1_024_000 → 1_048_576` (load-bearing mis-classification correction signal). `src/properties.rs set_nested` rewritten: leaf-Object skip + non-leaf scalar-replace-with-Object; `properties_to_hocon` sorts keys via `Vec<&String>` + `.sort()` before iteration.
- **go.hocon**: `internal/parser/parser.go parseRoot` rejects when only `TokenEOF`/`TokenNewline` remain post-lex. `config.go multipliers` map gains single-letter K-E entries; overflow check uses `result/mult != n` on integer path; fractional path uses `prod >= math.Exp2(63)` (the exact float64 value of 2^63, precision-safe vs `> math.MaxInt64` which rounds up in float64). `internal/resolver/resolver.go propsToObjectVal` rewritten: non-leaf scalar conflict replaces scalar with new `ObjectVal` and descends; leaf existing-object skip.

Mis-classification audit (surfaced during spec drafting + Codex spec review + multi-agent review):

1. **rs.hocon S21.4** — prior ✅ status cited `get_bytes_no_space` test that exercised `512MB` (multi-letter SI), never single-letter K/M/G/T. Underlying `parse_bytes` mapped single-letter `K` to `1_000` (SI decimal) contradicting spec L1385 (powers of two). **BREAKING fix landed in #3h** (`1K` was 1000, is now 1024); CHANGELOG.md has migration table. Matrix cell remains ✅ post-fix (behavior corrected).
2. **rs.hocon S23.4** — prior ✅ status cited `converts_to_hocon_value` test using `a.b=1\nc=hello` (no conflict). Underlying `set_nested` silently dropped data on conflict (leaf unconditional overwrite + non-leaf scalar conflict silently skipped). **Non-BREAKING correction landed in #3h** (corrects silent-data-loss bug; no reasonable consumer relied on the prior behavior). Matrix cell remains ✅ post-fix.

Spec ★1 decision deferrals:

- `Z`/`Y` single-letter byte units deferred to a future cluster pending uniform overflow policy and potential BigInt accessor design.
- Lightbend-strict case rejection (`KB` upper+upper, `mb` lower+lower — Lightbend errors but all 3 impls currently accept) deferred — separate spec-strictness issue, doesn't affect the S21.4 matrix cell.

E-namespace updates:

- **E10** added to [`extra-spec-conventions.md`](extra-spec-conventions.md) documenting Lightbend's silent-accept-empty quirk (parse `""` → `SimpleConfigObject({})`) and the o3co strict-spec posture (parse error per L130). Per-impl override list on ef01–ef06 fixtures (same pattern as E5 ce05 / E8 us02/us03/us13 / E9 ir03/ir04).

Rate change (in-scope):

- ts: 94.6% → 96.2% (+1.6pp; +3 cells = S3.1/S21.4/S23.4 flipped ❌ → ✅).
- rs: 95.1% → 95.3% (+0.2pp net; +1 cell = S3.1 ⚠→✅; S21.4/S23.4 mis-class corrections preserve ✅ count but change underlying behavior).
- go: 93.6% → 95.2% (+1.6pp; +3 cells = S3.1/S21.4/S23.4 flipped ❌ → ✅).

Spec-total: ts 84.2% → **85.6%**, rs 87.3% → **87.6%**, go 83.7% → **85.2%**. **All three impls now ≥85% spec-total** — the cluster goal.

Multi-agent review observations during Phase 6 #3h:

1. **Convergent issue #1 — Empty-file guard not applied to included files** (ts Codex + rs Codex): the initial S3.1 fix guarded only the top-level parse entry; `IncludeLoader` / `include_loader` bypassed the check, so `include "empty.conf"` silently accepted. Convergent across 2 impls + 2 reviewers → must-fix per convergence rule. Fix: shared helper called from both entry and include paths.
2. **Convergent issue #2 — Fractional byte overflow at float64 boundary** (go Claude + go Codex + rs Claude): on go specifically, `prod > math.MaxInt64` is `2^63 > 2^63 = false` because `float64(math.MaxInt64)` rounds up to exactly `2^63`. `"8.0E"` slipped through silently, `int64(prod)` corrupted to `MaxInt64`. Rs had the same logical ordering issue (overflow check ran AFTER lossy `as i64` cast). 3 reviewers, 2 impls → must-fix. Fix: precision-safe boundary (`math.Exp2(63)` in go, hoisted check + tightened comparison in rs).
3. **ts unit-less byte overflow asymmetry** (ts Codex single-reviewer Important): `parseBytes('9007199254740993')` (unit-less, default-bytes path) silently rounded while `parseBytes('9007199254740993B')` (with-unit path) threw — inconsistent invariant. Fixed by applying same overflow guard to unit-less path.

Pre-existing bugs filed as follow-up (out of scope, per "unrelated issues → separate PR" rule):

- **rs.hocon parse_duration overflow** ([rs.hocon#95](https://github.com/o3co/rs.hocon/issues/95)) — same overflow-guard-missing pattern as the fixed `parse_bytes`, but on the duration path. Out of #3h scope (cluster targets S3/S21/S23, not duration). Surfaced by Claude rs.hocon #94 review (Important #2).

Cross-impl review feedback loop: 6 reviewers (3 Claude general-purpose Opus + 3 Codex) caught **3 issues** (2 convergent, 1 single-reviewer); all 3 addressed in-PR before merge. The convergent finding pattern (same shape flagged across 2 impls independently) again validates the multi-reviewer convergence rule's "default to must-fix" treatment — both fixes landed.

CI: all 22 required CI checks across the 3 impl PRs SUCCESS. go.hocon shows `codecov/project` FAILURE (-0.21pp from baseline drift); codecov is non-required per branch protection, `codecov/patch` is SUCCESS, same pattern as Phase 6 #3f. rs.hocon required one extra commit beyond the initial review-fix batch (`3cbc3b4`) to normalize Windows path separators in the new pc conformance test (HOCON tokenizer treats `\` in quoted strings as escape — Windows-only failure).

### Cleared in Phase 6 #3f (2026-05-19)

S13a.13 self-referential look-back no-prior short-circuit (HOCON.md L841) landed in all 3 impls via [ts.hocon#105](https://github.com/o3co/ts.hocon/pull/105), [rs.hocon#93](https://github.com/o3co/rs.hocon/pull/93), and [go.hocon#92](https://github.com/o3co/go.hocon/pull/92). xx.hocon ground truth pinned by 11 fixtures in `testdata/hocon/self-ref-lookback/` sr01–sr11 (10 `-expected.json` sidecars + sr05 `.error`). Includes regression guards for nested paths (sr09/sr10), array variant (sr07/sr08), required-vs-optional boundary (sr05/sr06), and mutual-ref non-self-ref boundary (sr11 forward-ref).

- **S13a.13** (3-way ❌ → ✅) — `a = ${?a}foo` with no prior `a` now resolves to `"foo"`, not `"foofoo"`. The self-ref look-back short-circuits when no prior value exists: optional → undefined (concat-fold omits); required → resolve error. The previous buggy fall-through re-resolved the current Concat (which contains `${?a}` and the literal `foo`), producing `"foo"` for the substitution and `"foofoo"` for the outer concat. Spec HOCON.md L841.

Architecture (uniform algorithm, divergent per-impl mechanism — see "Spec ★1 deviation" below): in `resolveSubst`, when the looked-up value at the substitution's path is detected as self-referential, check the `priorValues` map. If a prior exists, look-back returns it (unchanged regression behavior). If no prior, short-circuit: optional substitution yields nothing (concat-fold elides it via the existing Phase 6 #3b optional-omission rule); required substitution raises `ResolveError`. The two-spec interaction (S13a.13 short-circuit + S10.3b optional-omission) is mutually reinforcing.

Per-impl placement:

- **ts.hocon**: `src/internal/resolver/substitution-resolver.ts` — added a `WeakSet<ConcatPlaceholder> resolvingConcats` tracking which Concat nodes are currently being iterated by `resolveConcat` (push on entry, delete on finally). The `isSelfRef` short-circuit fires only when `found === a concat in resolvingConcats`. External lookups (`b = ${a}` where `a = ${?a}foo`) don't mark `a`'s concat as iterating → fall through to normal `resolveVal(found)` where the cycle guard handles inner self-refs correctly. Conformance test uses `existsSync` skip-guard matching the pattern in `tests/concat-errors.test.ts` / `tests/include-reservation.test.ts`.
- **rs.hocon**: `src/resolver/substitution_resolver.rs` — added a `resolving_field_path: Vec<String>` stack on the resolver. `resolve_res_obj` pushes leaf key before each `resolve_val` call and pops after (3 sites, balanced; Result-aware so `?` propagation doesn't leak). The `is_owner` guard requires `resolving_field_path == s.segments` (text-equal) before the self-ref short-circuit fires. Multi-segment prior lookup uses `lookup_path(prior_obj, &s.segments[1..])` to navigate nested object priors; nested-object-literal form (`foo { a = "x"; a = ${?foo.a}bar }`) has a leaf-segment fallback after root-segment lookup fails (caught by Copilot review).
- **go.hocon**: `internal/resolver/resolver.go` — two-pronged fix: (a) fast-path cache check at top of `resolveSubst` returns cached value immediately when `!r.resolving[key]` and the cache has the path (handles forward declaration order `a = ${?a}foo; b = ${a}`); (b) `isSelfRef` detection replaces structural path-equality (`slices.Equal(segTexts(...), segStrs)`) with AST-node pointer-identity (`sp == s`), firing only when the found value literally contains the substitution node being resolved (handles reverse declaration order via the existing `r.resolving` cycle guard). Removed the `r.priorValues[segmentsToKey(segments)] = existing` write in nested `setPath` base case to avoid bare-leaf-key collisions with top-level same-named fields (caught by Copilot review).

Cross-impl side effects:

- **Cycle-detection branch alignment** (ts.hocon): the cycle-detection branch's prior-lookup criterion was migrated from `s.prefixLen > 0` to `s.segments.length > 1` to match the new short-circuit branch. Both branches now consistently handle dotted-path-at-root cases (e.g. `${foo.a}` with `prefixLen=0` but `segments.length=2`). Convergent with Claude r2 review I2.

Spec ★1 deviation:

The S13a.13 design spec (`.claude/superpowers/specs/2026-05-17-s13a-self-ref-lookback-design.md`) ★1 Resolved Decision #1 declared *"Self-ref detection via path-equality is preserved... the existing detection mechanism is correct."* Round-2 multi-agent-review found this was wrong: structural path-equality detection misfires when an external field references a self-ref'd field's value (`a = ${?a}foo; b = ${a}` — `b`'s required `${a}` would error). All 3 impls independently arrived at 3 strictly-narrower mechanisms (ts WeakSet-of-iterating-concats / rs path-stack `is_owner` guard / go AST-node pointer-identity). All three are correct; choosing one canonical mechanism is deferred to a spec amendment in a follow-up xx.hocon PR (tracked in [xx.hocon#27](https://github.com/o3co/xx.hocon/issues/27) alongside 4 newly-surfaced pre-existing resolver bugs).

Rate change (in-scope):

- ts: 94.1% → 94.6% (+0.5pp; +1 cell = S13a.13 flipped ❌ → ✅).
- rs: 94.5% → 95.1% (+0.5pp; +1 cell).
- go: 93.0% → 93.6% (+0.5pp; +1 cell).

Spec-total: ts 83.7% → 84.2%, rs 86.8% → 87.3%, go 83.3% → 83.7%.

Multi-agent-review observations during Phase 6 #3f — this cluster surfaced the **most reviewer-found issues to date** in a single Phase 6 cluster:

1. **Round-1 convergent regression** (Codex P1 on go.hocon + Codex P2 on rs.hocon, **independently**): the round-1 fix's `isSelfRef` structural path-equality detection misfired on external lookups. ts.hocon's Codex review missed it (cache shielded the symptom on forward order) but Claude r2 review confirmed the same shape was present in ts. All 3 impls were re-fixed in round 2 with the divergent narrower mechanisms documented above. This is the **first Phase 6 cluster to require a round-2 multi-agent-review** after fix discovery, validating the convergence rule's "default to must-fix" treatment.
2. **Multi-segment self-ref latent bug** (subagent-discovered during round-1 TDD on all 3 impls): the spec described `sr09`/`sr10` as straightforward regression guards, but each impl independently found that the existing prior-value lookup didn't traverse nested object structure correctly. Path-traversal additions in all 3 impls. Subagent reports surface this as deviations from plan that were validated as real correctness fixes.
3. **Spec ★1 decision #1 inadequate**: convergent finding from Claude r2 reviews on ts + go + Copilot reviews; the spec's "path-equality is preserved" decision was the wrong abstraction. Deferred amendment to xx.hocon#27.

Copilot single-reviewer-flagged fixes addressed in-PR (round 3): ts conformance-test `existsSync` skip-guard + cycle-detection criterion alignment; rs nested-object-literal self-ref leaf-segment fallback + error-fixture path existence assertion + misleading cache-claim comment; go error-fixture gate separation + nested-leaf-key collision fix.

One pre-existing dismiss (verified-empirical): Copilot flagged go.hocon's S13a.13 conformance test as silently-skipping in clean local checkout; dismissed because CI's `make testdata` syncs expected JSON via `cp -R "$tmpdir/expected/hocon/."` recursively, matching the established pattern from all prior Phase 6 clusters (CI-green confirmed across all 6 OS×runtime matrix entries).

Pre-existing bugs surfaced (out of scope, see [xx.hocon#27](https://github.com/o3co/xx.hocon/issues/27)): nested external ref crash (`foo.a = ${?foo.a}bar; foo.b = ${foo.a}`); cache pollution on prior-with-external-ref (`a = "x"; a = ${?a}foo; b = ${a}` returns `"x"` not `"xfoo"`); same-field double-self-ref crash (`a = ${?a}1; a = ${?a}2`); order-dependent ref-before-self-ref (`b = ${a}; a = ${?a}foo`). These were filed for a future cluster; not addressed in this PR per the project's "unrelated issues → separate PR" rule.

CI followups: go.hocon shows `codecov/project` -0.38pp drop vs develop baseline (84.95% vs 85.33%) — codecov is non-required per branch protection, `codecov/patch` is SUCCESS (all modified lines covered), no functional blocker. Drop is from baseline drift, documented in the PR description for transparency.

### Cleared in Phase 6 #3d (2026-05-18)

S18 duration/bytes accessors-with-no-unit (HOCON.md L1290) landed in all 3 impls via [ts.hocon#103](https://github.com/o3co/ts.hocon/pull/103), [rs.hocon#91](https://github.com/o3co/rs.hocon/pull/91), and [go.hocon#89](https://github.com/o3co/go.hocon/pull/89). xx.hocon ground truth pinned by 22 fixtures in `testdata/hocon/units-default/` ud01–ud08 (duration) + up01–up05 (period) + ub01–ub06 (bytes) + un01–un03 (negative edge cases). **NO expected sidecars** — per-impl tests carry the assertion burden against hardcoded Lightbend-faithful expected values per spec §Test strategy.

- **S18.4** (3-way → ✅) — `getDuration("500")` / `getBytes("1024")` / `getPeriod("7")` (rs-only) now interpret a unit-less string as the family default (ms / bytes / days). ts/go were ❌; rs was ⚠️ (bytes worked, duration didn't due to missing `""` arm). Spec HOCON.md L1290.
- **S18.1** (ts ❌ → ✅, free rider) — Number-value default unit. Same `parseDuration`/`parseBytes` fix site as S18.4 because ts.hocon's `getDuration` routes through `v.raw` for both number and string types. rs/go were already ✅.
- **S19.1** (go ⚠️ → ✅) — `nano` / `nanos` aliases added to the existing `ns` case in `parseDuration` (same switch as the S18.4 fix). ts/rs were already ✅.
- **S19.2** (go ❌ → ✅) — Microsecond units `us` / `micro` / `micros` / `microsecond` / `microseconds` added as a new case in `parseDuration`. ts/rs were already ✅.

Architecture (uniform across 3 impls): per-family string-parse helper (`parseDuration`, `parsePeriod`, `parseBytes`) gains an empty-unit fallthrough returning the family default. HOCON_WS predicate (Phase 6 #1 spec) replaces stdlib `.trim()` / `unicode.IsSpace` for leading/trailing/between-token whitespace. Integer pre-classification regex `^[+-]?[0-9]+$` (Lightbend `SimpleConfig.isWholeNumber` pattern) distinguishes integer fast-path from fractional fallback. Lightbend-faithful per-family fractional handling: **duration** integer→`Long.parseLong` * ms / fractional→`Double.parseDouble` scaled to nanos; **period** integer-only via `Integer.parseInt` (fractional rejected); **bytes** integer→`BigInteger` * mult / fractional→`BigDecimal.toBigInteger()` truncate-toward-zero. **Bytes negative-accessor rejection** at `getBytes` / `GetBytes` matches Lightbend `getBytesBigInteger` signum check; bytes parse layer allows negative for downstream flexibility.

Per-impl notes:

- **ts.hocon**: localized fix in `src/coerce.ts`. `trimHoconWs` inlines the full lexer HOCON_WS codepoint set (verified byte-for-byte against `src/internal/lexer/lexer.ts`). `Math.trunc` for bytes fractional truncation. `+` sign accepted in scanner. Period (S20) remains ➖ — `getPeriod` not implemented.
- **rs.hocon**: significant API addition — **`Period { years: i32, months: i32, days: i32 }` struct** (`#[non_exhaustive]`) + `get_period` / `get_period_option` accessors. No `chrono` dependency. `is_hocon_whitespace` exposed `pub(crate)` from `src/lexer.rs`. Negative-accessor guard covers BOTH numeric (`b = -1`) and string (`b = "-1"`) paths post-review-fix. **rs-specific limitation**: `std::time::Duration` is unsigned, so `parse_duration("-500")` returns `Err` (Lightbend's `java.time.Duration` is signed); documented in CHANGELOG.
- **go.hocon**: `isHoconWS` inlined in `config.go` (no new lexer export). UTF-8 rune decoding (`utf8.DecodeRuneInString`) for HOCON_WS scanning between number/unit and trailing-after-unit (NBSP, EM space, etc. previously broke the byte-indexed loop). Bytes fractional × unit truncates AFTER multiply (`int64(f * float64(mult))`) — caught by Codex review.

Cross-impl side effects:

- **S21.5** (go ❌ → ✅, side-cleared) — Fractional byte values with multi-letter units (`0.5KB`, `1.5MiB`) now accepted via the same `int64(f * float64(mult))` rewrite. Single-letter byte abbreviations (`0.5K`, `1.5M`) remain blocked by S21.4 (#73). ts/rs were already ✅.

E-namespace updates:

- **S20.1–S20.4 moved from globally-OOS to per-impl OOS** — rs.hocon implemented `get_period`; ts/go remain ➖. The "Globally out-of-scope items" count drops from 21 to 17. rs's per-impl ➖ count drops from 21 to 17, increasing its denominator from 188 to 192. No new E-entry needed since `Period` follows Lightbend's `java.time.Period` named-getter contract (rs uses a `#[non_exhaustive]` struct rather than chrono dependency).

Rate change (in-scope):

- ts: 93.0% → 94.1% (+1.1pp; +2 cells = S18.1/S18.4 flipped ❌ → ✅).
- rs: 94.1% → 94.5% (+0.4pp net; +1 cell S18.4 ⚠→✅, +4 cells S20.1–S20.4 ➖→✅ for rs-only; denominator 188 → 192 dampens the rate lift).
- go: 91.2% → 93.0% (+1.8pp; +4 cells = S18.4 ❌→✅, S19.1 ⚠→✅ at +0.5 weight, S19.2 ❌→✅, S21.5 ❌→✅ side-clear).

Spec-total: ts 82.8% → 83.7%, rs 84.7% → 86.8%, go 81.6% → 83.3%.

Multi-agent-review observations during Phase 6 #3d — 6 reviewers (Claude × 3 + Codex × 3) caught **3 cross-impl convergent issues** independently flagged on multiple branches:

1. **Fixture path tracking** (Codex P1 on ts + rs): ts.hocon test read from sibling-repo path `../../xx.hocon/...` which breaks in clean CI checkout; rs.hocon relies on `make testdata` (works in CI but panics in clean local). Fixed: ts.hocon Makefile extended to also sync `testdata/hocon/units-default/` from xx.hocon archive (parallel to existing `expected/hocon/` sync) + fixture files committed directly; rs.hocon pattern validated via CI green.
2. **`get_bytes` bare-numeric negative bypass** (Codex P2 + Claude C2 on rs): the negative-accessor guard fired only on the string path; bare unquoted `b = -1` (ScalarType::Number) returned `Ok(-1)`. Convergent → must-fix; guard moved to cover both paths.
3. **`Period` tuple vs struct** (author flag + Claude I1 on rs): initial impl used `(i32, i32, i32)` tuple for `parse_period`/`get_period` return. User-confirmed: switched to `#[non_exhaustive] pub struct Period { pub years, pub months, pub days: i32 }` for future extensibility, named-field readability, and Lightbend `java.time.Period` named-getter parity.

Plus single-reviewer-flagged Important fixes addressed in-PR: ts `trimHoconWs` doc/code mismatch (codepoint set narrower than comment claimed) + unused `WHOLE_NUMBER_RE` export; rs `spec-compliance.md` S20.1–S20.4 stale "None of the three" narrative; go bytes fractional × unit truncation order (`"1.5KB"` returned 1000 instead of 1500) + multi-byte UTF-8 HOCON_WS in byte-indexed loop (NBSP/EM space broke the scanner) + `t.Logf` not `t.Skipf` for period skip (unconditional-pass anti-pattern). One Codex single-reviewer P1 on rs (fixture-tracking) dismissed with verified-empirical rationale: matches existing `concat_errors_test.rs` pattern, `make testdata` cache-aware early-exit correctly fetches fresh fixtures; CI confirmed (all rs.hocon#91 checks SUCCESS).

CI followups: go.hocon required 3 extra commits beyond the initial review-fix batch: (a) remove unused `specIssueS21_5` const after S21.5 test was un-skipped (`golangci-lint unused`); (b) gofmt alignment on the new `config_internal_test.go` exhaustive `isHoconWS` test; (c) `parseDuration`/`parseBytes` trailing-WS-then-garbage tests to clear codecov patch threshold (84.76% → ≥85.56%). All 24 CI checks across ts#103 / rs#91 / go#89 landed SUCCESS pre-merge.

### Cleared in Phase 6 #3e (2026-05-18)

S12.5 `include`-reserved-at-start-of-key-path (HOCON.md L570 strict-spec posture, intentionally stricter than Lightbend which silently accepts the dotted form due to a tokenizer-then-PathParser quirk) landed in all 3 impls via [ts.hocon#102](https://github.com/o3co/ts.hocon/pull/102), [rs.hocon#90](https://github.com/o3co/rs.hocon/pull/90), and [go.hocon#88](https://github.com/o3co/go.hocon/pull/88). xx.hocon ground truth pinned by 14 fixtures in `testdata/hocon/include-reservation/` ir01–ir14 (`.error` sidecars on 5: ir01/ir02/ir10/ir12/ir13; `-expected.json` on 7: ir05/ir06/ir07/ir08/ir09/ir11/ir14; **ir03/ir04 have NEITHER** — these are E9 Lightbend-silent-accept-quirk cases pinned via per-impl override list in each conformance test).

- **S12.5** (3-way ❌ → ✅) — Unquoted `include` at the start of an unquoted key path expression is now a parse error in all 3 impls. Triggers: `include = 1`, `include : 1`, `include += [1]`, `include { x = 1 }`, `include.foo = 1`, `include.foo.bar = 1`, `a = { include.bar = 1 }` (nested). Quoted form `"include" = 1` / `"include".foo = 1`, non-initial `foo.include = 1`, value-position `a = include`, and substitution paths `${include}` / `${include.foo}` are all unaffected. Spec HOCON.md L570.

Architecture (uniform across 3 impls): **post-PathParser detection** — the check fires on the constructed path-element list AFTER the path expression is parsed, not at the raw token stream layer. This handles both `include = 1` (one bare token) and `include.foo = 1` (one joined unquoted token, post-split into `["include", "foo"]`) with the same code path. `wasQuoted` / `was_quoted` / `firstTokenIsQuoted` provenance for the first token is **captured locally** in the field-parser before `parseKey` / `parse_key` advances the cursor — no struct changes to `AstField` / `FieldNode` / `AstField`, no `parseKey` signature broadening (rs/go) or only-private signature change (ts).

Per-impl placement:

- **ts.hocon**: `src/internal/parser/parser.ts` — `parseObject()` adds two guards: (a) peek-then-throw for bare `include` followed by `=`/`:`/`+=`/`{` (BEFORE `parseInclude()` runs, emitting the spec-aligned message); (b) post-`parseKey()` check on `key[0] === 'include' && !firstWasQuoted`. `parseKey()` private return refactored to `{ segments: string[]; firstWasQuoted: boolean }` — provenance threaded locally, `AstField.key` shape unchanged.
- **rs.hocon**: `src/parser.rs:191-228` — `parse_object()` captures `first_key_is_quoted = peek_kind() == TokenKind::QuotedString` snap before `parse_key()`, then `if !first_key_is_quoted && key[0] == "include"` → `Err(ParseError)`. No struct changes; `ParseError` constructor unchanged (avoids `#[non_exhaustive]` SemVer concerns).
- **go.hocon**: `internal/parser/parser.go` — `parseField` captures `firstTokenIsQuoted := p.current.IsQuoted` + `firstTokenType := p.current.Type` before `parseKey`, then post-check on the parsed key. The `parseInclude` "expected filename" error split into two spec-aligned diagnostics: (a) `include foo.conf` (unquoted-arg) → `"include argument must be a quoted string, got unquoted: ... (HOCON.md L958)"`; (b) `include =` / `include :` / `include +=` / `include {` → `"'include' is reserved as a key name; use \"include\" (quoted) to use it as a field (HOCON.md L570)"`. The split was caught by Claude review on go.hocon#88 round 1.

Cross-impl side effects:

- **S14a.10** (go ❌ → ✅, **side-cleared**) — `parseInclude` now requires the include argument to be a quoted `TokenString`; unquoted `include foo.conf` raises `*parser.Error`. Pin test converted to regression guard; spec test un-skipped. ts/rs were already ✅ pre-fix. **Side-closes go.hocon#80**.
- **Value-position `include`** (go, pre-existing bug, fixed in-scope) — `a = include` previously produced "unexpected token 17" because the go lexer always promotes bare `include` to `TokenInclude` regardless of position. Added a `TokenInclude` case in `parseSingleValue` that demotes to string scalar `"include"`. ir08 fixture pins this.

E9 in [`extra-spec-conventions.md`](extra-spec-conventions.md) flipped ❌ → ✅ in all 3 impls. ir03 / ir04 fixtures (Lightbend silent-accept due to tokenizer joining `include.foo` into one unquoted token before `isIncludeKeyword` runs) are loaded by each per-impl conformance test with a per-impl override list pinning them as parse errors. Same E-namespace pattern as E5 (S10.13 ce05 cluster 3b) and E8 (S8.6 cluster 3c).

Rate change (in-scope):

- ts: 92.5% → 93.0% (+0.5pp; +1 cell = S12.5 flipped ❌ → ✅).
- rs: 93.6% → 94.1% (+0.5pp; +1 cell = S12.5 flipped ❌ → ✅).
- go: 90.1% → 91.2% (+1.1pp; +2 cells = S12.5 + S14a.10 flipped ❌ → ✅).

Spec-total: ts 82.3% → 82.8%, rs 84.2% → 84.7%, go 80.6% → 81.6%.

Multi-agent-review observations during Phase 6 #3e — Claude + Codex (file-pipe `codex exec` not needed for `codex review` subcommand) caught one cross-impl convergent finding on **go.hocon#88 only**: Claude review's Important finding flagged that the `parseInclude` "expected filename" error message was misleading for the `include foo.conf` case (user intended an include statement but forgot quotes; the S12.5 "reserved as a key name" message says the opposite of what they need). Codex independently flagged a P1 fixture-tracking concern on **rs.hocon#90** — investigation showed the pattern matches existing `concat_errors_test.rs` and CI's `make testdata` early-exit logic correctly detects remote-vs-local SHA divergence and fetches fresh; CI passed (8/8 checks SUCCESS on rs#90). The go.hocon error-message split was fixed in-PR (commit 7b5c80a) per the multi-reviewer convergence rule's "Important = must-fix" treatment; the rs.hocon Codex P1 was dismissed with verified-empirical rationale (CI green) + overturn condition (CI fails → reopen). All 17 CI checks across the 3 PRs landed SUCCESS pre-merge.

### Cleared in Phase 6 #3b (2026-05-18)

S10.4/S10.13/S10.19 concat type-check tightening (HOCON.md L373/L385) landed in all 3 impls via [ts.hocon#101](https://github.com/o3co/ts.hocon/pull/101), [rs.hocon#89](https://github.com/o3co/rs.hocon/pull/89), and [go.hocon#87](https://github.com/o3co/go.hocon/pull/87). xx.hocon ground truth pinned by 15 fixtures in `testdata/hocon/concat-errors/` ce01–ce15 (`.error` sidecars on 13; ce09/ce15 success-path with `-expected.json`). go.hocon merge is **BREAKING** for the prior permissive `[1, 2] 3 → [1, 2, 3]` behaviour; ts and rs were already ❌ pre-fix, so their flips are non-breaking.

- **S10.4** (3-way ❌ → ✅) — Mixing arrays + objects in concat (`[1] {b:2}`, `{b:2} [1]`) now raises `ResolveError` in the pairwise-fold `joinPair` / `join_pair` helper. Spec L385.
- **S10.13** (ts ❌→✅, rs ❌→✅, go ⚠️→✅) — Array/object appearing in string concat (`[1, 2] 3`, `3 [1, 2]`, `{b:1} x`, `x {b:1}`) now raises `ResolveError`. Spec L373. go's prior `⚠️` covered the permissive scalar-append case.
- **S10.19** (3-way ❌ → ✅) — Substitution-resolved variants of S10.4 / S10.13 (e.g. `obj = {b:2}; a = [1] ${obj}`) participate in the same type-check; the resolver applies `joinPair` AFTER substitution resolution, so subst-resolved structured values are treated identically to literals. Spec L385-389.

Architecture (uniform across 3 impls): replaced 4 permissive branches in the pairwise-fold concat join with an error return / throw. Pre-existing success paths preserved unchanged — S15.3 numeric-object-to-array bridge (`[1] {"0":"x","1":"y"}` → `[1, "x", "y"]`), Object+Object deep-merge (S10.3), Array+Array concat (S10.2), Scalar+Scalar string-concat. Optional-substitution omission (`${?missing}`) runs **before** the fold, so a missing piece does not leave a hole for the type-check to fire on — `[1] ${?missing} {b:2}` still errors (S10.4 fires after omission collapses to `[1] {b:2}`), but `[1] ${?missing}` succeeds as `[1]` (single-piece fold has nothing to join).

Concat error context (Option A pattern, applied uniformly across 3 impls during multi-agent-review): position information (line/col) threaded from the AST into a new `ConcatPlaceholder.line/col` field on each concat piece, then read back when emitting the error. Without this, the `ResolveError` would report only the field's line/col (where `a = ...` starts), not the offending concat boundary. Surfaced as an "Important" finding by reviewers on all 3 PRs; fixed in-PR for cross-impl parity rather than deferred. Type-name helper (`typeName` / `type_name` / `valTypeName`) returns canonical strings — `"object"`, `"array"`, `"string"`, `"int"`, `"float"`, `"bool"`, `"null"` — including scalar-subtype discrimination (rs: branches on `ScalarValue.value_type`; go: branches on `ScalarVal.Type`; ts: branches on `valueType` field). This makes error messages immediately actionable.

Rs-only architectural notes (BREAKING, documented in CHANGELOG) — `ResolveError` gained a `pub(crate) concat_type_mismatch` constructor (additive, the struct is already `#[non_exhaustive]` from earlier work, so adding a new constructor is non-breaking). `stringify_value`'s `Array(_)` / `Object(_)` arms became unreachable after `join_pair` started erroring on container-in-string-concat; they are now `unreachable!()` rather than soft-fallbacks. Clippy initially flagged an unreachable catch-all in `type_name` after the exhaustive `ScalarType` match; resolved by removing the catch-all (multi-agent-review batch fix, commit 25ba0b3 + fmt fix 54a51d5).

Go-only architectural notes — `ConcatNode.Line()/Col()` exported methods added for AST consumer symmetry with `SubstNode`. The unexported `concatPlaceholder` struct carries the per-piece line/col downstream into `joinPair`. The S10.13 BREAKING change is the largest cross-impl behaviour delta in this PR (ts and rs already errored on the construct pre-fix; go silently produced `[1, 2, 3]`); migration is documented in [go.hocon CHANGELOG](https://github.com/o3co/go.hocon/blob/develop/CHANGELOG.md).

Cross-impl side effects:

- **S10.15** (rs ❌ → ✅, incidental) — Quoted whitespace between array substitutions (`${a} " " ${b}`) was previously silently accepted by rs because the quoted-whitespace scalar threaded through `join_pair` without type-check. Phase 6 #3b's scalar-between-arrays type-check now fires correctly, erroring the merge. Pinned by `tests/spec_phase5.rs::s10_15_quoted_ws_between_obj_substs_is_error` + array variant. go remains ❌ — its resolver elides separator tokens before `joinPair`, so the type-check is never reached (see `S10.15` row in [Top spec violations](#top-spec-violations-verified)).

E5 in [`extra-spec-conventions.md`](extra-spec-conventions.md) flipped ❌ → ✅ in all 3 impls (Lightbend silently accepts `{object} scalar`, `scalar {object}`, `[array] scalar`, `scalar [array]` constructs; o3co convention tightens to spec-conformant error). ce05-object-plus-scalar fixture is loaded by each per-impl test from `testdata/hocon/concat-errors/` without an xx.hocon `.error` sidecar — the impl tests use a per-impl override list to assert error for the cells where Lightbend silently accepts. Same E-namespace pattern as E8 (S8.6 cluster 3c) and E9 (S12.5 cluster 3e).

Rate change (in-scope):

- ts: 90.9% → 92.5% (+1.6pp; +3 cells = S10.4/S10.13/S10.19 flipped ❌ → ✅).
- rs: 91.5% → 93.6% (+2.1pp; +4 cells = S10.4/S10.13/S10.15/S10.19 flipped ❌ → ✅).
- go: 88.8% → 90.1% (+1.3pp; +3 cells = S10.4 ❌→✅, S10.13 ⚠️→✅ at +0.5 weight, S10.19 ❌→✅).

Spec-total: ts 80.9% → 82.3% (+1.4pp), rs 82.3% → 84.2% (+1.9pp), go 79.4% → 80.6% (+1.2pp).

Multi-agent-review observations during Phase 6 #3b — Claude + Codex (file-pipe `codex exec < INPUT > OUTPUT`) caught one cross-impl convergent issue on all 3 PRs independently: **position-info gap** (ResolveError reported only field-level line/col, not the concat-piece boundary). The convergence — 3 independent reviewers flagging the same gap — promoted it from "Important" to "must-fix in-PR" per the [multi-reviewer convergence rule](../../CLAUDE.md). Option A pattern (line/col on `ConcatPlaceholder`, populated from AST pos, read back in error path) adopted uniformly. Copilot rounds caught additional in-impl issues: rs.hocon#89 batch (clippy unreachable + type_name catch-all + 4 other minor fixes in one commit 25ba0b3); no Critical-class regressions.

### Cleared in Phase 6 #3g (2026-05-18)

S13c env-var list expansion `${X[]}` / `${?X[]}` (HOCON.md L893–L917) landed in all 3 impls via [ts.hocon#100](https://github.com/o3co/ts.hocon/pull/100), [rs.hocon#88](https://github.com/o3co/rs.hocon/pull/88), and [go.hocon#86](https://github.com/o3co/go.hocon/pull/86). xx.hocon ground truth was pinned earlier by ev01–ev11 fixtures and extended by [xx.hocon#21](https://github.com/o3co/xx.hocon/pull/21) (ev12a/ev12b/ev13) covering S13c.5 scalar-fallback suppression and the isolated optional-list-direct path.

- **S13c.1** (3-way ❌ → ✅) — `${X[]}` looks up `X_0`, `X_1`, … env vars (L900).
- **S13c.2** (3-way ❌ → ✅) — Scan stops at the first missing index (L905); empty-string element values are preserved (stop = key absent, not empty).
- **S13c.3** (3-way ❌ → ✅) — Required form (`${X[]}`) with no elements raises `ResolveError` (L910).
- **S13c.4** (3-way ❌ → ✅) — Optional form (`${?X[]}`) with no elements removes the field (L912).
- **S13c.5** (3-way ❌ → ✅) — `[]` suffix is supported only for environment variables (L902); when `listSuffix=true` and no `X_0` is present, the resolver does NOT fall through to the bare scalar env var.

Architecture (uniform across 3 impls): `SubstPayload.listSuffix` bool threaded through lexer → AST → resolver; new `resolveEnvList` / `resolve_env_list` helper that scans the relativized base first (when `prefixLen > 0`) then the bare base. The `[` arm in `parseSubstBody` gates on `!curStarted` so `${X.[]}` (empty segment before suffix) errors instead of being silently accepted, and validates `pendingWs` against ASCII space/tab only.

Cross-impl extra-spec convergence — [E6](extra-spec-conventions.md#e6) (config-defined wins) and [E7](extra-spec-conventions.md#e7) (whitespace before `[]`) both flipped 🤷 → ✅ in all 3 impls. E7 was narrowed during multi-agent-review *before merge*: initial drafts of all 3 PRs accepted broader Unicode whitespace (NBSP, CR, Zs); the I2 fix tightened acceptance to ASCII space (0x20) / tab (0x09) and now errors with `HOCON extra-spec E7` for other forms.

Cache-key disambiguation (ts + rs only; go's cache architecture is self-ref-recovery only and immune by construction) — `${X}` and `${X[]}` resolve via different code paths (scalar fallback vs `resolveEnvList`) but were initially keyed identically in the substitution cache. The C1 fix appends `[]` to the cache key when `listSuffix=true`. A round-2 follow-up on rs.hocon also taught `segments_to_key` to quote bracket-containing text so `${"X[]"}` (quoted segment whose text is literally `X[]`) does not collide with `${X[]}` either. ts.hocon's stricter `/[^a-zA-Z0-9\-_]/` quote trigger made it immune to the round-2 case by accident; the regression is pinned by tests in both repos.

Go-only — `ev08-self-append` (`x = ["x"]; x = ${?x} ${?LIST[]}`) was originally classified as a `t.Skip` tripwire pending cluster 3f (S13a.13 self-ref-lookback). A multi-impl probe during implementation showed all 3 impls pass naturally because `x = ["x"]` provides a clear prior value, distinguishing this case from S13a.13's "no prior value" tripwire. Promoted to SUCCESS in all 3 impls; tripwire test path removed.

Rs-only (BREAKING, documented in CHANGELOG) — `SubstPayload` (publicly re-exported from `lib.rs`) is now `#[non_exhaustive]`, and the `AstNode::Substitution` *variant* (parser module is `pub(crate)`, so technically not a public-API change but installed in lockstep for future-proofing) is also `#[non_exhaustive]`. Future field additions (e.g. the planned include-scope fallback work for [xx.hocon#22](https://github.com/o3co/xx.hocon/issues/22)) become non-breaking. Migration for downstream code that constructed `SubstPayload` via struct literal: switch to a constructor / builder helper (`#[non_exhaustive]` blocks external struct-literal construction entirely; `..Default::default()` does NOT satisfy it). For exhaustive pattern matches on `AstNode::Substitution { ... }`: add `..`. Most consumers should be unaffected — `SubstPayload` is primarily an internal pipeline value.

Rate change (in-scope) — uniform +2.7pp across all 3 impls (5 items × 3 impls = 15 cells flipped ❌ → ✅): ts 88.2% → 90.9% (+2.7pp), rs 88.8% → 91.5% (+2.7pp), go 86.1% → 88.8% (+2.7pp). Spec-total +2.4pp each: ts 78.5% → 80.9%, rs 79.9% → 82.3%, go 77.0% → 79.4%.

What remains deferred to [xx.hocon#22](https://github.com/o3co/xx.hocon/issues/22) — in include scope, `${X[]}` does NOT currently fall back to original-path config (the listSuffix branch runs before the relativized-path original-path config lookup). This diverges from `${X}` non-list which does fall back. Cross-impl scope (also affects ts + rs); fix requires a new ev12c-style fixture, the resolver re-ordering in go.hocon, and a parallel decision in rs.hocon (which currently lacks any original-path *config* fallback). Surfaced by Copilot review on go.hocon#86; left as a `discuss` thread there.

Multi-agent-review observations during Phase 6 #3g — Claude + Codex (file-pipe `codex exec < INPUT > OUTPUT`) caught 2 cross-impl convergent issues independently on each branch before PR creation: **I1** (`${X.[]}` silently accepted because the `[` arm only checked `len(segments) == 0`, missing the trailing-dot case; fixed via `!curStarted` uniform with the `.` arm), and **I2** (E7 whitespace allow-list too broad, fixed by tightening to ASCII space/tab). The convergence — each reviewer flagging the same issue on each independent branch — confirmed both as real correctness gaps rather than per-impl edge cases. Subsequent Copilot rounds on rs#88 and go#86 caught one Critical each (cache collision round-2 on rs, include-scope ordering on go — the latter deferred to xx.hocon#22), validating the layered review approach.

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
- **S13.9** — `null` in config blocks env var lookup (✅ in ts/go; originally classified ❌ in rs — see Top spec violations above. **2026-05-22 audit correction**: rs reclassified ✅ — the Phase 3 ❌ was a mis-classification; Lightbend reference verification (`ProbeS13_9.java` in `generate/`) confirmed all 3 impls match Lightbend's tree-level semantics — field is present as `null` scalar; null-as-missing is a *getter-level* statement (covered by S17.6))
- **S13.13** — optional undefined in string concat → empty string (✅ in all 3)
- **S13.14** — optional undefined in obj/array concat (✅ in go; ⚠️ in ts/rs — array variant broken, see Top spec violations above)
- **S13.16** — substitutions only in field values / array elements (✅ in all 3)
- **S13a.13** — `a = ${?a}foo` resolves to `"foo"` (was verified ❌ in all 3 in Phase 3; cleared ✅ in Phase 6 #3f — see "Cleared in Phase 6 #3f" section above)
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
- **S10.8** — string concat in field keys (verified ❌ in ts/go, ⚠️ in rs — fixed in Phase 6 #4 / v1.5.0)
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
- **S17.6** — null → other type: error (originally classified ✅ in rs/go; ⚠️ in ts — see Top spec violations above. **2026-05-22 audit correction**: rs reclassified ❌ — the Phase 4 ✅ was a mis-classification; `get_string` returns `Ok("null")` on null values per `tests/integration_test.rs` `#[ignore]` test pinned to [rs#80](https://github.com/o3co/rs.hocon/issues/80))
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
# Count only S-prefixed (spec) items, not E-prefixed (extra-spec) items.
# Some per-impl files have started listing E6/E7/etc. as `- **E<n>**` blocks
# with their own `status:` lines; those belong in extra-spec-conventions.md
# and must not double-count toward the S-spec rate.
for repo in ts.hocon rs.hocon go.hocon; do
  for g in ✅ ⚠️ ❌ 🤷 ➖; do
    n=$(awk '/^- \*\*S/{in_s=1; next} /^- \*\*[^S]/{in_s=0} in_s && /^  status:/' \
        /path/to/$repo/docs/spec-compliance.md | grep -c "^  status: $g")
    echo "$repo $g $n"
  done
done
```

4. Rates are computed as `(✅ + ⚠️·0.5) / N` with `N = 209` (spec-total) or `N = 209 − ➖_per_impl` (in-scope). After Phase 5 the per-impl ➖ counts diverge — globally shared ➖ is 21, but ts has 23 (+ S1.1, S13a.10) and go has 22 (+ S13a.10), so denominators are: ts=186, rs=188, go=187. When recomputing, count each repo's ➖ from its own `docs/spec-compliance.md`.
5. When the template gains or loses an item, **all three per-repo files must be synced** before this matrix is rebuilt; otherwise the totals will be inconsistent.

## Last verified

2026-05-22 (Phase 6 #4 / v1.5.0 — S10.8 unquoted space-concat in field keys cleared across all 3 impls) — **ts.hocon spec-total 85.9% → 86.4% (+0.5pp), in-scope 96.5% → 97.0% (+0.5pp); rs.hocon 88.3% → 88.5% (+0.2pp), 96.1% → 96.4% (+0.3pp); go.hocon 85.9% → 86.4% (+0.5pp), 96.0% → 96.5% (+0.5pp)**. Single S-cell flipped per impl: ts ❌→✅, rs ⚠️→✅, go ❌→✅. PRs: [ts.hocon#128](https://github.com/o3co/ts.hocon/pull/128) `1fc0582`, [rs.hocon#115](https://github.com/o3co/rs.hocon/pull/115) `ebb06f4`, [go.hocon#114](https://github.com/o3co/go.hocon/pull/114) `f238996`. Three Phase 6 #4 sub-findings filed as cross-impl follow-up [xx.hocon#42](https://github.com/o3co/xx.hocon/issues/42): (a) S8.6-in-key over-strict vs Lightbend (`foo -bar = 1` accepted by Lightbend, rejected by all 3 impls); (b) trailing-dot-then-whitespace whitespace preservation (`a b. c = 1` → Lightbend `{"a b":{" c":1}}` with leading-space sub-key, all 3 impls produce `{"a b":{"c":1}}`); (c) cross-impl comprehensive audit of "S8.6 + S11.1 + path-expression whitespace" interactions. Released: TBD.

2026-05-22 (v1.5.0 work — S13.9 rs mis-classification corrected via Lightbend ground-truth verification) — **rs.hocon spec-total 87.8% → 88.3% (+0.5pp), in-scope 95.6% → 96.1% (+0.5pp)** from S13.9 row removal. Lightbend reference probe (`ProbeS13_9.java`) confirmed rs.hocon's pre-fix behavior (field present as null in tree) matched Lightbend tree-level semantics; the matrix's prior "rs ❌" classification conflated tree-level vs getter-level "null treated same as missing" semantics. rs.hocon PR #111 (proposed fix) closed without merge as over-fix; rs.hocon#74 closed as not-a-violation. Getter-level null rejection is covered by S17.6 (rs.hocon PR #109, merged 2026-05-22).

2026-05-22 (v1.4.0 retroactive matrix audit — S13.15 cleared, S17.6 rs mis-class corrected) — **go.hocon spec-total 85.4% → 85.9% (+0.5pp), in-scope 95.5% → 96.0% (+0.5pp)**; rs.hocon and ts.hocon unchanged (S13.15 +1 ✅ / S17.6 −1 ✅ mis-class correction cancel for rs; ts was already classified ✅ on S13.15 pre-v1.4.0). Audit run on the v1.4.1 release tags of all 3 impls (go.hocon `84e73f4`, ts.hocon `8639cab`, rs.hocon `6346a5a`); full test suites green across go (0 failed, 0 skipped spec-violation tests for S13.15), ts (978 passed, 9 expected-fail — S13.15 not among them), rs (all bins 0 failed; `s13_15_spec_both_optional_undefined_field_absent` no longer `#[ignore]`'d). Methodology note added to the v1.4.0 audit section above: from v1.5.0 onward each release's "Last verified" entry must enumerate touched S-cells, not just the headline feature, to prevent recurrence of this v1.4.0 narrative gap.

2026-05-22 (v1.4.1 cross-impl Lightbend include-path bugfix release) — no rate change. Two cgordon-reported divergences fixed simultaneously in all 3 impls: empty / comment-only / whitespace-only included files now contribute `{}` instead of erroring ([go.hocon#105](https://github.com/o3co/go.hocon/issues/105) — narrow carve-out, top-level empty parses still error per S3.1); and includes appearing after a pre-existing parent key now correctly override that key per Lightbend "as if written inline" semantics, with self-referential append through include resolving against the parent's prior value ([go.hocon#106](https://github.com/o3co/go.hocon/issues/106) — go-only impl fix; ts.hocon and rs.hocon already conformed and shipped cross-impl regression tests). Companion go.hocon refinement: under `WithAllowUnresolved(true)`, required self-referential `${k}` with no prior value now defers the placeholder instead of erroring (required by the include child resolver running in lenient mode for #106). Neither behaviour was in the previous violation table — both were silent footguns; rate change: none on any cell. The regression-test sets (`issue105_*` + `issue106_*` across the 3 impls) are the durable artifact. Released: [go.hocon v1.4.1](https://github.com/o3co/go.hocon/releases/tag/v1.4.1), [ts.hocon v1.4.1](https://github.com/o3co/ts.hocon/releases/tag/v1.4.1), [rs.hocon v1.4.1](https://github.com/o3co/rs.hocon/releases/tag/v1.4.1).

2026-05-21 (xx.hocon#37 — E12 added) — no rate change. E12 (deferred substitution resolution — Lightbend-aligned `parse / withFallback / resolve()` lifecycle) added to [`extra-spec-conventions.md`](extra-spec-conventions.md#e12) as a cross-impl API convention with 14 normative decisions. External origin [go.hocon#99](https://github.com/o3co/go.hocon/issues/99) (@cgordon, third external issue). E-items do not contribute to the compliance denominator; rates unchanged in all three impls. New fixture group `testdata/hocon/deferred-resolution/` (31 scenario YAML files across 30 scenario IDs `dr01–dr30`; dr11 split into dr11a/dr11b) with paired Lightbend ground truth generated by new `DeferredResolutionRunner.java` (29 scenarios verified against Lightbend; 2 `lightbendSkip`: dr11b for intentional Lightbend divergence per decision 10, dr17 for E11-not-applicable). Cross-spec interactions for S13a (self-reference lookback across fallback layers) and S10 (concat type-check under AllowUnresolved) clarified inline in the E12 section. Status 🤷 in all 3 impls — impl PRs target v1.4.0 bundle release alongside E11.

2026-05-20 (Phase 6 #3c Phase 3 — E8 amendment) — re-rolled-up after the E8 amendment landed in all 3 impls ([ts.hocon#108](https://github.com/o3co/ts.hocon/pull/108) squash `569a7d6`, [rs.hocon#98](https://github.com/o3co/rs.hocon/pull/98) squash `e56d70f`, [go.hocon#97](https://github.com/o3co/go.hocon/pull/97) squash `9aa9238`). xx.hocon E8 spec rewrite in [xx.hocon#32](https://github.com/o3co/xx.hocon/pull/32) (commit `dd102e8`) driven by external issue [xx.hocon#31](https://github.com/o3co/xx.hocon/issues/31) — first issue from outside o3co (@cgordon reported `b = ${a}-bar` being rejected while Lightbend accepts it as `"foo-bar"`). The E8 amendment retracts the Phase 6 #3c Phase 2 strict-spec posture (the original "S8.6 strict reject of `-` at value-start" reading); adopts Lightbend's pragmatic reading of HOCON.md L270-276 ("begin" = value-position begin, not token-position). S8.6 cleared from "Top spec violations" (3 cells × 3 impls = 3 cells flipped from ⚠️ to ✅). Rate lift per impl (in-scope): ts 96.2% → 96.5% (+0.3pp), rs 95.3% → 95.6% (+0.3pp), go 95.2% → 95.5% (+0.3pp). Spec-total: ts 85.6% → 85.9%, rs 87.6% → 87.8%, go 85.2% → 85.4%. Multi-agent-review (Claude + Codex × 3 branches = 6 reviewers) caught 2 cross-impl convergent issues: **rs.hocon Codex P2** found a real correctness bug (Rust's `f64::parse("-inf")` was accepting `-inf`/`-nan` as numbers; added a JSON-number-shaped entry gate in `parse_scalar_value`), and **go.hocon Codex P2** found a parallel correctness bug (lexer-level int normalization was silently renaming keys like `01 = x` → `"1"`; fix moved normalization out of the lexer into `parser.parseSingleValue` value-construction site only). Both fixes landed in-PR before merge. **Process learnings recorded**: (a) `Refs #N` over `Closes #N` for cross-impl rollout PRs to avoid GitHub auto-close firing prematurely — xx.hocon#32 auto-closed #31 on merge; required a manual reopen + status comment during the per-impl rollout. (b) Sidecar pre-check pattern with "run `make testdata`" guidance propagated proactively from rs.hocon#98 (where Copilot first flagged it) to go.hocon#97 before review. (c) Lex-test flips with name change (`..._Rejected` → `..._E8_Accepted`) document the post-E8 contract in the test name itself. Architecture divergence (intentional, by per-impl token-model): ts/rs use Option B (unquoted-only token, normalization at resolve layer); go uses Option A (separate TokenInt/TokenFloat, dispatch peek-ahead at `-`, value-only normalization at parser). go.hocon additionally closed a pre-existing `+` reservation gap (was accepting `+foo`/`${a}+bar` as unquoted; ts/rs/Lightbend rejected) — BREAKING also for key position (`+c = 1`). us15 (`a = 1e+x`) no longer a tripwire in any impl (closed by `+` enforcement everywhere).

2026-05-19 (Phase 6 #3h) — re-rolled-up after S3.1 (empty file rejection) + S21.4 (single-letter byte abbreviations binary) + S23.4 (`.properties` object-wins) impl PRs landed in all three impls ([ts.hocon#106](https://github.com/o3co/ts.hocon/pull/106), [rs.hocon#94](https://github.com/o3co/rs.hocon/pull/94), [go.hocon#93](https://github.com/o3co/go.hocon/pull/93)). All 3 rows cleared from "Top spec violations". **Two rs.hocon mis-classifications corrected** (S21.4 decimal→binary BREAKING, S23.4 silent-data-loss → deterministic object-wins). E10 added to `extra-spec-conventions.md` for Lightbend's silent-accept-empty quirk. Rate lift per impl (in-scope): ts 94.6% → 96.2% (+1.6pp; 3 cells), rs 95.1% → 95.3% (+0.2pp net; 1 cell S3.1 ⚠→✅, 2 mis-class corrections preserve ✅ count), go 93.6% → 95.2% (+1.6pp; 3 cells). Spec-total: ts 84.2% → 85.6%, rs 87.3% → 87.6%, go 83.7% → 85.2% — **all three impls now ≥85% spec-total**, the cluster goal. Multi-agent-review (Claude × 3 + Codex × 3 = 6 reviewers across 3 branches) caught 3 cross-impl convergent issues independently: **include-path empty guard** (ts Codex + rs Codex, both impls' S3.1 fix only guarded top-level parse, not include path — must-fix per convergence rule), **fractional byte overflow at 2^63 float64 boundary** (go Claude + go Codex + rs Claude, `float64(math.MaxInt64)` rounds up to 2^63 making `> math.MaxInt64` skip overflow at boundary; corrupted `"8.0E"` to silent MaxInt64 saturation), and **ts unit-less byte overflow asymmetry** (Codex single-reviewer, default-bytes path skipped the overflow check). All 3 addressed in-PR. Pre-existing bug filed as follow-up: rs.hocon parse_duration overflow (rs.hocon#95). Architecture summary: shared empty-token-stream helper called from both top-level parse AND include loader; single-letter K-E entries as powers of two with overflow-checked multiplication (precision-safe float64 boundary via `math.Exp2(63)` in go / `2f64.powi(63)` in rs / `Number.MAX_SAFE_INTEGER` in ts); properties loader sort-then-iterate with object-wins at both leaf and non-leaf segments. `Z`/`Y` single-letter units explicitly deferred pending uniform overflow policy.

2026-05-18 (Phase 6 #3d) — re-rolled-up after the S18 string-with-no-unit + Lightbend per-family fractional impl PRs landed in all three impls ([ts.hocon#103](https://github.com/o3co/ts.hocon/pull/103), [rs.hocon#91](https://github.com/o3co/rs.hocon/pull/91), [go.hocon#89](https://github.com/o3co/go.hocon/pull/89)). S18.4 (3-way → ✅), S18.1 (ts ❌→✅ free rider), S19.1 (go ⚠→✅), S19.2 (go ❌→✅), and S21.5 (go ❌→✅ side-clear) all cleared from "Top spec violations". S20.1–S20.4 moved from globally-OOS to per-impl OOS (rs.hocon implemented `Period { years, months, days }` struct + `get_period`/`get_period_option` accessors; ts/go remain ➖); globally-OOS count drops from 21 to 17 and rs's denominator increases from 188 to 192. Rate lift per impl (in-scope): ts 93.0% → 94.1% (+1.1pp; 2 cells), rs 94.1% → 94.5% (+0.4pp net; 5 cells flipped but +4 ➖→✅ are denominator-dampened), go 91.2% → 93.0% (+1.8pp; 4 cells). Spec-total: ts 82.8% → 83.7%, rs 84.7% → 86.8%, go 81.6% → 83.3%. Multi-agent-review (Claude + Codex × 3 branches = 6 reviewers) caught 3 cross-impl convergent issues — **fixture path tracking** (Codex P1 on ts + rs), **`get_bytes` bare-numeric negative bypass** (Codex P2 + Claude C2 on rs, must-fix per convergence rule), and **`Period` tuple → `#[non_exhaustive]` struct** (author flag + Claude I1 on rs, user-confirmed before fix). Plus single-reviewer-flagged Important fixes in-PR: ts `trimHoconWs` doc/code mismatch + unused `WHOLE_NUMBER_RE` export, rs `spec-compliance.md` S20 stale narrative, go bytes fractional × unit truncation order + multi-byte UTF-8 HOCON_WS in byte-indexed loop + `t.Logf` not `t.Skipf` for period skip. go required 3 CI follow-up commits (unused const removal, gofmt alignment on new test file, trailing-WS-then-garbage tests to clear codecov patch threshold 84.76%→85.71%); all 24 CI checks across the 3 PRs landed SUCCESS pre-merge. Architecture summary: per-family `parseDuration`/`parsePeriod`/`parseBytes` helpers gain empty-unit fallthrough → family default (ms/days/bytes). Integer pre-classification regex `^[+-]?[0-9]+$` distinguishes integer fast-path from fractional fallback per Lightbend `SimpleConfig.isWholeNumber`. Bytes accessor rejects negative per Lightbend `getBytesBigInteger` signum check. HOCON_WS predicate replaces stdlib whitespace functions throughout the unit-parse paths.

2026-05-18 (Phase 6 #3e) — re-rolled-up after the S12.5 `include`-reservation impl PRs landed in all three impls ([ts.hocon#102](https://github.com/o3co/ts.hocon/pull/102), [rs.hocon#90](https://github.com/o3co/rs.hocon/pull/90), [go.hocon#88](https://github.com/o3co/go.hocon/pull/88)). S12.5 (3-way ❌→✅) + S14a.10 (go ❌→✅, side-cleared) cleared from "Top spec violations". E9 in `extra-spec-conventions.md` flipped ❌ → ✅ in all 3 impls (Lightbend silently accepts dotted `include.foo` due to tokenizer-then-PathParser quirk; o3co strict-spec tightens via per-impl override list for ir03/ir04 fixtures). Rate lift per impl (in-scope): ts 92.5% → 93.0% (+0.5pp; 1 cell), rs 93.6% → 94.1% (+0.5pp; 1 cell), go 90.1% → 91.2% (+1.1pp; 2 cells: S12.5 + S14a.10). Spec-total: ts 82.3% → 82.8%, rs 84.2% → 84.7%, go 80.6% → 81.6%. Architecture (uniform across 3 impls): **post-PathParser detection** on the constructed path-element list (not raw token stream), with per-element wasQuoted provenance captured **locally** in the field-parser before parseKey advances the cursor — no struct changes to AstField / FieldNode, no public-API broadening. Multi-agent-review caught one Important on go.hocon#88 (misleading error message for the S14a.10 unquoted-arg case — `include foo.conf` was getting the S12.5 reservation message instead of the spec-aligned "include argument must be a quoted string" message); fixed in-PR via spec-aligned error-message split (commit 7b5c80a). One Codex P1 on rs.hocon#90 (fixture-tracking concern) was dismissed with verified-empirical rationale — the pattern matches existing concat_errors_test.rs and CI's `make testdata` early-exit logic correctly detects remote-vs-local SHA divergence; all 8 CI checks PASS on rs#90 confirming the dismiss. Total 17 CI checks across the 3 PRs all SUCCESS pre-merge.

2026-05-18 (Phase 6 #3b) — re-rolled-up after the S10 concat type-check tightening impl PRs landed in all three impls ([ts.hocon#101](https://github.com/o3co/ts.hocon/pull/101), [rs.hocon#89](https://github.com/o3co/rs.hocon/pull/89), [go.hocon#87](https://github.com/o3co/go.hocon/pull/87)). S10.4 (3-way ❌→✅), S10.13 (ts/rs ❌→✅, go ⚠️→✅), and S10.19 (3-way ❌→✅) cleared from "Top spec violations"; S10.15 narrowed to go-only after rs incidentally cleared (scalar-between-array operands now errors via `join_pair` type-check; go elides separator tokens pre-fold so the type-check is never reached). E5 in `extra-spec-conventions.md` flipped ❌ → ✅ in all 3 impls (Lightbend silently accepts the construct; o3co tightens to spec). Rate lift per impl (in-scope): ts 90.9% → 92.5% (+1.6pp; 3 cells), rs 91.5% → 93.6% (+2.1pp; 4 cells including incidental S10.15), go 88.8% → 90.1% (+1.3pp; 3 cells, S10.13 was ⚠️ pre-fix). Spec-total: ts 80.9% → 82.3%, rs 82.3% → 84.2%, go 79.4% → 80.6%. go.hocon merge is **BREAKING** (prior permissive `[1, 2] 3 → [1, 2, 3]` removed; ts/rs were already error pre-fix so non-breaking there); migration documented in [go.hocon CHANGELOG](https://github.com/o3co/go.hocon/blob/develop/CHANGELOG.md). Multi-agent-review (Claude + Codex via mandatory `codex exec < INPUT > OUTPUT` file pipe) caught one cross-impl convergent issue on all 3 PRs: the **position-info gap** — ResolveError reported only the field's line/col, not the offending concat boundary. Convergence (3 independent reviewers flagging the same gap) promoted it from "Important" to "must-fix in-PR" per the multi-reviewer convergence rule; **Option A pattern** (line/col on `ConcatPlaceholder`, populated from AST pos, threaded into resolver) adopted uniformly. Subsequent Copilot rounds caught additional in-impl issues — rs.hocon#89 was batched as a 6-fix commit (25ba0b3 + fmt fix 54a51d5) covering clippy unreachable arm + type_name catch-all + 4 minor others; no Critical-class regressions on any PR.

2026-05-18 (Phase 6 #3g) — re-rolled-up after the S13c env-var-list expansion impl PRs landed in all three impls ([ts.hocon#100](https://github.com/o3co/ts.hocon/pull/100), [rs.hocon#88](https://github.com/o3co/rs.hocon/pull/88), [go.hocon#86](https://github.com/o3co/go.hocon/pull/86)) plus xx.hocon ground-truth follow-up fixtures ev12a/ev12b/ev13 ([xx.hocon#21](https://github.com/o3co/xx.hocon/pull/21)). S13c.1–S13c.5 cleared from "Top spec violations" in all 3 (5 items × 3 impls = 15 cells flipped ❌ → ✅); E6 and E7 flipped 🤷 → ✅ in `extra-spec-conventions.md` for all 3. Rate lift per impl (in-scope, uniform): ts 88.2% → 90.9% (+2.7pp), rs 88.8% → 91.5% (+2.7pp), go 86.1% → 88.8% (+2.7pp). Multi-agent-review (Claude + Codex via mandatory `codex exec < INPUT > OUTPUT` file pipe) on all 3 impl branches caught 2 cross-impl convergent issues (I1: empty-segment guard, I2: E7 whitespace allow-list tightening). Subsequent Copilot rounds caught one Critical-class issue each on rs.hocon#88 (cache-key collision for `${"X[]"}` vs `${X[]}` — fixed by adding bracket-quoting to `segments_to_key`) and go.hocon#86 (include-scope `${X[]}` does not fall back to original-path config — left as `discuss` and deferred to [xx.hocon#22](https://github.com/o3co/xx.hocon/issues/22) since it requires cross-impl coordination + new ev12c-style fixture). Architecture-divergence note: rs.hocon additionally marked `SubstPayload` (publicly re-exported, BREAKING) and the `AstNode::Substitution` variant (parser is `pub(crate)`, technically not public-API) as `#[non_exhaustive]` (CHANGELOG documents migration: use a constructor/builder for `SubstPayload`, add `..` to exhaustive pattern matches on `Substitution { ... }`) so future field additions (including the planned xx.hocon#22 fix) remain non-breaking. go.hocon's cache architecture (self-ref-recovery only) is structurally immune to the C1 cache-collision class that affected ts + rs; verified empirically before review. Go-specific test bookkeeping: `ev08-self-append` promoted from `t.Skip` tripwire to SUCCESS in all 3 impls after multi-impl probe confirmed the prior-value distinction from S13a.13.

2026-05-18 (Phase 6 #3c-followup) — re-rolled-up after [go.hocon#84](https://github.com/o3co/go.hocon/pull/84) landed (closes [go.hocon#81](https://github.com/o3co/go.hocon/issues/81)). go-only follow-up to Phase 6 #3c that adds parser-level numeric-key support (TokenFloat as key start, TokenInt/TokenFloat + adjacent unquoted/keyword tail concat, gated to prevent quoted-key re-split). S11.3 (`1.2.3 = x` → `["1","2","3"]`) and S11.4 (`10.0foo = x` → `["10","0foo"]`) closed as side effects — both rows removed from "Top spec violations" since all 3 impls are now ✅ on those items. S8.6 narrative tightened: us08/us09 now pass; status remains ⚠️ in all 3 because us13/us15 strict lex-time rejection is still a known gap. Rate change (in-scope) — go only: 85.0% → 86.1% (+1.1pp; +2 cells flipped ❌ → ✅). ts/rs unchanged. Multi-reviewer cycle (3 rounds Claude + Codex via mandatory `codex exec < INPUT > OUTPUT` file pipe) caught one Critical-class regression (quoted-key concat re-split — `"a.b"c = 1` was silently accepted as path `["a","bc"]` in round 1, fixed via `prevKeyTokenIsNumeric` gate) and two Important-class asymmetries (keyword-tail tokens not in concat predicate — round 2, fixed; signed-numeric multi-tail `123-456 = 1` — round 3, deferred to [go.hocon#83](https://github.com/o3co/go.hocon/issues/83)). Each finding was a real correctness issue that single-reviewer review would have missed.

2026-05-18 (Phase 6 #3c) — re-rolled-up after the third Phase 6 impl-gap wave landed in all three impls ([ts.hocon#96](https://github.com/o3co/ts.hocon/pull/96) + [ts.hocon#97](https://github.com/o3co/ts.hocon/pull/97), [rs.hocon#86](https://github.com/o3co/rs.hocon/pull/86), [go.hocon#82](https://github.com/o3co/go.hocon/pull/82)). S8.6 partially cleared in all 3 (1 item × 3 impls = 3 cells flipped from ❌ to ⚠️). Rate change per impl (in-scope): ts 88.4% → 88.2% (−0.2pp), rs 89.9% → 88.8% (−1.1pp), go 84.8% → 85.0% (+0.2pp). The rate movement is intentionally mixed because ❌ → ⚠️ flips contribute +0.5/N to the numerator but the develop-branch state since the previous Phase 6 #2 roll-up also included unrelated `✅` → `⚠️` shifts in ts/rs (other tracked items reclassified), so the net per-impl delta varies. Architecture divergence (intentional): ts/rs took Option B (single peek-ahead in unquoted-only lexer); go took Option A (greedy-with-backtrack `lex_number` per HOCON.md number grammar, since the lexer already has TokenInt/TokenFloat). Multi-reviewer convergence on the `parseSubstBody` segment-start gate: Claude+Codex flagged the missing `!curStarted` gate on rs.hocon PR #86, which then surfaced the same bug already merged in ts.hocon PR #96 (fixed via follow-up PR #97); go.hocon PR #82 implemented the gate correctly from the start based on rs/ts learning. Side-effect fix (go-only): CI `.github/workflows/test.yml` now uses `-coverpkg=./...` so cross-package coverage is measured correctly for codecov (was falsely failing patch-coverage on PRs with cross-package integration tests). Two strict-spec gaps remain `it.fails`/`#[should_panic]`/`t.Skip` tripwires (us13 `01`, us15 `1e+x`); go.hocon additionally defers parser numeric-key support for us08/us09 to [go.hocon#81](https://github.com/o3co/go.hocon/issues/81).

2026-05-17 (Phase 6 #2) — re-rolled-up after the second Phase 6 impl-gap wave landed in all three impls ([ts.hocon#95](https://github.com/o3co/ts.hocon/pull/95), [rs.hocon#85](https://github.com/o3co/rs.hocon/pull/85), [go.hocon#80](https://github.com/o3co/go.hocon/pull/80)). S15.1–S15.7 cleared from "Top spec violations" in all 3 (6 cells × 3 impls = 18 cells flipped from ❌ to ✅, plus S15.4 promoted from incidental-pass to explicit-guard ✅). Rate lift per impl (in-scope): ts 84.7% → 88.4% (+3.7pp), rs 85.4% → 89.9% (+4.5pp), go 81.6% → 84.8% (+3.2pp). Three new entries E2/E3/E4 in `extra-spec-conventions.md` document canonical-text-strict integer-key parse rule (`"00"` / `"+1"` / `"-0"` rejected via pre-filter regex despite native JS/Rust/Go parsers accepting them). Side-effect fixes from the separator-skip required by S15.3 simultaneously cleared S10.2 (rs), S10.14 (ts), S10.17 (rs), and S13.14 (ts, rs) — 5 additional cells flipped ❌/⚠️ → ✅. Multi-reviewer convergence on the multi-piece concat: 5/6 reviewers (3 Codex + 2 Claude general-purpose Opus) independently flagged that the initial single-pass loop variant produced wrong results for overlapping numeric keys; follow-up fixture [xx.hocon#11](https://github.com/o3co/xx.hocon/pull/11) (na03e-multi-piece-overlap) pinned Lightbend ground truth and all 3 impls refactored to true left-to-right pairwise fold via `joinPair`. rs.hocon additionally extended serde `deserialize_seq` + `deserialize_enum` (`OwnedHoconDeserializer`) to thread the conversion through typed `Vec<T>` deserialization.

2026-05-16 (Phase 6 #1) — re-rolled-up after the first Phase 6 impl-gap wave landed in all three impls ([ts.hocon#94](https://github.com/o3co/ts.hocon/pull/94), [rs.hocon#84](https://github.com/o3co/rs.hocon/pull/84), [go.hocon#78](https://github.com/o3co/go.hocon/pull/78)). S6.1/6.2/6.4 cleared from "Top spec violations" in all 3 (3 items × 3 impls = 9 cells flipped from ❌/⚠️ to ✅); S6.3 coverage broadened (BOM anywhere, not just start-of-input). Rate lift per impl: ts 83.3% → 84.7%, rs 84.0% → 85.4%, go 80.2% → 81.6% (in-scope). 3-way convergent intentional behavior changes recorded in PR descriptions and the "Cleared in Phase 6 #1" section above: BOM mid-stream is now whitespace; CR inside `${...}` is now inter-segment whitespace (was error). New `extra-spec-conventions.md` doc added (E-namespace, separate from S-namespace) capturing NEL handling (E1) as the first cross-impl extra-spec convention; S6.6 row from go.hocon's per-impl file (added in #78) was reclassified out of canonical scope and removed via [go.hocon#79](https://github.com/o3co/go.hocon/pull/79).

2026-05-13 (Phase 5) — re-rolled-up after Phase 5 per-impl `🤷` mop-up landed in all three impls ([ts.hocon#91](https://github.com/o3co/ts.hocon/pull/91), [rs.hocon#82](https://github.com/o3co/rs.hocon/pull/82), [go.hocon#76](https://github.com/o3co/go.hocon/pull/76)). 65 items total (ts 20 / rs 17 / go 28) cleared from `🤷` to verified ✅ / ⚠️ / ❌ / ➖. All three impls now have `🤷 = 0`. New per-impl ➖ added (NOT globally OOS): ts S1.1 + S13a.10, go S13a.10 — denominators diverge to ts=186, rs=188, go=187. New cross-impl violation rows: S3.1 expanded to 3-way (ts/go ❌, rs ⚠️), S18.4 3-way (ts/go ❌, rs ⚠️), S10.15 + S13.15 (rs+go ❌), S19.8 (ts+rs ❌), S23.4 (ts+go ❌), plus 12 single-impl rows (ts: S13a.3/S18.1/S22.2; rs: S10.2/S10.17; go: S1.1/S8.2/S11.3/S13a.12/S14a.10/S19.1/S19.2). Multi-reviewer convergence: Copilot caught S1.1 misclassification on go.hocon — initially marked ➖ on incorrect "Go strings are UTF-8" rationale; reclassified ❌ after probe showed silent U+FFFD substitution.

2026-05-13 (Phase 4) — re-rolled-up after Phase 4 type-conversion / values test-debt PRs landed in all three impls ([ts.hocon#90](https://github.com/o3co/ts.hocon/pull/90), [rs.hocon#81](https://github.com/o3co/rs.hocon/pull/81), [go.hocon#75](https://github.com/o3co/go.hocon/pull/75)). 12–13 items × 3 impls promoted from 🤷 to verified ✅ / ⚠️ / ❌ / ➖. S17.5 reclassified globally OOS (denominator: 21 OOS, in-scope N = 188). New cross-impl violation rows: S15.1–S15.3/S15.5–S15.7 (3-way ❌), S17.6 (ts ⚠️), S17.7/S17.8 (go ⚠️), S21.4 (ts+go ❌), S21.5 (go ❌). Multi-reviewer convergence on S15.3 across ts/rs/go: initial tests didn't exercise concatenation context; uniform rewrite to `[a] ${obj}` adopted.

2026-05-12 — re-rolled-up after Phase 3 substitution/include test-debt PRs landed in all three impls ([ts.hocon#85](https://github.com/o3co/ts.hocon/pull/85), [rs.hocon#77](https://github.com/o3co/rs.hocon/pull/77), [go.hocon#69](https://github.com/o3co/go.hocon/pull/69)). 11 items × 3 impls promoted from 🤷 to verified ✅ / ⚠️ / ❌; S13a.10 explicitly deferred as not externally observable. 3 new cross-impl violation rows added to Top spec violations (S13.9, S13.14, S13a.13); S13a.13 is convergent ❌ across all three impls; S13.14 has go as the lone compliant impl.
