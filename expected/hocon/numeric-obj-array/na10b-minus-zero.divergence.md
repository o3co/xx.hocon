# na10b-minus-zero — Lightbend vs o3co divergence

**Extra-spec rule:** [E4 — leading sign char on zero rejection](../../../docs/extra-spec-conventions.md#e4--numeric-key-array-conversion-leading--key-forms-rejected-including-0--0--0)

**Fixture:** [na10b-minus-zero.conf](../../../testdata/hocon/numeric-obj-array/na10b-minus-zero.conf)

## Source

```hocon
items = {"-0":"a","0":"b"}
```

## Parse tree (identical between Lightbend and o3co)

```json
{
  "items": {
    "-0": "a",
    "0": "b"
  }
}
```

See `na10b-minus-zero-expected.json` for the generator-captured output.

## Accessor-time divergence: `getList("items")`

| Implementation | Result | Reason |
| --- | --- | --- |
| Lightbend (typesafe-config 1.4.x) | `["a"]` **or** `["b"]` (non-deterministic) | `Integer.parseInt("-0", 10) == 0` and `Integer.parseInt("0", 10) == 0`. The negative-filter `if (i < 0) continue;` does NOT skip `-0` (because the parsed int is `0`, not `<0`). Both end up in `HashMap<Integer, ConfigValue>` at key `0`; the second `put` overwrites the first. Winning value depends on object key iteration order. |
| o3co (ts/rs/go) | `["b"]` (deterministic) | The pre-filter regex `^(0\|[1-9][0-9]*)$` rejects `"-0"` (leading `-`). Only `"0"` is eligible → array `["b"]`. |

## Why we diverge

`"-0"` and `"0"` both numerically denote zero. We reject leading sign characters to guarantee canonical text per integer. See the [E4 entry in extra-spec-conventions.md](../../../docs/extra-spec-conventions.md#e4--numeric-key-array-conversion-leading--key-forms-rejected-including-0--0--0) for the full rationale.

This case demonstrates that Lightbend's negative-filter (`i < 0`) is incomplete: it catches `"-1"`, `"-2"`, … but lets `"-0"` slip through. Our pre-filter handles it uniformly.

## Where this is asserted

- Per-impl tests (ts/rs/go) load this fixture and assert `getList("items") == ["b"]`.
- Lightbend's behaviour is not asserted programmatically (Option B); this document records it for traceability.
