@echo off
cd /d "%~dp0\.."
if not exist tests\out mkdir tests\out
javac -d tests\out app\src\main\java\com\example\bubblepilot\GameSolver.java app\src\main\java\com\example\bubblepilot\ShotGate.java tests\AnalyzeScreenshot.java tests\ShotGateTest.java tests\ImageScaleTest.java tests\NewGamePaletteTest.java tests\TrajectoryPhysicsTest.java tests\GeometryRegressionTest.java tests\VideoSafetyTest.java tests\VideoContinuousTest.java
if errorlevel 1 exit /b 1
java -cp tests\out AnalyzeScreenshot tests\reference_game.png
if errorlevel 1 exit /b 1
java -cp tests\out ShotGateTest
if errorlevel 1 exit /b 1
java -cp tests\out ImageScaleTest tests\reference_game.png
if errorlevel 1 exit /b 1
java -cp tests\out NewGamePaletteTest tests\new_game_overlay.png
if errorlevel 1 exit /b 1
java -cp tests\out com.example.bubblepilot.TrajectoryPhysicsTest
if errorlevel 1 exit /b 1
java -cp tests\out com.example.bubblepilot.GeometryRegressionTest tests\reference_game.png tests\new_game_overlay.png
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp tests\out com.example.bubblepilot.VideoSafetyTest tests\video_frames
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp tests\out com.example.bubblepilot.VideoContinuousTest tests\video2_frames
if errorlevel 1 exit /b 1
