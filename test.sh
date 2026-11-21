#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
if command -v javac >/dev/null 2>&1; then
  javac --release 17 -encoding UTF-8 -d build src/Main.java src/WordSearchGenerator.java src/PuzzleRenderer.java tests/GeneratorTest.java
else
  java com.sun.tools.javac.Main -source 17 -target 17 -Xlint:-options -encoding UTF-8 -d build src/Main.java src/WordSearchGenerator.java src/PuzzleRenderer.java tests/GeneratorTest.java
fi
exec java -Dfile.encoding=UTF-8 -cp build GeneratorTest
