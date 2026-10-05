# HansolXmlToImage2 (단일 파일 버전) 빌드·실행 가이드

ARS XML을 Mermaid flowchart(`.md`/`.mmd`)와 이미지(`.png`/`.jpg`/`.svg`/`.pdf`)로 변환합니다.
CLI와 GUI를 제공하며, 코드는 Java 8 호환입니다. 외부 Java 라이브러리는 쓰지 않습니다.

| 파일 | 내용 |
|---|---|
| `HansolXmlToImage2.java` | 변환 기능 + CLI (기본 패키지, v2.1.0) |
| `HansolXmlToImage2Gui.java` | GUI (Swing). `HansolXmlToImage2.java`의 클래스를 사용 |
| `pom.xml` | Maven 빌드 (폴더 안의 `*.java` 전체를 컴파일) |

이 문서의 명령은 **Windows 명령 프롬프트(cmd)** 기준입니다. PowerShell에서는 `%변수%`, `for` 문,
`^` 등이 다르게 해석되므로 cmd를 사용하십시오.

---

## 1. 준비물

| 용도 | 필요한 것 |
|---|---|
| 빌드, MD 변환 | JDK 1.8 이상 (Maven 사용 시 Maven 3.1.0 이상) |
| 이미지 변환 | Maven 빌드 시 자동 설치 (빌드할 때 인터넷 연결 필요, 2장). Maven을 쓰지 않으면 Node.js(18.19 이상 또는 20 이상) + npm (3-2) |
| exe 단독 실행본 만들기 | 위 항목 + Launch4j 3.50 + Temurin JRE 8 (Windows x64 zip) (4장) |

---

## 2. 빌드

**Maven**

```bat
mvn clean package
```

결과는 `target\hansol-xml-to-image2-single.jar`(CLI + GUI)입니다. JDK 9 이상으로 빌드해도 Java 8용으로 컴파일됩니다.

**이미지 변환 런타임 자동 설치**

`mvn package`를 실행하면 이미지 변환에 필요한 런타임을 `target\mermaid\`에 자동으로 설치합니다.
설치에는 `frontend-maven-plugin`을 사용합니다. 실행할 때는 jar 옆의 이 폴더를 자동으로 찾으므로, 따로 설치하거나 `-D` 옵션을 줄 필요가 없습니다.

| 설치 항목 | 버전 | 위치 |
|---|---|---|
| Node.js (npm 포함) | v22.17.1 (npm 10.9.2) | `target\mermaid\node\` |
| mermaid-cli / mermaid | 11.6.0 / 11.6.0 (고정) | `target\mermaid\node_modules\` |
| puppeteer / chrome-headless-shell | 23.11.1 / 131.0.6778.204 | `target\mermaid\chrome\` |

- 처음 빌드할 때 nodejs.org, npm 저장소, Chrome 다운로드 서버에 접속합니다(합계 약 640MB 설치).
  Node 압축 파일은 Maven 로컬 저장소에 보관되어 다음 빌드부터 다시 받지 않습니다. 패키지와 Chrome은 `clean`할 때마다 다시 설치됩니다.
- **빌드한 PC와 같은 OS에서만** 동작합니다(Windows용은 Windows에서 빌드).
- 다른 폴더로 옮길 때는 jar와 `mermaid` 폴더를 **같은 폴더에 함께** 두십시오.
- 설치를 건너뛰려면(MD만 쓰거나 오프라인일 때): `mvn clean package -Dskip.mermaid=true`
- 빌드 로그의 `[ERROR] npm warn deprecated puppeteer@23...`는 npm 경고를 플러그인이 ERROR로 표시한 것입니다. 빌드에는 영향이 없습니다.

| pom 속성 (`-D`로 변경 가능) | 기본값 | 용도 |
|---|---|---|
| `frontend.plugin.version` | `1.6` | frontend-maven-plugin 버전. 1.6~1.11.3이 Maven 3.1.0을 지원합니다. 사내 저장소에 있는 버전을 지정하십시오 |
| `mermaid.node.version` | `v22.17.1` | 설치할 Node 버전 (넥서스에 있는 버전) |
| `nodeDownloadRoot` | `https://nodejs.org/dist/` | Node 다운로드 주소 (사내 미러) |
| `mermaid.node.serverId` | (비어 있음) | Node 다운로드 계정의 settings.xml server id. 비어 있으면 인증 없음 |
| `mermaid.npm.registry` | `https://registry.npmjs.org/` | npm 저장소 (사내 미러) |
| `mermaid.chrome.downloadBaseUrl` | `https://storage.googleapis.com/chrome-for-testing-public` | Chrome 다운로드 주소 (사내 미러) |
| `skip.mermaid` | `false` | `true`이면 런타임 설치 생략 |
| `mermaid.local.node.dir` | (없음) | `-Plocal-node`와 함께 사용. 로컬에 설치된 Node 폴더 (다운로드 생략) |
| `mermaid.local.dir` | (없음) | `-Plocal-mermaid`와 함께 사용. node_modules와 chrome을 준비한 폴더 (npm, Chrome 다운로드 생략) |

