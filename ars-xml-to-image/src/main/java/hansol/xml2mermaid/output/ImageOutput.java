package hansol.xml2mermaid.output;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.model.Flowchart;

/** .png/.svg/.pdf: writes the .mmd, then renders it with Mermaid CLI. */
final class ImageOutput implements OutputWriter {
    private final ImageRenderer renderer;
    private final OutputFormat format;

    ImageOutput(ImageRenderer renderer, OutputFormat format) {
        if (renderer == null) throw new IllegalArgumentException("Image output requires a renderer");
        this.renderer = renderer;
        this.format = format;
    }

    @Override public void write(Flowchart chart, OutputRequest r) throws Exception {
        // Several image formats of one run share the .mmd; the caller checked it beforehand.
        TextFiles.write(r.mermaidFile(), MermaidSyntax.flowchart(chart, r), true);
        LOG.info("Mermaid: " + r.mermaidFile());
        renderer.render(r.mermaidFile(), r.output, r.renderLog(), format, r.overwrite);
        LOG.info("Image: " + r.output);
        LOG.info("Render log: " + r.renderLog());
    }
}
