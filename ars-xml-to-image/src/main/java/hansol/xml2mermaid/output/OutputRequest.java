package hansol.xml2mermaid.output;

import java.nio.file.Path;

/** Output path, intermediate/log paths and display options for writing one file. */
public final class OutputRequest {
    public final Path output;
    public final String direction;
    public final boolean showId;
    public final boolean overwrite;
    /** Input file name, written into the generated header. */
    public final String sourceName;
    private final Path mermaidFile, convertLog, renderLog;

    public OutputRequest(Path output, Path mermaidFile, Path convertLog, Path renderLog,
                         String direction, boolean showId, boolean overwrite, String sourceName) {
        this.output = output;
        this.mermaidFile = mermaidFile;
        this.convertLog = convertLog;
        this.renderLog = renderLog;
        this.direction = direction;
        this.showId = showId;
        this.overwrite = overwrite;
        this.sourceName = sourceName;
    }

    /** CLI layout: result.png -> result.mmd, result.png.convert.log, result.png.render.log */
    public static OutputRequest besideOutput(Path output, String direction, boolean showId,
                                             boolean overwrite, String sourceName) {
        String name = output.getFileName().toString();
        return new OutputRequest(output,
            output.resolveSibling(name.substring(0, name.lastIndexOf('.')) + ".mmd"),
            output.resolveSibling(name + ".convert.log"), output.resolveSibling(name + ".render.log"),
            direction, showId, overwrite, sourceName);
    }

    /** Intermediate Mermaid file used for image output. */
    public Path mermaidFile() { return mermaidFile; }

    public Path convertLog() { return convertLog; }

    public Path renderLog() { return renderLog; }
}
