import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XML -> Mermaid flowchart (.mmd/.md) and optionally PNG/JPG/SVG/PDF. Command line entry point;
 * the GUI (HansolXmlToImage2Gui.java, same folder) reuses these classes.
 * Java 8+, no Java library dependencies. Mermaid syntax targets Mermaid 11.6.
 * Image output requires Node.js and @mermaid-js/mermaid-cli (11.6.0 tested).
 *
 * Compile: javac -encoding UTF-8 HansolXmlToImage2.java
 * Run:     java HansolXmlToImage2 [options] input.xml output.mmd|md|png|jpg|svg|pdf [TD|LR|BT|RL]
 * Properties: -Dhansol.rules=node-types.properties -Dmermaid.config=mermaid-config.json
 *             -Dmermaid.cli=/path/to/src/cli.js -Dnode.executable=/path/to/node
 *             -Dpuppeteer.cache.dir=/path/to/browsers -Drender.timeout.seconds=120
 * Without -Dmermaid.cli/-Dnode.executable/-Dpuppeteer.cache.dir, the runtime installed by
 * "mvn package" next to the jar is used (<jar folder>/mermaid), then ./node_modules and node on PATH.
 *
 * Elements, attributes and NodeTypes outside the confirmed rules are logged
 * (per occurrence in <output>.convert.log, aggregated in a summary) so that new
 * rules can be added to the rules file without recompiling.
 */
public final class HansolXmlToImage2 {
    static final String VERSION = "2.1.0";
    static final int EXIT_OK = 0, EXIT_FAILED = 1, EXIT_USAGE = 2, EXIT_STRICT = 3;

    // LOG goes to console and file; DETAIL (per-occurrence findings) only to file.
    static final Logger LOG = Logger.getLogger("hansol.xml2mermaid");
    static final Logger DETAIL = Logger.getLogger("hansol.xml2mermaid-detail");

    private HansolXmlToImage2() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static final String USAGE =
        "Usage: HansolXmlToImage2 [options] <input.xml> <output.mmd|md|png|jpg|svg|pdf> [TD|LR|BT|RL]\n"
        + "Options:\n"
        + "  --strict                 fail (exit 3) when any unknown rule is found\n"
        + "  --overwrite              replace existing output, intermediate and log files\n"
        + "  --rules <file>           NodeType/ignore rules (default: -Dhansol.rules or built-in)\n"
        + "  --mermaid-config <file>  Mermaid config JSON for image output (default: -Dmermaid.config)\n"
        + "  --puppeteer-config <f>   Puppeteer config JSON for image output\n"
        + "  --timeout <seconds>      image rendering timeout (default 120)\n"
        + "  --scale <1-10>           PNG/JPG resolution factor (default 1)\n"
        + "  --jpeg-quality <1-100>   JPG quality (default 90)\n"
        + "  --show-id                append the XML Node Id to each node label\n"
        + "  --verbose                also log every ignored element/attribute occurrence\n"
        + "  --version, -h/--help\n"
        + "Exit codes: 0 ok, 1 failed, 2 usage error, 3 unknown rules found with --strict";

