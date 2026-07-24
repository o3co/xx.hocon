# object-4.error/test.conf — expected.json records a Lightbend quirk; strict impls error

**Observed (reference impl, typesafe-config 1.4.6):** succeeds with
`{"a": {"b": 3}}` — the trailing `test` string in `a = { b: 3 } test` is
silently discarded.

**Spec analysis:** same as `array-5.error` (see its `.divergence.md`): S10.13
makes object-in-string-concat a type error; upstream also expects an error;
the o3co strict posture is documented in the spec corpus (`concat-errors/`,
docs/fixture-conventions.md §Lightbend quirks).

**Consumer guidance:** strict implementations reject this fixture; treat that
rejection as posture-conforming, not as a failure.
