@echo off
SET "SCRIPT_DIR=%~dp0"
java -jar "%SCRIPT_DIR%fxgraph-cli.jar" %*
