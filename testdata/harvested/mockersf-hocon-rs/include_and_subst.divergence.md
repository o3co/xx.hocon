# include_and_subst.conf — Lightbend-limitation error, not spec-normative

**Observed (reference impl, typesafe-config 1.4.6):** `ConfigException$Parse` —
"Due to current limitations of the config parser, when an include statement is
nested inside a list value, it cannot be the empty object or a list."

**Spec analysis (HOCON.md §"Include syntax"):** "An include statement can
appear in place of an object field." An object literal inside an array is
still an object, so `in_array = [ { include "substitution.conf" } ]` is
spec-valid include placement. The error is a self-declared parser limitation,
not a spec rule.

**Upstream expectation:** mockersf/hocon.rs performs the inclusion in both the
object and the array position.

**Consumer guidance:** the `.error` sidecar records observed reference
behaviour only. A spec-conforming implementation may legitimately succeed
here (resolving to the included object's contents); do not treat this fixture
as a spec-normative must-error case.
