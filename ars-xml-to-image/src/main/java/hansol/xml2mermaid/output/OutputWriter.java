package hansol.xml2mermaid.output;

import hansol.xml2mermaid.model.Flowchart;

/** Writes a converted Flowchart in one output format (strategy). Created by OutputFormat. */
public interface OutputWriter {
    void write(Flowchart chart, OutputRequest request) throws Exception;
}
