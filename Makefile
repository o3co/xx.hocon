.PHONY: generate generate-harvested clean differential differential-corpus differential-adapters differential-fuzz

generate:
	cd generate && ./gradlew run

generate-harvested:
	cd generate && ./gradlew generateHarvested

clean:
	find expected/hocon -name '*.json' -delete

# ---- Cross-impl differential harness (the in-house "cgordon") ----
# Runs the Lightbend oracle (typesafe-config) against the go/rs/ts/py adapters
# over the seed corpus and reports divergences from the reference implementation.
#
# Adapters are installed from each language's package registry (PyPI / npm /
# crates.io / Go module proxy), pinned to the sibling release, so the harness
# reproduces from a clean xx.hocon checkout with NO sibling repos present. The
# vendored adapter sources (which import the registry-installed library) live
# under differential/adapters/; `make differential-adapters` builds them.
# Override a path on the command line, e.g.
#   make differential PY_SCRIPT=/abs/path/to/hocon_json.py
# Requires: JDK 21 (gradle oracle) + the toolchains for whichever adapters you
# build (python3, node/npm, cargo, go).
ADAPTERS        := differential/adapters
# Sibling release the adapters are pinned to. Bump together on the next release;
# the per-language manifests (requirements.txt / package.json / Cargo.toml) pin
# the same version and must be kept in sync with this.
GO_IMPL_VERSION ?= v1.8.0
# The py adapter needs Python >= 3.11 (hocon-parser's requires-python). Override
# if `python3` on your PATH is older, e.g. `make differential-adapters PYTHON=python3.11`.
PYTHON          ?= python3

GO_ADAPTER ?= $(ADAPTERS)/go/bin/hocon-json
RS_ADAPTER ?= $(ADAPTERS)/rs/target/release/hocon-json
TS_SCRIPT  ?= $(ADAPTERS)/ts/hocon-json.mjs
PY_PYTHON  ?= $(ADAPTERS)/py/.venv/bin/python
PY_SCRIPT  ?= $(ADAPTERS)/py/hocon_json.py

differential-adapters:
	# py — hocon-parser from PyPI into a local venv
	$(PYTHON) -m venv $(ADAPTERS)/py/.venv
	$(ADAPTERS)/py/.venv/bin/pip install --quiet --upgrade pip
	$(ADAPTERS)/py/.venv/bin/pip install --quiet -r $(ADAPTERS)/py/requirements.txt
	# ts — @o3co/ts.hocon from npm
	cd $(ADAPTERS)/ts && npm install
	# rs — hocon-parser from crates.io (the vendored bin depends on it)
	cd $(ADAPTERS)/rs && cargo build --release
	# go — cmd/hocon-json from the module proxy
	GOBIN=$(abspath $(ADAPTERS)/go/bin) go install github.com/o3co/go.hocon/cmd/hocon-json@$(GO_IMPL_VERSION)

differential-corpus:
	cd generate && ./gradlew differentialCorpus

differential:
	cd generate && ./gradlew differential \
	  -Dadapter.go="$(abspath $(GO_ADAPTER))" \
	  -Dadapter.rs="$(abspath $(RS_ADAPTER))" \
	  -Dadapter.ts="node $(abspath $(TS_SCRIPT))" \
	  -Dadapter.py="$(abspath $(PY_PYTHON)) $(abspath $(PY_SCRIPT))"

# Grammar fuzz: generate FUZZ_COUNT seeded docs (reproducible from FUZZ_SEED),
# diff each against the oracle, shrink divergences to minimal repros under
# differential/fuzz-findings/ + differential/report/fuzz-summary.md.
FUZZ_SEED  ?= 1
FUZZ_COUNT ?= 200

differential-fuzz:
	cd generate && ./gradlew differentialFuzz \
	  -Dadapter.go="$(abspath $(GO_ADAPTER))" \
	  -Dadapter.rs="$(abspath $(RS_ADAPTER))" \
	  -Dadapter.ts="node $(abspath $(TS_SCRIPT))" \
	  -Dadapter.py="$(abspath $(PY_PYTHON)) $(abspath $(PY_SCRIPT))" \
	  -Dfuzz.seed=$(FUZZ_SEED) -Dfuzz.count=$(FUZZ_COUNT)
