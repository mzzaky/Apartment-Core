@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_DIR=%~dp0.maven
set MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip

if not exist "%MAVEN_DIR%\apache-maven-%MAVEN_VERSION%" (
    echo [INFO] Downloading Maven %MAVEN_VERSION%...
    mkdir "%MAVEN_DIR%" 2>nul
    curl -k -L -o "%MAVEN_DIR%\maven.zip" "%MAVEN_URL%"
    echo [INFO] Extracting Maven...
    tar -xf "%MAVEN_DIR%\maven.zip" -C "%MAVEN_DIR%"
    del "%MAVEN_DIR%\maven.zip"
)

echo [INFO] Running Maven...
"%MAVEN_DIR%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd" %*
