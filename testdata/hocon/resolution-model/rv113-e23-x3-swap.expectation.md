# rv113-e23-x3-swap — expectation from the reference model, not from Lightbend

kind: e23-order
lightbend-1.4.9: {"a": 1, "b": 1}

E23 table (HOCON.md L870-873): with `b : 2` written first, `a = b = 2`. Lightbend resolves `a` first by HashMap order and gives `a = b = 1` whichever of the first two lines comes first; with the keys renamed so that its order is the document order, it gives `a = b = 2` like the convention.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "resolution-model/".
