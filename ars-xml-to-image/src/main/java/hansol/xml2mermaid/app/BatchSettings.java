package hansol.xml2mermaid.app;

import hansol.xml2mermaid.output.OutputFormat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Options for one batch run. BatchConverter works on a copy. */
public final class BatchSettings {
    /** null: write next to each input file. */
    public Path outputDir;
    public Set<OutputFormat> formats = EnumSet.of(OutputFormat.MD);
    public String direction = "TD";
    public boolean showId, strict, verbose;
    /** Keep base.mmd next to the outputs (otherwise it is a temporary file). */
    public boolean keepMermaid;
    public ConflictPolicy conflict = ConflictPolicy.OVERWRITE;
    public Path rulesFile, mermaidConfig, puppeteerConfig;
    public long timeoutSeconds = 120;
    public int scale = 1;
    public int jpegQuality = 90;

    BatchSettings copy() {
        BatchSettings c = new BatchSettings();
        c.outputDir = outputDir;
        c.formats = formats.isEmpty() ? EnumSet.noneOf(OutputFormat.class) : EnumSet.copyOf(formats);
        c.direction = direction;
        c.showId = showId; c.strict = strict; c.verbose = verbose; c.keepMermaid = keepMermaid;
        c.conflict = conflict;
        c.rulesFile = rulesFile; c.mermaidConfig = mermaidConfig; c.puppeteerConfig = puppeteerConfig;
        c.timeoutSeconds = timeoutSeconds; c.scale = scale; c.jpegQuality = jpegQuality;
        return c;
    }

    boolean hasImageFormat() {
        for (OutputFormat f : formats) if (f.image) return true;
        return false;
    }

    /** Text formats first, so the .md exists even when rendering fails. */
    List<OutputFormat> orderedFormats() {
        List<OutputFormat> list = new ArrayList<OutputFormat>();
        for (OutputFormat f : formats) if (!f.image) list.add(f);
        for (OutputFormat f : formats) if (f.image) list.add(f);
        return list;
    }
}
