package hansol.xml2mermaid.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** One line per record: "[timestamp] LEVEL message". Stack traces only in the file. */
final class LineFormatter extends Formatter {
    private final boolean timestamps;

    LineFormatter(boolean timestamps) { this.timestamps = timestamps; }

    @Override public String format(LogRecord r) {
        StringBuilder s = new StringBuilder();
        if (timestamps) {
            s.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(r.getMillis())))
             .append(' ');
        }
        s.append(String.format("%-5s ", Logs.levelName(r.getLevel())));
        s.append(Logs.escapeControls(formatMessage(r)));
        s.append(System.lineSeparator());
        if (timestamps && r.getThrown() != null) {
            StringWriter trace = new StringWriter();
            r.getThrown().printStackTrace(new PrintWriter(trace));
            s.append(trace);
        }
        return s.toString();
    }
}
