@echo off
REM Manages the local Docker stack for the Azure Cloud Native Order Platform.
REM
REM The stack (docker-compose.yml) runs:
REM   - Azure SQL Edge        (mssql)              -> port 1433
REM   - Service Bus emulator  (servicebus-emulator) -> ports 5672 (AMQP) / 5300 (mgmt)
REM   - Redis                 (redis)              -> port 6379
REM   - Spring Boot app       (order-platform-app) -> port 8080
REM
REM Usage:
REM   run-dynamodb.bat            -> start (build + create if missing, start if stopped)
REM   run-dynamodb.bat start      -> start
REM   run-dynamodb.bat stop       -> stop and remove the containers
REM   run-dynamodb.bat status     -> report each container's state
REM   run-dynamodb.bat restart    -> stop then start
REM   run-dynamodb.bat logs       -> tail the application logs
setlocal

where docker >nul 2>nul
if errorlevel 1 (
    echo ERROR: Docker is not installed or not on PATH.
    echo Install Docker Desktop from https://www.docker.com/products/docker-desktop/ and try again.
    exit /b 1
)

set "COMPOSE_FILE=docker-compose.yml"
set "APP_CONTAINER=order-platform-app"
set "ACTION=%~1"

if "%ACTION%"=="" set "ACTION=start"

REM ---------------------------------------------------------------------
REM status
REM ---------------------------------------------------------------------
if "%ACTION%"=="status" goto :status

REM ---------------------------------------------------------------------
REM logs
REM ---------------------------------------------------------------------
if "%ACTION%"=="logs" goto :logs

REM ---------------------------------------------------------------------
REM stop
REM ---------------------------------------------------------------------
if "%ACTION%"=="stop" goto :stop

REM ---------------------------------------------------------------------
REM restart
REM ---------------------------------------------------------------------
if "%ACTION%"=="restart" goto :stop

REM ---------------------------------------------------------------------
REM start (default)
REM ---------------------------------------------------------------------
docker inspect -f "{{.State.Running}}" %APP_CONTAINER% >nul 2>nul
if errorlevel 1 (
    echo Containers do not exist yet. Building and starting the stack with docker compose...
    docker compose up -d --build
    goto :done
)

for /f "delims=" %%i in ('docker inspect -f "{{.State.Running}}" %APP_CONTAINER% 2^>nul') do set "RUNNING=%%i"
if "%RUNNING%"=="true" (
    echo The order platform stack is already running.
    goto :status_short
)

echo Stack exists but is stopped. Starting it...
docker compose up -d
goto :done

:done
echo.
echo Stack started:
echo   App                http://localhost:8080  ^(Swagger: /swagger-ui/index.html^)
echo   Azure SQL Edge     localhost:1433
echo   Service Bus emu.   localhost:5672 ^(AMQP^) / localhost:5300 ^(mgmt^)
echo   Redis              localhost:6379
goto :eof

:status_short
echo   App                http://localhost:8080  ^(Swagger: /swagger-ui/index.html^)
goto :eof

REM ---------------------------------------------------------------------
:status
docker compose ps
goto :eof

REM ---------------------------------------------------------------------
:logs
docker compose logs -f --tail=100 app
goto :eof

REM ---------------------------------------------------------------------
:stop
docker inspect -f "{{.State.Running}}" %APP_CONTAINER% >nul 2>nul
if errorlevel 1 (
    echo No containers found for this project. Nothing to stop.
    goto :eof
)
echo Stopping and removing the stack...
docker compose down

echo Stack stopped and removed.
if "%ACTION%"=="restart" goto :start_after_stop
goto :eof

REM ---------------------------------------------------------------------
:start_after_stop
echo.
echo Starting the stack again...
docker compose up -d --build
echo.
echo App is running on http://localhost:8080
goto :eof

endlocal

