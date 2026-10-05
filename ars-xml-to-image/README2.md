# HansolXmlToImage2 개발 가이드

XML → Mermaid flowchart(`.mmd`/`.md`) 및 이미지(`.png`/`.jpg`/`.svg`/`.pdf`) 변환기. CLI와 GUI(Swing) 제공.
JDK 1.8, Mermaid 11.6 (mermaid-cli 11.6.0) 기준. 외부 Java 라이브러리 없음.

## 빌드 방법 (간단)

`src/main/java` 하나를 빌드하면 jar 하나에 CLI와 GUI가 함께 들어갑니다. JDK 8이 필요합니다.

| 방법 | 명령 | 결과 |
|---|---|---|
| 스크립트 (Windows) | `build.bat` | `build\hansol-xml-to-image2.jar` |
| 스크립트 (macOS/Linux) | `./build.sh` | `build/hansol-xml-to-image2.jar` |
| Maven | `mvn clean package` | `target/hansol-xml-to-image2.jar` (자세한 내용은 README3.md) |

JDK 위치는 `JAVA8_HOME`으로 지정합니다(없으면 PATH의 `javac`). 예: `set JAVA8_HOME=C:\Java\jdk1.8.0_xxx`

### HansolXmlToImage2 (CLI) 실행

```bat
java -jar build\hansol-xml-to-image2.jar --rules node-types.properties sample.xml sample.md
java -jar build\hansol-xml-to-image2.jar --rules node-types.properties sample.xml sample.png
java -jar build\hansol-xml-to-image2.jar --help
```

### GUI 실행

같은 jar의 GUI 진입점 `hansol.xml2mermaid.ui.ConverterApp`를 지정합니다.

```bat
java -Dhansol.rules=node-types.properties -Dmermaid.config=mermaid-config.json ^
     -cp build\hansol-xml-to-image2.jar hansol.xml2mermaid.ui.ConverterApp
```

- MD만 만들 때는 Java만 있으면 됩니다.
- 이미지(PNG/JPG/SVG/PDF)는 Node.js와 mermaid-cli 11.6.0이 필요합니다. **Maven으로 빌드하면 `target/mermaid`에 자동 설치되고 옵션 없이 쓰입니다**(README3.md 2장).
  `build.bat`/`build.sh`로 빌드했거나 직접 설치한 것을 쓰려면 아래 옵션으로 지정합니다.
  `-Dmermaid.cli=...\node_modules\@mermaid-js\mermaid-cli\src\cli.js -Dnode.executable=...\node.exe`

### Windows 배포본 (설치 불필요)

`packaging/windows/build-win2.sh`(macOS/Linux에서 실행)로 `dist-win2/hansol-xml-to-image2-win-x64.zip`을 만듭니다.
JRE 8, Node, mermaid-cli, Chrome이 함께 들어 있습니다.

| 실행 파일 | 용도 |
|---|---|
| `HansolXmlToImage2-GUI.exe` | GUI (더블클릭) |
| `HansolXmlToImage2.exe` | CLI (명령 프롬프트) |

### 단일 exe (exe 파일 하나로 실행)

`packaging/windows/build-standalone.sh`(macOS/Linux, 인터넷 필요)로 **파일 하나짜리 exe**를 만듭니다.
이 exe 안에 JRE 8, Node, mermaid-cli 11.6.0과 의존 패키지 전체, Chrome, jar, 설정 파일이 모두 들어 있습니다.

| 결과 (`dist-win2/`) | 내용 |
|---|---|
| `HansolXmlToImage2-standalone.exe` | 리팩터링 버전 (약 245MB) |
| `HansolXmlToImage2-single-standalone.exe` | 단일 파일 버전, `../ars-xml-image-single` 소스 사용 (약 245MB) |

```bat
HansolXmlToImage2-standalone.exe                        :: 인자 없음(더블클릭): GUI
HansolXmlToImage2-standalone.exe sample.xml sample.png  :: 인자 있음: CLI
```

