#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p tests/out
javac -encoding UTF-8 -d tests/out app/src/main/java/com/example/bubblepilot/GameSolver.java app/src/main/java/com/example/bubblepilot/ShotGate.java tests/AnalyzeScreenshot.java tests/ShotGateTest.java tests/ImageScaleTest.java tests/NewGamePaletteTest.java tests/TrajectoryPhysicsTest.java tests/GeometryRegressionTest.java tests/VideoSafetyTest.java tests/VideoContinuousTest.java app/src/main/java/com/example/bubblepilot/SkillVision.java app/src/main/java/com/example/bubblepilot/SkillController.java tests/TrainingRegressionTest.java tests/SkillControllerTest.java
java -cp tests/out AnalyzeScreenshot tests/reference_game.png

java -cp tests/out ShotGateTest

java -cp tests/out ImageScaleTest tests/reference_game.png

java -cp tests/out NewGamePaletteTest tests/new_game_overlay.png

java -cp tests/out com.example.bubblepilot.TrajectoryPhysicsTest
java -cp tests/out com.example.bubblepilot.GeometryRegressionTest tests/reference_game.png tests/new_game_overlay.png

java -Djava.awt.headless=true -cp tests/out com.example.bubblepilot.VideoSafetyTest tests/video_frames

java -Djava.awt.headless=true -cp tests/out com.example.bubblepilot.VideoContinuousTest tests/video2_frames

java -Djava.awt.headless=true -cp tests/out com.example.bubblepilot.TrainingRegressionTest tests/training
java -cp tests/out com.example.bubblepilot.SkillControllerTest
