pom.xml:
	clj -Spom

.PHONY: sources
sources: pom.xml
	mvn dependency:sources

.PHONY: clean
clean:
	rm -rf target/

.PHONY: test
test:
	clj -X:test

.PHONY: run
run:
	clj -M -m me.untethr.nostr.app

.PHONY: uberjar
uberjar:
	clj -M:uberdeps

.PHONY: run-uberjar
run-uberjar:
	java -cp target/me.untethr.nostr-relay.jar \
		clojure.main -m me.untethr.nostr.app

.PHONY: deploy-archive
deploy-archive: clean uberjar
	tar -czvf target/me.untethr.nostr-relay.tar.gz conf/* -C target me.untethr.nostr-relay.jar
