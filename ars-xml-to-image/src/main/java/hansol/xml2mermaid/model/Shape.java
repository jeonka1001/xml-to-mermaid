package hansol.xml2mermaid.model;

import java.util.Locale;

/** Mermaid 11.x classic flowchart node shapes, written with quoted labels. */
public enum Shape {
    RECT("[\"", "\"]"), ROUND("(\"", "\")"), STADIUM("([\"", "\"])"),
    SUBROUTINE("[[\"", "\"]]"), CYLINDER("[(\"", "\")]"), CIRCLE("((\"", "\"))"),
    DOUBLECIRCLE("(((\"", "\")))"), ASYMMETRIC(">\"", "\"]"), DIAMOND("{\"", "\"}"),
    HEXAGON("{{\"", "\"}}"), PARALLELOGRAM("[/\"", "\"/]"),
    PARALLELOGRAM_ALT("[\\\"", "\"\\]"), TRAPEZOID("[/\"", "\"\\]"),
    TRAPEZOID_ALT("[\\\"", "\"/]");

    public final String open, close;

    Shape(String open, String close) { this.open = open; this.close = close; }

    /** Name used in the rules file, e.g. "parallelogram-alt". */
    public String key() { return name().toLowerCase(Locale.ROOT).replace('_', '-'); }

    public static Shape of(String key, String property) {
        for (Shape s : values()) if (s.key().equals(key.trim().toLowerCase(Locale.ROOT))) return s;
        StringBuilder all = new StringBuilder();
        for (Shape s : values()) all.append(all.length() == 0 ? "" : ", ").append(s.key());
        throw new IllegalArgumentException("Invalid shape '" + key + "' for " + property
            + "; allowed: " + all);
    }
}
