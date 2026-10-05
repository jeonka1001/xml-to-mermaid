package hansol.xml2mermaid.report;

import static hansol.xml2mermaid.log.Logs.DETAIL;
import static hansol.xml2mermaid.log.Logs.LOG;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Collects findings: each occurrence goes to the detail log, and logSummary()
 * aggregates them per kind. The summary is the list of rules to add next.
 */
public final class Report {
    static final int MAX_EXAMPLES = 5;

    private static final class Entry {
        final Category category;
        final String key, consequence;
        int count;
        final List<String> examples = new ArrayList<String>();

        Entry(Category category, String key, String consequence) {
            this.category = category; this.key = key; this.consequence = consequence;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private final boolean verbose;

    public Report(boolean verbose) { this.verbose = verbose; }

    public void unknown(String key, String location, String consequence) {
        add(Category.UNKNOWN, key, location, consequence);
    }

    public void notice(String key, String location, String consequence) {
        add(Category.NOTICE, key, location, consequence);
    }

    public void ignored(String key, String location) {
        add(Category.IGNORED, key, location, "ignored by rules");
    }

    private void add(Category c, String key, String location, String consequence) {
        String id = c + "|" + key;
        Entry e = entries.get(id);
        if (e == null) entries.put(id, e = new Entry(c, key, consequence));
        e.count++;
        if (e.examples.size() < MAX_EXAMPLES) e.examples.add(location);
        if (c == Category.IGNORED) {
            if (verbose) DETAIL.fine("[IGNORED] " + key + " at " + location);
        } else {
            DETAIL.log(c == Category.UNKNOWN ? Level.WARNING : Level.INFO,
                "[" + c + "] " + key + " at " + location + " -> " + consequence);
        }
    }

    /** Number of distinct finding kinds in the category. */
    public int kinds(Category c) {
        int n = 0;
        for (Entry e : entries.values()) if (e.category == c) n++;
        return n;
    }

    public boolean hasUnknownRules() {
        for (Entry e : entries.values()) if (e.category == Category.UNKNOWN) return true;
        return false;
    }

    public void logSummary() {
        for (Category c : Category.values()) {
            List<Entry> list = new ArrayList<Entry>();
            for (Entry e : entries.values()) if (e.category == c) list.add(e);
            if (list.isEmpty()) continue;
            Level level = c == Category.UNKNOWN ? Level.WARNING : Level.INFO;
            LOG.log(level, "[" + c + " summary] " + list.size() + " kind(s)");
            for (Entry e : list) {
                StringBuilder ex = new StringBuilder();
                for (String s : e.examples) ex.append(ex.length() == 0 ? "" : ", ").append(s);
                if (e.count > e.examples.size()) ex.append(", ...");
                LOG.log(level, "  " + e.key + " : " + e.count + " (" + ex + ")"
                    + (c == Category.IGNORED ? "" : " -> " + e.consequence));
            }
        }
        if (entries.isEmpty()) LOG.info("No unknown rules found");
    }
}
