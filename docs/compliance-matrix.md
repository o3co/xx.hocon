# HOCON Spec Compliance Matrix

Cross-implementation roll-up of [`spec-checklist.md`](spec-checklist.md) for the three sibling implementations. This file is the public-facing one-page summary; per-item detail lives in each implementation's own `docs/spec-compliance.md`.

## Top-line compliance rate

| Implementation | Spec-total | In-scope | ✅ | ⚠️ | ❌ | 🤷 | ➖ |
|---|---:|---:|---:|---:|---:|---:|---:|
| [ts.hocon](https://github.com/o3co/ts.hocon/blob/develop/docs/spec-compliance.md) | **66.5%** | **73.5%** | 137 | 4 | 16 | 32 | 20 |
| [rs.hocon](https://github.com/o3co/rs.hocon/blob/develop/docs/spec-compliance.md) | **68.9%** | **76.2%** | 142 | 4 | 16 | 27 | 20 |
| [go.hocon](https://github.com/o3co/go.hocon/blob/develop/docs/spec-compliance.md) | **63.4%** | **70.1%** | 131 | 3 | 15 | 40 | 20 |

Where:

- **Spec-total** = `(✅ + ⚠️·0.5) / 209`. Denominator includes ALL items, including the 20 globally out-of-scope. Out-of-scope items intentionally lower this number — it is the answer to "how much of HOCON.md does this implementation handle?".
- **In-scope** = `(✅ + ⚠️·0.5) / 189`. Denominator excludes the 20 globally out-of-scope items. This is the answer to "of what the implementation chooses to support, how much is covered?".
- `❌` and `🤷` contribute 0. `🤷` is treated as 0 because an unverified claim is, by policy, not a pass — pinning it as ✅/❌ requires a test.

Both numbers are shown side by side so neither over-claims nor under-claims. See [`spec-checklist.md`](spec-checklist.md) for the convention rationale.

## Status legend

| Glyph | Meaning |
| --- | --- |
| ✅ | Test exists and passes |
| ⚠️ | Test exists, partial pass / pinning a spec-violating behavior |
| ❌ | Test exists and fails, OR known spec violation documented in source |
| 🤷 | No test — implementation claim only, unverified |
| ➖ | Globally out of scope (rationale required) |

## Globally out-of-scope items (20)

These 20 items are marked `➖` in all three implementations, by policy:

| Items | Rationale class |
|---|---|
| S14a.4, S14f.5 | classpath resources are a JVM-only concept |
| S16.1 | MIME Type is set by HTTP servers, not parsers |
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
| S3.1 | rs | ⚠️ | Empty file → empty object; spec L130 says empty is invalid |
| S3.4 | ts | ❌ | Unbraced root + stray `}` accepted ([#55](https://github.com/o3co/ts.hocon/issues/55)) |
| S6.1 | ts, rs, go | ❌ | Lexer ignores Unicode Zs/Zl/Zp whitespace categories ([ts#72](https://github.com/o3co/ts.hocon/issues/72), [rs#62](https://github.com/o3co/rs.hocon/issues/62), [go#59](https://github.com/o3co/go.hocon/issues/59)) |
| S6.2 | ts, rs, go | ❌ | Non-breaking spaces (U+00A0, U+2007, U+202F) not treated as whitespace ([ts#72](https://github.com/o3co/ts.hocon/issues/72), [rs#62](https://github.com/o3co/rs.hocon/issues/62), [go#59](https://github.com/o3co/go.hocon/issues/59)) |
| S6.4 | ts, rs, go | ⚠️ | Of 8 ASCII control whitespace chars, only tab + CR recognized; vtab, FF, FS–US fail in all 3 impls ([ts#72](https://github.com/o3co/ts.hocon/issues/72), [rs#62](https://github.com/o3co/rs.hocon/issues/62), [go#59](https://github.com/o3co/go.hocon/issues/59)) |
| S8.1 | ts | ⚠️ | Lexer allows backtick in unquoted strings, contrary to spec L245 forbidden set |
| S8.6 | ts, rs, go | ❌ | Lexer permits `0-9` and `-` as unquoted starts; non-numeric forms like `123abc` / `-foo` are coerced to strings instead of rejected ([ts#73](https://github.com/o3co/ts.hocon/issues/73), [rs#63](https://github.com/o3co/rs.hocon/issues/63), [go#60](https://github.com/o3co/go.hocon/issues/60)) |
| S10.4 | ts, rs, go | ❌ | Mixing arrays + objects in concat silently allowed; spec L385 requires error ([ts#75](https://github.com/o3co/ts.hocon/issues/75), [rs#65](https://github.com/o3co/rs.hocon/issues/65), [go#63](https://github.com/o3co/go.hocon/issues/63)) |
| S10.8 | ts, rs, go | ❌ / ⚠️ | Unquoted concat in field keys (`a b = 1`) rejected; spec L317/L556 requires acceptance as key "a b". rs partial pass: quoted variant works ([ts#76](https://github.com/o3co/ts.hocon/issues/76), [rs#66](https://github.com/o3co/rs.hocon/issues/66), [go#65](https://github.com/o3co/go.hocon/issues/65)) |
| S10.13 | ts, rs, go | ❌ / ❌ / ⚠️ | Array/object in string concat silently accepted; spec L373 requires error. go ⚠️ permissive: `a = [1, 2] 3` → `[1, 2, 3]` ([ts#77](https://github.com/o3co/ts.hocon/issues/77), [rs#67](https://github.com/o3co/rs.hocon/issues/67)) |
| S10.14 | ts | ⚠️ | Object substitutions correct; whitespace not stripped around array substitutions (included as extra element) ([ts#78](https://github.com/o3co/ts.hocon/issues/78)) |
| S10.19 | ts, rs, go | ❌ | Mixing substitution-resolved object with literal array silently accepted; spec L385-389 requires error ([ts#79](https://github.com/o3co/ts.hocon/issues/79), [rs#68](https://github.com/o3co/rs.hocon/issues/68), [go#63](https://github.com/o3co/go.hocon/issues/63)) |
| S11.4 | go | ❌ | Parser does not accept TokenFloat keys; `10.0foo = x` is rejected rather than producing the spec-defined path split ([go#62](https://github.com/o3co/go.hocon/issues/62)). ts/rs verified compliant. |
| S11.8 | go | ❌ | Parser rejects TokenBool in key position; spec L504 requires stringification to `"true"` / `"false"`. Impl is stricter than spec ([go#66](https://github.com/o3co/go.hocon/issues/66)) |
| S12.5 | ts, rs, go | ❌ | `include.foo = 1` silently accepted as key `["include", "foo"]`; spec L570 reserves `include` from beginning a path expression ([ts#80](https://github.com/o3co/ts.hocon/issues/80), [rs#71](https://github.com/o3co/rs.hocon/issues/71), [go#67](https://github.com/o3co/go.hocon/issues/67)) |
| S13b.2 | ts, rs | ❌ | `+=` on non-array prior value silently allowed; spec L732 requires error. go ✅ correctly rejects ([ts#81](https://github.com/o3co/ts.hocon/issues/81), [rs#72](https://github.com/o3co/rs.hocon/issues/72)) |
| S13.9 | rs | ❌ | `HOME = null; result = ${?HOME}` resolves `result` to a present null scalar instead of erasing the field per L618 "null treated same as missing"; env value is correctly blocked ([rs#74](https://github.com/o3co/rs.hocon/issues/74)). ts ✅, go ✅. |
| S13.11 | go | ⚠️ | Lenient mode drops optional substitutions in nested-include scope ([#45](https://github.com/o3co/go.hocon/issues/45)) |
| S13.14 | ts, rs | ⚠️ | Object substitution concat ✅; array variant `[1] ${?missing} [2]` produces `[1, " ", " ", 2]` instead of `[1, 2]` — whitespace artefacts leak as extra elements per L637 ([ts#83](https://github.com/o3co/ts.hocon/issues/83), [rs#75](https://github.com/o3co/rs.hocon/issues/75)). go ✅. |
| S13a.13 | ts, rs, go | ❌ | `a = ${?a}foo` with no prior `a` resolves to `"foofoo"` not `"foo"` — the self-ref look-back picks up the trailing literal as its prior value per L841 ([ts#84](https://github.com/o3co/ts.hocon/issues/84), [rs#76](https://github.com/o3co/rs.hocon/issues/76), [go#68](https://github.com/o3co/go.hocon/issues/68)) |
| S13c.1–S13c.5 | ts, rs, go | ❌ | `${X[]}` env-var list not implemented; each implementation's lexer rejects `[` / `]` inside `${...}` body |
| S14c.2 | rs | ❌ | Non-relativized substitution path fallback not implemented ([#44](https://github.com/o3co/rs.hocon/issues/44)) |

## Shared test debt

Spec items with no test coverage in **any** of the three implementations. These are the natural targets for future test-debt PRs:

- **S15.1–S15.7** — numerically-indexed object → array conversion (entire section)
- **S17.5, S17.7, S17.8** — type conversion error cases (null/object/array as wrong type)
- **S21.4, S21.5** — single-letter byte abbreviations, fractional byte values

See each `<repo>/docs/spec-compliance.md` for the full per-impl `🤷` list.

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

4. Rates are computed as `(✅ + ⚠️·0.5) / N` with `N = 209` (spec-total) or `N = 209 − ➖ = 189` (in-scope).
5. When the template gains or loses an item, **all three per-repo files must be synced** before this matrix is rebuilt; otherwise the totals will be inconsistent.

## Last verified

2026-05-12 — re-rolled-up after Phase 3 substitution/include test-debt PRs landed in all three impls ([ts.hocon#85](https://github.com/o3co/ts.hocon/pull/85), [rs.hocon#77](https://github.com/o3co/rs.hocon/pull/77), [go.hocon#69](https://github.com/o3co/go.hocon/pull/69)). 11 items × 3 impls promoted from 🤷 to verified ✅ / ⚠️ / ❌; S13a.10 explicitly deferred as not externally observable. 3 new cross-impl violation rows added to Top spec violations (S13.9, S13.14, S13a.13); S13a.13 is convergent ❌ across all three impls; S13.14 has go as the lone compliant impl.
