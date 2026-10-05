package hansol.xml2mermaid.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read-only copy of one XML element, decoupled from the DOM API.
 * Rules and the converter work only with this type.
 */
public final class XmlElement {
    private final String name;
    private final Map<String, String> attributes;
    private final String ownText;
    private final String textContent;
    private final List<XmlElement> children;

    XmlElement(String name, Map<String, String> attributes, String ownText, String textContent,
               List<XmlElement> children) {
        this.name = name;
        this.attributes = Collections.unmodifiableMap(attributes);
        this.ownText = ownText;
        this.textContent = textContent;
        this.children = Collections.unmodifiableList(children);
    }

    public String name() { return name; }

    /** Attributes in document order, without xmlns declarations. */
    public Map<String, String> attributes() { return attributes; }

    /** Trimmed attribute value, or "" when absent. */
    public String attribute(String attr) {
        String v = attributes.get(attr);
        return v == null ? "" : v.trim();
    }

    /** Non-whitespace text directly inside this element (not in children), trimmed. */
    public String ownText() { return ownText; }

    /** All text inside this element including descendants, trimmed. */
    public String textContent() { return textContent; }

    public List<XmlElement> children() { return children; }

    /** Direct children with the given name, in document order. */
    public List<XmlElement> children(String childName) {
        List<XmlElement> list = new ArrayList<XmlElement>();
        for (XmlElement c : children) if (c.name.equals(childName)) list.add(c);
        return list;
    }
}
