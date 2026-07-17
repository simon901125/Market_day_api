@echo off
setlocal

REM --------------------------------------------------
REM Local test launcher.
REM This file is ignored by Git and must not be committed.
REM It reads SQL Server credentials from run-local.cmd,
REM runs unit/controller/smoke tests, rebuilds MarketDayDB_Test,
REM and then runs all *IT integration tests.
REM --------------------------------------------------

for %%I in ("%~dp0..\..") do set "PROJECT_ROOT=%%~fI"
set "LOCAL_CONFIG=%PROJECT_ROOT%\run-local.cmd"
set "SCHEMA_FILE=%PROJECT_ROOT%\src\test\resources\sql\MarketDayDBIntegration.sql"

if not exist "%LOCAL_CONFIG%" (
    echo [ERROR] Missing %LOCAL_CONFIG%
    echo Create run-local.cmd and configure DB_USERNAME and DB_PASSWORD first.
    exit /b 1
)

if not exist "%SCHEMA_FILE%" (
    echo [ERROR] Missing %SCHEMA_FILE%
    exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"set DB_USERNAME=" "%LOCAL_CONFIG%"') do set "TEST_DB_USERNAME=%%B"
for /f "tokens=1,* delims==" %%A in ('findstr /B /C:"set DB_PASSWORD=" "%LOCAL_CONFIG%"') do set "TEST_DB_PASSWORD=%%B"

if not defined TEST_DB_USERNAME (
    echo [ERROR] DB_USERNAME is not configured in run-local.cmd.
    exit /b 1
)

if not defined TEST_DB_PASSWORD (
    echo [ERROR] DB_PASSWORD is not configured in run-local.cmd.
    exit /b 1
)

set "TEST_DB_URL=jdbc:sqlserver://localhost:1433;databaseName=MarketDayDB_Test;encrypt=true;trustServerCertificate=true"

pushd "%PROJECT_ROOT%"

if /I "%~1"=="integration" goto integration_tests

echo [1/2] Running unit, controller, and Spring Boot smoke tests...
call mvnw.cmd clean test
set "TEST_EXIT_CODE=%ERRORLEVEL%"

if not "%TEST_EXIT_CODE%"=="0" (
    popd
    echo [ERROR] Unit, controller, or smoke test failed.
    exit /b %TEST_EXIT_CODE%
)

:integration_tests
echo [2/2] Rebuilding MarketDayDB_Test and running SQL Server integration tests...
call mvnw.cmd "-Dtest=*IT" test
set "TEST_EXIT_CODE=%ERRORLEVEL%"
popd

if not "%TEST_EXIT_CODE%"=="0" (
    echo [ERROR] SQL Server integration test failed.
    exit /b %TEST_EXIT_CODE%
)

echo [SUCCESS] All unit, controller, smoke, and SQL Server integration tests passed.
exit /b 0
