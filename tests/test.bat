@echo off
cd /d "%~dp0\.."
if not exist tests\out mkdir tests\out
javac -d tests\out app\src\main\java\com\example\bubblepilot\GameSolver.java app\src\main\java\com\example\bubblepilot\ShotGate.java tests\AnalyzeScreenshot.java tests\ShotGateTest.java tests\ImageScaleTest.java
if errorlevel 1 exit /b 1
java -cp tests\out AnalyzeScreenshot tests\reference_game.png

if errorlevel 1 exit /b 1
java -cp tests\out ShotGateTest

java -cp tests\out ImageScaleTest tests\reference_game.png
