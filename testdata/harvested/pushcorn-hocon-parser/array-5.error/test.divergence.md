# array-5.error/test.conf — expected.json records a Lightbend quirk; strict impls error

**Observed (reference impl, typesafe-config 1.4.6):** succeeds with
`{"a": [3, 4]}` — the trailing `test` string in `a = [ 3, 4 ]  test` is
silently discarded.

**Spec analysis:** S10.13 (HOCON.md §Value concatenation): arrays and objects
may not appear in string value concatenations — this is a type error. The
upstream project agrees (the case is named `.error`), and the o3co strict
posture is already documented for the spec corpus (`concat-errors/`
`ce01`–`ce15`, docs/fixture-conventions.md §Lightbend quirks).

**Consumer guidance:** the `-expected.json` here records reference behaviour
(a known Lightbend leniency), not the spec rule. Strict implementations —
including all four o3co parsers — reject this fixture with a concat type
error; treat that rejection as posture-conforming, not as a failure.
