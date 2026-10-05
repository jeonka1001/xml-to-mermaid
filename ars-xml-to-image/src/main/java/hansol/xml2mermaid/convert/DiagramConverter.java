package hansol.xml2mermaid.convert;

import static hansol.xml2mermaid.log.Logs.safe;

import hansol.xml2mermaid.model.FlowLink;
import hansol.xml2mermaid.model.FlowNode;
import hansol.xml2mermaid.model.Flowchart;
import hansol.xml2mermaid.reader.XmlElement;
import hansol.xml2mermaid.report.Report;
import hansol.xml2mermaid.rule.LinkRule;
import hansol.xml2mermaid.rule.NodeRule;
import hansol.xml2mermaid.rule.RuleContext;
import hansol.xml2mermaid.rule.RuleRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies the rules to the Diagram document structure:
 * Diagram(Version) / Nodes / Node and Diagram / Links / Link.
 * Per-Node and per-Link conversion is delegated to the rules in RuleRegistry.
 */
public final class DiagramConverter {
    static final Set<String> DIAGRAM_ATTRIBUTES = set("Version");
    static final Set<String> DIAGRAM_CHILDREN = set("Nodes", "Links");
    static final Set<String> NODES_CHILDREN = set("Node");
    static final Set<String> LINKS_CHILDREN = set("Link");
    static final Set<String> NONE = Collections.emptySet();

    private DiagramConverter() {}

    public static Flowchart convert(XmlElement root, RuleRegistry rules, Report report) {
        if (!"Diagram".equals(root.name())) {
            throw new IllegalArgumentException("Expected root element: Diagram");
        }
        RuleContext ctx = new RuleContext(rules, report, "Diagram");
        ctx.checkStructure(root, DIAGRAM_ATTRIBUTES, DIAGRAM_CHILDREN);
        String version = root.attribute("Version");
        if (version.isEmpty()) {
            ctx.unknown("Diagram without Version", "converted with current rules");
        } else if (!rules.isKnownVersion(version)) {
            ctx.unknown("Diagram Version '" + safe(version) + "'", "converted with current rules");
        }

        List<XmlElement> nodeSections = root.children("Nodes");
        List<XmlElement> linkSections = root.children("Links");
        if (nodeSections.isEmpty()) throw new IllegalArgumentException("Missing Diagram/Nodes");
        if (nodeSections.size() > 1) ctx.unknown("Multiple Diagram/Nodes sections", "all sections merged");
        if (linkSections.size() > 1) ctx.unknown("Multiple Diagram/Links sections", "all sections merged");

        Flowchart chart = new Flowchart();
        for (XmlElement section : nodeSections) {
            ctx.at("Nodes").checkStructure(section, NONE, NODES_CHILDREN);
            for (XmlElement node : section.children("Node")) convertNode(node, rules, ctx, chart);
        }
        if (chart.nodes().isEmpty()) throw new IllegalArgumentException("No Node elements found");

        Set<String> linkIds = new HashSet<String>();
        int index = 0;
        for (XmlElement section : linkSections) {
            ctx.at("Links").checkStructure(section, NONE, LINKS_CHILDREN);
            for (XmlElement link : section.children("Link")) {
                convertLink(link, ++index, linkIds, rules.linkRule(), ctx, chart);
            }
        }
        return chart;
    }

    private static void convertNode(XmlElement node, RuleRegistry rules, RuleContext parent,
                                    Flowchart chart) {
        String id = node.attribute("Id");
        if (id.isEmpty()) throw new IllegalArgumentException("Missing Id on Node");
        if (chart.hasNode(id)) throw new IllegalArgumentException("Duplicate Node Id: " + safe(id));
        RuleContext ctx = parent.at("Node[Id=" + safe(id) + "]");
        NodeRule rule = rules.nodeRule(node.attribute("NodeType"));
        ctx.checkStructure(node, rule.knownAttributes(), rule.knownChildren());
        FlowNode converted = rule.convert(node, ctx);
        if (!id.equals(converted.xmlId)) {
            throw new IllegalStateException(rule.getClass().getSimpleName()
                + " returned xmlId " + safe(converted.xmlId) + " for " + ctx.location());
        }
        chart.addNode(converted);
    }

    private static void convertLink(XmlElement link, int index, Set<String> linkIds, LinkRule rule,
                                    RuleContext parent, Flowchart chart) {
        String linkId = link.attribute("Id");
        RuleContext ctx = parent.at(linkId.isEmpty() ? "Link[#" + index + "]" : "Link[Id=" + safe(linkId) + "]");
        if (linkId.isEmpty()) ctx.notice("Link without Id", "converted");
        else if (!linkIds.add(linkId)) ctx.notice("Duplicate Link Id", "converted");
        ctx.checkStructure(link, rule.knownAttributes(), rule.knownChildren());
        FlowLink converted = rule.convert(link, ctx);
        if (!chart.hasNode(converted.from) || !chart.hasNode(converted.to)) {
            throw new IllegalArgumentException(ctx.location() + " references missing Node: "
                + safe(converted.from) + " -> " + safe(converted.to));
        }
        chart.addLink(converted);
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}
