package hansol.xml2mermaid.model;

/** A flowchart edge between two Node@Id values. An empty label draws a plain arrow. */
public final class FlowLink {
    public final String from, to, label;

    public FlowLink(String from, String to, String label) {
        this.from = from; this.to = to; this.label = label;
    }
}
