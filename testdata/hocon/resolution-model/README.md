# resolution-model/ — o3co cases for the resolution model

Cases written while designing [`docs/resolution-model.md`](../../../docs/resolution-model.md) (the #67 / #125–#128
resolver window). They pin the stack, look-back, restricted-lookup, memo and cycle rules of that document. The
zig-hocon conformance rows the document cites (`self-referential-examples/037`, …) are not copied here; they arrive
with the suite vendoring (#132).

Each case is one of three kinds (see [`docs/fixture-conventions.md`](../../../docs/fixture-conventions.md#resolution-model-fixtures)):

- **lightbend** — listed in `GenerateExpected.java`; the expected file is Lightbend 1.4.9's output (`make generate`).
- **E23 / o3co** — has a `<name>.expectation.md` naming the E23 rule or the resolution-model §11 decision (J1–J5);
  the expected file is written by `make generate-model` (the reference model, `generate/reference-model/`).
- **pending-step-b** — `-pending-step-b` in the name, and an `.expectation.md` with `kind: pending-step-b`; the
  expected file is the E23 final value, which needs E23 rule 1 (window step (b)). Until step (b) lands, the
  implementations produce the interim value recorded in the sidecar and mark the case pending.

Include targets are `<name>-inner.conf` and are not cases. The group is in `HERMETIC_NO_ENV_GROUPS`: no case
reads the environment. Cases that need an environment variable set (own-path env fallback, J5) are per-impl tests.

| case | kind | source |
| --- | --- | --- |
| `rv001-br-a-concat-self` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv002-br-a-opt-self` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv003-br-bar-nested` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv004-br-bar-x` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv005-br-db-url` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv006-br-early-o` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv007-br-outer-use` | lightbend (json) | window step (a) probe (ts/py review) |
| `rv008-br-py-A` | lightbend (json) | window step (a) probe: py's A |
| `rv009-br-py-B` | lightbend (json) | window step (a) probe: py's B |
| `rv010-br-server-concat-self` | lightbend (json) | brief (ts/py step a) |
| `rv011-br-top-seen` | lightbend (`.error`) | window step (a) probe (ts/py review) |
| `rv012-e23-p2-bfirst` | lightbend (json) | E23 table: P2 with b first ("either line order") |
| `rv013-e23-p2` | lightbend (json) | E23 table: #67 P2 |
| `rv014-e23-start` | lightbend (json) | E23 terms example |
| `rv015-e23-x1-bfirst` | lightbend (json) | E23 table extra row 1, b first |
| `rv016-e23-x1` | lightbend (json) | E23 table extra row 1 |
| `rv017-e23-x2` | lightbend (json) | E23 table extra row 2 |
| `rv018-e23-x3` | lightbend (json) | E23 table extra row 3 (HOCON.md L870) |
| `rv019-e23-x4` | lightbend (json) | E23 table extra row 4 |
| `rv020-fo-e23-x3-swap` | lightbend (json) | e23-x3-swap renamed (b->a, a->b) |
| `rv021-fo-rv-restricted-then-full` | lightbend (`.error`) | rv-restricted-then-full renamed (p->b, z->c) |
| `rv022-pye-01` | lightbend (json) | window probe `edge.txt` line 1 |
| `rv023-pye-03` | lightbend (json) | window probe `edge.txt` line 3 |
| `rv024-pye-04` | lightbend (json) | window probe `edge.txt` line 4 |
| `rv025-pye-05` | lightbend (json) | window probe `edge.txt` line 5 |
| `rv026-pye-06` | lightbend (json) | window probe `edge.txt` line 6 |
| `rv027-pye-07` | lightbend (json) | window probe `edge.txt` line 7 |
| `rv028-pye-08` | lightbend (json) | window probe `edge.txt` line 8 |
| `rv029-pye-09` | lightbend (json) | window probe `edge.txt` line 9 |
| `rv030-pye-10` | lightbend (json) | window probe `edge.txt` line 10 |
| `rv031-pye-11` | lightbend (json) | window probe `edge.txt` line 11 |
| `rv032-pye-12` | lightbend (json) | window probe `edge.txt` line 12 |
| `rv033-pye-13` | lightbend (`.error`) | window probe `edge.txt` line 13 |
| `rv034-pye-14` | lightbend (`.error`) | window probe `edge.txt` line 14 |
| `rv035-pye-15` | lightbend (json) | window probe `edge.txt` line 15 |
| `rv036-pye-16` | lightbend (json) | window probe `edge.txt` line 16 |
| `rv037-pye-17` | lightbend (json) | window probe `edge.txt` line 17 |
| `rv038-pye-18` | lightbend (json) | window probe `edge.txt` line 18 |
| `rv039-pye-20` | lightbend (`.error`) | window probe `edge.txt` line 20 |
| `rv040-pye-22` | lightbend (json) | window probe `edge.txt` line 22 |
| `rv041-pye-23` | lightbend (`.error`) | window probe `edge.txt` line 23 |
| `rv042-pyp-p01` | lightbend (json) | window probe `p01.conf` |
| `rv043-pyp-p02` | lightbend (json) | window probe `p02.conf` |
| `rv044-pyp-p03` | lightbend (json) | window probe `p03.conf` |
| `rv045-pyp-p04` | lightbend (json) | window probe `p04.conf` |
| `rv046-pyp-p05` | lightbend (json) | window probe `p05.conf` |
| `rv047-pyp-p06` | lightbend (json) | window probe `p06.conf` |
| `rv048-pyp-p07` | lightbend (json) | window probe `p07.conf` |
| `rv049-pyp-p08` | lightbend (json) | window probe `p08.conf` |
| `rv050-pyp-p09` | lightbend (json) | window probe `p09.conf` |
| `rv051-pyp-p10` | lightbend (`.error`) | window probe `p10.conf` |
| `rv052-pyp-p11` | lightbend (json) | window probe `p11.conf` |
| `rv053-pyp-p12` | lightbend (json) | window probe `p12.conf` |
| `rv054-pyp-p13` | lightbend (json) | window probe `p13.conf` |
| `rv055-pyp-p14` | lightbend (`.error`) | window probe `p14.conf` |
| `rv056-pyp-p15` | lightbend (json) | window probe `p15.conf` |
| `rv057-pyp-p16` | lightbend (json) | window probe `p16.conf` |
| `rv058-pyp-p17` | lightbend (json) | window probe `p17.conf` |
| `rv059-pyp-p18` | lightbend (json) | window probe `p18.conf` |
| `rv060-pyp-p19` | lightbend (json) | window probe `p19.conf` |
| `rv061-pyp-p20` | lightbend (`.error`) | window probe `p20.conf` |
| `rv062-pyp-p21` | lightbend (`.error`) | window probe `p21.conf` |
| `rv063-pyp-p22` | lightbend (json) | window probe `p22.conf` |
| `rv064-pyp-p23` | lightbend (json) | window probe `p23.conf` |
| `rv065-pyp-p24` | lightbend (json) | window probe `p24.conf` |
| `rv066-pyp-p25` | lightbend (`.error`) | window probe `p25.conf` |
| `rv067-pyp-p30` | lightbend (`.error`) | window probe `p30.conf` |
| `rv068-rv-a-concat-list` | lightbend (json) | lightbend-model 3.2 |
| `rv069-rv-dm-top-ignores` | lightbend (json) | top is a stack whose last entry ignores fallbacks |
| `rv070-rv-empty-ancestor` | lightbend (json) | EMPTY reached through an ancestor |
| `rv071-rv-empty-obj-entry` | lightbend (json) | empty object entry and allKeysShadowed |
| `rv072-rv-flag-include` | lightbend (json) | flagged object arriving through include |
| `rv073-rv-flag-top-parse` | lightbend (json) | flagged object on top |
| `rv074-rv-flag-top-walk` | lightbend (json) | flagged object from a Subst merged in the walk |
| `rv075-rv-fold-remainder` | lightbend (json) | unfolded vs folded look-back remainder |
| `rv076-rv-inc-bottom-subst` | lightbend (json) | relativized subst at stack bottom; original path exists |
| `rv077-rv-inc-own-path-undefined` | lightbend (json) | own-path self-ref in an included file with nothing below; original path exists |
| `rv078-rv-lookback-through-list-anc` | lightbend (json) | 054 + external reader |
| `rv079-rv-merged-leaf-ext` | lightbend (`.error`) | same, read from outside |
| `rv080-rv-merged-leaf` | lightbend (`.error`) | look-back on a leaf merged from two layers |
| `rv081-rv-nonobj-middle` | lightbend (`.error`) | E16 open note: non-object middle |
| `rv082-rv-own-path-concat-single` | lightbend (json) | own-path optional inside a single-entry concat |
| `rv083-rv-partial-shadow` | lightbend (`.error`) | E16 open note: partly shadowed |
| `rv084-rv-pluseq-concat-elem` | lightbend (json) | a concatenation as a list element |
| `rv085-rv-pluseq-include` | lightbend (json) | += across include |
| `rv086-rv-pluseq-nested-include` | lightbend (json) | += in an included file under a key (relativized optional self-ref) |
| `rv087-rv-restrict-list-opt` | lightbend (json) | optional inside a list entry under restriction |
| `rv088-rv-restricted-dm-late` | lightbend (json) | same, b resolved before the reader |
| `rv089-rv-self-bottom-noenv` | lightbend (`.error`) | own-path bottom entry, no env: undefined |
| `rv090-rv-self-single` | lightbend (`.error`) | E23 amendment 3: single own-path self-reference, no env |
| `rv091-rv-shadow-merge` | lightbend (json) | E16 open note: matching key still merges |
| `rv092-rv-sibling-inprog` | lightbend (json) | restricted lookup into a field in progress |
| `rv093-rv-single-alias-cycle` | lightbend (`.error`) | two single-entry fields |
| `rv094-rv-single-alias-opt` | lightbend (`.error`) | two single-entry fields, optional link |
| `rv095-rv-xd1` | lightbend (`.error`) | lightbend-model (d) xd1 |
| `rv096-rv-xd2` | lightbend (`.error`) | lightbend-model (d) xd2 |
| `rv097-rv-y1` | lightbend (`.error`) | lightbend-model y1 |
| `rv098-rv-y10` | lightbend (json) | lightbend-model y10 |
| `rv099-tsc-c01` | lightbend (`.error`) | window probe `c01.conf` |
| `rv100-tsc-c02` | lightbend (json) | window probe `c02.conf` |
| `rv101-tsc-c03` | lightbend (json) | window probe `c03.conf` |
| `rv102-tsc-c04` | lightbend (json) | window probe `c04.conf` |
| `rv103-tsc-c05` | lightbend (json) | window probe `c05.conf` |
| `rv104-tsc-c06` | lightbend (json) | window probe `c06.conf` |
| `rv105-tsc-c07` | lightbend (json) | window probe `c07.conf` |
| `rv106-tsc-c08` | lightbend (json) | window probe `c08.conf` |
| `rv107-tsc-c09` | lightbend (json) | window probe `c09.conf` |
| `rv108-tsk-k1` | lightbend (json) | window probe `k1.conf` |
| `rv109-tsk-k2` | lightbend (json) | window probe `k2.conf` |
| `rv110-tsk-k3` | lightbend (json) | window probe `k3.conf` |
| `rv111-tsk-k4` | lightbend (json) | window probe `k4.conf` |
| `rv112-tsk-k5` | lightbend (json) | window probe `k5.conf` |
| `rv113-e23-x3-swap` | e23-order | E23 table extra row 3, first two lines swapped |
| `rv114-fo-e23-p2-bfirst` | e23-view-reentry | P2 b-first renamed (b->a, a->b) |
| `rv115-fo-rq29` | o3co-J2 | rq29 with inner a renamed to z (x resolved before z) |
| `rv116-pye-19` | e23-view-reentry | window probe `edge.txt` line 19 |
| `rv117-rv-restricted-dm` | o3co-J1 | lookup into an unresolved object stacked over a scalar |
| `rv118-rv-restricted-dm2` | o3co-J1 | same, two keys |
| `rv119-rv-restricted-dm3` | o3co-J1 | same, deeper path |
| `rv120-rv-restricted-then-full` | e23-order | Subst first resolved under restriction, then fully |
| `rv121-e23-x5-pending-step-b` | pending-step-b | E23 "why we diverge" example |
| `rv122-alias-presence-j2` | o3co-J2 | presence through an already-resolved alias (found in review) |
| `rv123-alias-presence-renamed` | lightbend (`.error`) | rv122 with the keys renamed |
| `rv124-alias-presence-other-view` | lightbend (json) | presence of a memoized alias is checked in the view that produced it (found in review) |
