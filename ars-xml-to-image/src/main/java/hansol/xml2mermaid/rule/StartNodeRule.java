package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.Shape;

/** Confirmed rule: StartNode is drawn as a rounded (stadium) node. */
public final class StartNodeRule extends AbstractNodeRule {
    public StartNodeRule() { super("StartNode", Shape.STADIUM); }
}
