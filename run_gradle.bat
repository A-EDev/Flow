@echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
echo Using Classpath: %CLASSPATH%
echo JAVA_HOME: %JAVA_HOME%
where java

echo Starting Gradle Wrapper...
java -version
java -Xmx512m -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
if %ERRORLEVEL% NEQ 0 (
    echo Command failed with error level %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