    static int run(String[] args) {
        Options opt;
        try {
            opt = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(USAGE);
            return EXIT_USAGE;
        }
        if (opt.help) { System.out.println(USAGE); return EXIT_OK; }
        if (opt.version) { System.out.println("HansolXmlToImage2 " + VERSION); return EXIT_OK; }

        Path convertLog = opt.output.resolveSibling(opt.output.getFileName() + ".convert.log");
        Path mmd = opt.format.image ? opt.output.resolveSibling(opt.baseName + ".mmd") : null;
        Path renderLog = opt.format.image
            ? opt.output.resolveSibling(opt.output.getFileName() + ".render.log") : null;
        if (!opt.overwrite) {
            for (Path p : Arrays.asList(opt.output, mmd, convertLog, renderLog)) {
                if (p != null && Files.exists(p)) {
                    System.err.println("Error: file already exists: " + p + " (use --overwrite)");
                    return EXIT_FAILED;
                }
            }
        }
        List<Handler> handlers;
        try {
            Files.createDirectories(opt.output.getParent());
            handlers = LogSetup.install(convertLog, opt.overwrite, opt.verbose);
        } catch (IOException e) {
            System.err.println("Error: cannot create log file " + convertLog + ": " + e);
            return EXIT_FAILED;
        }
        try {
            LOG.info("HansolXmlToImage2 " + VERSION + " (Java " + System.getProperty("java.version") + ")");
            LOG.info("Input: " + opt.input);
            Renderer renderer = opt.format.image ? Renderer.fromOptions(opt) : null;
            Rules rules = Rules.load(opt.rulesFile);
            LOG.info("Rules: " + rules.source);

            Report report = new Report(opt.verbose);
            Diagram diagram = DiagramParser.parse(opt.input, rules, report);
            String mermaid = MermaidWriter.write(diagram, opt.direction, opt.showId,
                opt.input.getFileName().toString());
            LOG.info("Parsed " + diagram.nodes.size() + " nodes, " + diagram.links.size() + " links");
            report.logSummary();

            if (opt.strict && report.hasUnknownRules()) {
                LOG.severe("Unknown rules found and --strict is set; no output written. See " + convertLog);
                return EXIT_STRICT;
            }
            if (opt.format == Format.MD) {
                writeText(opt.output, MermaidWriter.markdown(mermaid,
                    opt.input.getFileName().toString()), opt.overwrite);
                LOG.info("Markdown: " + opt.output);
            } else if (opt.format == Format.MMD) {
                writeText(opt.output, mermaid, opt.overwrite);
                LOG.info("Mermaid: " + opt.output);
            } else {
                writeText(mmd, mermaid, opt.overwrite);
                LOG.info("Mermaid: " + mmd);
                renderImage(renderer, mmd, opt.output, renderLog, opt.format, opt.overwrite);
                LOG.info("Image: " + opt.output);
                LOG.info("Render log: " + renderLog);
            }
            LOG.info("Convert log: " + convertLog);
            if (report.hasUnknownRules()) {
                LOG.warning("Completed with unknown rules; review the summary in " + convertLog);
            }
            return EXIT_OK;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Conversion failed: " + e.getMessage(), e);
            return EXIT_FAILED;
        } finally {
            LogSetup.uninstall(handlers);
        }
    }

    /**
     * Renders mmd to output. JPG: Mermaid CLI 11.6 renders only svg/png/pdf, so a PNG is
     * rendered to a staging directory and re-encoded with ImageIO on a white background.
     */
    static void renderImage(Renderer renderer, Path mmd, Path output, Path log, Format format,
                            boolean overwrite) throws Exception {
        if (format != Format.JPG) {
            renderer.render(mmd, output, log, format, overwrite);
            return;
        }
        Path work = Files.createTempDirectory(output.getParent(), ".jpeg-");
        Path png = work.resolve("render.png");
        Path jpg = work.resolve("render.jpg");
        try {
            renderer.render(mmd, png, log, Format.PNG, overwrite);
            Jpeg.encode(png, jpg, renderer.jpegQuality);
            if (overwrite) Files.move(jpg, output, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(jpg, output); // protect existing output
        } finally {
            Files.deleteIfExists(png);
            Files.deleteIfExists(jpg);
            Files.deleteIfExists(work);
        }
    }

    static void writeText(Path file, String content, boolean overwrite) throws IOException {
        OpenOption[] mode = overwrite
            ? new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE}
            : new OpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        Files.write(file, content.getBytes(StandardCharsets.UTF_8), mode);
    }

    // ------------------------------------------------------------------ options

    enum Format {
        MMD(false), MD(false), PNG(true), JPG(true), SVG(true), PDF(true);
        final boolean image;
        Format(boolean image) { this.image = image; }

        /** File extension without the dot. */
        String extension() { return name().toLowerCase(Locale.ROOT); }
    }

    static final class Options {
        Path input, output, rulesFile, mermaidConfig, puppeteerConfig;
        String baseName, direction = "TD";
        Format format;
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
            String name = o.output.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot <= 0 ? "" : name.substring(dot + 1).toUpperCase(Locale.ROOT);
            if ("JPEG".equals(ext)) ext = "JPG";
            try {
                o.format = Format.valueOf(ext);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Output extension must be mmd, md, png, jpg, jpeg, svg or pdf");
            }
            o.baseName = name.substring(0, dot);
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

