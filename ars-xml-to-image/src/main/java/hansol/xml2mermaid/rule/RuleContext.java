package hansol.xml2mermaid.rule;

import static hansol.xml2mermaid.log.Logs.safe;

import hansol.xml2mermaid.reader.XmlElement;
import hansol.xml2mermaid.report.Report;
import java.util.List;
import java.util.Set;

/** What a rule needs while converting one element: its location, reporting and shared checks. */
public final class RuleContext {
    private final RuleRegistry rules;
    private final Report report;
    private final String location;

    public RuleContext(RuleRegistry rules, Report report, String location) {
        this.rules = rules;
        this.report = report;
        this.location = location;
    }

    /** Same registry and report at another location, e.g. "Node[Id=5]". */
    public RuleContext at(String newLocation) { return new RuleContext(rules, report, newLocation); }

    public String location() { return location; }

    /** Structure outside the confirmed rules (fails with --strict). */
    public void unknown(String key, String consequence) { report.unknown(key, location, consequence); }

    /** Data condition worth reviewing (never fails). */
    public void notice(String key, String consequence) { report.notice(key, location, consequence); }

    /**
     * Reports attributes and child elements outside the known sets (UNKNOWN, or
     * IGNORED when listed in the rules file) and text directly inside the element.
     */
    public void checkStructure(XmlElement e, Set<String> knownAttributes, Set<String> knownChildren) {
        String name = e.name();
        for (String attr : e.attributes().keySet()) {
            if (knownAttributes.contains(attr)) continue;
            String key = "Attribute " + safe(name) + "@" + safe(attr);
            if (rules.ignoresAttribute(name, attr)) report.ignored(key, location);
            else report.unknown(key, location, "skipped");
        }
        for (XmlElement child : e.children()) {
            if (knownChildren.contains(child.name())) continue;
            String key = "Element " + safe(name) + "/" + safe(child.name());
            if (rules.ignoresElement(child.name())) report.ignored(key, location);
            else report.unknown(key, location, "skipped");
        }
        if (!e.ownText().isEmpty()) report.unknown("Text content in " + safe(name), location, "skipped");
    }

    /** First direct child with the name, or null. A repeated child is reported. */
    public XmlElement single(XmlElement owner, String childName) {
        List<XmlElement> list = owner.children(childName);
        if (list.size() > 1) {
            unknown("Multiple " + safe(owner.name()) + "/" + childName, "first " + childName + " used");
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /** Text of the owner's direct child Text, or "" when absent. */
    public String text(XmlElement owner) {
        XmlElement text = single(owner, "Text");
        if (text == null) return "";
        for (String attr : text.attributes().keySet()) {
            String key = "Attribute Text@" + safe(attr);
            if (rules.ignoresAttribute("Text", attr)) report.ignored(key, location);
            else report.unknown(key, location, "skipped");
        }
        for (XmlElement child : text.children()) {
            unknown("Element " + safe(owner.name()) + "/Text/" + safe(child.name()),
                "its text is included in the label");
        }
        return text.textContent();
    }
}
