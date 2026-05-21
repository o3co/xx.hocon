# include-package/ — E11 conformance fixtures

This fixture group covers [E11](../../../../docs/extra-spec-conventions.md#e11)
(`include package(...)` qualifier — service-locator pattern for non-JVM HOCON).

## Key difference from other fixture groups

Lightbend typesafe-config has no concept of `package(...)`. **These fixtures are
never run through the Java generator.** No `expected/hocon/include-package/`
directory exists and none should be created.

Per-impl conformance tests do the following instead:

1. **Register test-package content** into the impl's registry (or skip if not
   applicable — see ipk03).
2. **Parse the `.conf` fixture**.
3. **Assert the expected outcome** (success with specific merged output, or a
   specific error category — see table below).

Per-impl override lists (`IMPL_OVERRIDE_ERRORS` in ts.hocon, `KNOWN_LIGHTBEND_QUIRKS`
in rs.hocon, `implErrors` in go.hocon) cover ipk03, which is n/a for ts.hocon.

---

## Fixture index

| ID    | File                            | Type              | Expected outcome                                           | Spec decision |
|-------|---------------------------------|-------------------|------------------------------------------------------------|---------------|
| ipk01 | `ipk01-basic.conf`              | success           | merged `{"host":"example.com","port":8080,"app.name":"lib"}` | —             |
| ipk02 | `ipk02-one-arg-rejected.conf`   | parse error       | one-arg `package("…")` rejected                           | decision 2    |
| ipk03 | `ipk03-collision.conf`          | registration error | same `(id, file)`, different content → error at registration | decision 3  |
| ipk04 | `ipk04-lookup-miss.conf`        | parse error       | registry miss with empty registry                          | decision 4    |
| ipk05 | `ipk05-required-miss.conf`      | parse error       | `required(package(...))` + miss                            | decision 7    |
| ipk06 | `ipk06-byte-exact-id-case.conf` | parse error       | identifier case mismatch → lookup miss                     | decision 5    |
| ipk07 | `ipk07-byte-exact-file-case.conf` | parse error     | file case mismatch → lookup miss                           | decision 5    |
| ipk08 | `ipk08-empty-content.conf`      | success           | empty registered content → contributes `{}`, merge succeeds | decision 4 note |
| ipk09 | `ipk09-file-empty.conf`         | parse error       | empty string file argument                                 | decision 6    |
| ipk10 | `ipk10-file-absolute.conf`      | parse error       | absolute path `/etc/passwd`                                | decision 6    |
| ipk11 | `ipk11-file-traversal.conf`     | parse error       | `..` traversal `../escape.conf`                            | decision 6    |
| ipk12 | `ipk12-file-backslash.conf`     | parse error       | backslash `x\y.conf`                                       | decision 6    |
| ipk13 | `ipk13-cycle-self.conf`         | cycle error       | self-include via `("foo", "self.conf")`                    | decision 8    |
| ipk14 | `ipk14-cycle-mutual.conf`       | cycle error       | mutual include `("foo", "a.conf")` ↔ `("foo", "b.conf")`  | decision 8    |

### Per-impl override: ipk03

ipk03 exercises the explicit-registry collision policy (decision 3). ts.hocon has
no explicit registry (it delegates to Node `require.resolve`), so ipk03 is
**not applicable** for ts.hocon. Per-impl conformance tests for ts.hocon MUST
skip ipk03 (or mark it as expected-n/a) via the per-impl override list.

---

## `_packages/` — test-package content layout

The `_packages/` directory contains HOCON source files that per-impl tests
register into the impl's registry before parsing a fixture. The leading `_`
signals that files here are NOT standalone fixtures — per-impl test runners
that glob the directory for `.conf` files MUST skip `_packages/`.

### Directory encoding convention

Since `identifier` strings often contain `/` (Go-module-path style like
`github.com/example/lib`), directory names encode `/` as `_` for filesystem
compatibility. Per-impl test code reads these files and registers them using
the exact `(identifier, file)` pair shown in each file's header comment —
the filesystem path is NOT the registry key; the comment IS authoritative.