        private static int intInRange(String value, int min, int max, String option) {
            try {
                int v = Integer.parseInt(value.trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {
                // reported below
            }
            throw new IllegalArgumentException(option + " must be an integer " + min + ".." + max + ": " + value);
        }

        private static String value(String[] args, int i, String option) {
            if (i >= args.length) throw new IllegalArgumentException(option + " requires a value");
            return args[i];
        }

        private static Path optionalPath(String value) {
            return value == null || value.trim().isEmpty()
                ? null : Paths.get(value.trim()).toAbsolutePath().normalize();
        }
    }

    // ------------------------------------------------------------------ rules

    /** Mermaid 11.x classic flowchart node shapes (quoted labels). */
    enum Shape {
        RECT("[\"", "\"]"), ROUND("(\"", "\")"), STADIUM("([\"", "\"])"),
        SUBROUTINE("[[\"", "\"]]"), CYLINDER("[(\"", "\")]"), CIRCLE("((\"", "\"))"),
        DOUBLECIRCLE("(((\"", "\")))"), ASYMMETRIC(">\"", "\"]"), DIAMOND("{\"", "\"}"),
        HEXAGON("{{\"", "\"}}"), PARALLELOGRAM("[/\"", "\"/]"),
        PARALLELOGRAM_ALT("[\\\"", "\"\\]"), TRAPEZOID("[/\"", "\"\\]"),
        TRAPEZOID_ALT("[\\\"", "\"/]");

        final String open, close;
        Shape(String open, String close) { this.open = open; this.close = close; }

        String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }

        static Shape of(String key, String property) {
            for (Shape s : values()) if (s.key().equals(key.trim().toLowerCase(Locale.ROOT))) return s;
            StringBuilder all = new StringBuilder();
            for (Shape s : values()) all.append(all.length() == 0 ? "" : ", ").append(s.key());
            throw new IllegalArgumentException("Invalid shape '" + key + "' for " + property
                + "; allowed: " + all);
        }
    }

    static final class Rules {
        static final String NODE_TYPE_PREFIX = "nodetype.";
        // Built-in defaults; the shipped node-types.properties mirrors these.
        static final String[][] DEFAULTS = {
            {"nodetype.StartNode", "stadium"},
            {"nodetype.ScriptNode", "rect"},
            {"fallback.shape", "rect"},
            {"diagram.versions", "14"},
            {"ignore.elements", "CustomProperties,Script"},
            {"ignore.attributes", ""},
        };

        final Map<String, Shape> nodeTypes = new LinkedHashMap<String, Shape>();
        Shape fallback;
        final Set<String> versions = new LinkedHashSet<String>();
        final Set<String> ignoreElements = new HashSet<String>();
        final Set<String> ignoreAttributes = new HashSet<String>();
        String source = "built-in";

        static Rules load(Path file) throws IOException {
            Properties p = new Properties();
            for (String[] d : DEFAULTS) p.setProperty(d[0], d[1]);
            Rules r = new Rules();
            if (file != null) {
                if (!Files.isRegularFile(file)) throw new IOException("Rules file not found: " + file);
                Properties user = new Properties();
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    user.load(in);
                }
                for (String k : user.stringPropertyNames()) {
                    boolean known = k.startsWith(NODE_TYPE_PREFIX) && k.length() > NODE_TYPE_PREFIX.length();
                    for (String[] d : DEFAULTS) known |= d[0].equals(k);
                    if (!known) LOG.warning("Rules file: unknown key ignored: " + Text.safe(k));
                    else p.setProperty(k, user.getProperty(k));
                }
                r.source = file.toString();
            }
            for (String k : new TreeSet<String>(p.stringPropertyNames())) {
                if (k.startsWith(NODE_TYPE_PREFIX)) {
                    r.nodeTypes.put(k.substring(NODE_TYPE_PREFIX.length()), Shape.of(p.getProperty(k), k));
                }
            }
            r.fallback = Shape.of(p.getProperty("fallback.shape"), "fallback.shape");
            r.versions.addAll(list(p.getProperty("diagram.versions")));
            r.ignoreElements.addAll(list(p.getProperty("ignore.elements")));
            r.ignoreAttributes.addAll(list(p.getProperty("ignore.attributes")));
            return r;
        }

        boolean ignoresElement(String name) { return ignoreElements.contains(name); }

        /** Entries are "Element@attr" or "*@attr". */
        boolean ignoresAttribute(String element, String attr) {
            return ignoreAttributes.contains(element + "@" + attr) || ignoreAttributes.contains("*@" + attr);
        }

