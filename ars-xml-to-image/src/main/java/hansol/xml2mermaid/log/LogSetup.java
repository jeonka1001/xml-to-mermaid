package hansol.xml2mermaid.log;

import static hansol.xml2mermaid.log.Logs.DETAIL;
import static hansol.xml2mermaid.log.Logs.LOG;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;

/** Attaches the convert log file and console handlers for one run. */
public final class LogSetup {
    private LogSetup() {}

    public static List<Handler> install(Path file, boolean overwrite, boolean verbose) throws IOException {
        return install(file, overwrite, verbose, true);
    }

    /** console=false for the GUI, which keeps its own handler on Logs.LOG. */
    public static List<Handler> install(Path file, boolean overwrite, boolean verbose, boolean console)
            throws IOException {
        OutputStream out = overwrite
            ? Files.newOutputStream(file, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            : Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Handler fileHandler = new FlushingHandler(out, new LineFormatter(true), true);
        try {
            fileHandler.setEncoding("UTF-8"); // independent of the Windows console code page
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        Level fileLevel = verbose ? Level.FINE : Level.INFO;
        fileHandler.setLevel(fileLevel);
        List<Handler> handlers = new ArrayList<Handler>();
        handlers.add(fileHandler);
        if (console) {
            // Console keeps the platform encoding so Windows cmd displays it correctly.
            Handler consoleHandler = new FlushingHandler(System.err, new LineFormatter(false), false);
            consoleHandler.setLevel(Level.INFO);
            handlers.add(consoleHandler);
        }
        LOG.setUseParentHandlers(false);
        LOG.setLevel(fileLevel);
        for (Handler h : handlers) LOG.addHandler(h);
        DETAIL.setUseParentHandlers(false);
        DETAIL.setLevel(fileLevel);
        DETAIL.addHandler(fileHandler);
        return handlers;
    }

    public static void uninstall(List<Handler> handlers) {
        for (Handler h : handlers) {
            LOG.removeHandler(h);
            DETAIL.removeHandler(h);
            if (h instanceof FlushingHandler && ((FlushingHandler) h).ownsStream) h.close();
            else h.flush();
        }
    }
}