**인트라넷(사내 넥서스)만 쓰는 경우**

런타임 설치는 Maven 저장소 외에 세 곳에서 파일을 받습니다. 모두 넥서스 주소로 바꾸면 외부 접속 없이 빌드됩니다.
Python은 필요 없습니다(소스를 컴파일하는 네이티브 모듈이 없음).

| 대상 | 넥서스 저장소 형식 (예) | 원본 | 받는 파일 (Windows 기준) | settings.xml 속성 |
|---|---|---|---|---|
| Node.js | raw (proxy 또는 hosted) | `https://nodejs.org/dist/` | `v22.17.1/node-v22.17.1-win-x64.zip` | `nodeDownloadRoot`, 계정이 필요하면 `mermaid.node.serverId` |
| npm 패키지 | npm (proxy 또는 group) | `https://registry.npmjs.org/` | mermaid, @mermaid-js/mermaid-cli, puppeteer와 그 의존 패키지 | `mermaid.npm.registry` |
| Chrome | raw (proxy 또는 hosted) | `https://storage.googleapis.com/chrome-for-testing-public` | `131.0.6778.204/win64/chrome-headless-shell-win64.zip` | `mermaid.chrome.downloadBaseUrl` |

- hosted(업로드 방식) 저장소라면 위 **경로 그대로** 파일을 올려야 합니다.
- Node 버전을 바꾸면(`mermaid.node.version`) 경로의 버전도 바뀝니다. 넥서스에 그 버전이 있어야 합니다.

`settings.xml`의 `internal-repos` 프로필에 속성을 추가합니다(사내 Maven 저장소 설정과 같은 프로필).

```xml
<properties>
  <nodeDownloadRoot>YOUR_NEXUS_NODEJS_RAW_URL/</nodeDownloadRoot>
  <mermaid.node.serverId>nexus-nodejs</mermaid.node.serverId>
  <mermaid.npm.registry>YOUR_NEXUS_NPM_URL/</mermaid.npm.registry>
  <mermaid.chrome.downloadBaseUrl>YOUR_NEXUS_CHROME_RAW_URL</mermaid.chrome.downloadBaseUrl>
</properties>
```

인증이 필요한 경우는 대상마다 방법이 다릅니다.
- **Node**: `settings.xml`의 `<server><id>nexus-nodejs</id>`에 계정을 두고, `mermaid.node.serverId`에 그 id를 지정합니다. 계정이 필요 없으면 비워 둡니다.
- **npm**: `%USERPROFILE%\.npmrc`에 계정을 둡니다. 예: `//YOUR_NEXUS_NPM_HOST/repository/YOUR_NPM_REPO/:_auth=<base64(아이디:비밀번호)>`
- **Chrome**: 다운로드 도구에 계정 옵션이 없습니다. 이 저장소는 **익명 읽기**를 허용하십시오.
  주소에 계정을 넣으면 빌드 로그에 그대로 출력되므로 넣지 마십시오.

사내 인증서(사설 CA)를 쓰는 넥서스라면 Java(Maven)와 Node 모두 그 인증서를 신뢰해야 합니다.
- Java: JDK의 `cacerts`에 인증서를 등록합니다.
- Node: `set NODE_EXTRA_CA_CERTS=<인증서 파일>`을 설정합니다.

**로컬에 설치된 Node 사용 (다운로드 생략)**

