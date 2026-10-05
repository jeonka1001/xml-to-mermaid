package hansol.xml2mermaid.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conversion result, independent of XML and of the output format. Keeps XML order. */
public final class Flowchart {
    private final Map<String, FlowNode> nodes = new LinkedHashMap<String, FlowNode>();
    private final List<FlowLink> links = new ArrayList<FlowLink>();

    public void addNode(FlowNode node) { nodes.put(node.xmlId, node); }

    public void addLink(FlowLink link) { links.add(link); }

    public boolean hasNode(String xmlId) { return nodes.containsKey(xmlId); }

    public Collection<FlowNode> nodes() { return Collections.unmodifiableCollection(nodes.values()); }

    public List<FlowLink> links() { return Collections.unmodifiableList(links); }
}
