# HansolXmlToImage2 Maven 빌드 가이드

`pom.xml`로 HansolXmlToImage2를 빌드하는 방법입니다. 외부 라이브러리 의존성은 없으며,
Maven 플러그인만 내려받습니다. 결과 jar는 Java 8 바이트코드입니다.

## 1. 준비

| 항목 | 버전 | 확인 명령 |
|---|---|---|
| JDK | 1.8 (권장) 또는 9 이상 | `java -version`, `javac -version` |
| Maven | 3.1.0 이상 (3.1.0, 3.9.16에서 검증) | `mvn -v` |

- `JAVA_HOME`이 사용할 JDK를 가리켜야 합니다. Maven은 `JAVA_HOME`의 JDK로 컴파일합니다.

  ```bat
  set JAVA_HOME=C:\Java\jdk1.8.0_xxx
  mvn -v
  ```

- JDK 9 이상으로 빌드하면 `jdk9+` 프로파일이 자동으로 켜져 `release 8`로 컴파일합니다.
  이때 Java 9+ API(`var`, `List.of` 등)를 쓰면 **빌드가 실패**하므로 Java 8 호환이 보장됩니다.

## 2. 빌드

| 대상 | 명령 | 결과 jar | Main-Class |
|---|---|---|---|
| `src/main/java` (CLI + GUI, v2.2.0) | `mvn clean package` | `target/hansol-xml-to-image2.jar` | `hansol.xml2mermaid.HansolXmlToImage2` |

> 이전 빌드의 클래스가 남지 않도록 `clean`을 함께 실행하는 것을 권장합니다.

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

## 3. 실행

**CLI (리팩터링 버전)**

```bat
java -jar target\hansol-xml-to-image2.jar --rules node-types.properties sample.xml sample.md
java -jar target\hansol-xml-to-image2.jar --help
```

**GUI (리팩터링 버전)**

```bat
java -Dhansol.rules=node-types.properties -Dmermaid.config=mermaid-config.json ^
     -cp target\hansol-xml-to-image2.jar hansol.xml2mermaid.ui.ConverterApp
```

**이미지 출력(PNG/JPG/SVG/PDF)**: `mvn package`가 설치한 `target\mermaid`를 자동으로 사용하므로 옵션 없이 실행합니다.

```bat
java -jar target\hansol-xml-to-image2.jar sample.xml sample.png
```

직접 설치한 Node와 mermaid-cli를 쓰려면 JVM 옵션으로 지정합니다(지정하면 `target\mermaid`보다 우선).

```bat
java -Dmermaid.cli=C:\tools\mermaid\node_modules\@mermaid-js\mermaid-cli\src\cli.js ^
     -Dnode.executable="C:\Program Files\nodejs\node.exe" ^
     -Dmermaid.config=mermaid-config.json ^
     -jar target\hansol-xml-to-image2.jar sample.xml sample.png
```

Node, mermaid-cli, Chrome까지 함께 넣은 설치 없는 Windows 배포본은 `packaging/windows/build-win2.sh`로 만듭니다
(README2.md 참고).

## 4. pom.xml 구성

| 항목 | 내용 |
|---|---|
| 좌표 | `hansol:hansol-xml-to-image2:2.2.0`, packaging `jar` |
| 인코딩 | 소스 UTF-8 (`project.build.sourceEncoding`) |
| 컴파일 | `maven-compiler-plugin` 3.8.1, source/target 1.8, `-Xlint:all,-options` (경고 표시) |
| jar | `maven-jar-plugin` 3.2.2, Manifest `Main-Class` = `${main.class}` |
| 재현 가능 빌드 | `project.build.outputTimestamp` 고정 (jar 내부 시각이 빌드마다 같음) |
| 플러그인 버전 고정 | clean 3.1.0, resources 3.2.0, surefire 2.22.2, install 2.5.2 (모두 Maven 3.1.0에서 동작하는 버전) |
| 저장소 | 사내 저장소 `repository-public`(모든 플러그인), `repository-maven-depen`(frontend-maven-plugin). 주소는 settings.xml에서 지정(5장). `central`은 비활성화 |
| 이미지 런타임 | `frontend-maven-plugin`(`${frontend.plugin.version}`, 기본 1.6)으로 `target/mermaid`에 Node, mermaid-cli, Chrome 설치 (2장) |
| 프로파일 `jdk9+` | JDK 9 이상에서 자동 활성화, `maven.compiler.release=8` |