        private static List<String> list(String value) {
            List<String> out = new ArrayList<String>();
            if (value == null) return out;
            for (String s : value.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
            return out;
        }
    }

    // ------------------------------------------------------------------ findings

    /**
     * UNKNOWN: structure outside the confirmed rules (fails with --strict).
     * NOTICE: data condition worth reviewing (never fails).
     * IGNORED: known structure intentionally not used (rules file ignore.*).
     */
    enum Category { UNKNOWN, NOTICE, IGNORED }

    static final class Report {
        static final int MAX_EXAMPLES = 5;

        static final class Entry {
            final Category category;
            final String key, consequence;
            int count;
            final List<String> examples = new ArrayList<String>();
            Entry(Category category, String key, String consequence) {
                this.category = category; this.key = key; this.consequence = consequence;
            }
        }

        private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
        private final boolean verbose;

        Report(boolean verbose) { this.verbose = verbose; }

        void unknown(String key, String location, String consequence) {
            add(Category.UNKNOWN, key, location, consequence);
        }

        void notice(String key, String location, String consequence) {
            add(Category.NOTICE, key, location, consequence);
        }

        void ignored(String key, String location) {
            add(Category.IGNORED, key, location, "ignored by rules");
        }

        private void add(Category c, String key, String location, String consequence) {
            String id = c + "|" + key;
            Entry e = entries.get(id);
            if (e == null) entries.put(id, e = new Entry(c, key, consequence));
            e.count++;
            if (e.examples.size() < MAX_EXAMPLES) e.examples.add(location);
            if (c == Category.IGNORED) {
                if (verbose) DETAIL.fine("[IGNORED] " + key + " at " + location);
            } else {
                DETAIL.log(c == Category.UNKNOWN ? Level.WARNING : Level.INFO,
                    "[" + c + "] " + key + " at " + location + " -> " + consequence);
            }
        }

        /** Number of distinct finding kinds in the category. */
        int kinds(Category c) {
            int n = 0;
            for (Entry e : entries.values()) if (e.category == c) n++;
            return n;
        }

        boolean hasUnknownRules() {
            for (Entry e : entries.values()) if (e.category == Category.UNKNOWN) return true;
            return false;
        }

        void logSummary() {
            for (Category c : Category.values()) {
                List<Entry> list = new ArrayList<Entry>();
                for (Entry e : entries.values()) if (e.category == c) list.add(e);
                if (list.isEmpty()) continue;
                Level level = c == Category.UNKNOWN ? Level.WARNING : Level.INFO;
                LOG.log(level, "[" + c + " summary] " + list.size() + " kind(s)");
                for (Entry e : list) {
                    StringBuilder ex = new StringBuilder();
                    for (String s : e.examples) ex.append(ex.length() == 0 ? "" : ", ").append(s);
                    if (e.count > e.examples.size()) ex.append(", ...");
                    LOG.log(level, "  " + e.key + " : " + e.count + " (" + ex + ")"
                        + (c == Category.IGNORED ? "" : " -> " + e.consequence));
                }
            }
            if (entries.isEmpty()) LOG.info("No unknown rules found");
        }
    }

    // ------------------------------------------------------------------ model

    static final class NodeItem {
        final String xmlId, label;
        final Shape shape;
        NodeItem(String xmlId, String label, Shape shape) {
            this.xmlId = xmlId; this.label = label; this.shape = shape;
        }
    }

    static final class LinkItem {
        final String from, to, label;
        LinkItem(String from, String to, String label) { this.from = from; this.to = to; this.label = label; }
    }

    static final class Diagram {
        final Map<String, NodeItem> nodes = new LinkedHashMap<String, NodeItem>();
        final List<LinkItem> links = new ArrayList<LinkItem>();
    }

    // ------------------------------------------------------------------ parser

    static final class DiagramParser {
        static final Set<String> DIAGRAM_ATTRS = set("Version");
        static final Set<String> NODE_ATTRS = set("Id", "NodeType");
        static final Set<String> LINK_ATTRS = set("Id");
        static final Set<String> END_ATTRS = set("Id");

        private static final ErrorHandler THROWING = new ErrorHandler() {
            @Override public void warning(SAXParseException e) {}
            @Override public void error(SAXParseException e) throws SAXException { throw e; }
            @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
        };

