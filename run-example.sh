#!/usr/bin/env sh
set -eu

OUT="${TMPDIR:-/tmp}/property-agent-error-tracking-classes"
mkdir -p "$OUT"
javac -d "$OUT" $(find src/main/java -name '*.java' -print)
java -cp "$OUT" dev.infrai.property.PropertyAgentExample
