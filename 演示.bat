@echo off
chcp 65001 >nul 2>&1
title PetClinic Demo - Midterm Report
cd /d "%~dp0"

set "PATH=D:\WorkBuddy\resources\vendor\PortableGit\usr\bin;%PATH%"
set NO_COLOR=1

echo.
echo   Starting demo, make sure the 5 containers are healthy first.
echo   If it says "not ready", run:  docker compose up -d
echo.

"D:\WorkBuddy\resources\vendor\PortableGit\usr\bin\bash.exe" scripts/demo.sh

echo.
echo   ---- demo finished ----
pause