        private final Rules rules;
        private final Report report;
        private final Diagram diagram = new Diagram();

        private DiagramParser(Rules rules, Report report) { this.rules = rules; this.report = report; }

        static Diagram parse(Path input, Rules rules, Report report) throws Exception {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // XXE protection: no DOCTYPE, no external entities, DTDs or schemas.
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            Document doc;
            javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(THROWING); // report via the exception only, not stderr
            try (InputStream in = Files.newInputStream(input)) {
                doc = builder.parse(in);
            }
            DiagramParser p = new DiagramParser(rules, report);
            p.readDiagram(doc.getDocumentElement());
            return p.diagram;
        }

        private void readDiagram(Element root) {
            if (!"Diagram".equals(root.getNodeName())) {
                throw new IllegalArgumentException("Expected root element: Diagram");
            }
            checkAttributes(root, DIAGRAM_ATTRS, "Diagram");
            String version = root.getAttribute("Version").trim();
            if (version.isEmpty()) {
                report.unknown("Diagram without Version", "Diagram", "converted with current rules");
            } else if (!rules.versions.contains(version)) {
                report.unknown("Diagram Version '" + Text.safe(version) + "'", "Diagram",
                    "converted with current rules");
            }
            checkText(root, "Diagram");
            List<Element> nodeSections = new ArrayList<Element>(), linkSections = new ArrayList<Element>();
            for (Element e : elements(root)) {
                String n = e.getNodeName();
                if ("Nodes".equals(n)) nodeSections.add(e);
                else if ("Links".equals(n)) linkSections.add(e);
                else other(e, "Diagram", "Diagram");
            }
            if (nodeSections.isEmpty()) throw new IllegalArgumentException("Missing Diagram/Nodes");
            if (nodeSections.size() > 1) {
                report.unknown("Multiple Diagram/Nodes sections", "Diagram", "all sections merged");
            }
            if (linkSections.size() > 1) {
                report.unknown("Multiple Diagram/Links sections", "Diagram", "all sections merged");
            }
            for (Element section : nodeSections) {
                checkAttributes(section, Collections.<String>emptySet(), "Nodes");
                checkText(section, "Nodes");
                for (Element e : elements(section)) {
                    if ("Node".equals(e.getNodeName())) readNode(e);
                    else other(e, "Nodes", "Nodes");
                }
            }
            if (diagram.nodes.isEmpty()) throw new IllegalArgumentException("No Node elements found");
            Set<String> linkIds = new HashSet<String>();
            int index = 0;
            for (Element section : linkSections) {
                checkAttributes(section, Collections.<String>emptySet(), "Links");
                checkText(section, "Links");
                for (Element e : elements(section)) {
                    if ("Link".equals(e.getNodeName())) readLink(e, ++index, linkIds);
                    else other(e, "Links", "Links");
                }
            }
        }

        private void readNode(Element node) {
            String xmlId = node.getAttribute("Id").trim();
            if (xmlId.isEmpty()) throw new IllegalArgumentException("Missing Id on Node");
            if (diagram.nodes.containsKey(xmlId)) {
                throw new IllegalArgumentException("Duplicate Node Id: " + Text.safe(xmlId));
            }
            String loc = "Node[Id=" + Text.safe(xmlId) + "]";
            checkAttributes(node, NODE_ATTRS, loc);
            checkText(node, loc);
            String type = node.getAttribute("NodeType").trim();
            Shape shape = rules.nodeTypes.get(type);
            if (type.isEmpty()) {
                shape = rules.fallback;
                report.unknown("Node without NodeType", loc, "drawn as " + shape.key());
            } else if (shape == null) {
                shape = rules.fallback;
                report.unknown("NodeType '" + Text.safe(type) + "'", loc, "drawn as " + shape.key());
            }
            Element text = null;
            for (Element e : elements(node)) {
                if ("Text".equals(e.getNodeName())) {
                    if (text == null) text = e;
                    else report.unknown("Multiple Node/Text", loc, "first Text used");
                } else {
                    other(e, "Node", loc);
                }
            }
            String label = readLabel(text, "Node", loc);
            if (label.isEmpty()) {
                label = "Node " + xmlId;
                report.notice("Empty Node Text", loc, "label '" + Text.safe(label) + "' used");
            }
            diagram.nodes.put(xmlId, new NodeItem(xmlId, label, shape));
        }

