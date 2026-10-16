@echo off
chcp 65001 >nul
rem ============================================================
rem  AdvancedEnchants offline build script (no Maven needed)
rem  Requires local paper-api jar (default 1.20.4) + adventure-api.
rem  Usage: build-no-maven.cmd      Output: dist\AdvancedEnchants-1.0.0.jar
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "JAVA_HOME_DIR=D:\java21"
set "JAVAC=%JAVA_HOME_DIR%\bin\javac.exe"
set "JAR=%JAVA_HOME_DIR%\bin\jar.exe"
if not exist "%JAVAC%" (
  set "JAVAC=javac"
  set "JAR=jar"
)

rem ---- locate the local Maven repository that actually holds paper-api ----
set "PAPER_REL=io\papermc\paper\paper-api\1.20.4-R0.1-SNAPSHOT\paper-api-1.20.4-R0.1-SNAPSHOT.jar"
set "REPO="
if exist "%USERPROFILE%\.m2\repository\%PAPER_REL%" set "REPO=%USERPROFILE%\.m2\repository"
if not defined REPO if exist "E:\apache-maven-3.9.11\conf\%PAPER_REL%" set "REPO=E:\apache-maven-3.9.11\conf"
if not defined REPO if exist "D:\apache-maven\repository\%PAPER_REL%" set "REPO=D:\apache-maven\repository"
if not defined REPO set "REPO=%USERPROFILE%\.m2\repository"

set "PAPER=%REPO%\%PAPER_REL%"
set "ADV=%REPO%\net\kyori\adventure-api\4.14.0\adventure-api-4.14.0.jar"
set "ADVK=%REPO%\net\kyori\adventure-key\4.14.0\adventure-key-4.14.0.jar"
set "EXAM=%REPO%\net\kyori\examination-api\1.3.0\examination-api-1.3.0.jar"

if not exist "%PAPER%" (
  echo [ERROR] paper-api not found: %PAPER%
  echo         Run "mvn dependency:resolve" once, or edit REPO in this script.
  exit /b 1
)

set "CP=%PAPER%;%ADV%;%ADVK%;%EXAM%"

echo [1/4] clean target\classes ...
if exist target\classes rmdir /s /q target\classes
mkdir target\classes

echo [2/4] compile java sources ...
dir /s /b src\main\java\*.java > target\sources.txt
"%JAVAC%" -encoding UTF-8 --release 21 -nowarn -cp "%CP%" -d target\classes @target\sources.txt
if errorlevel 1 (
  echo [ERROR] compile failed
  exit /b 1
)

echo [3/4] copy resources ...
copy /y src\main\resources\plugin.yml target\classes\ >nul
copy /y src\main\resources\config.yml target\classes\ >nul
mkdir target\classes\enchants 2>nul
copy /y src\main\resources\enchants\*.yml target\classes\enchants\ >nul

echo [4/4] package ...
if not exist dist mkdir dist
if exist dist\AdvancedEnchants-1.0.0.jar del /q dist\AdvancedEnchants-1.0.0.jar
"%JAR%" --create --file dist\AdvancedEnchants-1.0.0.jar -C target\classes .
if errorlevel 1 (
  echo [ERROR] packaging failed
  exit /b 1
)

echo.
echo DONE: dist\AdvancedEnchants-1.0.0.jar
endlocal
