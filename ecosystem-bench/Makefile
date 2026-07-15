# Build every library adapter, then run the conformance driver.
# Requires: go, cargo, node/npm, python3, ruby/gem on PATH.

.PHONY: all build run clean go ts rust py ruby

all: build run

build: go ts rust py ruby

go:
	cd go-adapters && go build -o bin/ ./cmd/...

ts:
	cd ts-adapters && npm install

rust:
	cd rust-adapters && cargo build --release

py:
	cd py-adapters && python3 -m venv venv && ./venv/bin/pip install -q -r requirements.txt

ruby:
	cd ruby-adapters && GEM_HOME=.gems gem install hocon -v 1.4.0 --no-document

run:
	python3 driver.py

clean:
	rm -rf go-adapters/bin rust-adapters/target ts-adapters/node_modules \
	       ts-adapters/package-lock.json py-adapters/venv ruby-adapters/.gems \
	       ruby-adapters/Gemfile.lock results.json
