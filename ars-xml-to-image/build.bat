@echo off
rem Developer build: src\main\java -> build\hansol-xml-to-image2.jar (JDK 1.8)
rem JDK 8 location: JAVA8_HOME (otherwise javac on PATH)
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "BIN="
if defined JAVA8_HOME set "BIN=%JAVA8_HOME%\bin\"
if exist build rmdir /s /q build
mkdir build\classes
rem javac argument files treat backslash as an escape: write quoted paths with forward slashes.
(for /r "src\main\java" %%f in (*.java) do (set "p=%%f" & echo "!p:\=/!")) > build\sources.txt
"%BIN%javac" -Xlint:all -encoding UTF-8 -d build\classes @build\sources.txt || exit /b 1
> build\manifest.txt echo Main-Class: hansol.xml2mermaid.HansolXmlToImage2
"%BIN%jar" cfm build\hansol-xml-to-image2.jar build\manifest.txt -C build\classes . || exit /b 1
echo build\hansol-xml-to-image2.jar
echo Example: java -jar build\hansol-xml-to-image2.jar --rules node-types.properties sample.xml sample.mmd