버전을 올릴 때는 `pom.xml`의 `<version>`과 `src/main/java/hansol/xml2mermaid/HansolXmlToImage2.java`의
`VERSION` 상수를 함께 바꾸십시오.

## 5. 사내 저장소 / 망분리 환경

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

**로컬 저장소 반입 (저장소 접속이 아예 안 될 때)**: 접속되는 PC에서 한 번 빌드(`mvn clean package`)한 뒤,
`%USERPROFILE%\.m2\repository`를 대상 PC로 복사하고 오프라인으로 빌드합니다.

```bat
mvn -o clean package
```

**이미지 런타임**: Node, npm 패키지, Chrome은 Maven 저장소가 아닌 별도 서버에서 받습니다.
2장의 "인트라넷(사내 넥서스)만 쓰는 경우"대로 `nodeDownloadRoot`, `mermaid.npm.registry`, `mermaid.chrome.downloadBaseUrl`을 넥서스로 지정하거나,
`-Dskip.mermaid=true`로 빌드한 뒤 `-Dmermaid.cli` 등으로 미리 설치한 런타임을 지정하십시오.

## 6. 문제 해결

| 증상 | 원인 / 조치 |
|---|---|
| `No compiler is provided in this environment. Perhaps you are running on a JRE rather than a JDK?` | `JAVA_HOME`이 JRE를 가리킵니다. JDK 경로로 바꾸십시오. |
| `cannot find symbol ... List.of` 등 | Java 9+ API를 사용했습니다. Java 8 API로 바꾸십시오. |
| 한글 깨짐 (컴파일 경고 `unmappable character`) | 소스 파일을 UTF-8로 저장하십시오(pom은 UTF-8로 컴파일). |
| 이미지 변환 시 `Mermaid CLI not found: <찾아본 경로들>` | `target/mermaid`가 jar 옆에 있는지 확인하십시오(`-Dskip.mermaid=true`로 빌드하지 않았는지 포함). 직접 설치한 경우 `-Dmermaid.cli`로 `src/cli.js` 위치를 지정하십시오. |
| `Could not transfer artifact ... from/to repository-public` 또는 `repository-maven-depen` | settings.xml에 저장소 주소(5장)가 지정되어 있는지, `<server><id>` 계정이 맞는지 확인하십시오. |
| `Could not find artifact com.github.eirslett:frontend-maven-plugin...` | `repository-maven-depen`에 플러그인, 상위 pom(`frontend-plugins`), `frontend-plugin-core`가 있는지 확인하십시오. 다른 버전만 있다면 `-Dfrontend.plugin.version`으로 지정하십시오. |
| `Could not download Node.js: Got error code 401` (또는 404) | 401: `mermaid.node.serverId`와 settings.xml `<server>` 계정을 확인하십시오. 404: `nodeDownloadRoot` 아래에 `v22.17.1/node-v22.17.1-win-x64.zip`이 있는지 확인하십시오. |
| `npm error code E404` / `E401` (mermaid-cli 단계) | `mermaid.npm.registry` 주소와 `%USERPROFILE%\.npmrc` 계정을 확인하십시오. |
| Chrome 단계에서 다운로드 실패 | `mermaid.chrome.downloadBaseUrl` 아래에 `131.0.6778.204/win64/chrome-headless-shell-win64.zip`이 있는지, 익명 읽기가 허용되는지 확인하십시오. |
| 빌드 중 `install-node-and-npm` / `npm` 단계 실패 | 인터넷 또는 사내 미러 접속을 확인하십시오(2장 속성). `frontend-maven-plugin`을 찾지 못하면 저장소에 있는 버전을 `-Dfrontend.plugin.version`으로 지정하십시오. |
