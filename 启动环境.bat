@echo off
chcp 65001 >nul 2>&1
title Start PetClinic Environment
cd /d "%~dp0"

echo.
echo   Starting MySQL + Nacos + 3 services...
echo   Wait about 70 seconds until every container shows (healthy).
echo.

docker compose up -d
echo.
echo   ---- current status ----
docker compose ps

echo.
echo   All (healthy)? Then double-click  demo.bat
echo   Not yet? Wait 30s and double-click this file again to re-check.
pause
