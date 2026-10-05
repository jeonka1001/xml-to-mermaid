package hansol.xml2mermaid.output;

import static hansol.xml2mermaid.log.Logs.LOG;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs Mermaid CLI (node cli.js) to render a .mmd file. One instance renders
 * sequentially; cancel() may be called from another thread.
 * System properties: -Dmermaid.cli -Dnode.executable -Dpuppeteer.cache.dir
 * Without them, the runtime installed by "mvn package" next to the jar is used
 * (<jar folder>/mermaid: node, node_modules, chrome), then ./node_modules and node on PATH.
 */
public final class ImageRenderer {
    private final Path cli, mermaidConfig, puppeteerConfig;
    private final String node, puppeteerCacheDir;
    private final long timeoutSeconds;
    private final int scale, jpegQuality;

    private volatile boolean cancelled;
    private volatile Process current;
    private volatile Path currentWork, currentLog;

    private ImageRenderer(Path cli, String node, Path mermaidConfig, Path puppeteerConfig,
                          String puppeteerCacheDir, long timeoutSeconds, int scale, int jpegQuality) {
        this.cli = cli; this.node = node; this.mermaidConfig = mermaidConfig;
        this.puppeteerConfig = puppeteerConfig; this.puppeteerCacheDir = puppeteerCacheDir;
        this.timeoutSeconds = timeoutSeconds; this.scale = scale; this.jpegQuality = jpegQuality;
    }

    public static ImageRenderer create(Path mermaidConfig, Path puppeteerConfig, long timeoutSeconds) {
        return create(mermaidConfig, puppeteerConfig, timeoutSeconds, 1, 90);
    }

    /**
     * Validates the rendering environment, so a run fails before any output is written.
     * scale: PNG/JPG device scale factor 1..10 (Mermaid CLI -s). jpegQuality: 1..100.
     */
    public static ImageRenderer create(Path mermaidConfig, Path puppeteerConfig, long timeoutSeconds,
                                       int scale, int jpegQuality) {
        Path bundled = bundledRuntimeDir();
        Path cli = findCli(bundled);
        logMermaidVersions(cli);
        String node = System.getProperty("node.executable");
        if (node == null && bundled != null) {
            Path bundledNode = bundled.resolve("node").resolve(isWindows() ? "node.exe" : "node");
            if (Files.isRegularFile(bundledNode)) node = bundledNode.toString();
        }
        String cacheDir = System.getProperty("puppeteer.cache.dir");
        if (cacheDir == null && bundled != null && Files.isDirectory(bundled.resolve("chrome"))) {
            cacheDir = bundled.resolve("chrome").toString();
        }
        for (Path p : Arrays.asList(mermaidConfig, puppeteerConfig)) {
            if (p != null && !Files.isRegularFile(p)) {
                throw new IllegalArgumentException("Config file not found: " + p);
            }
        }
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("Timeout must be positive");
        if (scale < 1 || scale > 10) throw new IllegalArgumentException("Scale must be 1..10");
        if (jpegQuality < 1 || jpegQuality > 100) throw new IllegalArgumentException("JPEG quality must be 1..100");
        return new ImageRenderer(cli, node != null ? node : "node",
            mermaidConfig, puppeteerConfig, cacheDir, timeoutSeconds, scale, jpegQuality);
    }

    private static final String CLI_PATH = "node_modules/@mermaid-js/mermaid-cli/src/cli.js";

    /** -Dmermaid.cli, else <jar folder>/mermaid/node_modules/..., else ./node_modules/... */
    private static Path findCli(Path bundled) {
        String property = System.getProperty("mermaid.cli");
        List<Path> candidates = new ArrayList<Path>();
        if (property != null) {
            candidates.add(Paths.get(property));
        } else {
            if (bundled != null) candidates.add(bundled.resolve(CLI_PATH));
            candidates.add(Paths.get(CLI_PATH));
        }
        StringBuilder searched = new StringBuilder();
        for (Path c : candidates) {
            Path abs = c.toAbsolutePath().normalize();
            if (Files.isRegularFile(abs)) return abs;
            searched.append(searched.length() == 0 ? "" : ", ").append(abs);
        }
        throw new IllegalArgumentException("Mermaid CLI not found: " + searched
            + "; build with mvn package (installs it next to the jar), run npm install"
            + " @mermaid-js/mermaid-cli@11.6.0, or set -Dmermaid.cli");
    }

    private static final String TESTED_MERMAID = "11.6.0";

