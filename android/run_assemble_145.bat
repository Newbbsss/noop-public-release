@echo off
setlocal
set JAVA_HOME=C:\Users\Gilbert\.jdks\jbr-17.0.14
set ANDROID_SDK_ROOT=N:\android-sdk-local
set ANDROID_HOME=N:\android-sdk-local
set PATH=%JAVA_HOME%\bin;%PATH%
if not exist "N:\android-sdk-local" subst N: "C:\Users\Gilbert\Documents\Ai app store"
cd /d N:\noop-v8.4.0-src\android
echo START %DATE% %TIME% > assemble145_bat.log
call "C:\Users\Gilbert\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat" :app:assembleFullRelease --no-daemon --max-workers=1 --console=plain >> assemble145_bat.log 2>&1
echo GRADLE_EXIT=%ERRORLEVEL% >> assemble145_bat.log
echo DONE > assemble145_bat.done
exit /b %ERRORLEVEL%
