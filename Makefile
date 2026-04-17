.PHONY: generate clean

generate:
	cd generate && ./gradlew run

clean:
	find expected/hocon -name '*.json' -delete
