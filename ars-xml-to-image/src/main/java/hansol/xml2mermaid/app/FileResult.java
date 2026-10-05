package hansol.xml2mermaid.app;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/** Outcome of converting one input file in a batch. */
public final class FileResult {
    public enum Status { DONE, DONE_WITH_UNKNOWN, STRICT_FAILED, FAILED, SKIPPED, CANCELLED }

    public final Status status;
    public final int nodes, links, unknownKinds;
    public final List<Path> outputs;
    /** null when nothing was logged (skipped/cancelled before start). */
    public final Path convertLog;
    public final String message;

    FileResult(Status status, int nodes, int links, int unknownKinds, List<Path> outputs,
               Path convertLog, String message) {
        this.status = status;
        this.nodes = nodes;
        this.links = links;
        this.unknownKinds = unknownKinds;
        this.outputs = Collections.unmodifiableList(outputs);
        this.convertLog = convertLog;
        this.message = message;
    }

    static FileResult of(Status status, Path convertLog, String message) {
        return new FileResult(status, 0, 0, 0, Collections.<Path>emptyList(), convertLog, message);
    }
}
