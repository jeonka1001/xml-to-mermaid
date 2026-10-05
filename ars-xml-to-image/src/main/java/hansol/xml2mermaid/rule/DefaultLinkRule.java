package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.FlowLink;
import hansol.xml2mermaid.reader.XmlElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Confirmed rule: Link/Origin@Id -> Link/Destination@Id, labeled with Link/Text. */
public class DefaultLinkRule implements LinkRule {
    protected static final Set<String> LINK_ATTRIBUTES = set("Id");
    protected static final Set<String> LINK_CHILDREN = set("Text", "Origin", "Destination");
    protected static final Set<String> ENDPOINT_ATTRIBUTES = set("Id");

    @Override public Set<String> knownAttributes() { return LINK_ATTRIBUTES; }

    @Override public Set<String> knownChildren() { return LINK_CHILDREN; }

    @Override public FlowLink convert(XmlElement link, RuleContext ctx) {
        XmlElement origin = ctx.single(link, "Origin");
        XmlElement destination = ctx.single(link, "Destination");
        if (origin == null || destination == null) {
            throw new IllegalArgumentException(ctx.location() + " is missing Origin or Destination");
        }
        return new FlowLink(endpoint(origin, ctx), endpoint(destination, ctx), label(link, ctx));
    }

    /** Edge label. Default: text of the direct child Text; empty draws a plain arrow. */
    protected String label(XmlElement link, RuleContext ctx) {
        return ctx.text(link);
    }

    private String endpoint(XmlElement end, RuleContext ctx) {
        RuleContext at = ctx.at(ctx.location() + "/" + end.name());
        at.checkStructure(end, ENDPOINT_ATTRIBUTES, Collections.<String>emptySet());
        String id = end.attribute("Id");
        if (id.isEmpty()) throw new IllegalArgumentException("Missing Id on " + at.location());
        return id;
    }

    protected static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}
