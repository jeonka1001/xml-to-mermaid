// Launcher for the Windows distributions. One source, three executables:
//
//	HansolXmlToImage2.exe             (mode=cli)  console, java.exe -jar ... <args>
//	HansolXmlToImage2-GUI.exe         (mode=gui)  -H windowsgui, javaw.exe ... <GUI main class>
//	HansolXmlToImage2-standalone.exe  (mode=auto) single file: the application folder is appended
//	                                  as a payload (payload.go); no arguments starts the GUI,
//	                                  arguments run the CLI.
//
// Every bundled runtime path is passed as a JVM property, so nothing has to be installed or
// put on PATH. CLI arguments are forwarded unchanged and the exit code is propagated.
// launcher.properties in the application folder may set "jar" and "gui.main.class".
// Extra JVM options: set XML2IMG_JAVA_OPTS=-Drender.timeout.seconds=300
package main

import (
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
)

// Set at build time: go build -ldflags "-X main.mode=gui"
var mode = "cli"

const (
	defaultJar          = "lib/hansol-xml-to-image2.jar"
	defaultGuiMainClass = "hansol.xml2mermaid.ui.ConverterApp"
)

func main() {
	os.Exit(run())
}

func run() int {
	args := os.Args[1:]
	gui := mode == "gui" || (mode == "auto" && len(args) == 0)
	self, err := os.Executable()
	if err != nil {
		fail(gui, "Cannot locate the launcher executable: "+err.Error())
		return 1
	}
	if resolved, err := filepath.EvalSymlinks(self); err == nil {
		self = resolved
	}

	app := filepath.Dir(self)
	var progress io.Writer // first-run extraction progress; a windowsgui build has no console
	if mode != "gui" {
		progress = os.Stderr
	}
	if extracted, err := standaloneAppDir(self, progress); err != nil {
		fail(gui, "Cannot prepare the bundled runtime: "+err.Error())
		return 1
	} else if extracted != "" {
		app = extracted
	}

	exe := ""
	if runtime.GOOS == "windows" {
		exe = ".exe"
	}
	path := func(parts ...string) string { return filepath.Join(append([]string{app}, parts...)...) }
	props := readLauncherProperties(path("launcher.properties"))
	jar := path(filepath.FromSlash(propOr(props, "jar", defaultJar)))

	javaName := "java"
	if gui && runtime.GOOS == "windows" {
		javaName = "javaw" // no console window
	}
	java := path("runtime", "jre", "bin", javaName+exe)
	for _, required := range []string{java, jar} {
		if _, err := os.Stat(required); err != nil {
			fail(gui, "Missing bundled file:\n"+required+"\n\nExtract the whole zip and run the exe from that folder.")
			return 1
		}
	}

	jvm := strings.Fields(os.Getenv("XML2IMG_JAVA_OPTS"))
	jvm = append(jvm,
		"-Dnode.executable="+path("runtime", "node", "node"+exe),
		"-Dmermaid.cli="+path("runtime", "mermaid", "node_modules", "@mermaid-js", "mermaid-cli", "src", "cli.js"),
		"-Dpuppeteer.cache.dir="+path("runtime", "chrome"),
		"-Dhansol.rules="+path("node-types.properties"),
		"-Dmermaid.config="+path("mermaid-config.json"))
	if gui {
		jvm = append(jvm, "-cp", jar, propOr(props, "gui.main.class", defaultGuiMainClass))
	} else {
		jvm = append(jvm, "-jar", jar)
	}
	jvm = append(jvm, args...)

	cmd := exec.Command(java, jvm...)
	if gui {
		// The window outlives the launcher; Java reports its own errors in the window.
		if err := cmd.Start(); err != nil {
			fail(gui, "Cannot start Java:\n"+err.Error())
			return 1
		}
		if mode == "auto" {
			freeConsole() // double-clicked: close the console window the system opened
		}
		return 0
	}

	cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	// Ctrl+C reaches java directly; the launcher waits for its exit code.
	signal.Ignore(os.Interrupt)
	code := 0
	if err := cmd.Run(); err != nil {
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			code = exitErr.ExitCode()
		} else {
			fail(gui, "Cannot start Java: "+err.Error())
			code = 1
		}
	}
	// Started by double-click (no arguments): keep the usage text visible.
	if mode == "cli" && len(args) == 0 {
		fmt.Fprint(os.Stderr, "\nRun from a command prompt: HansolXmlToImage2.exe input.xml output.png\n"+
			"For the window version run HansolXmlToImage2-GUI.exe\nPress Enter to close...")
		fmt.Scanln()
	}
	return code
}

func propOr(props map[string]string, key, def string) string {
	if v := props[key]; v != "" {
		return v
	}
	return def
}

func fail(gui bool, message string) {
	if gui {
		showError(message)
		return
	}
	fmt.Fprintln(os.Stderr, "launcher: "+strings.Replace(message, "\n", " ", -1))
}
