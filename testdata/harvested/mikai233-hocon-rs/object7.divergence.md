# object7.conf — Lightbend-limitation parse error; unbreakable cycle behind it

**Observed (reference impl, typesafe-config 1.4.6):** `ConfigException$Parse` —
"Due to current limitations of the config parser, += does not work nested
inside a list" (the `cc += ["qq"]` nested inside `c += { ... }`).

**Spec analysis (HOCON.md §"The `+=` field separator"):** as with
`add_assign.conf`, the nested `+=` form is spec-valid syntax sugar; the parse
error is a self-declared limitation.

**However:** the fixture also ends in `qq = ${dd}`, `dd = ${hh}`, `hh = ${qq}`
— an unbreakable substitution cycle, which the spec requires to be an error
(HOCON.md §"Self-Referential Substitutions": "unbreakable cycles that generate
an error"). A spec-conforming implementation that parses the nested `+=`
correctly is still expected to fail this fixture at resolution time.

**Consumer guidance:** "this fixture errors" is spec-robust; the error *stage*
(parse vs. resolve) and type are implementation-specific. Do not assert on the
reference error class here.
