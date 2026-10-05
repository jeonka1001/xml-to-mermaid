package hansol.xml2mermaid.output;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Output format, selected by the output file extension (CLI) or by checkboxes (GUI). */
public enum OutputFormat {
    MMD("mmd", false), MD("md", false), PNG("png", true), JPG("jpg", true),
    SVG("svg", true), PDF("pdf", true);

    /** File extension without the dot. */
    public final String extension;
    /** Image formats need Node.js and Mermaid CLI. */
    public final boolean image;

    OutputFormat(String extension, boolean image) {
        this.extension = extension;
        this.image = image;
    }

    public static OutputFormat fromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot <= 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if ("jpeg".equals(ext)) return JPG;
        for (OutputFormat f : values()) if (f.extension.equals(ext)) return f;
        throw new IllegalArgumentException("Output extension must be mmd, md, png, jpg, jpeg, svg or pdf");
    }

    /** Files this format creates besides the convert log; checked before writing anything. */
    public List<Path> plannedFiles(OutputRequest r) {
        return image ? Arrays.asList(r.output, r.mermaidFile(), r.renderLog())
                     : Collections.singletonList(r.output);
    }

    /** renderer is required for image formats and ignored otherwise. */
    public OutputWriter createWriter(ImageRenderer renderer) {
        switch (this) {
            case MMD: return new MermaidOutput();
            case MD: return new MarkdownOutput();
            case JPG: return new JpegOutput(renderer);
            default: return new ImageOutput(renderer, this);
        }
    }
}
