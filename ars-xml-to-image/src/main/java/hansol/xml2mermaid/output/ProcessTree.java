package hansol.xml2mermaid.output;

import static hansol.xml2mermaid.log.Logs.LOG;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Terminates the Mermaid CLI and its browser children after a timeout. */
final class ProcessTree {
    private ProcessTree() {}

    static void kill(Process process, Path marker, Path log) {
        try {
            if (!killWithProcessHandle(process) && isWindows()) killWithTaskkill(marker, log);
        } catch (Exception e) {
            LOG.warning("Could not stop child processes; a browser process may remain: " + e);
        }
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Java 9+ ProcessHandle via reflection, since this source targets Java 8. */
    private static boolean killWithProcessHandle(Process process) throws Exception {
        Method toHandle;
        try {
            toHandle = Process.class.getMethod("toHandle");
        } catch (NoSuchMethodException java8) {
            return false;
        }
        Class<?> handleType = Class.forName("java.lang.ProcessHandle");
        Object handle = toHandle.invoke(process);
        Object descendants = handleType.getMethod("descendants").invoke(handle);
        Method destroy = handleType.getMethod("destroyForcibly");
        for (Object child : ((java.util.stream.Stream<?>) descendants).toArray()) destroy.invoke(child);
        return true;
    }

    /**
     * Java 8 on Windows: find the node process by the unique staging path in its
     * command line, then end its tree with taskkill /T. The path is passed through
     * an environment variable, never interpolated into the PowerShell script.
     */
    private static void killWithTaskkill(Path marker, Path log) throws Exception {
        Path pidFile = marker.resolve("render-pids.txt");
        ProcessBuilder ps = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
            "-Command", "$m = $env:HANSOL_RENDER_MARK; Get-CimInstance Win32_Process | "
            + "Where-Object { $_.CommandLine -and $_.CommandLine.Contains($m) -and $_.ProcessId -ne $PID }"
            + " | ForEach-Object { $_.ProcessId }");
        ps.environment().put("HANSOL_RENDER_MARK", marker.toString());
        ps.redirectErrorStream(true);
        ps.redirectOutput(pidFile.toFile());
        Process find = ps.start();
        if (!find.waitFor(30, TimeUnit.SECONDS)) {
            find.destroyForcibly();
            throw new IOException("process lookup timed out");
        }
        for (String line : Files.readAllLines(pidFile, Charset.defaultCharset())) {
            String pid = line.trim();
            if (!pid.matches("\\d+")) continue;
            ProcessBuilder kill = new ProcessBuilder("taskkill", "/PID", pid, "/T", "/F");
            kill.redirectErrorStream(true);
            kill.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            Process k = kill.start();
            if (!k.waitFor(15, TimeUnit.SECONDS)) k.destroyForcibly();
        }
        Files.deleteIfExists(pidFile);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
