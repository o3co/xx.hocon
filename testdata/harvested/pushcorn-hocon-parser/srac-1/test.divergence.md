# srac-1/test.conf — Lightbend-limitation error, not spec-normative

**Observed (reference impl, typesafe-config 1.4.6):** `ConfigException$Parse` —
"Due to current limitations of the config parser, += does not work nested
inside a list" (`closures += { … }` inside an `exports += { … }` element).

**Spec analysis:** same construct family as
`mikai233-hocon-rs/add_assign.conf` — see that fixture's `.divergence.md`.
`+=` is pure sugar (HOCON.md §"The `+=` field separator") with no nesting
restriction. This fixture is itself the upstream regression case for
lightbend/config#160 (cited in its first line).

**Consumer guidance:** a spec-conforming implementation may legitimately
succeed here; do not treat this as a spec-normative must-error case. The four
o3co implementations parse nested `+=` successfully (see
docs/harvested-corpus.md, phase 1 dry run).
