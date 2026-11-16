@echo off
rem Builds RailSync and runs every JUnit test. Results print in this window.
call "%~dp0mvnw.cmd" -B test
pause