PC에 이미 설치된 Node를 쓰려면 `local-node` 프로필을 켭니다. 빌드 시작 시 그 폴더의 `node.exe`와 `node_modules\npm`을
`target\mermaid\node\`로 복사합니다. 플러그인은 같은 버전의 Node가 이미 있다고 판단해 **다운로드하지 않습니다**.
이 경우 `nodeDownloadRoot`와 `mermaid.node.serverId`는 쓰이지 않습니다.

```bat
node -v
mvn clean package -Plocal-node "-Dmermaid.local.node.dir=C:\Program Files\nodejs"
```

- `node -v` 결과가 `mermaid.node.version`(기본 `v22.17.1`)과 **같아야** 합니다. 다르면 `Node vX was installed, but we need version v22.17.1`이 출력되고 다운로드를 시도합니다.
  로컬 버전에 맞추려면 `-Dmermaid.node.version=vX.Y.Z`를 함께 지정하십시오(mermaid-cli 11.6.0은 Node 18.19 이상 또는 20 이상 필요).
- 폴더 위치는 `where node`로 확인합니다. 그 폴더 안에 `node.exe`와 `node_modules\npm`이 있어야 합니다.
- npm 패키지와 Chrome은 이 프로필과 관계없이 `mermaid.npm.registry`, `mermaid.chrome.downloadBaseUrl`에서 받습니다.
- 매번 옵션을 주지 않으려면 `settings.xml`에 지정합니다. 프로필 속성에 `<mermaid.local.node.dir>C:\Program Files\nodejs</mermaid.local.node.dir>`를 추가하고,
  `<activeProfiles>`에 `<activeProfile>local-node</activeProfile>`를 추가하십시오.

**로컬에 준비한 mermaid 폴더 사용 (npm 저장소, Chrome 다운로드 생략)**

mermaid-cli를 미리 설치한 폴더(압축 파일을 푼 폴더 등)가 있으면 `local-mermaid` 프로필을 켭니다.
그 폴더를 `target\mermaid\`로 복사하고, **npm 설치 단계와 Chrome 설치 단계를 모두 건너뜁니다**.
Node는 `local-node` 프로필 또는 `nodeDownloadRoot`에서 가져오므로, 둘을 함께 쓰면 외부 접속 없이 빌드됩니다.

```bat
mvn clean package -Plocal-node,local-mermaid "-Dmermaid.local.node.dir=C:\Program Files\nodejs" -Dmermaid.local.dir=C:\tools\mermaid
```

`mermaid.local.dir` 폴더는 다음 구조여야 합니다.

```
C:\tools\mermaid\
├─ package.json
├─ node_modules\
│  ├─ @mermaid-js\mermaid-cli\     11.6.0
│  ├─ mermaid\                      11.6.0
│  ├─ puppeteer\ ...                (mermaid-cli의 의존 패키지 전체)
└─ chrome\
   └─ chrome-headless-shell\
      └─ win64-131.0.6778.204\
         └─ chrome-headless-shell-win64\chrome-headless-shell.exe
```

- `node_modules`에는 mermaid-cli의 **의존 패키지까지 모두** 있어야 합니다. `npm install`로 설치한 폴더를 그대로 압축한 것이면 됩니다.
  mermaid-cli의 `.tgz` 하나만 푼 폴더에는 의존 패키지가 없어서 동작하지 않습니다.
- `chrome`이 없다면 `chrome-headless-shell-win64.zip`(Chrome for Testing 131.0.6778.204)을 아래처럼 풀어 넣으십시오.

  ```bat
  mkdir C:\tools\mermaid\chrome\chrome-headless-shell\win64-131.0.6778.204
  tar -xf chrome-headless-shell-win64.zip -C C:\tools\mermaid\chrome\chrome-headless-shell\win64-131.0.6778.204
  ```

- `node_modules` 안의 `.bin` 폴더는 복사하지 않습니다. 리눅스나 맥에서 만든 압축 파일이면 이 폴더가 심볼릭 링크라 복사할 수 없고, 실행할 때도 쓰이지 않습니다.
- 버전 확인: 이미지를 만들 때 실제로 쓰인 버전이 로그에 `Mermaid 11.6.0, mermaid-cli 11.6.0`처럼 남습니다.
  11.6.0이 아니면 `WARN Tested with mermaid 11.6.0 ...` 경고가 출력됩니다.
- 매번 옵션을 주지 않으려면 `settings.xml` 프로필 속성에 `<mermaid.local.dir>`를 추가하고, `<activeProfiles>`에 `local-mermaid`를 추가하십시오.

**사내 Maven 저장소 설정 (필수)**

pom은 Maven Central을 쓰지 않고 사내 저장소 두 곳에서만 플러그인을 받습니다. 저장소 주소와 계정은 pom에 넣지 않고
`%USERPROFILE%\.m2\settings.xml`에 지정합니다. **이 설정이 없으면** `Could not transfer artifact ... from/to repository-public` 오류로 빌드가 실패합니다.

| 저장소 id | 용도 | 주소 속성 |
|---|---|---|
| `repository-public` | 모든 Maven 플러그인 (compiler, jar, resources 등) | `repository.public.url` |
| `repository-maven-depen` | mermaid 런타임 플러그인 `frontend-maven-plugin` | `repository.maven-depen.url` |

```xml
<settings>
  <servers>
    <server>
      <id>repository-public</id>
      <username>YOUR_USERNAME</username>
      <password>YOUR_PASSWORD</password>
    </server>
    <server>
      <id>repository-maven-depen</id>
      <username>YOUR_USERNAME</username>
      <password>YOUR_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>internal-repos</id>
      <properties>
        <repository.public.url>YOUR_REPOSITORY_PUBLIC_URL</repository.public.url>
        <repository.maven-depen.url>YOUR_REPOSITORY_MAVEN_DEPEN_URL</repository.maven-depen.url>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>internal-repos</activeProfile>
  </activeProfiles>
