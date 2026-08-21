@rem -----------------------------------------------------------------------------
@rem Gradle start up script for Windows
@rem -----------------------------------------------------------------------------
@echo off
setlocal
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
