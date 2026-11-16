@echo off
rem Compiles RailSync and runs the console demo for the stages finished so far.
call "%~dp0mvnw.cmd" -B -q compile
if errorlevel 1 goto end
java -cp "%~dp0target\classes" com.railsync.ConsoleDemo
:end
pause
