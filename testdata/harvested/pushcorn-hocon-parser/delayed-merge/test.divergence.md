# delayed-merge/test.conf — reference cycle error vs clean final-value resolution

**Observed (reference impl, typesafe-config 1.4.6):**
`ConfigException$UnresolvedSubstitution` — "${sub} was part of a cycle of
substitutions".

**Spec analysis:** the final definition of `sub` (via `include "included"`) is
`[ ${will.be.overwritten} ]` with `will.be.overwritten = 10` — it contains no
self-reference, and the earlier self-referential definitions
(`sub = [ ${sub} ]`, `sub = [ ${sub}, 4 ]`) are dead under the forward-looking
final-value rule (array replaces array; no object merge is involved). Under
that reading there is no cycle: `sub = [10]`,
`merged_array = ${sub} [5, 6] = [10, 5, 6]` — which is exactly what all four
o3co implementations produce, unanimously.

This is the mirror image of the `mikai233-hocon-rs/object6.conf` divergence
(xx.hocon#67): both cases hinge on whether superseded entries of a delayed
merge stack participate in cycle detection. The reference implementation
engages them here (erroring) but resolves through them in object6 — the pair
is tracked together in xx.hocon#67.

**Consumer guidance:** the `.error` sidecar records observed reference
behaviour; a conforming implementation may legitimately succeed here. Do not
treat this as a spec-normative must-error case pending the #67 triage.