</settings>
```

- 계정이 필요 없는 저장소라면 `<servers>`는 빼도 됩니다. `<server><id>`는 pom의 저장소 id와 **정확히 같아야** 합니다.
- Maven에는 플러그인마다 저장소를 지정하는 문법이 없습니다. **선언 순서(public 다음 maven-depen)대로 찾아 먼저 있는 곳에서 받습니다.**
  그래서 public에 없는 `frontend-maven-plugin`은 maven-depen에서 받게 됩니다.
- `repository-maven-depen`에는 다음 세 가지가 함께 있어야 합니다. 그 밖의 의존 라이브러리는 두 저장소 중 어디에 있어도 됩니다.
  - `com.github.eirslett:frontend-maven-plugin` (pom, jar)
  - 상위 pom `com.github.eirslett:frontend-plugins`
  - `com.github.eirslett:frontend-plugin-core` (pom, jar)
- Maven Central(`central`)은 pom에서 비활성화되어 있어 외부로 요청하지 않습니다.
- `settings.xml`에 `<mirrorOf>*</mirrorOf>` 미러가 있으면 위 두 저장소가 **모두 그 미러로 바뀝니다**.
  두 저장소를 직접 쓰려면 `<mirrorOf>*,!repository-public,!repository-maven-depen</mirrorOf>`처럼 제외하십시오.
- 일회성으로는 `mvn clean package -Drepository.public.url=... -Drepository.maven-depen.url=...`로 지정할 수도 있습니다.

**Maven 없이 (javac)**

```bat
mkdir classes
javac -encoding UTF-8 -d classes *.java
```

---

## 3. 단순 실행

### 3-1. MD 변환 (Java만 있으면 됨)

```bat
java -jar target\hansol-xml-to-image2-single.jar sample.xml sample.md
java -jar target\hansol-xml-to-image2-single.jar -h
```

GUI:

```bat
java -cp target\hansol-xml-to-image2-single.jar HansolXmlToImage2Gui
```

javac로 빌드했다면 `-jar target\...jar` 대신 `-cp classes HansolXmlToImage2`(또는 `HansolXmlToImage2Gui`)를 쓰십시오.

### 3-2. 이미지 변환

**Maven으로 빌드한 경우** (2장의 런타임 자동 설치): 추가 설치나 옵션 없이 바로 실행합니다.

```bat
java -jar target\hansol-xml-to-image2-single.jar sample.xml sample.png
java -jar target\hansol-xml-to-image2-single.jar sample.xml sample.jpg
java -cp target\hansol-xml-to-image2-single.jar HansolXmlToImage2Gui
```

**Maven 없이 빌드했거나, 직접 설치한 mermaid-cli를 쓰는 경우**: mermaid-cli 11.6.0을 설치합니다(mermaid 11.6.0으로 고정).
예시 위치는 `C:\tools\mermaid`입니다.

```bat
mkdir C:\tools\mermaid
cd /d C:\tools\mermaid
echo {"private":true,"overrides":{"mermaid":"11.6.0"}}> package.json
npm install --omit=dev --no-audit --no-fund @mermaid-js/mermaid-cli@11.6.0 puppeteer@23
node -p "require('./node_modules/mermaid/package.json').version"
```

마지막 명령의 결과가 `11.6.0`이어야 합니다. 설치할 때 Puppeteer가 Chrome을 `%USERPROFILE%\.cache\puppeteer`에 내려받습니다.

설치한 mermaid-cli 위치를 지정해서 실행합니다(`node`가 PATH에 있어야 합니다).

```bat
set MMDC=C:\tools\mermaid\node_modules\@mermaid-js\mermaid-cli\src\cli.js
java -Dmermaid.cli=%MMDC% -jar target\hansol-xml-to-image2-single.jar sample.xml sample.png
java -Dmermaid.cli=%MMDC% -jar target\hansol-xml-to-image2-single.jar sample.xml sample.jpg
java -Dmermaid.cli=%MMDC% -cp target\hansol-xml-to-image2-single.jar HansolXmlToImage2Gui
```

- `node`가 PATH에 없으면 `-Dnode.executable="C:\Program Files\nodejs\node.exe"`를 추가합니다.
- 규칙 파일과 렌더링 설정은 선택입니다. 없으면 내장 기본값을 쓰고, 렌더링 설정이 없으면 Mermaid 기본 설정으로 그립니다.
  만드는 방법은 [부록](#부록-선택-설정-파일)에 있습니다.
  `-Dhansol.rules=node-types.properties -Dmermaid.config=mermaid-config.json`
- **렌더링 설정 없이 연결선이 500개를 넘으면** Mermaid 기본 상한 때문에 렌더링이 실패합니다. 큰 다이어그램은 `mermaid-config.json`을 지정하십시오.

---

## 4. exe 단독 실행본 만들기 (JRE 8 포함, 설치 불필요)

> **exe 파일 하나로 쓰려면** 리팩터링 프로젝트(`../ars-xml-to-image`)의 `packaging/windows/build-standalone.sh`가 만드는
> `HansolXmlToImage2-single-standalone.exe`를 쓰십시오. 이 폴더의 소스로 빌드하며, JRE 8, Node, mermaid-cli, Chrome이 모두 들어 있습니다.
> 처음 실행할 때 `%LOCALAPPDATA%\hx2\`에 한 번 풀고, 인자가 없으면 GUI, 있으면 CLI로 동작합니다(자세한 내용은 그 프로젝트의 README2.md).
> 아래는 Windows PC에서 Launch4j로 폴더형 실행본을 직접 만드는 방법입니다.

**Temurin JRE 8**, Node, mermaid-cli, Chrome을 모두 넣은 폴더형 실행본을 만듭니다.
exe 실행기는 **Launch4j 3.50**으로 만듭니다. 결과 폴더를 그대로 복사(또는 zip)하면, 다른 PC에서
Java나 Node를 **설치하지 않고** 실행할 수 있습니다.

| 준비물 (빌드 PC, Windows 10 이상 x64) | 비고 |
|---|---|
| JDK 1.8 + Maven | jar 빌드 (2장) |
| Launch4j 3.50 | https://launch4j.sourceforge.net 에서 `launch4j-3.50-win32.zip`(또는 설치 파일)을 받습니다. Launch4j도 실행에 Java가 필요하며, 위 JDK 1.8로 충분합니다 |
| Temurin JRE 8 (Windows x64, zip) | https://adoptium.net/temurin/releases/?version=8 에서 Package Type `JRE`, OS `Windows`, Architecture `x64`, `.zip`을 받습니다 |
| Node.js + npm | mermaid-cli 설치용. 이때 쓴 `node.exe`가 실행본에 들어갑니다 |
| 인터넷 연결 | npm, Chrome 다운로드. 망분리 환경이면 사내 npm 미러를 쓰거나, 인터넷이 되는 PC에서 만든 결과 폴더를 반입합니다 |

> 경로가 길면 Windows 경로 길이 제한(260자)에 걸릴 수 있습니다. `C:\work` 같은 짧은 경로에서 작업하십시오.
> 아래 명령은 cmd 창에 직접 입력하는 기준입니다. 배치 파일(.bat)에 넣을 때는 `for` 문의 `%i`를 `%%i`로 바꾸십시오.

### 4-1. 작업 폴더 준비와 jar 빌드

```bat
cd /d C:\work\ars-xml-image-single
mvn clean package
rmdir /s /q build dist 2>nul
set B=%CD%\build
set D=%CD%\dist\HansolXmlToImage2
mkdir %B% %D%\lib %D%\runtime\node %D%\runtime\mermaid
copy target\hansol-xml-to-image2-single.jar %D%\lib\
```

### 4-2. JRE 8 넣기

받은 zip의 SHA-256 값을 다운로드 페이지에 표시된 값과 비교한 뒤 `runtime\jre`로 풀어 넣습니다.
파일 이름은 받은 버전에 맞게 바꾸십시오(예: `OpenJDK8U-jre_x64_windows_hotspot_8u504b01.zip`).

```bat
set JRE_ZIP=%USERPROFILE%\Downloads\OpenJDK8U-jre_x64_windows_hotspot_8u504b01.zip
certutil -hashfile "%JRE_ZIP%" SHA256
tar -xf "%JRE_ZIP%" -C %D%\runtime
for /d %i in (%D%\runtime\jdk8u*-jre) do move "%i" %D%\runtime\jre
%D%\runtime\jre\bin\java -version
```

마지막 명령은 `openjdk version "1.8.0_..."`을 출력해야 합니다. zip을 풀면 `jdk8u504-b01-jre` 같은 폴더가 하나 생기며,
`for` 문이 그 폴더 이름을 `jre`로 바꿉니다.

### 4-3. Node 실행 파일 복사

```bat
where node
copy "C:\Program Files\nodejs\node.exe" %D%\runtime\node\
```

`where node`로 나온 경로가 다르면 그 경로의 `node.exe`를 복사하십시오. Windows 공식 `node.exe`는 파일 하나로 동작합니다.

### 4-4. mermaid-cli와 Chrome(headless shell) 설치

```bat
cd /d %D%\runtime\mermaid
echo {"private":true,"overrides":{"mermaid":"11.6.0"}}> package.json
set PUPPETEER_SKIP_DOWNLOAD=1
npm install --omit=dev --no-audit --no-fund @mermaid-js/mermaid-cli@11.6.0 puppeteer@23
set PUPPETEER_SKIP_DOWNLOAD=
node -p "require('./node_modules/mermaid/package.json').version"
set PUPPETEER_CACHE_DIR=%D%\runtime\chrome
npx puppeteer browsers install chrome-headless-shell
cd /d %B%\..
```

- 버전 확인 결과는 `11.6.0`이어야 합니다.
- `npx puppeteer browsers install`은 Puppeteer가 고정한 버전의 chrome-headless-shell(Puppeteer 23.11.1 기준 131.0.6778.204)을
  `runtime\chrome`에 받습니다.

### 4-5. (선택) 설정 파일 넣기

[부록](#부록-선택-설정-파일)의 `node-types.properties`, `mermaid-config.json`을 `dist\HansolXmlToImage2\`에 저장합니다.
없으면 내장 기본값과 Mermaid 기본 설정으로 동작합니다. 연결선이 500개를 넘는 큰 다이어그램은 `mermaid-config.json`이 필요합니다.

### 4-6. Launch4j 설정 파일 만들기

메모장 등으로 아래 두 파일을 `build\` 폴더에 **UTF-8**로 저장합니다. 두 파일의 차이는 `headerType`, `outfile`, `mainClass` 세 줄뿐입니다.

**build\cli.xml** (명령 프롬프트용, 콘솔)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
  <dontWrapJar>true</dontWrapJar>
  <headerType>console</headerType>
  <outfile>..\dist\HansolXmlToImage2\HansolXmlToImage2.exe</outfile>
  <errTitle>HansolXmlToImage2</errTitle>
  <classPath>
    <mainClass>HansolXmlToImage2</mainClass>
    <cp>%EXEDIR%\lib\hansol-xml-to-image2-single.jar</cp>
  </classPath>
  <jre>
    <path>runtime\jre</path>
    <requiresJdk>false</requiresJdk>
    <requires64Bit>true</requires64Bit>
    <minVersion>1.8.0</minVersion>
    <opt>-Dhansol.rules="%EXEDIR%\node-types.properties"</opt>
    <opt>-Dmermaid.config="%EXEDIR%\mermaid-config.json"</opt>
    <opt>-Dnode.executable="%EXEDIR%\runtime\node\node.exe"</opt>
    <opt>-Dmermaid.cli="%EXEDIR%\runtime\mermaid\node_modules\@mermaid-js\mermaid-cli\src\cli.js"</opt>
    <opt>-Dpuppeteer.cache.dir="%EXEDIR%\runtime\chrome"</opt>
  </jre>
</launch4jConfig>
```

