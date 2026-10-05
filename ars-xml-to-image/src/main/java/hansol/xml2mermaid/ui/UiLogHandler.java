package hansol.xml2mermaid.ui;

import hansol.xml2mermaid.log.Logs;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Shows Logs.LOG records in the window's log area. Per-occurrence details stay in the log files. */
final class UiLogHandler extends Handler {
    private static final int MAX_CHARS = 300000, KEEP_CHARS = 200000;

    private final Formatter formatter = new SimpleFormatter();
    private volatile JTextArea target;
    private volatile String prefix = "";

    void attach(JTextArea area) { target = area; }

    /** Prepended to each line, e.g. "[a.xml] " while that file is converted. */
    void setPrefix(String value) { prefix = value; }

    @Override public void publish(LogRecord record) {
        final JTextArea area = target;
        if (area == null || !isLoggable(record)) return;
        final String line = String.format("%-5s ", Logs.levelName(record.getLevel())) + prefix
            + Logs.escapeControls(formatter.formatMessage(record)) + "\n";
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                area.append(line);
                int length = area.getDocument().getLength();
                if (length > MAX_CHARS) area.replaceRange("", 0, length - KEEP_CHARS);
                area.setCaretPosition(area.getDocument().getLength());
            }
        });
    }

    @Override public void flush() {}

    @Override public void close() {}
}
