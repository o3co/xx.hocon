# HOCON resolution model (o3co, v2)

**In one line: keep every assignment, and resolve `${}` only when a value is taken out.**

This is the shared resolution algorithm for go.hocon, rs.hocon, ts.hocon and py.hocon. Parity is a gate: if two implementations give different answers on a row of the acceptance corpus (§12), stop and fix this document or the implementation before moving on.

The reference model of this document is [`generate/reference-model/`](../generate/reference-model/README.md), a Python model written against this text.
- It is an expectation generator for E23 / o3co fixtures, **not a fifth implementation**.
- Where this text and the model disagree, one of them is fixed in the same PR.
- Cases whose answer is Lightbend's always take their expected value from Lightbend.

Related documents:
- [E23](extra-spec-conventions.md#e23) is the convention this document implements where it departs from Lightbend. The anchor exists once xx.hocon#138 is merged, and E23 links back here.
- [E12](extra-spec-conventions.md#e12), [E16](extra-spec-conventions.md#e16) and [E17](extra-spec-conventions.md#e17) are the other conventions it relies on.

## 0. How to read this document

Every rule carries one tag:

- **[LB *File.java:line*]**: a Lightbend config 1.4.9 mechanism, reproduced exactly. Paths are relative to `config/src/main/java/com/typesafe/config/impl/`. Citations are against the 1.4.9 source tree.
- **[E23]**: the o3co convention in [E23](extra-spec-conventions.md#e23), where it deliberately departs from Lightbend. **[E23-am1]** … **[E23-am4]** are the E23 amendments listed in §13. am1 is in E23 (xx.hocon#138, commit 33c503e); am2–am4 are proposed and assumed here.
- **[E17]**, **[E12]**: other o3co conventions.
- **[interim → step (b)]**: a temporary rule. Step (b) replaces it with E23 rule 1.
- **[o3co]**: a decision made in this document where Lightbend gives no deterministic answer. Each one is listed in §11.

"Must" is normative. Anything this document does not cover keeps the implementation's current behaviour. The lexer and token rules, typed getters, rendering, error classes and error messages are out of scope. Only *which* inputs error is in scope.

## 1. Values

### 1.1 Value kinds

After parsing, a value is one of:

| kind | meaning |
|---|---|
| `Scalar` | string, number, boolean or null. A string also records whether it was quoted. |
| `List(items)` | each item is one value. Items are *not* stacks. |
| `Obj(fields, flag)` | `fields` is an insertion-ordered map from key to **slot**. `flag` is ignoresFallbacks, false unless a rule below sets it. |
| `Subst(path, optional, prefixLength)` | one `${…}` / `${?…}` occurrence. Each occurrence is its own instance with a stable identity. |
| `Concat(pieces)` | a value concatenation that still has at least one `Subst` among its own pieces after consolidation (§1.4). |

A **slot** is the pair (object instance, key) that holds a **stack**. A stack is a non-empty list of values, top first (latest assignment first). A stack never contains a stack: stacks are always flattened. [LB ConfigDelayedMerge.java:37-41]

A slot has a **path**, which is the full key path from the document root. Slots created by parsing (including slots of objects in included files, and slots inside objects that a `Subst` later returns) are **document slots**. Slots created while resolving (§3.5) are **synthesized slots**.

### 1.2 Resolved, ignoresFallbacks

- A value is **resolved** when it contains no `Subst` and no stack with more than one entry. A `Scalar` is always resolved. A `List` or `Obj` is resolved when all of its parts are. A `Subst` or `Concat` is never resolved.
- **ignoresFallbacks** [LB AbstractConfigValue.java:162-168, SimpleConfigObject.java:235-237, ConfigConcatenation.java:71-77, ConfigReference.java:55-58]:
  - a `Scalar`: true;
  - a `List`: true exactly when it is resolved;
  - an `Obj`: its `flag`;
  - a `Subst` or `Concat`: false.
- A **stack ignores fallbacks** exactly when its *last* entry does. [LB ConfigDelayedMerge.java:254-262]

### 1.3 Resolved concatenation (joining pieces)

Pieces are joined left to right. Each step joins the last built value `L` with the next piece `R` [LB ConfigConcatenation.java:91-140]:

1. `L` and `R` are both objects: the result is `fallback([R], [L])` (§2.1). The right piece is on top.
2. `L` and `R` are both lists: the result is their concatenation.
3. `L` is a list or an object, and `R` is an **unquoted** string: `R` is dropped. This includes whitespace and any unquoted text such as `s` (row 051). [LB ConfigConcatenation.java:84-86, 111-113]
4. Otherwise both are rendered to strings and joined. If either is an object or a list, the result is a WrongType error, which aborts the resolve.

An optional piece that resolves to MISSING is omitted. If every piece is omitted, the concatenation is MISSING. [LB ConfigConcatenation.java:181-231]

### 1.4 Consolidation at parse time

The parser applies §1.3 at parse time to every run of adjacent pieces that are neither a `Subst` nor a `Concat`. Whitespace between two pieces is kept only when neither neighbour is an object or a list. If one piece remains, it is the value (no `Concat`). Otherwise the value is `Concat(consolidated pieces)`. [LB ConfigConcatenation.java:142-179]

- So `a = {x=1} {y=${a.x}}` is a single `Obj` (S13a.11).
- `${x} {a=1} {b=2}` has the two pieces `${x}` and `{a=1, b=2}`.

## 2. Building stacks at parse time

### 2.1 `fallback(top, existing)`

Repeated assignment, dotted paths, includes, the merges in §3.2 W5 and the API merge in §8.3 all use one function. `top` is a stack, which is usually a single value. `existing` is a stack, or nothing. Apply the first rule that matches [LB AbstractConfigValue.java:192-284, SimpleConfigObject.java:249-295, ConfigDelayedMerge.java:270-283]:

| rule | condition | result |
|---|---|---|
| F1 | `existing` is nothing | `top` |
| F2 | `top` ignores fallbacks (§1.2: a resolved scalar or list, a flagged object, or a stack whose last entry ignores fallbacks) | `top`. Everything in `existing` is dropped and never evaluated (`a = {x=${nope}}` / `a = 5` gives `{a: 5}`; rv072–rv074). |
| F3 | `top` has more than one entry | `top ++ existing` |
| F4 | (`top = [t]`) `existing` has more than one entry, or its single entry is a `Subst` or `Concat` | `[t] ++ existing` |
| F5 | `existing = [e]`, and `t` and `e` are both `Obj` | `[merge(t, e)]`, as below |
| F6 | `existing = [e]`, `e` is a `Scalar` or `List`, and `t` is an `Obj` | `t` resolved: `[t′]`, where `t′` is `t` with the flag set (`e` is dropped: `c = [${nope}]` / `c = {a = 1}` gives `{c: {a: 1}}`). `t` unresolved: `[t] ++ existing` (rv096). |
| F7 | otherwise (`t` is a `Subst`, a `Concat`, or an unresolved `List`) | `[t] ++ existing` |

`merge(t, e)` is a new `Obj` [LB SimpleConfigObject.java:249-295]:

- A key present in both gets a new slot whose stack is `fallback(t.k, e.k)`.
- A key present in only one keeps **the same slot** (same identity).
- Key order is `e`'s keys first, in `e`'s order, then the keys only `t` has, in `t`'s order [E23 document order].
- The flag is **`e`'s flag**. [LB SimpleConfigObject.java:286]
- At parse time the new slots are document slots. While resolving, they are synthesized (§3.5).

### 2.2 Where the parser calls it

- `k = v`, where `k` already exists in the current object: `stack(k) := fallback([v], stack(k))`. [LB ConfigParser.java:330-347]
- `a.b.c = v` is `a = Obj{b: Obj{c: v}}`, applied with `fallback`. [LB ConfigParser.java:354-360]
- `k += v` is `k = Concat(Subst(fullPath(k), optional), List([v]))`. `fullPath` includes the enclosing object keys and is relative to the file being parsed. [LB ConfigParser.java:287-298]
- `include` (§7.1): for each key `k` of the included root, in the included root's order, `stack(k) := fallback(included.k, stack(k))`. Keys new to the including object are appended at the include's position. [LB ConfigParser.java:221-229]
- The parser holds a field's whole history this way. A value dropped by F2 is gone. There is no other "prior" slot.

### 2.3 Parse errors (unchanged; listed so that no implementation resolves them)

- `+=` anywhere inside a list value, including inside an object that is a list element, is a parse error (row 127/015). [LB ConfigParser.java:262-276]
- An `include` inside a list value whose included object is unresolved is a parse error. [LB ConfigParser.java:211-214]

## 3. Resolving

### 3.1 Entry points

- `resolve()` resolves the root object with `resolveObj(root, rootView, none)` (§3.6). The root view maps nothing.
- Fields are visited in insertion order (first appearance), depth first. Within a value, the order is left to right: list items in order, concatenation pieces in order. [E23 document order; Lightbend uses HashMap order, ConfigParser.java:233 / SimpleConfigObject.java:309]
- Fields whose value is MISSING are omitted from the result.

### 3.2 `valueOf(slot, view, restrict)`: walking a stack

Let `st` be the slot's stack *in the view* (§4.1). Take the memoized result if there is one (§5.3). Otherwise set `merged := nothing`. For each entry `e` of `st`, top first, with `i` its index [LB ConfigDelayedMerge.java:63-180]:

- **W1. Cut.** If `merged` ignores fallbacks (§1.2), stop. [LB :88]
- **W2. Shadow skip.** If all of these hold, skip `e` without evaluating it [LB :140-148, :183-197]:
  - `merged` is a single `Obj`;
  - `e` is a **non-empty** `Obj`;
  - every key of `e` is present in `merged`, and that key's stack ignores fallbacks.
- **W3. Look-back entry.** If `e` is a `Subst` or a `Concat` [LB :101-126]:
  - If `st` has more than one entry and the slot is a document slot, resolve `e` in `view.with(slot ↦ substitute(st, i+1))` (§4.2).
  - Otherwise (a single entry, or a synthesized slot) resolve `e` in `view`. **No view is created.** [E23 rule 0: "a field with a single assignment never creates a view"; LB has no ConfigDelayedMerge for a single value]
- **W4. Forward entry.** Otherwise (an `Obj`, a `List` or a `Scalar`), resolve `e` in `view`. [LB :127-149]
- **W5. Fold.** If the result is MISSING, it contributes nothing, and the next entry shows through. Otherwise set `merged := r` if `merged` is nothing, else `merged := fallback(merged, r)` (§2.1). [LB :158-170]

The result is MISSING if nothing contributed, the single value if `merged` has one entry, and otherwise a **partial stack** (only possible under a restriction, §3.4).

### 3.3 `lookup(subst, view, restrict)`: finding the target of `${p1.….pn}`

`find(path, view)`:

1. Let `slot` be the root's slot for `p1`. If there is none, the path is **not found**.
2. For each `j = 1..n`:
   - If the slot is mapped to EMPTY in the view, apply §4.4.
   - If `j = n`, the path is **found**, and this slot is the leaf.
   - Otherwise compute `v := valueOf(slot, view, restrict = [p(j+1) … pn])`, with the presence rule of §5.3. If `v` is MISSING or not an object (and not a partial stack whose top layers are objects, §3.4), the path is **not found**. Otherwise `slot := child(v, p(j+1))` (§3.4). If there is no such child, the path is **not found**.

Then the leaf slot is resolved with `valueOf(leaf, view, callerRestrict)`. `callerRestrict` is the restriction under which this `Subst` itself is being resolved: none at the top level, or the remaining path when the `Subst` is a stack entry walked under a restriction (049). **The leaf is not resolved fully when the caller is restricted.** [LB ResolveSource.java:44-59 restores the caller's restriction; ConfigReference.java:82-89]

- **Found means present in the raw value** (§5.3). A found leaf that resolves to MISSING gives MISSING for `${?…}`, and an unresolved error for `${…}`. It never falls back to §7.2 (rq29, #123). [LB ResolveSource.java:71-88, 102]
- A list is not an object: a path through a list is not found (rm02).
- Only the ancestors on the path are resolved, and only restricted. Siblings are never touched (S13a.14, rows 038/047/049).

### 3.4 Restricted resolution

`resolve(value, view, restrict)` with `restrict = [k, rest…]`:

- **`Obj`.** The result is the object with only key `k` changed [LB SimpleConfigObject.java:348-385]:
  - If `rest` is non-empty, `k`'s slot is replaced by a synthesized slot holding `valueOf(slot_k, view, rest)`. The key is removed when that is MISSING.
  - If `rest` is empty, the leaf slot is left raw, with the same slot identity.
  - Other keys are raw, keeping the same slots.
- **`List`.** The list is returned unchanged and unresolved. [LB SimpleConfigList.java:151-154]
- **`Subst`.** Resolved as in §3.3, with `callerRestrict = restrict`.
- **`Concat`.** Every piece is resolved **unrestricted**, and the joined result is returned. [LB ConfigConcatenation.java:200-208]
- **Merging partial values (W5)** uses `fallback` exactly as at parse time. A partial (unresolved) object over a scalar or list therefore gives a two-entry **partial stack** (F6/F7). The walk continues per W1, using the last entry's ignoresFallbacks.
- **`child(v, key)`** when `v` is a partial stack: take its entries from the top while they are objects, and stop at the first non-object. Collect `key`'s stack from each of those objects that has `key`. If none has it, the key is not found. Otherwise the child is a synthesized slot whose stack is the `fallback` fold of the collected stacks, top first. [o3co, §11 J1]

### 3.5 Synthesized slots

A slot created while resolving has these properties:
- its path is the full path of the key it represents;
- a view never maps it (§3.2 W3 creates no view for it);
- it is never in a memo key's "mappings at or below" (§5.3).

The slots in question are: a restricted object's replaced child, a `child()` of a partial stack, and a key merged by F5 during resolution.

This is what makes a look-back through a merged leaf (rv079/rv080) a re-entry of the same `Subst` in the same view, which is Lightbend's cycle error. It also bounds the number of views (§10). [LB: the merged ConfigDelayedMerge is new per lookup; its reference is still marked, ResolveContext.java:176-181]

### 3.6 Resolving values (unrestricted)

| value | rule |
|---|---|
| `Scalar` | itself |
| `List` | each item in `view`. A MISSING item is dropped. [LB SimpleConfigList.java:95-125, 146-165] |
| `Obj` | `resolveObj`: each key in order. A key whose slot is EMPTY in the view is **omitted** (§4.3). Otherwise the value is `valueOf(slot, view, none)`, and MISSING values are omitted. The result keeps the object's flag. [LB SimpleConfigObject.java:307-345, 389-405] |
| `Subst` | §5.1 and §6 |
| `Concat` | §5.2, then §1.3 |

## 4. Views (look-back)

### 4.1 Definition

- A **view** is an immutable map from document slot to a **substitute**. A substitute is a stack, or EMPTY.
- Two views are equal when they map the same slots to the same substitute instances.
- The root view is empty.
- Whenever a slot is reached (by `valueOf`, `resolveObj` or `find`) and the view maps it, its stack *is* the substitute.
- This also holds when the slot is reached through an ancestor: the ancestor's value then contains the substitute (zig-hocon `self-referential-examples/054`). [LB ResolveSource.java:206-287 (replace, replaceCurrentParent, replaceWithinCurrentParent); E23 rule 0]

### 4.2 Substitute

`substitute(st, i)` describes the entries below index `i - 1` [LB ConfigDelayedMerge.java:205-223 makeReplacement]:

- No entries: **EMPTY**, which records the entry `st[i-1]` it stands under (§4.4).
- One entry: that entry, **the same instance**.
- Several: the fold `m := [st[i]]; m := fallback(m, [st[j]])` for `j = i+1 …`.

The same `(st, i)` always gives the same substitute instance.

### 4.3 EMPTY inside values

A slot mapped to EMPTY is **absent** from every value built in that view. `resolveObj` omits the key (rv070: `o { a = ${o} }` / `o { a = {x : 1} }` gives `{o: {a: {x: 1}}}`). [LB SimpleConfigObject.java:203-217: replaceChild with null removes the key]

### 4.4 A lookup that reaches EMPTY

When `find` reaches a slot mapped to EMPTY:

- **Own-path exception** [E23-am3; LB treats every such lookup as absent]: if the looking-up `Subst` has the slot's own path and is written inside the bottom entry that the EMPTY stands under, the path is **not found**. §7.2 then continues: original path, then environment. Then `${?…}` gives MISSING, and `${…}` an unresolved error.
  - 003: `foo : ${foo}` / `foo : {a : 1}` is an error.
  - `X = ${X}` / `X = {x: 1}` with `X=seed` gives `{x: 1}`.
  - `a = ${a}"-suffix"` / `a = ${?nope}` with `a=seed` gives `"seed-suffix"`.
  - An include's relativized own-path reference falls back to the original path (rv077).
- **Otherwise** (an alias, or a `Subst` written in another field), the slot is treated as **absent** for the lookup order of §7.2. That is, the original path (step 2) and then the environment (step 3) are tried. [LB ResolveSource.java:90-136: the key is removed from root′]
  - If one of them finds a value, that value is the result.
  - If neither does, it is a **re-entry** (§6.2), not an undefined path. [E23 rule 0: "nothing to look back at … is a cycle"; E23-am4]
  - In this step the re-entry is caught at the same `Subst` (§6.3), so the observable result equals Lightbend's: `${?…}` gives MISSING, and `${…}` an error.
  - The difference appears only in step (b), where E23 rule 1 may break this cycle at an optional further out. Lightbend never can.

A single-entry field never maps to EMPTY (§3.2 W3). Reaching it again is handled by §6.2: `a = ${a}` is a cycle error even with `a` set in the environment, and `a = ${?a}` is MISSING. [E23-am3; LB ConfigReference.java:71-105]

## 5. Memo

### 5.1 Substitutions [E23 rule 2 with E23-am1; LB ResolveContext.java:149-227]

- Keep `memoS[instance] = result` for **unrestricted** resolutions of each `Subst` instance. The key is the instance only, regardless of view.
- Look up `memoS` before anything else in §6.1. A hit skips cycle detection. [LB :151-166]
- **First completed result wins.** When a resolution finishes and `memoS[instance]` was already set (by a resolution of the same instance inside a view, which finished first), the stored result is returned and kept.
- A restricted resolution of a `Subst` is stored only if its result is resolved. [LB :192-213]
- **MISSING is stored only when it is the result of a cycle break by marker re-entry (§6.3).** That is the E23 rule 1 case, interim in this step. MISSING is never stored for:
  - an undefined path;
  - a path whose target resolved to MISSING;
  - a re-entry caused by reaching EMPTY (§4.4).

  This keeps 063 (`{d: []}`) while 061/062 keep `a = {z: 3}`. [E23-am1; LB stores no null at all]

### 5.2 Concatenations [E23-am2]

- Keep `memoC[(instance, M)] = result` for every `Concat` with a substitution among its pieces. Here `M` is the set of the view's mappings for slots **strictly below** the concatenation's own field path.
- The concatenation's own field's mapping, and any mapping elsewhere, do not count. The same "first completed result wins" rule applies.
- MISSING is not stored.
- Lightbend memoizes every value instance by identity, regardless of view. This makes rq37 give `b = "foo"`. [LB ResolveContext.java:149-227 MemoKey(value, restrict)]

### 5.3 Stacks

- Keep `memoV[(slot, stack in the view, M, restrict)] = result` for finished `valueOf`. Here `M` is the set of the view's mappings for slots **at or below** the slot's path. MISSING is not stored.
- Mappings of unrelated slots do not count. This is the shape of Lightbend's memo: siblings keep their identity in the look-back tree, and only the replaced node and its ancestors are new instances. [LB ResolveSource.java:206-265, ResolveContext.java:149-227]
- **A full result satisfies a restricted request** [LB :151-164], **except for presence**: when `find` descends through key `k` and the full result has no `k`, the restricted resolution is performed anyway, and decides whether `k` is found.
  - Example (rq29): `x.y = ${?unset}` already resolved to `{}`, so `${x.y}` is still found (raw), resolves to MISSING, and is an error.
  - Lightbend gives this only in some key orders (rv115). [o3co, §11 J2]
- Never cache by path.

### 5.4 Abandoned work

When §6.3 catches a re-entry at a `Subst` frame, every `memoS`, `memoC` and `memoV` entry written since that frame started is discarded. [LB ConfigReference.java:73-105: the catch keeps the context from before the lookup]

## 6. Cycles

### 6.1 Active substitutions [LB ConfigReference.java:71, 109-113; E23 rule 0 view sensitivity]

Resolving a `Subst` `S` in view `V` (unrestricted or restricted):

1. Check `memoS` (§5.1). On a hit, return it.
2. If `(S, V)` is **active**, raise a **re-entry** (§6.2). [LB ResolveContext.java:176-181]
3. Mark `(S, V)` active, perform `lookup` (§3.3, §7.2), then unmark it.

- Only `Subst` instances are ever marked. Stack walks, objects, lists and concatenations are not marked.
- The marker includes the view. So `S` reached again inside a look-back view is resolved **afresh** there [E23 rule 0]. Lightbend's markers ignore the view, and it errors in document order on 037, 048, 023 and P2-with-`b`-first.

### 6.2 Re-entry

A re-entry happens in two cases:
- (a) §6.1 step 2: an active `(S, V)` is needed again;
- (b) §4.4: a lookup reaches EMPTY outside the own-path exception.

### 6.3 What a re-entry does — **[interim → step (b)]** (Lightbend's innermost-reference rule)

- The re-entry is caught by the **innermost active `Subst` frame**. That is the `Subst` whose lookup is currently running, which led to the re-entry.
  - In case (a), the re-entered `S` itself has not started a frame, so the frame outside it catches.
  - In case (b), the frame of the `Subst` doing the failing lookup catches.

  [LB ConfigReference.java:96-105, ResolveContext.java:176-181]
- If the catching `Subst` is optional, its result is MISSING. It is remembered per §5.1, but only in case (a). Resolution continues normally from there; nothing outside that frame is affected.
- If the catching `Subst` is required, the error is UnresolvedSubstitution ("part of a cycle"), and it **aborts the whole resolve**. No enclosing optional can absorb it. [LB ConfigReference.java:102-105: a RuntimeException]
- Memo entries are discarded per §5.4.
- Step (b) replaces this section with E23 rule 1 (break at the first optional nearest the closing point).

**Pinned interim values** for the #128 rows. They must be identical in all four implementations. The E23 final value is given for step (b).

| row | interim (this step) | E23 final (step b) | Lightbend 1.4.9 as named |
|---|---|---|---|
| 024 `e += ${?c}` ×2 / `c = ${e}` | ERR `${e}` part of a cycle | `e = [[]]`, `c = []` | `{c: [], e: []}` |
| 025 (`c` first) | ERR `${e}` part of a cycle | `e = [[]]`, `c = []` | `{c: [], e: []}` |
| 026 `a += ${?b}` / `b += ${?a}` | `{a: [[]], b: []}` | same | same |
| 027 (lines swapped) | `{b: [[]], a: []}` | same | `{a: [[]], b: []}` |
| 039 `v { x = ${?r} }` / `r = ${v}` | ERR `${v}` part of a cycle | `{v: {}, r: {}}` | `{r: {}, v: {}}` |
| 045 `d = yes${?c}` / `c = ${d}` | ERR `${d}` part of a cycle | `d = c = "yes"` | same as final |
| 046 `e += ${?c}` / `c = ${e}` | ERR `${e}` part of a cycle | `e = c = []` | same as final |
| 057 `b = [${?a}]` / `a = [${?b}]` | `{b: [[]], a: []}` | same | `{a: [[]], b: []}` |
| 063 `d = [${?c}]` / `c = ${?d}` | `{d: []}` | same | `{c: [], d: []}` |
| 064 (`c` first) | `{c: [], d: []}` | same | same |
| 065 `e = x${?c}` / `e = ${e}y${?c}` / `c = ${e}` | ERR `${e}` part of a cycle | `e = "xyx"`, `c = "x"` | `{c: "xy", e: "xy"}` |
| 066 (`c` first) | ERR `${e}` part of a cycle | `e = "xyx"`, `c = "x"` | `{c: "xy", e: "xy"}` |

Rows 022 and 023 (#127) need no cycle break in this model: both give `{b: {c: [[1], 2]}, y: [1]}`.

## 7. Include and environment

### 7.1 Include

- An included file is parsed on its own.
- When it is included inside an object at path `P` (non-empty), every `Subst` in it is **relativized**: its path becomes `P ++ path`, and `prefixLength` increases by `|P|`. Objects, lists, stacks and concatenations are relativized recursively. An include at the root is not relativized. [LB ConfigParser.java:216-219, ConfigReference.java:128-131]
- The included keys are merged per §2.2. `+=` across includes then needs no special case (#134).

### 7.2 Lookup order [LB ResolveSource.java:90-136; E17]

For `Subst(path, optional, prefixLength)`:

1. `find(path)`, the full, relativized path.
2. **Only if not found** and `prefixLength > 0`: `find(path[prefixLength:])`, the original path.
3. If still not found, the environment:
   - when `prefixLength > 0`, first the variable named by the full dotted path, then the one named by the original dotted path [E17: full base first, then bare; Lightbend consults only the bare name];
   - otherwise only the original name.
4. If still not found: `${?…}` gives MISSING, and `${…}` an UnresolvedSubstitution error, which aborts the resolve.

Further rules:
- A path that is found but resolves to MISSING does **not** consult steps 2–3 (rq29, #123, rq01).
- `${X[]}` list expansion keeps its current semantics (E6, S13c, E17 order).
- Step 3 runs only when the resolve options enable the environment (the default).

### 7.3 Optionals in context (unchanged, S13.11–S13.15) [LB]

An undefined (MISSING) optional:
- as a whole stack entry: the entry is skipped (W5), and a field left with nothing is not created;
- as a list item: the item is dropped;
- as a concatenation piece: the piece is omitted (§1.3).

## 8. API

### 8.1 `resolve()`

§3.1.

### 8.2 `allowUnresolved`

- A required `Subst` that would raise an *undefined* UnresolvedSubstitution resolves to itself instead. [LB ConfigReference.java:107-111]
- Cycle errors (§6.3) still abort. [LB :102-105]

### 8.3 `withFallback(receiver, fallback)` [E12; LB SimpleConfigObject.mergedWithObject]

- The roots combine as `fallback(receiver's root stack, fallback's root stack)`. Per key this is the §2.1 rules: the receiver's assignments are on top, and a receiver value that ignores fallbacks drops the fallback's entries (F2).
- **Key order**: the receiver's keys first, in the receiver's order, then the keys that only the fallback has. This applies at every object level merged by this call. It overrides F5's parse-time order. [E23 terms: "Config combined with withFallback puts the receiver's keys first"; §11 J4]
- Look-back crosses layers as in E12 (dr04–dr06).

## 9. Whole-document result

The result tree contains merged, resolved values only. No `Subst`, `Concat` or multi-entry stack remains (except under `allowUnresolved`).

## 10. Termination

- Views only ever map document slots (§3.5). Each mapping is to `substitute(st, i)` of a stack that is itself a document stack or a substitute.
- So the set of distinct views is finite, and so is the set of `(S, V)` markers.
- Every nested resolution of the same `Subst` happens under a strictly larger view or hits a marker. Resolution therefore terminates.
- An implementation must not create views keyed by anything else, such as a path or a freshly built stack.

## 11. Judgement calls ([o3co])

**Principle.** First, the result must not depend on key names or on line order (E23's point). Within that, match Lightbend. Each call below applies it.

| id | question | decision | alternative |
|---|---|---|---|
| J1 | Lookup through a partial stack (§3.4), e.g. `b = 5` / `b = {k: ${c}}` / reader `${b.k}` | found: `b.k = 1` (rv117–rv119) | Lightbend's ConfigDelayedMergeObject peek (ConfigDelayedMergeObject.java:246-310) skips a key whose value does not ignore fallbacks, so the key is "not found", but only when the reader runs before `b` (rv088 finds it). Rejected: it makes a field that resolves to `{k: 1}` have no `k`, depending on key names. |
| J2 | Presence after the parent is memoized (§5.3), rq29 | judged on the raw value: ERR in every key order | Lightbend in document order uses the memoized `{}` and falls back to the original path (rv115: `z = 1`). Rejected: order-dependent, and contradicts #123's "found but removed does not fall back". |
| J3 | Look-back on a merged (synthesized) leaf (§3.5), rv079/rv080 | no view: cycle error, as in Lightbend | A path-keyed view would follow E23's field-history reading and give `a.x = {m: 1, n: 2}`. Rejected: it does not terminate without further rules (§10), and it diverges from Lightbend with no row asking for it. |
| J4 | `withFallback` key order at nested levels (§8.3) | receiver first at every level (an insertion-ordered map) | Lightbend orders object fields by key hash. `deferred-resolution/dr31` (receiver `o { a = [${?o.b}] }`, fallback `o { b = [${?o.a}] }`) gives `o.a = [[]]` in Lightbend and in this document. `dr32` swaps the names: Lightbend still resolves `a` first, so it keeps the nested value in the *fallback's* field, while this document keeps it in the receiver's. Lightbend's answer depends on the names, so it is not order-stable: dr31 is Lightbend-generated, dr32 is `lightbendSkip` with the receiver-first value. |
| J5 | EMPTY reached by an alias (§4.4) | aligned with Lightbend: absent for the original-path and environment lookups; a re-entry only when nothing is found | Treat it as a cycle at once (no environment lookup): differs from Lightbend whenever an environment variable named like the field is set. Treat it as fully absent (an undefined path): Lightbend-exact, but then E23 rule 1 cannot break such a cycle in step (b), which contradicts E23 rule 0 ("it does not count as an absent field"). Checked on the 248-case window corpus: the chosen rule changes no result except the env-set case (now Lightbend's `b = "bar"`). |

## 12. Acceptance

The fixtures that pin this document:

- **`testdata/hocon/resolution-model/` (`rv001`–`rv121`)** has 121 o3co cases. See [fixture-conventions.md "Resolution-model fixtures"](fixture-conventions.md#resolution-model-fixtures).
  - 112 are Lightbend-aligned: 88 json and 24 `.error`.
  - 8 are E23 / o3co. Each has an `.expectation.md` and a model-written expected file:
    - `e23-order`: rv113, rv120;
    - `e23-view-reentry`: rv114, rv116;
    - `o3co-J1`: rv117–rv119;
    - `o3co-J2`: rv115.
  - 1 is `pending-step-b` (rv121).
- **`testdata/hocon/deferred-resolution/` dr31 / dr32** pin J4.
- **zig-hocon conformance rows**, which arrive with the suite vendoring (#132), referenced by ID:

| expectation | rows |
| --- | --- |
| Lightbend's value | `include-substitution/010`, `015`; `self-referential-examples/003`, `031`, `033`, `034`, `037`, `038`, `041`, `047`, `048`, `049`, `051`, `054`, `061`, `062`; `duplicate-keys-and-object-merging/020`; `include-merging/012`, `013`, `015`; `plus-equals-field-separator/015` (parse error), `022`, `023`; `self-referential-substitutions/007` |
| E23 (rule 2) | `self-referential-examples/063` → `{d: []}` |
| interim, pinned in §6.3 (E23 final in the same table) | `plus-equals-field-separator/024`, `025`, `026`, `027`; `self-referential-examples/039`, `045`, `046`, `057`, `064`, `065`, `066` |

Rules for the implementations:
- Every case must give its expected value in all four implementations.
- Every existing test that does not pin a known-wrong value stays green. Tests that pin a known-wrong value are re-pinned, citing the issue (for example ts `s13a12-prefix-self-ref.test.ts:161`, py `test_s13a12` L133 / L229).
- Today's prior / reset-key / fold code is replaced by §2–§3 and must not remain as a second path.

The reference model was checked against Lightbend 1.4.9 on a 248-case window corpus: these fixtures, the zig rows above, `stack-override`, `resolve-memo`, `subst-removed-target`, `include-optional-prior`, and key-renamed variants.
- 225 cases agree with Lightbend.
- The other 23 are classified: `e23-order` 5, `e23-view-reentry` 5, `e23-memo` 2, `interim-128` 7, and Lightbend artefacts (J1, J2) 4.
- None is unclassified.

## 13. E23 amendments

1. **E23-am1** (in E23, xx.hocon#138 commit 33c503e). A rule-1 drop is remembered only when the cycle closed on a substitution that is still being resolved. Reaching EMPTY is not remembered (§5.1, §6.3). Without it, 061/062 (#126) lose `a`.
2. **E23-am2** (proposed). Rule 2 applies to every substitution and to every concatenation containing one (§5.2). Without it, rq37 gives `b = "foofoo"`.
3. **E23-am3** (proposed). The own-path environment exception applies only to the bottom entry of a field with several assignments. A single `a = ${a}` is a cycle (Lightbend errors even with `a` set); a single `a = ${?a}` is missing (§4.4).
4. **E23-am4** (proposed, from J5). When a field with nothing to look back at is reached by any other substitution, it is first looked up as absent (original path, then environment), as Lightbend does. Only if nothing is found is it a cycle under rule 1. This narrows rule 0's "it does not count as an absent field" to that last case (§4.4).