- **처음 실행할 때만** 런타임을 `%LOCALAPPDATA%\hx2\<빌드ID>\`에 풉니다. 약 650MB이고 수십 초가 걸립니다. 다음 실행부터는 바로 시작합니다.
  빌드할 때마다 빌드 ID가 바뀌므로 이전 버전과 섞이지 않습니다. 오래된 `hx2\` 하위 폴더는 지워도 됩니다.
- 압축을 풀 위치는 환경 변수 `HX2_HOME`으로 바꿀 수 있습니다(예: `set HX2_HOME=D:\hx2`).
- 동작 환경은 **Windows 10/11 64비트**입니다. Java, Node, Chrome을 따로 설치할 필요가 없고, 인터넷이나 넥서스에 접속하지 않습니다.
- 압축을 풀 때 상위 폴더(`..`)나 절대 경로가 들어 있는 항목은 거부합니다(zip slip 방지).
  여러 개를 동시에 처음 실행해도 하나의 폴더만 완성됩니다.
- 서명되지 않은 exe라서 처음 실행할 때 SmartScreen이나 백신 경고가 뜰 수 있습니다.

## GUI 사용법

1. **입력**: [파일 추가](여러 개 선택), [폴더 추가](하위 폴더 포함 여부 선택), 또는 목록에 드래그 앤 드롭합니다.
2. **출력 폴더**: 입력 파일과 같은 폴더 또는 지정 폴더를 고릅니다.
3. **형식**: MD는 항상 생성되고, PNG/JPG/SVG/PDF는 여러 개를 고를 수 있습니다.
4. **기존 파일**: 덮어쓰기 / 건너뛰기 / 새 이름으로 저장(`이름 (2).md`) 중에서 고릅니다.
   한 번에 변환하는 파일 중 이름이 같은 것(다른 폴더의 `a.xml` 두 개 등)은 정책과 관계없이 번호가 붙습니다.
5. **결과**: `<출력 폴더>\<파일명>.md`, `<파일명>.png` 등이 생기고, 로그는 `<출력 폴더>\logs\<파일명>.convert.log`에 남습니다.
   목록에서 행을 더블클릭하면 그 파일의 로그가 열립니다.
6. 한 파일이 실패해도 나머지는 계속 처리됩니다. [취소]를 누르면 진행 중인 렌더링을 중단합니다.

마지막으로 쓴 설정은 다음 실행 때 복원됩니다(Windows 레지스트리 `HKCU\Software\JavaSoft\Prefs`).

## 처리 흐름과 패키지

```
input.xml
  │ reader   DiagramXmlReader → XmlElement 트리        (XML 파싱만, 규칙 모름)
  ▼
  │ convert  DiagramConverter                         (Diagram/Nodes/Node, Links/Link 구조 순회)
  │ rule     RuleRegistry → NodeRule / LinkRule        (요소별 변환 규칙, 다형성)
  ▼
Flowchart (model)                                     (출력 형식과 무관한 결과)
  │ output   OutputFormat → OutputWriter               (mmd / md / 이미지, 다형성)
  ▼
output.mmd | .md | .png/.jpg/.svg/.pdf  +  <출력>.convert.log

app   XmlConverter(파일 1개: reader→convert)  BatchConverter(여러 파일, GUI용)
CLI   HansolXmlToImage2 ─ XmlConverter ─ OutputWriter
GUI   ui.ConverterApp → MainFrame ─ BatchConverter ─ XmlConverter ─ OutputWriter
```

| 패키지 | 클래스 | 수정할 때 |
|---|---|---|
| `hansol.xml2mermaid` | `HansolXmlToImage2`(CLI 진입점), `Options`(명령행) | CLI 옵션 추가 |
| `app` | `XmlConverter`, `BatchConverter`, `BatchSettings`, `ConflictPolicy`, `FileResult`, `BatchListener` | 일괄 처리, 파일명/충돌 정책 |
| `ui` | `ConverterApp`(GUI 진입점), `MainFrame`, `FileTableModel`, `UiLogHandler`, `UiSettings` | 화면 변경 |
| `reader` | `DiagramXmlReader`, `XmlElement` | XML 읽기 방식 변경 (보통 수정 불필요) |
| `rule` | `NodeRule`, `AbstractNodeRule`, `StartNodeRule`, `ScriptNodeRule`, `ShapeNodeRule`, `UnknownNodeRule`, `LinkRule`, `DefaultLinkRule`, `RuleContext`, `RuleRegistry` | **변환 규칙 추가/변경** |
| `convert` | `DiagramConverter` | Diagram 최상위 구조(섹션) 규칙 변경 |
| `model` | `Flowchart`, `FlowNode`, `FlowLink`, `Shape` | 도형 종류 추가 |
| `output` | `OutputWriter`, `MermaidOutput`, `MarkdownOutput`, `ImageOutput`, `JpegOutput`, `MermaidSyntax`, `ImageRenderer`, `ProcessTree`, `OutputFormat`, `OutputRequest` | 출력 형식 추가/문법 변경 |
| `report` | `Report`, `Category` | 감지 결과 분류/요약 방식 |
| `log` | `Logs`, `LogSetup`, `LineFormatter`, `FlushingHandler` | 로그 형식 |

## 규칙 구조 (rule 패키지)

```
NodeRule (interface)            nodeType(), knownAttributes(), knownChildren(), convert()
 └─ AbstractNodeRule            기본 동작: 라벨 = 직속 Text, 도형 1개, 허용 구조 Id/NodeType + Text
     ├─ StartNodeRule           StartNode → stadium     (확인된 규칙)
     ├─ ScriptNodeRule          ScriptNode → rect       (확인된 규칙)
     ├─ ShapeNodeRule           규칙 파일 nodetype.X=shape 로 생성
     └─ UnknownNodeRule         규칙 없는 NodeType: UNKNOWN 보고 + fallback 도형
