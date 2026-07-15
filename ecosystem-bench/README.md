# ecosystem-bench

Conformance harness for JVM-free HOCON libraries. Each library gets a small
**adapter CLI** (read a `.conf` path → print resolved JSON to stdout, non-zero
exit + stderr on error); a shared driver runs every adapter over the fixtures in
this repository and compares the output to each fixture's Lightbend-generated
`-expected.json`.

Results are recorded in [`../docs/ecosystem-conformance.md`](../docs/ecosystem-conformance.md).

## Layout

```
driver.py            shared runner + scoring (repo-relative; XX_ROOT overrides)
go-adapters/         o3co/go.hocon, gurkankaymak/hocon, go-akka/configuration
ts-adapters/         @o3co/ts.hocon, hocon-parser, @pushcorn/hocon-parser
rust-adapters/       hocon-parser (o3co), hocon (mockersf), hocon-rs (mikai233)
py-adapters/         pyhocon, hocon
ruby-adapters/       hocon (puppetlabs)
```

Build artifacts (`bin/`, `target/`, `node_modules/`, `venv/`, `.gems/`) are
git-ignored and produced by `make build`.

## Run

```bash
make build   # build/install all adapters (needs go, cargo, node, python3, ruby)
make run     # run the driver, print per-library results, write results.json
```

Or a single language, e.g. only Go + the driver:

```bash
make go
python3 driver.py
```

## Adapter contract

An adapter is any executable that:

1. takes one argument, the path to a `.conf` file;
2. parses and fully resolves it, then prints the resulting config as JSON to
   stdout;
3. exits non-zero with a message on stderr if parsing/resolution fails.

Adding a library is one new adapter plus one row in the `LIBS` registry in
`driver.py`. See `docs/ecosystem-conformance.md` for the scoring method
(normalization rules, the two reported corpora, and the fixture groups held
separate).
