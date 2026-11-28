# rv120-rv-restricted-then-full — expectation from the reference model, not from Lightbend

kind: e23-order
lightbend-1.4.9: {"a": {"q": 1, "r": 1}, "p": {"q": 1, "r": 1}, "z": {"q": 1, "r": 1}}

In document order `a` is resolved first and `${a.q}` re-enters the active `${p}`: a cycle error. Lightbend resolves `p` (HashMap bucket 0) first and succeeds; with the keys renamed so that its order is the document order (`a`, `b`, `c`) it errors as well.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "resolution-model/".
