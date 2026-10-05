package hansol.xml2mermaid.rule;

import static hansol.xml2mermaid.log.Logs.LOG;
import static hansol.xml2mermaid.log.Logs.safe;

import hansol.xml2mermaid.model.Shape;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Holds every conversion rule. Two ways to add a NodeType rule:
 *   1. rules file: nodetype.X=shape (no recompilation, default label behavior)
 *   2. a NodeRule class (custom label/structure), registered in builtIns()
 * A rules file line replaces a class rule for the same NodeType.
 */
public final class RuleRegistry {
    static final String NODE_TYPE_PREFIX = "nodetype.";

    private final Map<String, NodeRule> nodeRules = new LinkedHashMap<String, NodeRule>();
    private LinkRule linkRule = new DefaultLinkRule();
    private Shape fallbackShape = Shape.RECT;
    private final Set<String> versions = new LinkedHashSet<String>();
    private final Set<String> ignoreElements = new HashSet<String>();
    private final Set<String> ignoreAttributes = new HashSet<String>();
    private String source = "built-in";

    /** Confirmed rules. Register new NodeRule classes here. */
    public static RuleRegistry builtIns() {
        RuleRegistry r = new RuleRegistry();
        r.register(new StartNodeRule());
        r.register(new ScriptNodeRule());
        r.versions.add("14");
        r.ignoreElements.addAll(Arrays.asList("CustomProperties", "Script"));
        return r;
    }

    /** Built-in rules with the rules file (UTF-8 properties) applied on top; file may be null. */
    public static RuleRegistry load(Path file) throws IOException {
        RuleRegistry r = builtIns();
        if (file == null) return r;
        if (!Files.isRegularFile(file)) throw new IOException("Rules file not found: " + file);
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
        }
        for (String key : new TreeSet<String>(p.stringPropertyNames())) {
            String value = p.getProperty(key);
            if (key.startsWith(NODE_TYPE_PREFIX) && key.length() > NODE_TYPE_PREFIX.length()) {
                String type = key.substring(NODE_TYPE_PREFIX.length());
                NodeRule previous = r.nodeRules.get(type);
                if (previous != null) {
                    LOG.info("Rules file: " + safe(key) + " replaces " + previous.getClass().getSimpleName());
                }
                r.register(new ShapeNodeRule(type, Shape.of(value, key)));
            } else if ("fallback.shape".equals(key)) {
                r.fallbackShape = Shape.of(value, key);
            } else if ("diagram.versions".equals(key)) {
                replace(r.versions, value);
            } else if ("ignore.elements".equals(key)) {
                replace(r.ignoreElements, value);
            } else if ("ignore.attributes".equals(key)) {
                replace(r.ignoreAttributes, value);
            } else {
                LOG.warning("Rules file: unknown key ignored: " + safe(key));
            }
        }
        r.source = file.toString();
        return r;
    }

    public void register(NodeRule rule) { nodeRules.put(rule.nodeType(), rule); }

    public void setLinkRule(LinkRule rule) { linkRule = rule; }

    /** Rule for the NodeType. Types without a rule get the fallback rule, which reports them. */
    public NodeRule nodeRule(String nodeType) {
        NodeRule rule = nodeRules.get(nodeType);
        return rule != null ? rule : new UnknownNodeRule(fallbackShape);
    }

    public LinkRule linkRule() { return linkRule; }

    public boolean isKnownVersion(String version) { return versions.contains(version); }

    public boolean ignoresElement(String name) { return ignoreElements.contains(name); }

    /** Entries are "Element@attr" or "*@attr". */
    public boolean ignoresAttribute(String element, String attr) {
        return ignoreAttributes.contains(element + "@" + attr) || ignoreAttributes.contains("*@" + attr);
    }

    public String source() { return source; }

    private static void replace(Set<String> target, String commaList) {
        target.clear();
        for (String s : commaList.split(",")) if (!s.trim().isEmpty()) target.add(s.trim());
    }
}
