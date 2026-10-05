package hansol.xml2mermaid.model;

/** A flowchart node. xmlId is the original Node@Id; output assigns Mermaid IDs. */
public final class FlowNode {
    public final String xmlId, label;
    public final Shape shape;

    public FlowNode(String xmlId, String label, Shape shape) {
        this.xmlId = xmlId; this.label = label; this.shape = shape;
    }
}
