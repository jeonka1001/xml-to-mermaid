package hansol.xml2mermaid.output;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.model.Flowchart;

/** .mmd: Mermaid source only. */
final class MermaidOutput implements OutputWriter {
    @Override public void write(Flowchart chart, OutputRequest r) throws Exception {
        TextFiles.write(r.output, MermaidSyntax.flowchart(chart, r), r.overwrite);
        LOG.info("Mermaid: " + r.output);
    }
}
