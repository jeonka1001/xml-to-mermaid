package hansol.xml2mermaid;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.app.XmlConverter;
import hansol.xml2mermaid.log.LogSetup;
import hansol.xml2mermaid.output.ImageRenderer;
import hansol.xml2mermaid.output.OutputRequest;
import hansol.xml2mermaid.output.OutputWriter;
import hansol.xml2mermaid.report.Report;
import hansol.xml2mermaid.rule.RuleRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;

/**
 * XML -> Mermaid flowchart (.mmd/.md) and optionally PNG/JPG/SVG/PDF. Command line entry point;
 * the GUI is hansol.xml2mermaid.ui.ConverterApp.
 * Java 8+, no Java library dependencies. Mermaid syntax targets Mermaid 11.6.
 *
 * Flow: reader (XML -> XmlElement) -> convert + rule (XmlElement -> Flowchart)
 *       -> output (Flowchart -> file). See README2.md for the package layout.
 */
public final class HansolXmlToImage2 {
    public static final String VERSION = "2.2.0";
    static final int EXIT_OK = 0, EXIT_FAILED = 1, EXIT_USAGE = 2, EXIT_STRICT = 3;

    private HansolXmlToImage2() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Options opt;
        try {
            opt = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(Options.USAGE);
            return EXIT_USAGE;
        }
        if (opt.help) { System.out.println(Options.USAGE); return EXIT_OK; }
        if (opt.version) { System.out.println("HansolXmlToImage2 " + VERSION); return EXIT_OK; }

        OutputRequest request = OutputRequest.besideOutput(opt.output, opt.direction, opt.showId,
            opt.overwrite, opt.input.getFileName().toString());
        if (!opt.overwrite) {
            List<Path> planned = new ArrayList<Path>(opt.format.plannedFiles(request));
            planned.add(request.convertLog());
            for (Path p : planned) {
                if (Files.exists(p)) {
                    System.err.println("Error: file already exists: " + p + " (use --overwrite)");
                    return EXIT_FAILED;
                }
            }
        }
        List<Handler> handlers;
        try {
            Files.createDirectories(opt.output.getParent());
            handlers = LogSetup.install(request.convertLog(), opt.overwrite, opt.verbose);
        } catch (IOException e) {
            System.err.println("Error: cannot create log file " + request.convertLog() + ": " + e);
            return EXIT_FAILED;
        }
        try {
            LOG.info("HansolXmlToImage2 " + VERSION + " (Java " + System.getProperty("java.version") + ")");
            LOG.info("Input: " + opt.input);
            ImageRenderer renderer = opt.format.image
                ? ImageRenderer.create(opt.mermaidConfig, opt.puppeteerConfig, opt.timeoutSeconds,
                    opt.scale, opt.jpegQuality) : null;
            OutputWriter writer = opt.format.createWriter(renderer);
            RuleRegistry rules = RuleRegistry.load(opt.rulesFile);
            LOG.info("Rules: " + rules.source());

            XmlConverter.Result result = XmlConverter.convert(opt.input, rules, opt.verbose);
            Report report = result.report;

            if (opt.strict && report.hasUnknownRules()) {
                LOG.severe("Unknown rules found and --strict is set; no output written. See "
                    + request.convertLog());
                return EXIT_STRICT;
            }
            writer.write(result.chart, request);
            LOG.info("Convert log: " + request.convertLog());
            if (report.hasUnknownRules()) {
                LOG.warning("Completed with unknown rules; review the summary in " + request.convertLog());
            }
            return EXIT_OK;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Conversion failed: " + e.getMessage(), e);
            return EXIT_FAILED;
        } finally {
            LogSetup.uninstall(handlers);
        }
    }
}
