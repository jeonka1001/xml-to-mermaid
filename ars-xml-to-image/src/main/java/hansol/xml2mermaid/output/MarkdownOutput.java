package hansol.xml2mermaid.output;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.model.Flowchart;

/** .md: Mermaid source in a ```mermaid block (rendered by GitLab, GitHub, Confluence, ...). */
final class MarkdownOutput implements OutputWriter {
    @Override public void write(Flowchart chart, OutputRequest r) throws Exception {
        String md = "# " + MermaidSyntax.singleLine(r.sourceName) + "\n\n```mermaid\n"
            + MermaidSyntax.flowchart(chart, r) + "```\n";
        TextFiles.write(r.output, md, r.overwrite);
        LOG.info("Markdown: " + r.output);
    }
}
