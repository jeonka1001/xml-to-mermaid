package hansol.xml2mermaid.log;

import java.io.OutputStream;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/** StreamHandler that flushes every record, so the log is complete even on a crash. */
final class FlushingHandler extends StreamHandler {
    final boolean ownsStream;

    FlushingHandler(OutputStream out, Formatter formatter, boolean ownsStream) {
        super(out, formatter);
        this.ownsStream = ownsStream;
    }

    @Override public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
    }
}
