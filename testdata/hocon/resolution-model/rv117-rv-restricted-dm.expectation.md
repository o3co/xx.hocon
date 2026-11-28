# rv117-rv-restricted-dm — expectation from the reference model, not from Lightbend

kind: o3co-J1
lightbend-1.4.9: ERR

resolution-model §11 J1: `b = 5` / `b = {k: ${c}}` resolves to `{k: 1}`, so `${b.k}` is found. Lightbend errors only when the reader runs before `b` (its ConfigDelayedMergeObject peek skips a key whose value does not ignore fallbacks); rv088 (reader resolved after `b`) shows it succeeding.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "resolution-model/".