    /** Logs the mermaid / mermaid-cli versions used by cli.js; warns when they are not the tested ones. */
    private static void logMermaidVersions(Path cli) {
        Path cliPackage = cli.getParent() == null ? null : cli.getParent().getParent(); // .../@mermaid-js/mermaid-cli
        if (cliPackage == null) return;
        // Node resolution order: mermaid-cli's own node_modules first, then the enclosing node_modules.
        Path nested = cliPackage.resolve("node_modules").resolve("mermaid").resolve("package.json");
        Path scope = cliPackage.getParent();
        Path top = scope == null || scope.getParent() == null ? nested
            : scope.getParent().resolve("mermaid").resolve("package.json");
        String mermaid = packageVersion(Files.isRegularFile(nested) ? nested : top);
        String cliVersion = packageVersion(cliPackage.resolve("package.json"));
        LOG.info("Mermaid " + mermaid + ", mermaid-cli " + cliVersion);
        if (!TESTED_MERMAID.equals(mermaid) || !TESTED_MERMAID.equals(cliVersion)) {
            LOG.warning("Tested with mermaid " + TESTED_MERMAID + " and mermaid-cli " + TESTED_MERMAID
                + "; found mermaid " + mermaid + ", mermaid-cli " + cliVersion);
        }
    }

    private static String packageVersion(Path packageJson) {
        try {
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(new String(Files.readAllBytes(packageJson), StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : "unknown";
        } catch (IOException | RuntimeException e) {
            return "unknown";
        }
    }

    /** <folder of the jar (or of target/classes)>/mermaid, where mvn package installs the runtime. */
    static Path bundledRuntimeDir() {
        try {
            Path location = Paths.get(ImageRenderer.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path base = location.getParent();
            return base == null ? null : base.resolve("mermaid");
        } catch (Exception e) {
            return null; // e.g. no code source: fall back to properties and PATH
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    int jpegQuality() { return jpegQuality; }

    /** Stops the running render (and its browser) and makes later renders fail with CancellationException. */
    public void cancel() {
        cancelled = true;
        Process p = current;
        if (p != null) ProcessTree.kill(p, currentWork, currentLog);
    }

    void render(Path mmd, Path output, Path log, OutputFormat format, boolean overwrite) throws Exception {
        if (cancelled) throw new CancellationException("Rendering cancelled");
        // Render to a private staging directory. Publish only a completed file.
        Path work = Files.createTempDirectory(output.getParent(), ".mermaid-render-");
        Path staged = work.resolve(output.getFileName());
        Process process = null;
        try {
            if (overwrite) Files.deleteIfExists(log);
            Files.createFile(log);
            List<String> cmd = new ArrayList<String>(Arrays.asList(node, cli.toString(),
                "-i", mmd.toString(), "-o", staged.toString(), "-b", "white"));
            if (mermaidConfig != null) { cmd.add("-c"); cmd.add(mermaidConfig.toString()); }
            if (puppeteerConfig != null) { cmd.add("-p"); cmd.add(puppeteerConfig.toString()); }
            if (format == OutputFormat.PNG && scale != 1) { cmd.add("-s"); cmd.add(String.valueOf(scale)); }
            if (format == OutputFormat.PDF) cmd.add("-f"); // fit PDF page to the diagram
            LOG.info("Rendering with Mermaid CLI: " + cli + " (node " + node
                + (mermaidConfig == null ? "" : ", config " + mermaidConfig) + ")");
            // Direct argv execution works on Windows and Linux, without cmd/sh.
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (puppeteerCacheDir != null) pb.environment().put("PUPPETEER_CACHE_DIR", puppeteerCacheDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            currentWork = work;
            currentLog = log;
            process = pb.start();
            current = process;
            if (cancelled) ProcessTree.kill(process, work, log); // cancel() ran before current was set
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                ProcessTree.kill(process, work, log);
                throw new IOException("Rendering timed out after " + timeoutSeconds + "s. Log: " + log);
            }
            if (cancelled) throw new CancellationException("Rendering cancelled");
            if (process.exitValue() != 0) {
                throw new IOException("Mermaid exit code " + process.exitValue() + ". Log: " + log);
            }
            if (!Files.isRegularFile(staged) || Files.size(staged) == 0) {
                throw new IOException("Mermaid produced no output. Log: " + log);
            }
            if (overwrite) Files.move(staged, output, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(staged, output); // protect existing output
        } finally {
            current = null;
            if (process != null && process.isAlive()) process.destroyForcibly();
            try {
                Files.deleteIfExists(staged);
                Files.deleteIfExists(work);
            } catch (IOException cleanup) {
                LOG.warning("Temporary files remain: " + work);
            }
        }
    }
}
