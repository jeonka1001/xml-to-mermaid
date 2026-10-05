package hansol.xml2mermaid;

import hansol.xml2mermaid.output.OutputFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Command line options. Options may also come from system properties (see USAGE). */
final class Options {
    static final String USAGE =
        "Usage: HansolXmlToImage2 [options] <input.xml> <output.mmd|md|png|jpg|svg|pdf> [TD|LR|BT|RL]\n"
        + "Options:\n"
        + "  --strict                 fail (exit 3) when any unknown rule is found\n"
        + "  --overwrite              replace existing output, intermediate and log files\n"
        + "  --rules <file>           NodeType/ignore rules (default: -Dhansol.rules or built-in)\n"
        + "  --mermaid-config <file>  Mermaid config JSON for image output (default: -Dmermaid.config)\n"
        + "  --puppeteer-config <f>   Puppeteer config JSON for image output (default: -Dpuppeteer.config)\n"
        + "  --timeout <seconds>      image rendering timeout (default: -Drender.timeout.seconds or 120)\n"
        + "  --scale <1-10>           PNG/JPG resolution factor (default 1)\n"
        + "  --jpeg-quality <1-100>   JPG quality (default 90)\n"
        + "  --show-id                append the XML Node Id to each node label\n"
        + "  --verbose                also log every ignored element/attribute occurrence\n"
        + "  --version, -h/--help\n"
        + "Exit codes: 0 ok, 1 failed, 2 usage error, 3 unknown rules found with --strict";

    Path input, output, rulesFile, mermaidConfig, puppeteerConfig;
    String direction = "TD";
    OutputFormat format;
    long timeoutSeconds;
    int scale = 1, jpegQuality = 90;
    boolean strict, overwrite, showId, verbose, help, version;

    static Options parse(String[] args) {
        Options o = new Options();
        List<String> positional = new ArrayList<String>();
        String rules = System.getProperty("hansol.rules");
        String config = System.getProperty("mermaid.config");
        String puppeteer = System.getProperty("puppeteer.config");
        String timeout = System.getProperty("render.timeout.seconds", "120");
        String scale = "1", quality = "90";
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--strict".equals(a)) o.strict = true;
            else if ("--overwrite".equals(a)) o.overwrite = true;
            else if ("--show-id".equals(a)) o.showId = true;
            else if ("--verbose".equals(a)) o.verbose = true;
            else if ("-h".equals(a) || "--help".equals(a)) o.help = true;
            else if ("--version".equals(a)) o.version = true;
            else if ("--rules".equals(a)) rules = value(args, ++i, a);
            else if ("--mermaid-config".equals(a)) config = value(args, ++i, a);
            else if ("--puppeteer-config".equals(a)) puppeteer = value(args, ++i, a);
            else if ("--timeout".equals(a)) timeout = value(args, ++i, a);
            else if ("--scale".equals(a)) scale = value(args, ++i, a);
            else if ("--jpeg-quality".equals(a)) quality = value(args, ++i, a);
            else if (a.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + a);
            else positional.add(a);
        }
        if (o.help || o.version) return o;
        if (positional.size() < 2 || positional.size() > 3) {
            throw new IllegalArgumentException("Expected <input.xml> <output> [direction]");
        }
        o.input = Paths.get(positional.get(0)).toAbsolutePath().normalize();
        o.output = Paths.get(positional.get(1)).toAbsolutePath().normalize();
        if (positional.size() == 3) o.direction = positional.get(2).toUpperCase(Locale.ROOT);
        if (!Arrays.asList("TD", "LR", "BT", "RL").contains(o.direction)) {
            throw new IllegalArgumentException("Direction must be TD, LR, BT or RL");
        }
        o.format = OutputFormat.fromFileName(o.output.getFileName().toString());
        if (!Files.isRegularFile(o.input)) {
            throw new IllegalArgumentException("Input file not found: " + o.input);
        }
        o.rulesFile = optionalPath(rules);
        o.mermaidConfig = optionalPath(config);
        o.puppeteerConfig = optionalPath(puppeteer);
        try {
            o.timeoutSeconds = Long.parseLong(timeout.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Timeout must be a number of seconds: " + timeout);
        }
        if (o.timeoutSeconds <= 0) throw new IllegalArgumentException("Timeout must be positive");
        o.scale = intInRange(scale, 1, 10, "--scale");
        o.jpegQuality = intInRange(quality, 1, 100, "--jpeg-quality");
        return o;
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) throw new IllegalArgumentException(option + " requires a value");
        return args[i];
    }

    private static int intInRange(String value, int min, int max, String option) {
        try {
            int v = Integer.parseInt(value.trim());
            if (v >= min && v <= max) return v;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new IllegalArgumentException(option + " must be an integer " + min + ".." + max + ": " + value);
    }

    private static Path optionalPath(String value) {
        return value == null || value.trim().isEmpty()
            ? null : Paths.get(value.trim()).toAbsolutePath().normalize();
    }
}
