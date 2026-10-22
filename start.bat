@echo off
rem Starts the Smart Library demo: private MySQL (port 3307) + the Java server (port 8080).
rem The Windows "MySQL80" service on port 3306 is a separate, unused installation.
rem Private MySQL: data in mysql_data\, user root / password root.

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin"

netstat -ano | findstr ":3307" | findstr "LISTENING" >nul
if not errorlevel 1 goto mysqlready

echo Starting private MySQL on port 3307...
start "Smart Library MySQL" /MIN "%MYSQL_BIN%\mysqld.exe" --console --basedir="C:\Program Files\MySQL\MySQL Server 8.0" --datadir="%~dp0mysql_data" --port=3307 --mysqlx=0
echo Waiting for MySQL...
set WAITN=0
:waitmysql
"%MYSQL_BIN%\mysqladmin.exe" ping -h 127.0.0.1 -P 3307 -uroot -proot --silent >nul 2>&1
if not errorlevel 1 goto mysqlready
set /a WAITN+=1
if %WAITN% GEQ 20 (
    echo MySQL did not start on port 3307.
    exit /b 1
)
timeout /t 1 /nobreak >nul
goto waitmysql

:mysqlready
echo MySQL is ready on 3307.

if not exist out mkdir out
echo Compiling...
javac -cp "lib/*" -d out backend\*.java
if errorlevel 1 (
    echo Compile failed.
    exit /b 1
)

echo Starting Smart Library on http://localhost:8080  (Ctrl+C to stop)
java -cp "out;lib/*" Main
