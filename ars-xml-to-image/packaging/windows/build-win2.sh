#!/usr/bin/env bash
# Windows 배포본 빌드 (HansolXmlToImage2): dist-win2/hansol-xml-to-image2-win-x64.zip
#
# 설치 없이 Windows(x64)에서 바로 실행되도록 런타임을 모두 동봉한다.
#   HansolXmlToImage2.exe      CLI 실행기 (Go, packaging/windows/launcher)
#   HansolXmlToImage2-GUI.exe  GUI 실행기 (같은 소스, -X main.mode=gui, 콘솔 창 없음)
#   lib/                   src/main/java 를 JDK 1.8 javac 로 컴파일한 jar
#   runtime/jre            Temurin JRE 8
#   runtime/node           node.exe
#   runtime/mermaid        @mermaid-js/mermaid-cli 11.6.0 + mermaid 11.6.0 고정
#   runtime/chrome         chrome-headless-shell (Puppeteer 가 고정한 버전, win64)
#   node-types.properties  변환 규칙 (재컴파일 없이 수정)
#   mermaid-config.json    Mermaid 렌더링 설정
#
# 필요: bash, curl, JDK 1.8(javac/jar), go, node/npm, unzip, zip, shasum
#       JDK 8 위치는 JAVA8_HOME 으로 지정 (없으면 macOS java_home -v 1.8 사용)
set -euo pipefail

NODE_VERSION=v22.17.1
MERMAID_CLI_VERSION=11.6.0
MERMAID_VERSION=11.6.0
PUPPETEER_RANGE='^23'   # mermaid-cli 11.6.0 의 peerDependency
JAVA_FEATURE=8

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CACHE="$ROOT/.cache"
OUT="$ROOT/dist-win2"
STAGE="$OUT/hansol-xml-to-image2"
ZIP="$OUT/hansol-xml-to-image2-win-x64.zip"

JAVA8_HOME="${JAVA8_HOME:-$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)}"
[ -x "$JAVA8_HOME/bin/javac" ] || { echo "JDK 1.8 을 찾지 못했습니다. JAVA8_HOME 을 지정하십시오." >&2; exit 1; }

sha256_check() { # <file> <expected>
  local actual
  actual="$(shasum -a 256 "$1" | awk '{print $1}')"
  [ "$actual" = "$2" ] || { echo "SHA-256 불일치: $1" >&2; exit 1; }
}

fetch() { # <url> <file> <sha256>
  if [ ! -f "$2" ] || [ "$(shasum -a 256 "$2" | awk '{print $1}')" != "$3" ]; then
    echo "다운로드: $(basename "$2")"
    curl -fsSL -o "$2.part" "$1"
    sha256_check "$2.part" "$3"
    mv "$2.part" "$2"
  fi
}

rm -rf "$OUT"
mkdir -p "$CACHE" "$STAGE/lib" "$STAGE/runtime"

# --------------------------------------------------------------- Java 앱
echo "[1/6] Java 컴파일 ($("$JAVA8_HOME/bin/javac" -version 2>&1))"
CLASSES="$OUT/.classes"
mkdir -p "$CLASSES"
find "$ROOT/src/main/java" -name '*.java' | sed 's/.*/"&"/' > "$OUT/.sources"
"$JAVA8_HOME/bin/javac" -Xlint:all -encoding UTF-8 -d "$CLASSES" @"$OUT/.sources"
printf 'Main-Class: hansol.xml2mermaid.HansolXmlToImage2\n' > "$OUT/.manifest"
"$JAVA8_HOME/bin/jar" cfm "$STAGE/lib/hansol-xml-to-image2.jar" "$OUT/.manifest" -C "$CLASSES" .
rm -rf "$CLASSES" "$OUT/.manifest" "$OUT/.sources"