**build\gui.xml** (더블클릭용, 콘솔 창 없음)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<launch4jConfig>
  <dontWrapJar>true</dontWrapJar>
  <headerType>gui</headerType>
  <outfile>..\dist\HansolXmlToImage2\HansolXmlToImage2-GUI.exe</outfile>
  <errTitle>HansolXmlToImage2</errTitle>
  <classPath>
    <mainClass>HansolXmlToImage2Gui</mainClass>
    <cp>%EXEDIR%\lib\hansol-xml-to-image2-single.jar</cp>
  </classPath>
  <jre>
    <path>runtime\jre</path>
    <requiresJdk>false</requiresJdk>
    <requires64Bit>true</requires64Bit>
    <minVersion>1.8.0</minVersion>
    <opt>-Dhansol.rules="%EXEDIR%\node-types.properties"</opt>
    <opt>-Dmermaid.config="%EXEDIR%\mermaid-config.json"</opt>
    <opt>-Dnode.executable="%EXEDIR%\runtime\node\node.exe"</opt>
    <opt>-Dmermaid.cli="%EXEDIR%\runtime\mermaid\node_modules\@mermaid-js\mermaid-cli\src\cli.js"</opt>
    <opt>-Dpuppeteer.cache.dir="%EXEDIR%\runtime\chrome"</opt>
  </jre>
