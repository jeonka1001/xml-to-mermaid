package hansol.xml2mermaid.app;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.convert.DiagramConverter;
import hansol.xml2mermaid.model.Flowchart;
import hansol.xml2mermaid.reader.DiagramXmlReader;
import hansol.xml2mermaid.report.Report;
import hansol.xml2mermaid.rule.RuleRegistry;
import java.nio.file.Path;

/** One XML file -> Flowchart + findings. Shared by the CLI and the batch converter. */
public final class XmlConverter {
    private XmlConverter() {}

    public static final class Result {
        public final Flowchart chart;
        public final Report report;

        Result(Flowchart chart, Report report) { this.chart = chart; this.report = report; }
    }

    public static Result convert(Path input, RuleRegistry rules, boolean verbose) throws Exception {
        Report report = new Report(verbose);
        Flowchart chart = DiagramConverter.convert(DiagramXmlReader.read(input), rules, report);
        LOG.info("Parsed " + chart.nodes().size() + " nodes, " + chart.links().size() + " links");
        report.logSummary();
        return new Result(chart, report);
    }
}
