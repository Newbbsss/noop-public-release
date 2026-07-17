@echo off
cd /d "C:\Users\Gilbert\Documents\Ai app store\noop-v8.4.0-src\android"
set JAVA_HOME=C:\Users\Gilbert\.jdks\jbr-17.0.14
set ANDROID_HOME=N:\android-sdk-local
set ANDROID_SDK_ROOT=N:\android-sdk-local
set ORG_GRADLE_PROJECT_org_gradle_jvmargs=-Xmx2048m
echo START %DATE% %TIME%>> publish-138-console.log
powershell -NoProfile -ExecutionPolicy Bypass -File publish-main-release.ps1 -VersionName 8.6.138-fable -VersionCode 408 -ForceUpdate >> publish-138-console.log 2>&1
echo EXIT=%ERRORLEVEL% %DATE% %TIME%>> publish-138-console.log
