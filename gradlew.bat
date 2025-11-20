@rem -----------------------------------------------------------------------------
@rem Gradle startup script for Windows
@rem -----------------------------------------------------------------------------

@if "%DEBUG%" == "" @echo off
set JAVA_EXE=java

set CLASSPATH=%~dp0\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
