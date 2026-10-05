package hansol.xml2mermaid.rule;

import hansol.xml2mermaid.model.Shape;

/** Rule created from a rules file line "nodetype.X=shape": default behavior with that shape. */
public final class ShapeNodeRule extends AbstractNodeRule {
    public ShapeNodeRule(String nodeType, Shape shape) { super(nodeType, shape); }
}
