# test03.conf — Lightbend-limitation error, not spec-normative

**Observed (reference impl, typesafe-config 1.4.6):** `ConfigException$Parse` —
"Due to current limitations of the config parser, when an include statement is
nested inside a list value, ${} substitutions inside the included file cannot
be resolved correctly. Either move the include outside of the list value or
remove the ${} statements from the included file" (the
`"array": [ { include "test01.conf" } ]` element).

**Spec analysis (HOCON.md §"Include syntax"):** "An include statement can
appear in place of an object field." An object literal inside an array is
still an object, so the array-element include is spec-valid placement. The
error is a self-declared parser limitation, not a spec rule.

**Note:** this fixture is an upstream-modified variant of the Lightbend suite's
`test03.conf` (see PROVENANCE.yaml); the array-include line is part of the
upstream modification.

**Consumer guidance:** the `.error` sidecar records observed reference
behaviour only. A spec-conforming implementation may legitimately succeed
here; do not treat this fixture as a spec-normative must-error case.
