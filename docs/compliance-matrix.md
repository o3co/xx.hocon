# HOCON Spec Compliance Matrix

Cross-implementation roll-up of [`spec-checklist.md`](spec-checklist.md) for the three sibling implementations. This file is the public-facing one-page summary; per-item detail lives in each implementation's own `docs/spec-compliance.md`.

## Top-line compliance rate

| Implementation | Spec-total | In-scope | ✅ | ⚠️ | ❌ | 🤷 | ➖ |
|---|---:|---:|---:|---:|---:|---:|---:|
| [ts.hocon](https://github.com/o3co/ts.hocon/blob/main/docs/spec-compliance.md) | **54.8%** | **60.6%** | 114 | 1 | 7 | 67 | 20 |
| [rs.hocon](https://github.com/o3co/rs.hocon/blob/develop/docs/spec-compliance.md) | **57.2%** | **63.2%** | 119 | 1 | 6 | 63 | 20 |
| [go.hocon](https://github.com/o3co/go.hocon/blob/main/docs/spec-compliance.md) | **52.6%** | **58.2%** | 109 | 2 | 6 | 72 | 20 |

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
| S8.1 | ts | ⚠️ | Lexer allows backtick in unquoted strings, contrary to spec L245 forbidden set |
| S8.6 | ts | ❌ | Lexer permits `0-9` and `-` as unquoted starts; parser turns `123abc` / `-foo` into strings instead of rejecting |
| S10.13 | go | ⚠️ | Permissive: `a = [1, 2] 3` → `[1, 2, 3]`; spec L373 requires error for array/object in string concat |
| S11.4 | go | ❌ | Parser does not accept TokenFloat keys; `10.0foo = x` is rejected rather than producing the spec-defined path split |
| S13.11 | go | ⚠️ | Lenient mode drops optional substitutions in nested-include scope ([#45](https://github.com/o3co/go.hocon/issues/45)) |
| S13c.1–S13c.5 | ts, rs, go | ❌ | `${X[]}` env-var list not implemented; each implementation's lexer rejects `[` / `]` inside `${...}` body |
| S14c.2 | rs | ❌ | Non-relativized substitution path fallback not implemented ([#44](https://github.com/o3co/rs.hocon/issues/44)) |

## Shared test debt

Spec items with no test coverage in **any** of the three implementations. These are the natural targets for future test-debt PRs:

- **S5.3–S5.6** — invalid comma patterns (double trailing, leading, consecutive)
- **S6.1, S6.2, S6.4** — Unicode whitespace categories (Zs/Zl/Zp, non-breaking spaces, ASCII control set)
- **S10.4** — mixing arrays + objects in concat is an error
- **S15.1–S15.7** — numerically-indexed object → array conversion (entire section)
- **S17.5, S17.7, S17.8** — type conversion error cases (null/object/array as wrong type)
- **S21.4, S21.5** — single-letter byte abbreviations, fractional byte values

See each `<repo>/docs/spec-compliance.md` for the full per-impl `🤷` list.

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

2026-05-12 — initial population after d.2 execution pass + Codex review round 2.
