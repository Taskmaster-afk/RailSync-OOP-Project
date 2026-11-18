@echo off
rem Builds RailSync (skipping tests for speed) and starts the desktop app.
call "%~dp0mvnw.cmd" -B -q package -DskipTests
if errorlevel 1 goto end
java -jar "%~dp0target\railsync-1.1.0.jar"
:end
