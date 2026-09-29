#!/usr/bin/env sh
set -eu

OUT="${TMPDIR:-/tmp}/property-agent-error-tracking-test-classes"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java src/test/java -name '*.java' -print)
java -cp "$OUT" dev.infrai.property.PropertyAgentLoopServiceTest
