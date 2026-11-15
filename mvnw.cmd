@echo off
setlocal
set "MAVEN_BASEDIR=%~dp0"
set "MAVEN_SKIP_RC=true"
set "MVN_HOME=%~dp0.tools\apache-maven-3.9.11"
if exist "%MVN_HOME%\bin\mvn.cmd" goto run
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\bootstrap-maven.ps1"
if errorlevel 1 exit /b 1
:run
call "%MVN_HOME%\bin\mvn.cmd" "-Dmaven.repo.local=%~dp0.tools\repository" %*
exit /b %ERRORLEVEL%
