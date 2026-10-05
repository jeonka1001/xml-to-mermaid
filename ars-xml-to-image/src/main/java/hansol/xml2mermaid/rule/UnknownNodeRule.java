package hansol.xml2mermaid.rule;

import static hansol.xml2mermaid.log.Logs.safe;

import hansol.xml2mermaid.model.FlowNode;
import hansol.xml2mermaid.model.Shape;
import hansol.xml2mermaid.reader.XmlElement;

/** Fallback for a NodeType without a rule: reports it as UNKNOWN and draws the fallback shape. */
final class UnknownNodeRule extends AbstractNodeRule {
    UnknownNodeRule(Shape fallback) { super(null, fallback); }

    @Override public FlowNode convert(XmlElement node, RuleContext ctx) {
        String type = node.attribute("NodeType");
        String consequence = "drawn as " + defaultShape().key();
        if (type.isEmpty()) ctx.unknown("Node without NodeType", consequence);
        else ctx.unknown("NodeType '" + safe(type) + "'", consequence);
        return super.convert(node, ctx);
    }
}
