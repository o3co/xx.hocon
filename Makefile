.PHONY: generate clean differential differential-corpus differential-adapters

generate:
	cd generate && ./gradlew run

clean:
	find expected/hocon -name '*.json' -delete

# ---- Cross-impl differential harness (the in-house "cgordon") ----
# Runs the Lightbend oracle (typesafe-config) against the go/rs/ts adapters over
# the seed corpus and reports divergences from the reference implementation.
# Adapter paths assume the sibling canonical repos with their adapters built
# (run `make differential-adapters` first). Override on the command line, e.g.
#   make differential TS_SCRIPT=/abs/path/to/ts.hocon/tools/hocon-json.ts
# Requires: a JDK 21 (for the gradle oracle) and Node >= 22 (the ts adapter is
# a plain .mjs that imports ts.hocon's built dist — run `pnpm build` there).
GO_ADAPTER ?= ../go.hocon/bin/hocon-json
RS_ADAPTER ?= ../rs.hocon/target/debug/examples/hocon-json
TS_SCRIPT  ?= ../ts.hocon/tools/hocon-json.mjs

differential-adapters:
	cd ../go.hocon && go build -o bin/hocon-json ./cmd/hocon-json
	cd ../rs.hocon && cargo build --example hocon-json
	cd ../ts.hocon && pnpm install && pnpm build

differential-corpus:
	cd generate && ./gradlew differentialCorpus

differential:
	cd generate && ./gradlew differential \
	  -Dadapter.go="$(abspath $(GO_ADAPTER))" \
	  -Dadapter.rs="$(abspath $(RS_ADAPTER))" \
	  -Dadapter.ts="node $(abspath $(TS_SCRIPT))"
