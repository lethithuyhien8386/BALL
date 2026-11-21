#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p tests/out
javac -d tests/out app/src/main/java/com/example/bubblepilot/GameSolver.java app/src/main/java/com/example/bubblepilot/ShotGate.java tests/AnalyzeScreenshot.java tests/ShotGateTest.java
java -cp tests/out AnalyzeScreenshot tests/reference_game.png

java -cp tests/out ShotGateTest