### Layout

```
_packages/
├── github.com_example_lib/
│   └── reference.conf              # ("github.com/example/lib", "reference.conf") — ipk01
├── _ipk03-pkg-A.conf               # ("github.com/example/lib", "reference.conf") variant A — ipk03 first registration
├── _ipk03-pkg-B.conf               # ("github.com/example/lib", "reference.conf") variant B — ipk03 second registration (must fail)
├── github.com_example_lib_byte/
│   ├── Foo_Bar_x.conf              # ("Foo/Bar", "x.conf") — ipk06 (uppercase identifier)
│   └── github.com_example_lib_Reference.conf  # ("github.com/example/lib", "Reference.conf") — ipk07 (uppercase file)
├── github.com_example_lib_empty/
│   └── empty.conf                  # ("github.com/example/lib", "empty.conf") empty string — ipk08
└── _cycle/
    ├── ipk13-self.conf             # ("foo", "self.conf") → includes itself — ipk13
    ├── ipk14-a.conf                # ("foo", "a.conf") → includes ("foo", "b.conf") — ipk14
    └── ipk14-b.conf                # ("foo", "b.conf") → includes ("foo", "a.conf") — ipk14
```

### Per-fixture registry setup

| Fixture | Registrations required before parse |
|---------|--------------------------------------|
| ipk01   | `("github.com/example/lib", "reference.conf")` → contents of `_packages/github.com_example_lib/reference.conf` |
| ipk02   | none |
| ipk03   | Register `_ipk03-pkg-A.conf` as `("github.com/example/lib", "reference.conf")`, then attempt to register `_ipk03-pkg-B.conf` under same key — expect registration error (go.hocon / rs.hocon only) |
| ipk04   | none (empty registry) |
| ipk05   | none (empty registry) |
| ipk06   | `("Foo/Bar", "x.conf")` → contents of `_packages/github.com_example_lib_byte/Foo_Bar_x.conf` |
| ipk07   | `("github.com/example/lib", "Reference.conf")` → contents of `_packages/github.com_example_lib_byte/github.com_example_lib_Reference.conf` |
| ipk08   | `("github.com/example/lib", "empty.conf")` → empty string (contents of `_packages/github.com_example_lib_empty/empty.conf`) |
| ipk09   | none |
| ipk10   | none |
| ipk11   | none |
| ipk12   | none |
| ipk13   | `("foo", "self.conf")` → contents of `_packages/_cycle/ipk13-self.conf` |
| ipk14   | `("foo", "a.conf")` → contents of `_packages/_cycle/ipk14-a.conf`; `("foo", "b.conf")` → contents of `_packages/_cycle/ipk14-b.conf` |

---

## Expected outputs for success fixtures

### ipk01-basic

```json
{
  "host": "example.com",
  "port": 8080,
  "app": {
    "name": "lib"
  }
}
```

### ipk08-empty-content

```json
{
  "app": "host"
}
```

---

## Error categories (for per-impl assertion)

- **parse error**: any `HoconError` / equivalent raised during `parse()` (covers
  ipk02, ipk04, ipk05, ipk06, ipk07, ipk09, ipk10, ipk11, ipk12).
- **registration error**: raised during registry population, before `parse()` is
  called (ipk03 — go.hocon / rs.hocon only).
- **cycle error**: a subtype of parse error indicating an include cycle (ipk13,
  ipk14). Implementations MAY surface this as a distinct error type or as a
  generic parse error with a message mentioning "cycle" or "circular include".

---

## Cycle-detection key

Per decision 8, the cycle-detection key for `package(...)` includes the qualifier
kind plus the byte-exact pair:

```
("package", <identifier>, <file>)
```

This distinguishes `include package("foo", "x.conf")` from `include file("x.conf")`
even if their content happens to be identical. Implementations that track cycle
detection via a set of "currently-being-parsed" keys MUST include the qualifier
kind in the key.
