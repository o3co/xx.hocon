# rv114-fo-e23-p2-bfirst — expectation from the reference model, not from Lightbend

kind: e23-view-reentry
lightbend-1.4.9: ERR

E23 rule 0: inside `b`'s look-back view the alias `a` is resolved afresh, so `a = "hello"` and `b = {}` (the #67 P2 probe with the alias written first, keys renamed). Lightbend's cycle markers ignore the view: cycle error.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "resolution-model/".
