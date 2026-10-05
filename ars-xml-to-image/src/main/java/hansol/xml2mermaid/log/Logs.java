package hansol.xml2mermaid.log;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared loggers. LOG goes to console and file; DETAIL (per-occurrence findings) only to file. */
public final class Logs {
    public static final Logger LOG = Logger.getLogger("hansol.xml2mermaid");
    public static final Logger DETAIL = Logger.getLogger("hansol.xml2mermaid-detail");

    private Logs() {}

    /** XML-derived value for log messages: control characters escaped, length capped. */
    public static String safe(String value) {
        String v = value.length() > 120 ? value.substring(0, 120) + "..." : value;
        return escapeControls(v);
    }

    /** ERROR / WARN / INFO / DEBUG */
    public static String levelName(Level l) {
        if (l.intValue() >= Level.SEVERE.intValue()) return "ERROR";
        if (l.intValue() >= Level.WARNING.intValue()) return "WARN";
        if (l.intValue() >= Level.INFO.intValue()) return "INFO";
        return "DEBUG";
    }

    // Prevents forged log lines (OWASP log injection).
    public static String escapeControls(String v) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isISOControl(c) || c == ' ' || c == ' ') {
                s.append(String.format("\\u%04x", (int) c));
            } else {
                s.append(c);
            }
        }
        return s.toString();
    }
}