# ------------------------------------------------------------------- JRE
echo "[2/6] JRE $JAVA_FEATURE (Temurin, windows x64)"
JRE_META="$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/$JAVA_FEATURE/hotspot?os=windows&architecture=x64&image_type=jre")"
JRE_URL="$(printf '%s' "$JRE_META" | node -e 'const d=JSON.parse(require("fs").readFileSync(0));process.stdout.write(d[0].binary.package.link)')"
JRE_SHA="$(printf '%s' "$JRE_META" | node -e 'const d=JSON.parse(require("fs").readFileSync(0));process.stdout.write(d[0].binary.package.checksum)')"
JRE_ZIP="$CACHE/$(basename "$JRE_URL")"
fetch "$JRE_URL" "$JRE_ZIP" "$JRE_SHA"
TMP="$OUT/.jre"
mkdir -p "$TMP"
unzip -q "$JRE_ZIP" -d "$TMP"
mv "$TMP"/*/ "$STAGE/runtime/jre"
rm -rf "$TMP"

# ------------------------------------------------------------------ Node
echo "[3/6] Node $NODE_VERSION (win-x64)"
NODE_ZIP="$CACHE/node-$NODE_VERSION-win-x64.zip"
NODE_SHA="$(curl -fsSL "https://nodejs.org/dist/$NODE_VERSION/SHASUMS256.txt" | awk '$2=="node-'"$NODE_VERSION"'-win-x64.zip"{print $1}')"
fetch "https://nodejs.org/dist/$NODE_VERSION/node-$NODE_VERSION-win-x64.zip" "$NODE_ZIP" "$NODE_SHA"
mkdir -p "$STAGE/runtime/node"
unzip -q -j "$NODE_ZIP" "node-$NODE_VERSION-win-x64/node.exe" "node-$NODE_VERSION-win-x64/LICENSE" -d "$STAGE/runtime/node"

# --------------------------------------------------------- mermaid-cli
echo "[4/6] mermaid-cli $MERMAID_CLI_VERSION (mermaid $MERMAID_VERSION)"
MM="$STAGE/runtime/mermaid"
mkdir -p "$MM"
# mermaid-cli 11.6.0 은 mermaid ^11.0.2 를 받으므로 overrides 로 11.6.0 에 고정한다.
printf '{ "private": true, "overrides": { "mermaid": "%s" } }\n' "$MERMAID_VERSION" > "$MM/package.json"
# 브라우저는 아래에서 win64 용을 따로 받는다. --os/--cpu: 네이티브 선택 의존성을 win32-x64 용으로 받는다.
( cd "$MM" && PUPPETEER_SKIP_DOWNLOAD=1 npm install --no-audit --no-fund --omit=dev \
    --os=win32 --cpu=x64 "@mermaid-js/mermaid-cli@$MERMAID_CLI_VERSION" "puppeteer@$PUPPETEER_RANGE" >/dev/null )
for pkg in mermaid @mermaid-js/mermaid-cli; do
  v="$(node -p "require('$MM/node_modules/$pkg/package.json').version")"
  want="$([ "$pkg" = mermaid ] && echo "$MERMAID_VERSION" || echo "$MERMAID_CLI_VERSION")"
  [ "$v" = "$want" ] || { echo "$pkg 버전 불일치: $v (기대값 $want)" >&2; exit 1; }
done
if find "$MM/node_modules" -name '*.node' | grep -v 'win32-x64' | grep -q .; then
  echo "Windows 용이 아닌 네이티브 모듈이 포함되어 있습니다" >&2
  find "$MM/node_modules" -name '*.node' | grep -v 'win32-x64' >&2; exit 1
fi
find "$MM/node_modules" -name '*.map' -delete
rm -rf "$MM/node_modules/.bin"

# --------------------------------------------------------------- Chrome
echo "[5/6] chrome-headless-shell (win64)"
# mermaid-cli 는 headless: 'shell' 로 실행하므로 chrome-headless-shell 이 필요하다.
CHROME_REV="$(node -p "require('$MM/node_modules/puppeteer-core/lib/cjs/puppeteer/revisions.js')
  .PUPPETEER_REVISIONS['chrome-headless-shell']")"
[ -n "$CHROME_REV" ] || { echo "Puppeteer 의 chrome-headless-shell 버전을 찾지 못했습니다" >&2; exit 1; }
( cd "$MM" && node node_modules/@puppeteer/browsers/lib/cjs/main-cli.js install \
    "chrome-headless-shell@$CHROME_REV" --platform win64 --path "$STAGE/runtime/chrome" >/dev/null )

# --------------------------------------------------------------- 실행기
echo "[6/6] HansolXmlToImage2.exe / HansolXmlToImage2-GUI.exe (windows/amd64)"
( cd "$ROOT/packaging/windows/launcher" && CGO_ENABLED=0 GOOS=windows GOARCH=amd64 \
    go build -trimpath -ldflags "-s -w" -o "$STAGE/HansolXmlToImage2.exe" . )
( cd "$ROOT/packaging/windows/launcher" && CGO_ENABLED=0 GOOS=windows GOARCH=amd64 \
    go build -trimpath -ldflags "-s -w -H windowsgui -X main.mode=gui" -o "$STAGE/HansolXmlToImage2-GUI.exe" . )

cp "$ROOT/node-types.properties" "$ROOT/mermaid-config.json" "$ROOT/sample.xml" "$STAGE/"
{
  printf '\xef\xbb\xbf'
  cat <<TXT
HansolXmlToImage2 - Windows 실행본 (설치 불필요)

동봉 런타임: Temurin JRE $JAVA_FEATURE, Node $NODE_VERSION,
             mermaid-cli $MERMAID_CLI_VERSION (mermaid $MERMAID_VERSION),
             chrome-headless-shell $CHROME_REV (win64)

[화면(GUI)으로 사용]
  HansolXmlToImage2-GUI.exe 를 더블클릭합니다.
  1. XML 파일을 추가합니다 (여러 개 선택, 폴더 추가, 드래그 앤 드롭 가능).
  2. 출력 폴더와 이미지 형식(PNG/JPG/SVG/PDF, 여러 개 가능)을 고릅니다. MD 는 항상 생성됩니다.
  3. [변환 시작]. 결과: <출력 폴더>\<파일명>.md, <파일명>.png ...
     로그: <출력 폴더>\logs\<파일명>.convert.log (목록에서 더블클릭하면 열림)

[명령 프롬프트(CLI)로 사용] 압축을 푼 폴더에서 명령 프롬프트를 열고:
  HansolXmlToImage2.exe sample.xml sample.mmd        Mermaid 원문
  HansolXmlToImage2.exe sample.xml sample.md         Markdown (mermaid 코드블록)
  HansolXmlToImage2.exe sample.xml sample.png        이미지 (sample.mmd 도 함께 생성)
  HansolXmlToImage2.exe input.xml result.jpg         JPG (--jpeg-quality 1~100)
  HansolXmlToImage2.exe --scale 2 input.xml big.png  PNG/JPG 해상도 배율 (1~10)
  HansolXmlToImage2.exe input.xml result.svg LR
  HansolXmlToImage2.exe --help

  다른 폴더에서 실행해도 됩니다: C:\\tools\\hansol-xml-to-image2\\HansolXmlToImage2.exe in.xml out.png
  입력/출력 경로는 현재 폴더 기준입니다. exe 만 따로 복사하면 동작하지 않습니다.

[옵션]
  --strict      규칙 밖 요소가 있으면 출력 없이 실패 (종료 코드 3)
  --overwrite   기존 출력/중간파일/로그 덮어쓰기
  --show-id     노드 라벨에 XML Id 표시
  --verbose     무시 요소의 개별 위치까지 로그
  --timeout N   이미지 렌더링 제한 시간(초, 기본 120)
  추가 JVM 옵션: set XML2IMG_JAVA_OPTS=-Drender.timeout.seconds=300

[로그]
  <출력파일>.convert.log  변환 로그. 규칙 밖 요소(UNKNOWN)는 위치별로 기록되고 끝에 요약됩니다.
  <출력파일>.render.log   이미지 렌더링(Mermaid/Chrome) 로그

[규칙 추가]
  node-types.properties 를 수정하면 재컴파일 없이 반영됩니다.
  예) nodetype.MenuNode=diamond

[종료 코드] 0 성공, 1 실패, 2 사용법 오류, 3 --strict 에서 규칙 밖 요소 발견
TXT
} | sed 's/$/\r/' > "$STAGE/README.txt"

( cd "$OUT" && zip -qr -9 "$(basename "$ZIP")" "$(basename "$STAGE")" )
echo "Windows 배포본: $ZIP ($(du -h "$ZIP" | awk '{print $1}'))"
echo "스테이징 폴더(검사용): $STAGE"
