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

## How this file is maintained

1. Add a new item when a cross-impl convergence (or divergence worth documenting) is observed that does not map to a row in [`spec-checklist.md`](spec-checklist.md).
2. Use the next available `E<n>` ID; never reuse retired IDs.
3. Each entry records: source / rationale, per-impl status with test refs, and any cross-impl divergence notes.
4. E-items do not affect compliance rates in [`compliance-matrix.md`](compliance-matrix.md), but the matrix should link here so readers can find these conventions when scanning compliance state.

## Last verified

2026-05-16 — file created; E1 (NEL) added.
