package hansol.xml2mermaid.rule;

import static hansol.xml2mermaid.log.Logs.safe;

import hansol.xml2mermaid.model.FlowNode;
import hansol.xml2mermaid.model.Shape;
import hansol.xml2mermaid.reader.XmlElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Base NodeRule: label from the direct child Text, one fixed shape, and the
 * confirmed Node structure (attributes Id/NodeType, child Text).
 * Override label(), shape(), knownAttributes() or knownChildren() as needed.
 *
 * Example for a new NodeType with an extra child element:
 * <pre>
 * public final class MenuNodeRule extends AbstractNodeRule {
 *     public MenuNodeRule() { super("MenuNode", Shape.DIAMOND); }
 *     &#64;Override public Set&lt;String&gt; knownChildren() { return set("Text", "Timeout"); }
 * }
 * </pre>
 */
public abstract class AbstractNodeRule implements NodeRule {
    protected static final Set<String> NODE_ATTRIBUTES = set("Id", "NodeType");
    protected static final Set<String> NODE_CHILDREN = set("Text");

    private final String nodeType;
    private final Shape shape;

    protected AbstractNodeRule(String nodeType, Shape shape) {
        this.nodeType = nodeType;
        this.shape = shape;
    }

    @Override public String nodeType() { return nodeType; }

    @Override public Set<String> knownAttributes() { return NODE_ATTRIBUTES; }

    @Override public Set<String> knownChildren() { return NODE_CHILDREN; }

    @Override public FlowNode convert(XmlElement node, RuleContext ctx) {
        String id = node.attribute("Id");
        String label = label(node, ctx);
        if (label.isEmpty()) {
            label = "Node " + id;
            ctx.notice("Empty Node Text", "label '" + safe(label) + "' used");
        }
        return new FlowNode(id, label, shape(node, ctx));
    }

    /** Node label. Default: text of the direct child Text. */
    protected String label(XmlElement node, RuleContext ctx) {
        return ctx.text(node);
    }

    /** Node shape. Default: the shape given to the constructor. */
    protected Shape shape(XmlElement node, RuleContext ctx) {
        return shape;
    }

    protected final Shape defaultShape() { return shape; }

    protected static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}
