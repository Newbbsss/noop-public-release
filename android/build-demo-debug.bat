@echo off
setlocal

for %%I in ("%~dp0") do set "PROJECT_DIR=%%~sI"
for %%I in ("%~dp0..\..") do set "WORKSPACE_DIR=%%~sI"
if not exist N:\android-sdk-local subst N: "%~dp0..\.."
if exist N:\android-sdk-local set "WORKSPACE_DIR=N:"

set "JAVA_HOME=C:\Users\Gilbert\.jdks\jbr-17.0.14"
set "ANDROID_SDK_ROOT=%WORKSPACE_DIR%\android-sdk-local"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"

cd /d "%PROJECT_DIR%"
call "C:\Users\Gilbert\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat" :app:assembleDemoDebug --no-daemon
