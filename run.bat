@echo off
title PaperStation POS - Launcher
echo Iniciando PaperStation POS (Java 21+ / JavaFX 21)...

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    mvn javafx:run
) else (
    set JAVA_HOME=C:\Users\Jesus\.jdks\openjdk-25.0.2
    "C:\Users\Jesus\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd" javafx:run
)
pause
