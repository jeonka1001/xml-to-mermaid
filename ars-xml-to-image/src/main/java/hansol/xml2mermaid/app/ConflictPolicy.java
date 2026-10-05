package hansol.xml2mermaid.app;

/** What the batch converter does when an output file already exists. */
public enum ConflictPolicy {
    /** Replace existing files. */
    OVERWRITE,
    /** Leave the input unconverted. */
    SKIP,
    /** Use "name (2)", "name (3)", ... */
    RENAME
}
