# object-7/test.conf — error is spec-robust, but the reference error class is an internal crash

**Observed (reference impl, typesafe-config 1.4.6):**
`ConfigException$BugOrBroken` — "tried to replace ConfigDelayedMerge(…) which
is not in [SimpleConfigObject(…)]". This is Lightbend's internal-invariant
("should never happen") exception, triggered by `vh = null` followed by two
`vh += { … }` appends inside an object whose path was earlier set to null.

**Spec analysis (HOCON.md §"The `+=` field separator"):** `vh += b` expands to
`vh = ${?vh} [b]`; "If the previous value was not an array, an error will
result just as it would in the long form." The previous value here is `null`
(not an array), so an error is the spec-derivable outcome — but a *type/concat*
error, not an internal-invariant crash.

**Consumer guidance:** "this fixture errors" is spec-robust; the reference
error class is not meaningful and must not be matched. A conforming
implementation should reject this with an ordinary concatenation/type error.
Candidate for an upstream lightbend/config report (internal `BugOrBroken`
reachable from config text).
