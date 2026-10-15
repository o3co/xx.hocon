# Build every library adapter, then run the conformance driver.
# Requires: go, cargo, node/npm, python3 (>= 3.11, for o3co/py.hocon), ruby/gem on PATH.
# Override the interpreter used for the Python venvs with PYTHON=python3.11 etc.

PYTHON ?= python3

.PHONY: all build run clean go ts rust py ruby

all: build run

build: go ts rust py ruby

go:
	cd go-adapters && go build -o bin/ ./cmd/...

ts:
	cd ts-adapters && npm install --ignore-scripts

rust:
	cd rust-adapters && cargo build --release

py:
	cd py-adapters && $(PYTHON) -m venv venv && ./venv/bin/pip install -q -r requirements.txt
	# o3co/py.hocon imports as `hocon` (same as the third-party dist) -> own venv
	cd py-adapters && $(PYTHON) -m venv venv-o3co && ./venv-o3co/bin/pip install -q -r requirements-o3co.txt

ruby:
	cd ruby-adapters && GEM_HOME=.gems gem install hocon -v 1.4.0 --no-document

run:
	python3 driver.py

clean:
	rm -rf go-adapters/bin rust-adapters/target ts-adapters/node_modules \
	       ts-adapters/package-lock.json py-adapters/venv py-adapters/venv-o3co ruby-adapters/.gems \
	       ruby-adapters/Gemfile.lock results.json
