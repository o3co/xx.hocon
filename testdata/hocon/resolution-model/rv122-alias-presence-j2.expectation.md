# rv122-alias-presence-j2 — expectation from the reference model, not from Lightbend

kind: o3co-J2
lightbend-1.4.9: {"common": {"p": {}, "use": 1, "x": {}}, "x": {"y": 1}}

resolution-model §5.3 / §11 J2: `${common.x.y}` crosses the alias `x = ${p}`, and `p.y` is raw-present (an undefined optional), so the path is found, resolves to nothing, and the required reference is an error — in every key order. Lightbend resolves `p` first here (HashMap order); the memoized `p = {}` then hides `y`, so it falls back to the original path and gives `use = 1`. With the keys renamed so that the reader is resolved first (rv123) Lightbend errors too.

See docs/resolution-model.md (§11, §12) and docs/fixture-conventions.md "Resolution-model fixtures".