LinkRule (interface)
 └─ DefaultLinkRule             Origin@Id → Destination@Id, 라벨 = Text
RuleRegistry                    NodeType → NodeRule 선택, 규칙 파일 로드, ignore 목록
RuleContext                     규칙이 쓰는 공통 기능: 위치, unknown/notice 보고, 구조 검사, Text 읽기
```

`DiagramConverter`는 Node마다 `RuleRegistry.nodeRule(NodeType)`로 규칙을 고른 뒤,
그 규칙의 `knownAttributes()`/`knownChildren()`으로 구조를 검사하고 `convert()`를 호출합니다.
허용 구조가 규칙마다 다르므로, 새 NodeType에 고유한 자식 요소가 있어도 그 규칙에서만 허용할 수 있습니다.

## 새 규칙 추가 방법

로그(`<출력>.convert.log`) 끝의 `[UNKNOWN summary]`가 추가할 규칙 목록입니다.

**1) 도형만 지정 — 규칙 파일 (재컴파일 불필요)**

```properties
nodetype.MenuNode=diamond
```

**2) 라벨/허용 구조까지 바꿔야 할 때 — 규칙 클래스**

```java
package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.Shape;
import hansol.xml2mermaid.reader.XmlElement;
import java.util.Set;

public final class MenuNodeRule extends AbstractNodeRule {
    public MenuNodeRule() { super("MenuNode", Shape.DIAMOND); }

    // Node/Timeout 을 UNKNOWN 으로 보고하지 않음
    @Override public Set<String> knownChildren() { return set("Text", "Timeout"); }

    // 라벨에 Timeout 표시
    @Override protected String label(XmlElement node, RuleContext ctx) {
        XmlElement t = ctx.single(node, "Timeout");
        return super.label(node, ctx) + (t == null ? "" : " (timeout " + t.textContent() + "s)");
    }
}
```

`RuleRegistry.builtIns()`에 한 줄 등록합니다.

```java
r.register(new MenuNodeRule());
```

같은 NodeType이 규칙 파일에도 있으면 규칙 파일이 우선합니다(로그에 교체 사실이 기록됨).

**3) 무시할 요소/속성 — 규칙 파일**

```properties
ignore.elements=CustomProperties,Script,Points
ignore.attributes=Node@X,Node@Y,*@Color
```

**4) 출력 형식 추가** — `OutputWriter` 구현 클래스를 만들고 `OutputFormat`에 값과 `createWriter()` 분기를 추가합니다.
GUI에도 노출하려면 `ui/MainFrame`의 `IMAGE_FORMATS`에 추가합니다.
(예: JPG는 mermaid-cli가 지원하지 않아 `JpegOutput`이 PNG를 렌더링한 뒤 Java `ImageIO`로 변환합니다.)

## 로그 분류

| 분류 | 의미 | `--strict` |
|---|---|---|
| UNKNOWN | 확인된 규칙 밖의 구조 (처음 보는 NodeType/요소/속성/Version) | 실패(종료 코드 3) |
| NOTICE | 검토할 데이터 상태 (빈 Text, 중복 Link Id 등) | 영향 없음 |
| IGNORED | 규칙 파일 `ignore.*`로 의도적으로 무시 | 영향 없음 |

XML의 라벨/스크립트 값은 로그에 남기지 않고 요소 이름과 Id 위치만 남깁니다.

## Java 8 호환 주의
- `var`, `List.of`, `String.isBlank/repeat`, `Files.readString` 등 Java 9+ API를 쓰지 마십시오.
- `build.bat`/`build.sh`는 JDK 8 javac로 컴파일하므로 Java 9+ API를 쓰면 빌드가 실패합니다.
- Mermaid 문법은 11.6에서 렌더링을 확인한 classic 도형 문법(`Shape`)만 사용합니다.