        private void readLink(Element link, int index, Set<String> linkIds) {
            String linkId = link.getAttribute("Id").trim();
            String loc = linkId.isEmpty() ? "Link[#" + index + "]" : "Link[Id=" + Text.safe(linkId) + "]";
            if (linkId.isEmpty()) report.notice("Link without Id", loc, "converted");
            else if (!linkIds.add(linkId)) report.notice("Duplicate Link Id", loc, "converted");
            checkAttributes(link, LINK_ATTRS, loc);
            checkText(link, loc);
            Element text = null, origin = null, destination = null;
            for (Element e : elements(link)) {
                String n = e.getNodeName();
                if ("Text".equals(n)) {
                    if (text == null) text = e;
                    else report.unknown("Multiple Link/Text", loc, "first Text used");
                } else if ("Origin".equals(n)) {
                    if (origin == null) origin = e;
                    else report.unknown("Multiple Link/Origin", loc, "first Origin used");
                } else if ("Destination".equals(n)) {
                    if (destination == null) destination = e;
                    else report.unknown("Multiple Link/Destination", loc, "first Destination used");
                } else {
                    other(e, "Link", loc);
                }
            }
            if (origin == null || destination == null) {
                throw new IllegalArgumentException(loc + " is missing Origin or Destination");
            }
            String from = endpoint(origin, loc), to = endpoint(destination, loc);
            if (!diagram.nodes.containsKey(from) || !diagram.nodes.containsKey(to)) {
                throw new IllegalArgumentException(loc + " references missing Node: "
                    + Text.safe(from) + " -> " + Text.safe(to));
            }
            diagram.links.add(new LinkItem(from, to, readLabel(text, "Link", loc)));
        }

        private String endpoint(Element end, String linkLoc) {
            String name = end.getNodeName();
            String loc = linkLoc + "/" + name;
            checkAttributes(end, END_ATTRS, loc);
            checkText(end, loc);
            for (Element e : elements(end)) other(e, name, loc);
            String id = end.getAttribute("Id").trim();
            if (id.isEmpty()) throw new IllegalArgumentException("Missing Id on " + loc);
            return id;
        }

        private String readLabel(Element text, String owner, String loc) {
            if (text == null) return "";
            checkAttributes(text, Collections.<String>emptySet(), loc + "/Text");
            for (Element e : elements(text)) {
                report.unknown("Element " + owner + "/Text/" + Text.safe(e.getNodeName()), loc,
                    "its text is included in the label");
            }
            return text.getTextContent().trim();
        }

        /** An element that is not part of the confirmed structure at this position. */
        private void other(Element e, String parent, String loc) {
            String name = e.getNodeName();
            if (rules.ignoresElement(name)) {
                report.ignored("Element " + parent + "/" + Text.safe(name), loc);
            } else {
                report.unknown("Element " + parent + "/" + Text.safe(name), loc, "skipped");
            }
        }

        private void checkAttributes(Element e, Set<String> known, String loc) {
            String name = e.getNodeName();
            NamedNodeMap attrs = e.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                String attr = attrs.item(i).getNodeName();
                if (known.contains(attr) || attr.equals("xmlns") || attr.startsWith("xmlns:")) continue;
                String key = "Attribute " + Text.safe(name) + "@" + Text.safe(attr);
                if (rules.ignoresAttribute(name, attr)) report.ignored(key, loc);
                else report.unknown(key, loc, "skipped");
            }
        }

        /** Structural elements are expected to hold only child elements and whitespace. */
        private void checkText(Element e, String loc) {
            for (org.w3c.dom.Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
                if ((n.getNodeType() == org.w3c.dom.Node.TEXT_NODE
                        || n.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE)
                        && !n.getNodeValue().trim().isEmpty()) {
                    report.unknown("Text content in " + Text.safe(e.getNodeName()), loc, "skipped");
                    return;
                }
            }
        }

