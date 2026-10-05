package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.Shape;

/** Confirmed rule: ScriptNode is drawn as a rectangle. Its Script is not executed or summarized. */
public final class ScriptNodeRule extends AbstractNodeRule {
    public ScriptNodeRule() { super("ScriptNode", Shape.RECT); }
}
