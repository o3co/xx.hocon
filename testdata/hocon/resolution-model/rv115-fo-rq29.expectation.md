# rv115-fo-rq29 — expectation from the reference model, not from Lightbend

kind: o3co-J2
lightbend-1.4.9: {"common": {"x": {}, "z": 1}, "x": {"y": 1}}

resolution-model §11 J2: presence is judged on the raw value, so the fixed-up `${common.x.y}` is found (and resolves to nothing): an error in every key order, as rq29. Lightbend, with the inner `z` resolved after `x`, sees the memoized `x = {}`, falls back to the original path and gives `z = 1`.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "resolution-model/".