        private static List<Element> elements(Element parent) {
            List<Element> list = new ArrayList<Element>();
            for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) list.add((Element) n);
            }
            return list;
        }

        private static Set<String> set(String... values) {
            return new HashSet<String>(Arrays.asList(values));
        }
    }

    // ------------------------------------------------------------------ writers

    static final class MermaidWriter {
        static String write(Diagram d, String direction, boolean showId, String sourceName) {
            // Generated Mermaid IDs (N0, N1, ...) avoid reserved words such as "end".
            Map<String, String> ids = new HashMap<String, String>();
            StringBuilder out = new StringBuilder();
            out.append("%% Generated by HansolXmlToImage2 from ").append(Text.comment(sourceName)).append('\n');
            out.append("flowchart ").append(direction).append('\n');
            for (NodeItem n : d.nodes.values()) {
                String id = "N" + ids.size();
                ids.put(n.xmlId, id);
                String label = showId ? n.label + " [Id=" + n.xmlId + "]" : n.label;
                out.append("    ").append(id).append(n.shape.open)
                   .append(Text.mermaid(label)).append(n.shape.close).append('\n');
            }
            out.append('\n');
            for (LinkItem l : d.links) {
                out.append("    ").append(ids.get(l.from)).append(" -->");
                if (!l.label.isEmpty()) out.append("|\"").append(Text.mermaid(l.label)).append("\"|");
                out.append(' ').append(ids.get(l.to)).append('\n');
            }
            return out.toString();
        }

        static String markdown(String mermaid, String sourceName) {
            return "# " + Text.comment(sourceName) + "\n\n```mermaid\n" + mermaid + "```\n";
        }
    }

    static final class Text {
        private Text() {}

        // Mermaid decimal entities protect quote/pipe/markup characters in labels.
        // Multiline XML labels are flattened to spaces for renderer compatibility.
        static String mermaid(String value) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isWhitespace(c) || Character.isISOControl(c)) s.append(' ');
                else if ("\"|<>&#`\\[]{}()".indexOf(c) >= 0) s.append('#').append((int) c).append(';');
                else s.append(c);
            }
            return s.toString();
        }

        /** Single-line text for Mermaid comments and Markdown headings. */
        static String comment(String value) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                s.append(Character.isISOControl(c) || c == '`' ? ' ' : c);
            }
            return s.toString();
        }

        /** XML-derived value for log messages: control characters escaped, length capped. */
        static String safe(String value) {
            String v = value.length() > 120 ? value.substring(0, 120) + "..." : value;
            return escapeControls(v);
        }

        // Prevents forged log lines (OWASP log injection).
        static String escapeControls(String v) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (Character.isISOControl(c) || c == ' ' || c == ' ') {
                    s.append(String.format("\\u%04x", (int) c));
                } else {
                    s.append(c);
                }
            }
            return s.toString();
        }
    }

    // ------------------------------------------------------------------ logging

    static final class LogSetup {
        private LogSetup() {}

        static List<Handler> install(Path file, boolean overwrite, boolean verbose) throws IOException {
            return install(file, overwrite, verbose, true);
        }

        /** console=false for the GUI, which keeps its own handler on LOG. */
        static List<Handler> install(Path file, boolean overwrite, boolean verbose, boolean console)
                throws IOException {
            OutputStream out = overwrite
                ? Files.newOutputStream(file, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                : Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Handler fileHandler = new FlushingHandler(out, new LineFormatter(true));
            try {
                fileHandler.setEncoding("UTF-8"); // independent of the Windows console code page
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
            Level fileLevel = verbose ? Level.FINE : Level.INFO;
            fileHandler.setLevel(fileLevel);
            List<Handler> handlers = new ArrayList<Handler>();
            handlers.add(fileHandler);
            if (console) {
                // Console keeps the platform encoding so Windows cmd displays it correctly.
                Handler consoleHandler = new FlushingHandler(System.err, new LineFormatter(false));
                consoleHandler.setLevel(Level.INFO);
                handlers.add(consoleHandler);
            }
            LOG.setUseParentHandlers(false);
            LOG.setLevel(fileLevel);
            for (Handler h : handlers) LOG.addHandler(h);
            DETAIL.setUseParentHandlers(false);
            DETAIL.setLevel(fileLevel);
            DETAIL.addHandler(fileHandler);
            return handlers;
        }

        static void uninstall(List<Handler> handlers) {
            for (Handler h : handlers) {
                LOG.removeHandler(h);
                DETAIL.removeHandler(h);
                if (h instanceof FlushingHandler && ((FlushingHandler) h).ownsStream) h.close();
                else h.flush();
            }
        }
    }

    static final class FlushingHandler extends StreamHandler {
        final boolean ownsStream;
        FlushingHandler(OutputStream out, Formatter formatter) {
            super(out, formatter);
            ownsStream = out != System.err;
        }
        @Override public synchronized void publish(LogRecord record) {
            super.publish(record);
            flush();
        }
    }

    static final class LineFormatter extends Formatter {
        private final boolean timestamps;
        LineFormatter(boolean timestamps) { this.timestamps = timestamps; }

        @Override public String format(LogRecord r) {
            StringBuilder s = new StringBuilder();
            if (timestamps) {
                s.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(r.getMillis())))
                 .append(' ');
            }
            s.append(String.format("%-5s ", levelName(r.getLevel())));
            s.append(Text.escapeControls(formatMessage(r)));
            s.append(System.lineSeparator());
            if (timestamps && r.getThrown() != null) {
                StringWriter trace = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(trace));
                s.append(trace);
            }
            return s.toString();
        }

        static String levelName(Level l) {
            if (l.intValue() >= Level.SEVERE.intValue()) return "ERROR";
            if (l.intValue() >= Level.WARNING.intValue()) return "WARN";
            if (l.intValue() >= Level.INFO.intValue()) return "INFO";
            return "DEBUG";
        }
    }

    // ------------------------------------------------------------------ rendering

    static final class Renderer {
        final Path cli, mermaidConfig, puppeteerConfig;
        final String node, puppeteerCacheDir;
        final long timeoutSeconds;
        final int scale, jpegQuality;

        private volatile boolean cancelled;
        private volatile Process current;
        private volatile Path currentWork, currentLog;

        private Renderer(Path cli, String node, Path mermaidConfig, Path puppeteerConfig,
                         String puppeteerCacheDir, long timeoutSeconds, int scale, int jpegQuality) {
            this.cli = cli; this.node = node; this.mermaidConfig = mermaidConfig;
            this.puppeteerConfig = puppeteerConfig; this.puppeteerCacheDir = puppeteerCacheDir;
            this.timeoutSeconds = timeoutSeconds; this.scale = scale; this.jpegQuality = jpegQuality;
        }

        /** Validates the rendering environment before any parsing or output. */
        static Renderer fromOptions(Options o) {
            return create(o.mermaidConfig, o.puppeteerConfig, o.timeoutSeconds, o.scale, o.jpegQuality);
        }

        /** scale: PNG/JPG device scale factor 1..10 (Mermaid CLI -s). jpegQuality: 1..100. */
        static Renderer create(Path mermaidConfig, Path puppeteerConfig, long timeoutSeconds,
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
            return new Renderer(cli, node != null ? node : "node",
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

        /** <folder of the jar (or of the classes folder)>/mermaid, where mvn package installs the runtime. */
        static Path bundledRuntimeDir() {
            try {
                Path location = Paths.get(HansolXmlToImage2.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
                Path base = location.getParent();
                return base == null ? null : base.resolve("mermaid");
            } catch (Exception e) {
                return null; // e.g. no code source: fall back to properties and PATH
            }
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        }

        /** Stops the running render (and its browser); later renders fail with CancellationException. */
        void cancel() {
            cancelled = true;
            Process p = current;
            if (p != null) ProcessTree.kill(p, currentWork, currentLog);
        }

        void render(Path mmd, Path output, Path log, Format format, boolean overwrite) throws Exception {
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
                if (format == Format.PNG && scale != 1) { cmd.add("-s"); cmd.add(String.valueOf(scale)); }
                if (format == Format.PDF) cmd.add("-f"); // fit PDF page to the diagram
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

    static final class Jpeg {
        private Jpeg() {}

        /** quality: 1..100. JPEG has no alpha channel: the PNG is flattened onto white. */
        static void encode(Path png, Path jpg, int quality) throws IOException {
            BufferedImage source = ImageIO.read(png.toFile());
            if (source == null) throw new IOException("Cannot read rendered PNG: " + png);
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(source, 0, 0, null);
            } finally {
                g.dispose();
            }
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) throw new IOException("No JPEG writer available");
            ImageWriter writer = writers.next();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(jpg.toFile())) {
                if (out == null) throw new IOException("Cannot create " + jpg);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality / 100f);
                writer.setOutput(out);
                writer.write(null, new IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
        }
    }

    /** Terminates the Mermaid CLI and its browser children after a timeout. */
    static final class ProcessTree {
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
}
