package hansol.xml2mermaid.app;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.log.LogSetup;
import hansol.xml2mermaid.output.ImageRenderer;
import hansol.xml2mermaid.output.OutputFormat;
import hansol.xml2mermaid.output.OutputRequest;
import hansol.xml2mermaid.report.Category;
import hansol.xml2mermaid.rule.RuleRegistry;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * Converts several XML files sequentially. For input "a.xml" it writes a.md and
 * the selected images (a.png, a.jpg, ...) to the output folder, and the logs to
 * <output folder>/logs/a.convert.log and a.<ext>.render.log.
 * A failing file does not stop the batch.
 */
public final class BatchConverter {
    private final BatchSettings settings;
    private volatile boolean cancelled;
    private volatile ImageRenderer renderer;

    public BatchConverter(BatchSettings settings) {
        if (settings.formats.isEmpty()) throw new IllegalArgumentException("No output format selected");
        this.settings = settings.copy();
    }

    /** Stops the running render; remaining files are reported as CANCELLED. Thread-safe. */
    public void cancel() {
        cancelled = true;
        ImageRenderer r = renderer;
        if (r != null) r.cancel();
    }

    public void run(List<Path> inputs, BatchListener listener) {
        RuleRegistry rules;
        try {
            rules = RuleRegistry.load(settings.rulesFile);
            LOG.info("Rules: " + rules.source());
            if (settings.hasImageFormat()) {
                renderer = ImageRenderer.create(settings.mermaidConfig, settings.puppeteerConfig,
                    settings.timeoutSeconds, settings.scale, settings.jpegQuality);
            }
        } catch (Exception e) {
            LOG.severe("Cannot start: " + e.getMessage());
            for (int i = 0; i < inputs.size(); i++) {
                listener.finished(i, FileResult.of(FileResult.Status.FAILED, null, e.getMessage()));
            }
            return;
        }
        Set<String> used = new HashSet<String>();
        for (int i = 0; i < inputs.size(); i++) {
            if (cancelled) {
                listener.finished(i, FileResult.of(FileResult.Status.CANCELLED, null, "cancelled"));
                continue;
            }
            listener.started(i);
            listener.finished(i, convertOne(inputs.get(i).toAbsolutePath().normalize(), rules, used));
        }
    }

    private FileResult convertOne(Path input, RuleRegistry rules, Set<String> used) {
        Path dir = settings.outputDir != null ? settings.outputDir : input.getParent();
        List<OutputFormat> formats = settings.orderedFormats();
        String base;
        Path logs = dir.resolve("logs");
        try {
            Files.createDirectories(logs);
            base = chooseBaseName(dir, baseName(input), formats, used);
        } catch (Exception e) {
            LOG.severe(input.getFileName() + ": " + e.getMessage());
            return FileResult.of(FileResult.Status.FAILED, null, e.getMessage());
        }
        if (base == null) {
            LOG.info(input.getFileName() + ": skipped, output already exists in " + dir);
            return FileResult.of(FileResult.Status.SKIPPED, null, "output already exists");
        }
        used.add(key(dir, base));

        Path convertLog = logs.resolve(base + ".convert.log");
        List<Handler> handlers;
        try {
            handlers = LogSetup.install(convertLog, true, settings.verbose, false);
        } catch (IOException e) {
            return FileResult.of(FileResult.Status.FAILED, null, "cannot create log: " + e.getMessage());
        }
        List<Path> outputs = new ArrayList<Path>();
        Path temp = null;
        int nodes = 0, links = 0, unknown = 0;
        try {
            LOG.info("Input: " + input);
            XmlConverter.Result r = XmlConverter.convert(input, rules, settings.verbose);
            nodes = r.chart.nodes().size();
            links = r.chart.links().size();
            unknown = r.report.kinds(Category.UNKNOWN);
            if (settings.strict && r.report.hasUnknownRules()) {
                LOG.severe("Unknown rules found and strict is set; no output written");
                return new FileResult(FileResult.Status.STRICT_FAILED, nodes, links, unknown, outputs,
                    convertLog, unknown + " unknown rule kind(s)");
            }
            Path mmd;
            if (settings.keepMermaid) {
                mmd = dir.resolve(base + ".mmd");
            } else {
                temp = Files.createTempDirectory(dir, ".hansol-");
                mmd = temp.resolve(base + ".mmd");
            }
            String source = input.getFileName().toString();
            for (OutputFormat f : formats) {
                if (cancelled) throw new CancellationException("cancelled");
                Path out = dir.resolve(base + "." + f.extension);
                OutputRequest request = new OutputRequest(out, mmd, convertLog,
                    logs.resolve(base + "." + f.extension + ".render.log"),
                    settings.direction, settings.showId, true, source);
                f.createWriter(renderer).write(r.chart, request);
                outputs.add(out);
            }
            if (settings.keepMermaid && !Files.exists(mmd)) {
                OutputFormat.MMD.createWriter(null).write(r.chart, new OutputRequest(mmd, mmd, convertLog,
                    null, settings.direction, settings.showId, true, source));
            }
            if (settings.keepMermaid) outputs.add(mmd);
            LOG.info("Convert log: " + convertLog);
            return new FileResult(unknown > 0 ? FileResult.Status.DONE_WITH_UNKNOWN : FileResult.Status.DONE,
                nodes, links, unknown, outputs, convertLog, unknown > 0 ? unknown + " unknown rule kind(s)" : "");
        } catch (CancellationException e) {
            LOG.warning("Cancelled");
            return new FileResult(FileResult.Status.CANCELLED, nodes, links, unknown, outputs, convertLog,
                "cancelled");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Conversion failed: " + e.getMessage(), e);
            return new FileResult(FileResult.Status.FAILED, nodes, links, unknown, outputs, convertLog,
                e.getMessage());
        } finally {
            LogSetup.uninstall(handlers);
            deleteQuietly(temp);
        }
    }

    /**
     * Base name for the outputs, or null to skip. A name already written in this
     * batch is never reused, whatever the policy (two inputs named a.xml).
     */
    private String chooseBaseName(Path dir, String base, List<OutputFormat> formats, Set<String> used) {
        for (int n = 1; n < 1000; n++) {
            String candidate = n == 1 ? base : base + " (" + n + ")";
            if (used.contains(key(dir, candidate))) continue;
            if (!anyExists(dir, candidate, formats)) return candidate;
            switch (settings.conflict) {
                case OVERWRITE: return candidate;
                case SKIP: return null;
                default: break; // RENAME: try the next number
            }
        }
        throw new IllegalStateException("No free output name for " + base);
    }

    private boolean anyExists(Path dir, String base, List<OutputFormat> formats) {
        for (OutputFormat f : formats) if (Files.exists(dir.resolve(base + "." + f.extension))) return true;
        return settings.keepMermaid && Files.exists(dir.resolve(base + ".mmd"));
    }

    // Windows file names are case-insensitive.
    private static String key(Path dir, String base) {
        return dir.resolve(base).toString().toLowerCase(Locale.ROOT);
    }

    static String baseName(Path input) {
        String name = input.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                for (Path f : files) Files.deleteIfExists(f);
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            LOG.warning("Temporary files remain: " + dir);
        }
    }
}
