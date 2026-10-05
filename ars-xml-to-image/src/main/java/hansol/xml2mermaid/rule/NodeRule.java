package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.FlowNode;
import hansol.xml2mermaid.reader.XmlElement;
import java.util.Set;

/**
 * Conversion rule for one NodeType (strategy). RuleRegistry picks the rule by
 * Node@NodeType; the converter checks the Node's structure against
 * knownAttributes()/knownChildren() and then calls convert().
 *
 * Implement by extending AbstractNodeRule, then register in RuleRegistry.builtIns().
 */
public interface NodeRule {
    /** Node@NodeType value handled by this rule; null for the fallback rule. */
    String nodeType();

    /** Node attributes this rule understands. Others are reported as UNKNOWN. */
    Set<String> knownAttributes();

    /** Direct child elements of Node this rule understands. Others are reported as UNKNOWN. */
    Set<String> knownChildren();

    /** Converts the Node. The returned FlowNode's xmlId must be the Node@Id. */
    FlowNode convert(XmlElement node, RuleContext ctx);
}
