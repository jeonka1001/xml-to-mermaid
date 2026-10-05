#!/usr/bin/env bash
# Single-file Windows executables (no installation, no extra files):
#   dist-win2/HansolXmlToImage2-standalone.exe          refactored version (src/main/java)
#   dist-win2/HansolXmlToImage2-single-standalone.exe   single-file version (../ars-xml-image-single)
#
# Each exe = launcher (mode=auto) + zip of the whole application folder (JRE 8, Node, mermaid-cli
# 11.6.0 + all npm dependencies, chrome-headless-shell, jar, settings) + trailer (launcher/payload.go).
# First run extracts to %LOCALAPPDATA%\hx2\<id>; later runs start immediately.
# No arguments: GUI. With arguments: CLI (same options as HansolXmlToImage2.exe).
#
# Reuses the application folder made by build-win2.sh (runs it when missing).
# Needs: bash, go, python3, JDK 1.8 (JAVA8_HOME), and what build-win2.sh needs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${OUT:-$ROOT/dist-win2}"
STAGE="${STAGE:-$OUT/hansol-xml-to-image2}"
# TARGET_GOOS/TARGET_GOARCH: only for testing the mechanism on another OS (default windows/amd64)
TARGET_GOOS="${TARGET_GOOS:-windows}"
TARGET_GOARCH="${TARGET_GOARCH:-amd64}"
SINGLE_DIR="${SINGLE_DIR:-$ROOT/../ars-xml-image-single}"
JAVA8_HOME="${JAVA8_HOME:-$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)}"
[ -x "$JAVA8_HOME/bin/javac" ] || { echo "JDK 1.8 을 찾지 못했습니다. JAVA8_HOME 을 지정하십시오." >&2; exit 1; }
[ -f "$SINGLE_DIR/HansolXmlToImage2.java" ] || { echo "단일 파일 버전 소스가 없습니다: $SINGLE_DIR (SINGLE_DIR 지정)" >&2; exit 1; }

if [ ! -d "$STAGE/runtime/jre" ] || [ ! -f "$STAGE/lib/hansol-xml-to-image2.jar" ]; then
  echo "[0/3] build-win2.sh (application folder)"
  "$ROOT/packaging/windows/build-win2.sh"
fi

W="$OUT/.standalone"
rm -rf "$W"
mkdir -p "$W/single-classes"

echo "[1/3] launcher ($TARGET_GOOS/$TARGET_GOARCH, mode=auto)"
( cd "$ROOT/packaging/windows/launcher" && CGO_ENABLED=0 GOOS=$TARGET_GOOS GOARCH=$TARGET_GOARCH \
    go build -trimpath -ldflags "-s -w -X main.mode=auto" -o "$W/launcher.exe" . )

echo "[2/3] single-file version jar ($("$JAVA8_HOME/bin/javac" -version 2>&1))"
"$JAVA8_HOME/bin/javac" -Xlint:all -encoding UTF-8 -d "$W/single-classes" "$SINGLE_DIR"/*.java
printf 'Main-Class: HansolXmlToImage2\n' > "$W/single.manifest"
"$JAVA8_HOME/bin/jar" cfm "$W/hansol-xml-to-image2-single.jar" "$W/single.manifest" -C "$W/single-classes" .

echo "[3/3] payload + exe"
python3 - "$STAGE" "$W" "$OUT" <<'PY'
import hashlib, os, struct, sys, zipfile

stage, work, out = sys.argv[1:4]
MAGIC, ID_LEN = b"HXI2PAY1", 32
SKIP_TOP = {"lib", "HansolXmlToImage2.exe", "HansolXmlToImage2-GUI.exe", "launcher.properties"}

def app_files():
    """(archive name, path) for the shared application folder; symlinks are not packed."""
    skipped = 0
    for top in sorted(os.listdir(stage)):
        if top in SKIP_TOP:
            continue
        p = os.path.join(stage, top)
        if os.path.islink(p):
            skipped += 1
            continue
        if os.path.isfile(p):
            yield top, p
            continue
        for d, dirs, files in os.walk(p):
            dirs[:] = sorted(x for x in dirs if not os.path.islink(os.path.join(d, x)))
            for f in sorted(files):
                fp = os.path.join(d, f)
                if os.path.islink(fp):
                    skipped += 1
                    continue
                yield os.path.relpath(fp, stage).replace(os.sep, "/"), fp
    if skipped:
        print(f"  skipped {skipped} symbolic link(s)")

def build(name, jar_src, jar_name, gui_main):
    zpath = os.path.join(work, name + ".zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        count = 0
        for arc, fp in app_files():
            z.write(fp, arc)
            count += 1
        z.write(jar_src, "lib/" + jar_name)
        z.writestr("launcher.properties",
                   f"# read by the launcher (packaging/windows/launcher)\njar=lib/{jar_name}\ngui.main.class={gui_main}\n")
    h = hashlib.sha256()
    with open(zpath, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    pid = "hx2-" + h.hexdigest()[:24]  # content hash: every build gets its own folder
    launcher = os.path.join(work, "launcher.exe")
    exe = os.path.join(out, name + ".exe")
    offset = os.path.getsize(launcher)
    with open(exe, "wb") as o:
        for src in (launcher, zpath):
            with open(src, "rb") as f:
                while True:
                    b = f.read(1 << 20)
                    if not b:
                        break
                    o.write(b)
        o.write(pid.encode("ascii").ljust(ID_LEN, b" ") + struct.pack("<Q", offset) + MAGIC)
    os.chmod(exe, 0o755)
    print(f"  {os.path.basename(exe)}: {count + 2} files, {os.path.getsize(exe) / 1e6:.0f} MB, id {pid}")

build("HansolXmlToImage2-standalone", os.path.join(stage, "lib", "hansol-xml-to-image2.jar"),
      "hansol-xml-to-image2.jar", "hansol.xml2mermaid.ui.ConverterApp")
build("HansolXmlToImage2-single-standalone", os.path.join(work, "hansol-xml-to-image2-single.jar"),
      "hansol-xml-to-image2-single.jar", "HansolXmlToImage2Gui")
PY
rm -rf "$W"
echo "완료: $OUT/HansolXmlToImage2-standalone.exe, $OUT/HansolXmlToImage2-single-standalone.exe"
