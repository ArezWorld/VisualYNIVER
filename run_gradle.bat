@echo off
set JAVA_HOME=C:\jdk-17.0.9
set PATH=%JAVA_HOME%\bin;%PATH%
gradle-8.7\bin\gradle.bat assembleDebug
