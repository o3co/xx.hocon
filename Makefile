.PHONY: generate clean

generate:
	cd generate && ./gradlew run

clean:
	rm -rf expected/hocon/*.json
