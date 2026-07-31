# max_depth.conf — implementations may impose a document-depth limit

**Nesting:** 1728 levels, in ~6.9 kB. The next deepest fixture anywhere in this
corpus is **8**, so this file is three orders of magnitude past anything a real
config reaches — which is what it was harvested to probe.

**Observed (reference impl, typesafe-config 1.4.6):** parses successfully;
`max_depth-expected.json` is generated from it.

**Spec analysis:** HOCON.md places no bound on object or array nesting, so a
document this deep is spec-legal. But no implementation can recurse without a
stack, and what happens when one runs out is a property of the *language*, not
of HOCON:

| implementation | outcome | recoverable by the caller? |
|---|---|---|
| go.hocon | parses | — goroutine stacks grow on demand |
| ts.hocon | `ParseError` | yes — V8's `RangeError` is caught and rethrown |
| py.hocon | `ParseError` | yes — CPython's `RecursionError` is caught and rethrown |
| rs.hocon | `ParseError`, at a declared limit of 128 | yes — but only because it refuses *before* the stack runs out |

rs.hocon is the one that has to name a number. A Rust stack overflow is
`SIGABRT`: no `catch_unwind` contains it and it takes the caller's whole process
with it, so there is nothing to catch and rethrow. Its limit is `serde_json`'s
128, which its own JSONC adapter already enforced, and it holds on the 2 MiB
stack Rust gives a spawned thread — where the parser survived ~600 levels and
aborted by 1000 (o3co/rs.hocon#162).

This is the same shape as S1.2.6: a divergence that follows from the host
language rather than from a reading of the spec, so it is recorded rather than
resolved.

**Consumer guidance:** do **not** treat this fixture as a spec-normative
must-parse case. An implementation that refuses it with its own error type is
conforming; one that *crashes* on it is not, and that is the property worth
testing here. The thresholds above were measured with a synthetic probe rather
than with this file, because one fixture can only say pass or fail and not where
each implementation gives out.
