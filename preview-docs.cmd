@echo off
rem Serves the project folder on http://localhost:8080 with the JDK's built-in web server
rem and opens the documentation preview. Press Ctrl+C in this window to stop it.
cd /d "%~dp0"
start "" http://localhost:8080/docs/
jwebserver -p 8080 -b 127.0.0.1
