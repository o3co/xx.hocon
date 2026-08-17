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
engages them here (erroring) but resolves through them in object6.

**Triage (xx.hocon#67, settled 2026-08-17 as [E16](../../../../docs/extra-spec-conventions.md)):**
the pair was decided together, but not in the same direction — what makes a
superseded entry reachable is the *winning value's type*. An object winner
merges, so the stack below it stays live (object6: Lightbend is right, and all
four impls must be fixed). An array or scalar winner replaces, so the stack
below it is dead — which is this case, and here Lightbend is the one diverging
from the spec it defines. The o3co implementations keep their current
behaviour and the divergence is recorded in
`differential/known-divergences.json` against E16.

**Consumer guidance:** the `.error` sidecar records **observed** reference
behaviour and is not spec-normative. A conforming implementation MUST succeed
here, producing `sub = [10]` and `merged_array = [10, 5, 6]`.
