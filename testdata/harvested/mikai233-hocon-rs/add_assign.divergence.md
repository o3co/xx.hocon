# add_assign.conf — Lightbend-limitation error, not spec-normative

**Observed (reference impl, typesafe-config 1.4.6):** `ConfigException$Parse` —
"Due to current limitations of the config parser, += does not work nested
inside a list."

**Spec analysis (HOCON.md §"The `+=` field separator"):** `a += b` is defined
as a pure syntactic transformation to `a = ${?a} [b]`. The spec places no
restriction on `+=` appearing inside an object that is itself an element of
another `+=`/array value. The error is a self-declared parser limitation, not
a spec rule.

**Upstream expectation:** mikai233/hocon-rs resolves this fixture successfully
(`add_assign_expected.json` at the pinned commit).

**Consumer guidance:** the `.error` sidecar records observed reference
behaviour only. A spec-conforming implementation may legitimately succeed
here; do not treat this fixture as a spec-normative must-error case. Posture
per implementation is decided when the conformance runners are wired to this
corpus.