</launch4jConfig>
```

설정의 의미는 다음과 같습니다.

| 설정 | 의미 |
|---|---|
| `dontWrapJar` | jar를 exe에 넣지 않고 `lib\`의 jar를 실행합니다. exe는 실행기 역할만 합니다 |
| `jre/path` = `runtime\jre` | **exe 폴더 기준**의 동봉 JRE 8만 사용합니다. PC에 설치된 Java는 쓰지 않습니다 |
| `requires64Bit` | 64비트 JRE 8을 사용합니다(64비트 Windows 필요) |
| `%EXEDIR%` | 실행 시 exe가 있는 폴더로 바뀝니다. 클래스패스와 JVM 옵션은 이 값을 기준으로 지정합니다 |
| `chdir` 없음 | 작업 폴더를 바꾸지 않습니다. 명령 프롬프트의 현재 폴더 기준 상대 경로(`sample.xml` 등)가 그대로 동작합니다 |
| `headerType` | `console`은 `java.exe`로 실행하고 종료 코드를 그대로 돌려줍니다. `gui`는 `javaw.exe`로 실행해 콘솔 창을 띄우지 않습니다 |

### 4-7. exe 생성

Launch4j를 설치(또는 zip을 압축 해제)한 폴더의 `launch4jc.exe`(명령행 버전)로 두 exe를 만듭니다.

```bat
set L4J=C:\tools\launch4j\launch4jc.exe
"%L4J%" %B%\cli.xml
"%L4J%" %B%\gui.xml
```

`Successfully created ...HansolXmlToImage2.exe`가 각각 출력되면 성공입니다. `outfile`은 설정 파일 위치(`build\`) 기준이라
결과는 `dist\HansolXmlToImage2\`에 생깁니다.

### 4-8. 결과물

```
dist\HansolXmlToImage2\
├─ HansolXmlToImage2.exe        CLI (명령 프롬프트용)
├─ HansolXmlToImage2-GUI.exe    GUI (더블클릭)
├─ lib\hansol-xml-to-image2-single.jar
├─ runtime\jre\                 Temurin JRE 8 (64-bit)
├─ runtime\node\node.exe
├─ runtime\mermaid\             mermaid-cli 11.6.0 (mermaid 11.6.0)
├─ runtime\chrome\              chrome-headless-shell
└─ node-types.properties, mermaid-config.json   (선택)
```

```bat
dist\HansolXmlToImage2\HansolXmlToImage2.exe sample.xml sample.png
dist\HansolXmlToImage2\HansolXmlToImage2.exe -h
```

배포할 때는 `dist\HansolXmlToImage2` 폴더 전체를 zip으로 묶습니다(탐색기 [압축] 또는 `tar -a -c -f HansolXmlToImage2-win-x64.zip -C dist HansolXmlToImage2`).
**exe만 따로 복사하면 동작하지 않습니다.** 서명되지 않은 exe라서 처음 실행할 때 SmartScreen이나 백신 경고가 뜰 수 있습니다.

---

## 5. 사용법

### CLI

```
HansolXmlToImage2 [options] <input.xml> <output.mmd|md|png|jpg|svg|pdf> [TD|LR|BT|RL]
```

| 옵션 | 설명 |
|---|---|
| `--strict` | 규칙 밖 요소(UNKNOWN)가 있으면 출력 없이 실패 (종료 코드 3) |
| `--overwrite` | 기존 출력, 중간 파일(`.mmd`), 로그 덮어쓰기 |
| `--rules <file>` | 규칙 파일 (`-Dhansol.rules`와 같음) |
| `--mermaid-config <file>` | Mermaid 렌더링 설정 (`-Dmermaid.config`와 같음) |
| `--scale <1-10>` | PNG/JPG 해상도 배율 (기본 1) |
| `--jpeg-quality <1-100>` | JPG 품질 (기본 90) |
| `--timeout <초>` | 렌더링 제한 시간 (기본 120) |
| `--show-id` | 노드 라벨에 XML Id 표시 |
| `--verbose` | 무시한 요소의 위치까지 로그에 기록 |

- 종료 코드: 0 성공, 1 실패, 2 사용법 오류, 3 `--strict`에서 UNKNOWN 발견
- 로그: `<출력파일>.convert.log`(변환), `<출력파일>.render.log`(이미지 렌더링)
- 같은 이름의 출력, 중간 파일, 로그가 있으면 중단합니다. 다시 만들려면 `--overwrite`를 붙이십시오.

### GUI

1. [파일 추가] / [폴더 추가] / 드래그 앤 드롭으로 XML을 추가합니다(여러 개 가능).
2. 출력 폴더와 이미지 형식(PNG/JPG/SVG/PDF, 여러 개 선택 가능)을 고릅니다. MD는 항상 생성됩니다.
3. 기존 파일 처리 방식(덮어쓰기 / 건너뛰기 / 새 이름으로 저장)을 고르고 [변환 시작]을 누릅니다.
4. 결과는 `<출력 폴더>\<파일명>.md`, `<파일명>.png` 등이고, 로그는 `<출력 폴더>\logs\`에 남습니다. 목록에서 더블클릭하면 로그가 열립니다.

---

## 6. 문제 해결

| 증상 | 조치 |
|---|---|
| `Mermaid CLI not found: <찾아본 경로들>` | Maven 빌드라면 `target\mermaid`가 jar 옆에 있는지 확인하십시오(`-Dskip.mermaid=true`로 빌드하지 않았는지 포함). 그 외에는 `-Dmermaid.cli`에 `...\@mermaid-js\mermaid-cli\src\cli.js` 경로를 지정하십시오. |
| 이미지 변환 실패 (`Mermaid exit code ...`) | `<출력>.render.log`(GUI는 `logs\<파일명>.<확장자>.render.log`)를 확인하십시오. |
| `Edge limit exceeded` / `Maximum text size` | `mermaid-config.json`을 지정하십시오(부록). |
| render.log에 `Could not find ...` (브라우저를 찾지 못함) | 단순 실행: `npm install`을 `PUPPETEER_SKIP_DOWNLOAD` 없이 다시 실행하십시오. exe: 4-4의 `npx puppeteer browsers install` 단계를 확인하십시오. |
| exe 실행 시 Java Runtime(1.8.0)이 필요하다는 메시지 | exe 옆에 `runtime\jre\bin\java.exe`가 있는지 확인하십시오(4-2). 64비트 JRE 8이어야 합니다. |
| exe 실행 시 `Could not find or load main class` | `lib\hansol-xml-to-image2-single.jar`가 있는지, 4-6 설정의 `<cp>`에 `%EXEDIR%\`가 붙어 있는지 확인하십시오. |
| Launch4j 실행 중 경로 오류, 설치 중 경로 오류 | 작업 폴더를 `C:\work`처럼 짧은 경로로 옮기십시오. |
| `Could not transfer artifact ... from/to repository-public` 또는 `repository-maven-depen` | settings.xml에 사내 저장소 주소(2장)가 지정되어 있는지, `<server><id>` 계정이 맞는지 확인하십시오. |
| `Could not find artifact com.github.eirslett:frontend-maven-plugin...` | `repository-maven-depen`에 플러그인, 상위 pom(`frontend-plugins`), `frontend-plugin-core`가 있는지 확인하십시오. 다른 버전만 있다면 `-Dfrontend.plugin.version`으로 지정하십시오. |
| `Could not download Node.js: Got error code 401` (또는 404) | 401: `mermaid.node.serverId`와 settings.xml `<server>` 계정을 확인하십시오. 404: `nodeDownloadRoot` 아래에 `v22.17.1/node-v22.17.1-win-x64.zip`이 있는지 확인하십시오. |
| `npm error code E404` / `E401` (mermaid-cli 단계) | `mermaid.npm.registry` 주소와 `%USERPROFILE%\.npmrc` 계정을 확인하십시오. |
| Chrome 단계에서 다운로드 실패 | `mermaid.chrome.downloadBaseUrl` 아래에 `131.0.6778.204/win64/chrome-headless-shell-win64.zip`이 있는지, 익명 읽기가 허용되는지 확인하십시오. |
| `mvn` 빌드 시 `No compiler is provided` | `JAVA_HOME`이 JRE를 가리킵니다. JDK 경로로 바꾸십시오. |

---

## 부록: 선택 설정 파일

UTF-8로 저장하십시오.

**node-types.properties** (규칙 파일)

```properties
# NodeType -> 도형 (rect, round, stadium, subroutine, cylinder, circle, doublecircle, asymmetric,
#                    diamond, hexagon, parallelogram, parallelogram-alt, trapezoid, trapezoid-alt)
nodetype.StartNode=stadium
nodetype.ScriptNode=rect
# 로그에 "NodeType 'XXX'" 가 보고되면 의미를 확인한 뒤 추가합니다. 예) nodetype.MenuNode=diamond
fallback.shape=rect
diagram.versions=14
ignore.elements=CustomProperties,Script
ignore.attributes=
```

**mermaid-config.json** (렌더링 설정: 한글 글꼴, 큰 다이어그램 상한)

```json
{
  "theme": "default",
  "fontFamily": "\"Malgun Gothic\", \"맑은 고딕\", \"Apple SD Gothic Neo\", sans-serif",
  "maxTextSize": 1000000,
  "maxEdges": 5000,
  "flowchart": {
    "useMaxWidth": false
  }
}
```

---

## 검증 범위

- 빌드: JDK 1.8 / JDK 21에서 Maven과 javac 모두 확인했습니다(Java 8 바이트코드).
- 변환: mermaid-cli 11.6.0 + mermaid 11.6.0으로 확인했습니다. CLI는 v2.0.0과 결과가 같은지 비교했고,
  GUI는 일괄 변환·충돌 정책·취소를 JRE 8에서 확인했습니다.
- exe 실행본(4장): 4-6의 설정으로 Launch4j 3.50이 exe 두 개(콘솔용, GUI용)를 만드는 것을 확인했습니다.
  exe에 JRE 경로, `%EXEDIR%` 기준 클래스패스, JVM 옵션이 들어간 것도 확인했습니다. 또 실행기가 넘기는 명령
  (JRE 8 + 같은 클래스패스·옵션)을 같은 폴더 구조로 실행해 MD/PNG/JPG 변환과 GUI 실행을 확인했습니다(macOS).
  **Windows에서 exe를 직접 실행한 검증은 아직 하지 않았습니다.**
