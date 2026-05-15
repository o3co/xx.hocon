# na10a-plus-sign — Lightbend vs o3co divergence

**Extra-spec rule:** [E3 — leading `+` key rejection](../../../docs/extra-spec-conventions.md#e3--numeric-key-array-conversion-leading--key-forms-rejected-1--1)

**Fixture:** [na10a-plus-sign.conf](../../../testdata/hocon/numeric-obj-array/na10a-plus-sign.conf)

## Source

```hocon
items = {"+1":"a","0":"b"}
```

## Parse tree (identical between Lightbend and o3co)

```json
{
  "items": {
    "+1": "a",
    "0": "b"
  }
}
```

See `na10a-plus-sign-expected.json` for the generator-captured output.

## Accessor-time divergence: `getList("items")`

| Implementation | Result | Reason |
| --- | --- | --- |
| Lightbend (typesafe-config 1.4.x) | `["b", "a"]` (deterministic) | `Integer.parseInt("+1", 10) == 1` and `Integer.parseInt("0", 10) == 0`. Two distinct integer keys → no collision. Sorted by integer: `0 → "b"`, `1 → "a"` → `["b", "a"]`. |
| o3co (ts/rs/go) | `["b"]` (deterministic) | The pre-filter regex `^(0\|[1-9][0-9]*)$` rejects `"+1"` (leading `+`). Only `"0"` is eligible → array `["b"]`. |

## Why we diverge

`"+1"` and `"1"` both denote the same integer. We reject leading `+` to guarantee canonical text per integer (`N` ↔ `Integer.toString(N)` only). See the [E3 entry in extra-spec-conventions.md](../../../docs/extra-spec-conventions.md#e3--numeric-key-array-conversion-leading--key-forms-rejected-1--1) for the full rationale.

Note: this is the only one of the three E-row cases where Lightbend's behaviour is *deterministic* (no HashMap collision). The divergence is purely a deliberate canonical-form tightening.

## Where this is asserted

- Per-impl tests (ts/rs/go) load this fixture and assert `getList("items") == ["b"]`.
- Lightbend's behaviour is not asserted programmatically (Option B); this document records it for traceability.
