# Harvested ecosystem corpus

Fixtures collected from the test suites of other HOCON implementations, so the
o3co parsers can verify they handle the configs the wider ecosystem exercises —
in particular upstream regression fixtures born from real-world bug reports.

This corpus is a separate layer from the spec corpus (`testdata/hocon/`): it
does not feed `docs/spec-checklist.md` S-rows or the compliance-matrix
denominator.

## Layout

```
testdata/harvested/<source>/         verbatim fixture copies + PROVENANCE.yaml
expected/harvested/<source>/         generated expected outputs (do not edit)
```

`<source>` is `<github-owner>-<repo>` (e.g. `mikai233-hocon-rs`).

## Rules

- **Verbatim copies.** Files are copied byte-for-byte from the upstream repo at
  the commit pinned in `PROVENANCE.yaml`. Never edit a harvested fixture; if a
  fixture needs adaptation, it does not belong in this corpus.
- **Expected outputs come from the reference implementation only.** Upstream
  projects keep their own expectations; those are deliberately not vendored.
  `make generate-harvested` runs every fixture root through Lightbend
  typesafe-config (`parseFile().resolve()`, hermetic: system environment
  disabled) and auto-classifies:
  - resolves cleanly → `expected/harvested/<source>/<name>-expected.json`
  - throws → `expected/harvested/<source>/<name>.error` sidecar
    (`Exception class:` / `Message:`, same format as the spec corpus)
- **Roots vs companions.** Every `*.conf` in a source directory is a fixture
  root unless listed in that directory's `companions.txt` (include targets,
  cycle-chain members). Non-`.conf` files are never roots.
- **Spec cross-check.** Reference-implementation errors are not automatically
  spec rules. Every `.error` classification is checked against HOCON.md; when
  the cause is a self-declared Lightbend parser limitation rather than a spec
  rule, the fixture carries a sibling `<name>.divergence.md` and consumers
  must not treat it as a spec-normative must-error case (see
  `docs/harvested-corpus.md`).
- **Determinism gate.** Fixtures that reach the network (URL includes) are
  excluded at harvest time and recorded under `excluded:` in PROVENANCE.yaml,
  as are exact content duplicates of existing spec-corpus fixtures.
  Determinism is asserted for POSIX runners: some fixtures reference
  developer-machine Windows paths (`F:/...`) and rely on those paths never
  existing; a Windows runner with such a drive could flip their outcome.
- **Licensing.** Each PROVENANCE.yaml records the upstream license, and each
  source directory vendors the upstream license text as `LICENSE.upstream`.
  Only fixtures from permissively licensed projects (Apache-2.0 / MIT / ISC /
  BSD) are harvested.

## Adding a source

1. Clone the upstream repo, note `git rev-parse HEAD`.
2. Copy the fixture files verbatim into `testdata/harvested/<source>/`.
3. Write `PROVENANCE.yaml` (source block, exclusions with reasons, notes) and
   `companions.txt` if any fixture is only an include target.
4. Run `make generate-harvested` and commit fixtures + expected together.
5. Record results in `docs/harvested-corpus.md`.
