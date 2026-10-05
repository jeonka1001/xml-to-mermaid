package hansol.xml2mermaid.report;

/** Classification of a finding recorded during conversion. */
public enum Category {
    /** Structure outside the confirmed rules. Fails the run with --strict. */
    UNKNOWN,
    /** Data condition worth reviewing. Never fails the run. */
    NOTICE,
    /** Known structure intentionally not used (rules file ignore.*). */
    IGNORED
}
