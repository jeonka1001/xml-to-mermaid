#!/usr/bin/env bash
# 개발용 빌드: src/main/java -> build/hansol-xml-to-image2.jar (JDK 1.8)
# JDK 8 위치: JAVA8_HOME (없으면 PATH 의 javac)
set -euo pipefail
cd "$(dirname "$0")"
BIN="${JAVA8_HOME:+$JAVA8_HOME/bin/}"
rm -rf build && mkdir -p build/classes
find src/main/java -name '*.java' | sed 's/.*/"&"/' > build/sources.txt
"${BIN}javac" -Xlint:all -encoding UTF-8 -d build/classes @build/sources.txt
printf 'Main-Class: hansol.xml2mermaid.HansolXmlToImage2\n' > build/manifest.txt
"${BIN}jar" cfm build/hansol-xml-to-image2.jar build/manifest.txt -C build/classes .
echo "build/hansol-xml-to-image2.jar"
echo "실행 예: java -jar build/hansol-xml-to-image2.jar --rules node-types.properties sample.xml sample.mmd"
