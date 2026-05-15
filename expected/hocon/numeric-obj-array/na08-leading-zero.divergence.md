# na08-leading-zero — Lightbend vs o3co divergence

**Extra-spec rule:** [E2 — leading-zero key rejection](../../../docs/extra-spec-conventions.md#e2--numeric-key-array-conversion-leading-zero-key-forms-rejected-00--0)

**Fixture:** [na08-leading-zero.conf](../../../testdata/hocon/numeric-obj-array/na08-leading-zero.conf)

## Source

```hocon
items = {"00":"a","0":"b"}
```

## Parse tree (identical between Lightbend and o3co)

```json
{
  "items": {
    "00": "a",
    "0": "b"
  }
}
```

Both parsers preserve `"00"` and `"0"` as distinct object keys — see `na08-leading-zero-expected.json` for the generator-captured output.

## Accessor-time divergence: `getList("items")`

| Implementation | Result | Reason |
| --- | --- | --- |
| Lightbend (typesafe-config 1.4.x) | `["a"]` **or** `["b"]` (non-deterministic) | `Integer.parseInt("00", 10) == 0` and `Integer.parseInt("0", 10) == 0`. `DefaultTransformer` puts both into `HashMap<Integer, ConfigValue>`; the second `put` overwrites the first. The winning value depends on object key iteration order, which Java's `HashMap` does not guarantee. |
| o3co (ts/rs/go) | `["b"]` (deterministic) | The pre-filter regex `^(0\|[1-9][0-9]*)$` rejects `"00"`. Only `"0"` is eligible → array `["b"]`. |

## Why we diverge

Lightbend's HashMap-collision behaviour gives a non-deterministic result, which we reject in favour of canonical-text guarantee (each integer has exactly one textual form). See the [E2 entry in extra-spec-conventions.md](../../../docs/extra-spec-conventions.md#e2--numeric-key-array-conversion-leading-zero-key-forms-rejected-00--0) for the full rationale.

## Where this is asserted

- Per-impl tests (ts/rs/go) load this fixture and assert `getList("items") == ["b"]`.
- Lightbend's behaviour is not asserted programmatically (Option B); this document records it for traceability.
