package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.FlowLink;
import hansol.xml2mermaid.reader.XmlElement;
import java.util.Set;

/** Conversion rule for Link elements (strategy). Replace via RuleRegistry.setLinkRule(). */
public interface LinkRule {
    /** Link attributes this rule understands. Others are reported as UNKNOWN. */
    Set<String> knownAttributes();

    /** Direct child elements of Link this rule understands. Others are reported as UNKNOWN. */
    Set<String> knownChildren();

    /** Converts the Link. Endpoints are Node@Id values; the converter checks that they exist. */
    FlowLink convert(XmlElement link, RuleContext ctx);
}
