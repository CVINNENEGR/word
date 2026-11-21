@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
if not exist build mkdir build
java com.sun.tools.javac.Main -source 17 -target 17 -Xlint:-options -encoding UTF-8 -d build src\Main.java src\WordSearchGenerator.java src\PuzzleRenderer.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 -cp build Main %*
exit /b %errorlevel%
