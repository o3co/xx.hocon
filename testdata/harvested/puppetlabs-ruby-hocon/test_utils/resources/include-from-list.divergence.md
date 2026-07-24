# include-from-list.conf — Lightbend-limitation error, not spec-normative

**Observed (reference impl, typesafe-config 1.4.6):** `ConfigException$Parse` —
include nested inside a list value ("Due to current limitations of the config
parser…") on `a = [ { include "test01.conf" } ]`.

**Spec analysis:** same construct as `mockersf-hocon-rs/test03.conf` — see
that fixture's `.divergence.md`. HOCON.md §Include syntax permits an include
statement in place of an object field; an object literal inside an array is
still an object. This file is an upstream-modified variant of the Lightbend
suite's `include-from-list.conf` (the mirror copy in `testdata/hocon/` differs
and remains authoritative for the spec corpus).

**Consumer guidance:** a spec-conforming implementation may legitimately
succeed here; do not treat this as a spec-normative must-error case.
