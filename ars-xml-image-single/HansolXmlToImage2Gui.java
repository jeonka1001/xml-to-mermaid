import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

/**
 * GUI for HansolXmlToImage2 (single-file version). Java 8+, Swing only.
 * Uses the conversion classes of HansolXmlToImage2.java in the same folder.
 *
 * Compile: javac -encoding UTF-8 *.java
 * Run:     java -Dhansol.rules=node-types.properties -Dmermaid.config=mermaid-config.json HansolXmlToImage2Gui
 *          (image output also needs -Dmermaid.cli=... and -Dnode.executable=..., as for the CLI)
 *
 * For input "a.xml" a batch writes a.md and the selected images (a.png, a.jpg, ...) to the
 * output folder, and the logs to <output folder>/logs/a.convert.log and a.<ext>.render.log.
 */
public final class HansolXmlToImage2Gui {
    private HansolXmlToImage2Gui() {}

    public static void main(String[] args) {
        final UiLogHandler handler = new UiLogHandler();
        handler.setLevel(Level.INFO);
        HansolXmlToImage2.LOG.setUseParentHandlers(false);
        HansolXmlToImage2.LOG.setLevel(Level.INFO);
        HansolXmlToImage2.LOG.addHandler(handler);
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // keep the default look and feel
                }
                new MainFrame(handler).setVisible(true);
            }
        });
    }

    // ------------------------------------------------------------------ batch conversion

    /** What the batch does when an output file already exists. */
    enum ConflictPolicy { OVERWRITE, SKIP, RENAME }

    /** Options for one batch run. Batch works on a copy. */
    static final class BatchSettings {
        /** null: write next to each input file. */
        Path outputDir;
        Set<HansolXmlToImage2.Format> formats = EnumSet.of(HansolXmlToImage2.Format.MD);
        String direction = "TD";
        boolean showId, strict, verbose;
        /** Keep base.mmd next to the outputs (otherwise it is a temporary file). */
        boolean keepMermaid;
        ConflictPolicy conflict = ConflictPolicy.OVERWRITE;
        Path rulesFile, mermaidConfig, puppeteerConfig;
        long timeoutSeconds = 120;
        int scale = 1, jpegQuality = 90;

        BatchSettings copy() {
            BatchSettings c = new BatchSettings();
            c.outputDir = outputDir;
            c.formats = formats.isEmpty()
                ? EnumSet.noneOf(HansolXmlToImage2.Format.class) : EnumSet.copyOf(formats);
            c.direction = direction;
            c.showId = showId; c.strict = strict; c.verbose = verbose; c.keepMermaid = keepMermaid;
            c.conflict = conflict;
            c.rulesFile = rulesFile; c.mermaidConfig = mermaidConfig; c.puppeteerConfig = puppeteerConfig;
            c.timeoutSeconds = timeoutSeconds; c.scale = scale; c.jpegQuality = jpegQuality;
            return c;
        }

        boolean hasImageFormat() {
            for (HansolXmlToImage2.Format f : formats) if (f.image) return true;
            return false;
        }

        /** Text formats first, so the .md exists even when rendering fails. */
        List<HansolXmlToImage2.Format> orderedFormats() {
            List<HansolXmlToImage2.Format> list = new ArrayList<HansolXmlToImage2.Format>();
            for (HansolXmlToImage2.Format f : formats) if (!f.image) list.add(f);
            for (HansolXmlToImage2.Format f : formats) if (f.image) list.add(f);
            return list;
        }
    }

    /** Outcome of converting one input file. */
    static final class FileResult {
        enum Status { DONE, DONE_WITH_UNKNOWN, STRICT_FAILED, FAILED, SKIPPED, CANCELLED }

        final Status status;
        final int nodes, links, unknownKinds;
        final List<Path> outputs;
        /** null when nothing was logged (skipped/cancelled before start). */
        final Path convertLog;
        final String message;

        FileResult(Status status, int nodes, int links, int unknownKinds, List<Path> outputs,
                   Path convertLog, String message) {
            this.status = status;
            this.nodes = nodes;
            this.links = links;
            this.unknownKinds = unknownKinds;
            this.outputs = Collections.unmodifiableList(outputs);
            this.convertLog = convertLog;
            this.message = message;
        }

        static FileResult of(Status status, Path convertLog, String message) {
            return new FileResult(status, 0, 0, 0, Collections.<Path>emptyList(), convertLog, message);
        }
    }

    /** Progress callbacks, called on the thread running Batch.run(). */
    interface BatchListener {
        void started(int index);

        void finished(int index, FileResult result);
    }

    /** Converts several XML files sequentially. A failing file does not stop the batch. */
    static final class Batch {
        private final BatchSettings settings;
        private volatile boolean cancelled;
        private volatile HansolXmlToImage2.Renderer renderer;

        Batch(BatchSettings settings) {
            if (settings.formats.isEmpty()) throw new IllegalArgumentException("No output format selected");
            this.settings = settings.copy();
        }

        /** Stops the running render; remaining files are reported as CANCELLED. Thread-safe. */
        void cancel() {
            cancelled = true;
            HansolXmlToImage2.Renderer r = renderer;
            if (r != null) r.cancel();
        }

        void run(List<Path> inputs, BatchListener listener) {
            HansolXmlToImage2.Rules rules;
            try {
                rules = HansolXmlToImage2.Rules.load(settings.rulesFile);
                HansolXmlToImage2.LOG.info("Rules: " + rules.source);
                if (settings.hasImageFormat()) {
                    renderer = HansolXmlToImage2.Renderer.create(settings.mermaidConfig, settings.puppeteerConfig,
                        settings.timeoutSeconds, settings.scale, settings.jpegQuality);
                }
            } catch (Exception e) {
                HansolXmlToImage2.LOG.severe("Cannot start: " + e.getMessage());
                for (int i = 0; i < inputs.size(); i++) {
                    listener.finished(i, FileResult.of(FileResult.Status.FAILED, null, e.getMessage()));
                }
                return;
            }
            Set<String> used = new HashSet<String>();
            for (int i = 0; i < inputs.size(); i++) {
                if (cancelled) {
                    listener.finished(i, FileResult.of(FileResult.Status.CANCELLED, null, "cancelled"));
                    continue;
                }
                listener.started(i);
                listener.finished(i, convertOne(inputs.get(i).toAbsolutePath().normalize(), rules, used));
            }
        }

        private FileResult convertOne(Path input, HansolXmlToImage2.Rules rules, Set<String> used) {
            Path dir = settings.outputDir != null ? settings.outputDir : input.getParent();
            List<HansolXmlToImage2.Format> formats = settings.orderedFormats();
            String base;
            Path logs = dir.resolve("logs");
            try {
                Files.createDirectories(logs);
                base = chooseBaseName(dir, baseName(input), formats, used);
            } catch (Exception e) {
                HansolXmlToImage2.LOG.severe(input.getFileName() + ": " + e.getMessage());
                return FileResult.of(FileResult.Status.FAILED, null, e.getMessage());
            }
            if (base == null) {
                HansolXmlToImage2.LOG.info(input.getFileName() + ": skipped, output already exists in " + dir);
                return FileResult.of(FileResult.Status.SKIPPED, null, "output already exists");
            }
            used.add(key(dir, base));

            Path convertLog = logs.resolve(base + ".convert.log");
            List<Handler> handlers;
            try {
                handlers = HansolXmlToImage2.LogSetup.install(convertLog, true, settings.verbose, false);
            } catch (IOException e) {
                return FileResult.of(FileResult.Status.FAILED, null, "cannot create log: " + e.getMessage());
            }
            List<Path> outputs = new ArrayList<Path>();
            Path temp = null;
            int nodes = 0, links = 0, unknown = 0;
            try {
                HansolXmlToImage2.LOG.info("Input: " + input);
                HansolXmlToImage2.Report report = new HansolXmlToImage2.Report(settings.verbose);
                HansolXmlToImage2.Diagram diagram = HansolXmlToImage2.DiagramParser.parse(input, rules, report);
                nodes = diagram.nodes.size();
                links = diagram.links.size();
                HansolXmlToImage2.LOG.info("Parsed " + nodes + " nodes, " + links + " links");
                report.logSummary();
                unknown = report.kinds(HansolXmlToImage2.Category.UNKNOWN);
                if (settings.strict && report.hasUnknownRules()) {
                    HansolXmlToImage2.LOG.severe("Unknown rules found and strict is set; no output written");
                    return new FileResult(FileResult.Status.STRICT_FAILED, nodes, links, unknown, outputs,
                        convertLog, unknown + " unknown rule kind(s)");
                }
                String source = input.getFileName().toString();
                String mermaid = HansolXmlToImage2.MermaidWriter.write(diagram, settings.direction,
                    settings.showId, source);
                Path mmd;
                if (settings.keepMermaid) {
                    mmd = dir.resolve(base + ".mmd");
                } else {
                    temp = Files.createTempDirectory(dir, ".hansol-");
                    mmd = temp.resolve(base + ".mmd");
                }
                boolean mmdWritten = false;
                for (HansolXmlToImage2.Format f : formats) {
                    if (cancelled) throw new CancellationException("cancelled");
                    Path out = dir.resolve(base + "." + f.extension());
                    if (f == HansolXmlToImage2.Format.MD) {
                        HansolXmlToImage2.writeText(out, HansolXmlToImage2.MermaidWriter.markdown(mermaid, source), true);
                        HansolXmlToImage2.LOG.info("Markdown: " + out);
                    } else if (f == HansolXmlToImage2.Format.MMD) {
                        HansolXmlToImage2.writeText(out, mermaid, true);
                        HansolXmlToImage2.LOG.info("Mermaid: " + out);
                    } else {
                        if (!mmdWritten) {
                            HansolXmlToImage2.writeText(mmd, mermaid, true);
                            mmdWritten = true;
                            HansolXmlToImage2.LOG.info("Mermaid: " + mmd);
                        }
                        Path renderLog = logs.resolve(base + "." + f.extension() + ".render.log");
                        HansolXmlToImage2.renderImage(renderer, mmd, out, renderLog, f, true);
                        HansolXmlToImage2.LOG.info("Image: " + out);
                        HansolXmlToImage2.LOG.info("Render log: " + renderLog);
                    }
                    outputs.add(out);
                }
                if (settings.keepMermaid) {
                    if (!mmdWritten) HansolXmlToImage2.writeText(mmd, mermaid, true);
                    outputs.add(mmd);
                }
                HansolXmlToImage2.LOG.info("Convert log: " + convertLog);
                return new FileResult(unknown > 0 ? FileResult.Status.DONE_WITH_UNKNOWN : FileResult.Status.DONE,
                    nodes, links, unknown, outputs, convertLog, unknown > 0 ? unknown + " unknown rule kind(s)" : "");
            } catch (CancellationException e) {
                HansolXmlToImage2.LOG.warning("Cancelled");
                return new FileResult(FileResult.Status.CANCELLED, nodes, links, unknown, outputs, convertLog,
                    "cancelled");
            } catch (Exception e) {
                HansolXmlToImage2.LOG.log(Level.SEVERE, "Conversion failed: " + e.getMessage(), e);
                return new FileResult(FileResult.Status.FAILED, nodes, links, unknown, outputs, convertLog,
                    e.getMessage());
            } finally {
                HansolXmlToImage2.LogSetup.uninstall(handlers);
                deleteQuietly(temp);
            }
        }

        /**
         * Base name for the outputs, or null to skip. A name already written in this
         * batch is never reused, whatever the policy (two inputs named a.xml).
         */
        private String chooseBaseName(Path dir, String base, List<HansolXmlToImage2.Format> formats,
                                      Set<String> used) {
            for (int n = 1; n < 1000; n++) {
                String candidate = n == 1 ? base : base + " (" + n + ")";
                if (used.contains(key(dir, candidate))) continue;
                if (!anyExists(dir, candidate, formats)) return candidate;
                switch (settings.conflict) {
                    case OVERWRITE: return candidate;
                    case SKIP: return null;
                    default: break; // RENAME: try the next number
                }
            }
            throw new IllegalStateException("No free output name for " + base);
        }

        private boolean anyExists(Path dir, String base, List<HansolXmlToImage2.Format> formats) {
            for (HansolXmlToImage2.Format f : formats) {
                if (Files.exists(dir.resolve(base + "." + f.extension()))) return true;
            }
            return settings.keepMermaid && Files.exists(dir.resolve(base + ".mmd"));
        }

        // Windows file names are case-insensitive.
        private static String key(Path dir, String base) {
            return dir.resolve(base).toString().toLowerCase(Locale.ROOT);
        }

        static String baseName(Path input) {
            String name = input.getFileName().toString();
            return name.toLowerCase(Locale.ROOT).endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
        }

        private static void deleteQuietly(Path dir) {
            if (dir == null) return;
            try {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
                    for (Path f : files) Files.deleteIfExists(f);
                }
                Files.deleteIfExists(dir);
            } catch (IOException e) {
                HansolXmlToImage2.LOG.warning("Temporary files remain: " + dir);
            }
        }
    }

    // ------------------------------------------------------------------ UI support

    /** Shows LOG records in the window's log area. Per-occurrence details stay in the log files. */
    static final class UiLogHandler extends Handler {
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
            final String line = String.format("%-5s ", HansolXmlToImage2.LineFormatter.levelName(record.getLevel()))
                + prefix + HansolXmlToImage2.Text.escapeControls(formatter.formatMessage(record)) + "\n";
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

    /** Remembers the last used options (Windows: HKCU\Software\JavaSoft\Prefs). Failures are ignored. */
    static final class UiSettings {
        private final Preferences node;

        UiSettings() {
            Preferences n = null;
            try {
                n = Preferences.userRoot().node("hansol-xml-to-image2-single");
            } catch (RuntimeException e) {
                // settings are a convenience only
            }
            node = n;
        }

        String get(String key, String def) {
            try {
                return node == null ? def : node.get(key, def);
            } catch (RuntimeException e) {
                return def;
            }
        }

        boolean getBoolean(String key, boolean def) { return Boolean.parseBoolean(get(key, String.valueOf(def))); }

        int getInt(String key, int def) {
            try {
                return Integer.parseInt(get(key, String.valueOf(def)));
            } catch (NumberFormatException e) {
                return def;
            }
        }

        void put(String key, Object value) {
            try {
                if (node != null) node.put(key, String.valueOf(value));
            } catch (RuntimeException ignored) {
                // settings are a convenience only
            }
        }

        void flush() {
            try {
                if (node != null) node.flush();
            } catch (BackingStoreException | RuntimeException ignored) {
                // settings are a convenience only
            }
        }
    }

    /** Input files and their conversion status. */
    @SuppressWarnings("serial") // Swing models are not serialized
    static final class FileTableModel extends AbstractTableModel {
        static final String[] COLUMNS = {"파일", "폴더", "상태", "노드/링크", "UNKNOWN", "결과", "메시지"};

        static final class Row {
            final Path path;
            String status = "대기";
            FileResult result;

            Row(Path path) { this.path = path; }
        }

        private final List<Row> rows = new ArrayList<Row>();

        /** Adds files not yet in the list. Returns the number added. */
        int addAll(Collection<Path> paths) {
            Set<String> known = new HashSet<String>();
            for (Row r : rows) known.add(key(r.path));
            int first = rows.size();
            for (Path p : paths) {
                Path abs = p.toAbsolutePath().normalize();
                if (known.add(key(abs))) rows.add(new Row(abs));
            }
            if (rows.size() > first) fireTableRowsInserted(first, rows.size() - 1);
            return rows.size() - first;
        }

        void remove(int[] modelRows) {
            int[] sorted = modelRows.clone();
            Arrays.sort(sorted);
            for (int i = sorted.length - 1; i >= 0; i--) rows.remove(sorted[i]);
            fireTableDataChanged();
        }

        void clear() {
            rows.clear();
            fireTableDataChanged();
        }

        List<Path> paths() {
            List<Path> list = new ArrayList<Path>();
            for (Row r : rows) list.add(r.path);
            return list;
        }

        Row row(int index) { return rows.get(index); }

        void resetStatuses() {
            for (Row r : rows) { r.status = "대기"; r.result = null; }
            fireTableDataChanged();
        }

        void setRunning(int index) {
            rows.get(index).status = "변환 중";
            fireTableRowsUpdated(index, index);
        }

        void setResult(int index, FileResult result) {
            Row r = rows.get(index);
            r.result = result;
            r.status = label(result.status);
            fireTableRowsUpdated(index, index);
        }

        static String label(FileResult.Status s) {
            switch (s) {
                case DONE: return "완료";
                case DONE_WITH_UNKNOWN: return "완료 (UNKNOWN 있음)";
                case STRICT_FAILED: return "strict 실패";
                case FAILED: return "실패";
                case SKIPPED: return "건너뜀";
                default: return "취소됨";
            }
        }

        @Override public int getRowCount() { return rows.size(); }

        @Override public int getColumnCount() { return COLUMNS.length; }

        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override public Object getValueAt(int rowIndex, int column) {
            Row r = rows.get(rowIndex);
            FileResult res = r.result;
            switch (column) {
                case 0: return r.path.getFileName().toString();
                case 1: return r.path.getParent() == null ? "" : r.path.getParent().toString();
                case 2: return r.status;
                case 3: return res == null || res.nodes == 0 ? "" : res.nodes + " / " + res.links;
                case 4: return res == null || res.unknownKinds == 0 ? "" : res.unknownKinds + "종";
                case 5: return res == null ? "" : extensions(res.outputs);
                default: return res == null ? "" : res.message;
            }
        }

        private static String extensions(List<Path> outputs) {
            StringBuilder s = new StringBuilder();
            for (Path p : outputs) {
                String name = p.getFileName().toString();
                s.append(s.length() == 0 ? "" : ", ").append(name.substring(name.lastIndexOf('.') + 1));
            }
            return s.toString();
        }

        // Windows file names are case-insensitive.
        private static String key(Path p) { return p.toString().toLowerCase(Locale.ROOT); }
    }

    // ------------------------------------------------------------------ main window

    /** Main window: input list, output options, progress and log. */
    @SuppressWarnings("serial") // Swing windows are not serialized
    static final class MainFrame extends JFrame {
        private static final String[] CONFLICT_LABELS = {"덮어쓰기", "건너뛰기", "새 이름으로 저장"};
        private static final ConflictPolicy[] CONFLICTS =
            {ConflictPolicy.OVERWRITE, ConflictPolicy.SKIP, ConflictPolicy.RENAME};
        private static final HansolXmlToImage2.Format[] IMAGE_FORMATS = {HansolXmlToImage2.Format.PNG,
            HansolXmlToImage2.Format.JPG, HansolXmlToImage2.Format.SVG, HansolXmlToImage2.Format.PDF};

        private final UiLogHandler logHandler;
        private final UiSettings settings = new UiSettings();
        private final FileTableModel files = new FileTableModel();
        private final JTable table = new JTable(files);

        private final JButton addFiles = new JButton("파일 추가...");
        private final JButton addFolder = new JButton("폴더 추가...");
        private final JButton removeFiles = new JButton("선택 제거");
        private final JButton clearFiles = new JButton("모두 비우기");

        private final JRadioButton sameDir = new JRadioButton("입력 파일과 같은 폴더");
        private final JRadioButton customDir = new JRadioButton("지정 폴더:");
        private final JTextField outputDir = new JTextField(28);
        private final JButton browseOutput = new JButton("찾기...");
        private final JCheckBox md = new JCheckBox("MD", true);
        private final Map<HansolXmlToImage2.Format, JCheckBox> imageBoxes =
            new EnumMap<HansolXmlToImage2.Format, JCheckBox>(HansolXmlToImage2.Format.class);
        private final JComboBox<String> direction = new JComboBox<String>(new String[] {"TD", "LR", "BT", "RL"});
        private final JComboBox<String> conflict = new JComboBox<String>(CONFLICT_LABELS);
        private final JCheckBox keepMmd = new JCheckBox(".mmd 보존");
        private final JTextField rulesFile = new JTextField(28);
        private final JButton browseRules = new JButton("찾기...");
        private final JCheckBox strict = new JCheckBox("strict (규칙 밖 요소가 있으면 실패)");
        private final JCheckBox showId = new JCheckBox("노드에 Id 표시");
        private final JSpinner scale = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
        private final JSpinner jpegQuality = new JSpinner(new SpinnerNumberModel(90, 10, 100, 5));
        private final JSpinner timeout = new JSpinner(new SpinnerNumberModel(120, 10, 3600, 10));

        private final JProgressBar progress = new JProgressBar();
        private final JLabel summary = new JLabel(" ");
        private final JButton start = new JButton("변환 시작");
        private final JButton cancel = new JButton("취소");
        private final JButton openFolder = new JButton("출력 폴더 열기");
        private final JTextArea log = new JTextArea(8, 80);

        private BatchWorker worker;

        MainFrame(UiLogHandler logHandler) {
            super("HansolXmlToImage2 (single) " + HansolXmlToImage2.VERSION + " - XML → Mermaid 변환");
            this.logHandler = logHandler;
            for (HansolXmlToImage2.Format f : IMAGE_FORMATS) {
                imageBoxes.put(f, new JCheckBox(f == HansolXmlToImage2.Format.SVG ? "SVG (image)" : f.name()));
            }
            setContentPane(buildContent());
            wireActions();
            loadSettings();
            setRunning(false);
            logHandler.attach(log);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { onClose(); }
            });
            pack();
            setMinimumSize(new Dimension(860, 640));
            setLocationRelativeTo(null);
        }

        // -------------------------------------------------------------- layout

        private JComponent buildContent() {
            JPanel top = new JPanel(new BorderLayout(8, 8));
            top.add(buildFilePanel(), BorderLayout.CENTER);
            top.add(buildOptionsPanel(), BorderLayout.SOUTH);

            log.setEditable(false);
            log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane logScroll = new JScrollPane(log);
            logScroll.setBorder(BorderFactory.createTitledBorder("로그 (파일별 상세 로그: 출력 폴더\\logs)"));

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, logScroll);
            split.setResizeWeight(0.75);
            split.setBorder(null);

            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            root.add(split, BorderLayout.CENTER);
            root.add(buildActionPanel(), BorderLayout.SOUTH);
            return root;
        }

        private JComponent buildFilePanel() {
            table.setFillsViewportHeight(true);
            table.setRowHeight(Math.max(table.getRowHeight(), 22));
            int[] widths = {200, 230, 120, 80, 80, 110, 200};
            for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            TransferHandler drop = new FileDropHandler();
            table.setTransferHandler(drop);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(820, 220));
            scroll.setTransferHandler(drop);

            JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
            buttons.add(addFiles);
            buttons.add(addFolder);
            buttons.add(removeFiles);
            buttons.add(clearFiles);
            JPanel east = new JPanel(new BorderLayout());
            east.add(buttons, BorderLayout.NORTH);

            JPanel panel = new JPanel(new BorderLayout(8, 0));
            panel.setBorder(BorderFactory.createTitledBorder("입력 XML 파일 (드래그 앤 드롭 가능, 더블클릭: 로그 열기)"));
            panel.add(scroll, BorderLayout.CENTER);
            panel.add(east, BorderLayout.EAST);
            return panel;
        }

        private JComponent buildOptionsPanel() {
            ButtonGroup group = new ButtonGroup();
            group.add(sameDir);
            group.add(customDir);
            md.setEnabled(false); // always written

            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createTitledBorder("출력 / 옵션"));
            addRow(panel, 0, "출력 폴더", flow(sameDir, customDir, outputDir, browseOutput));
            JPanel formats = flow(md);
            for (JCheckBox box : imageBoxes.values()) formats.add(box);
            formats.add(new JLabel("  (MD는 항상 생성, 이미지는 여러 개 선택 가능)"));
            addRow(panel, 1, "형식", formats);
            addRow(panel, 2, "방향", flow(direction, new JLabel("   기존 파일:"), conflict, keepMmd));
            addRow(panel, 3, "규칙 파일", flow(rulesFile, browseRules, strict, showId));
            addRow(panel, 4, "이미지", flow(new JLabel("배율(PNG/JPG):"), scale, new JLabel("   JPG 품질:"),
                jpegQuality, new JLabel("   렌더링 시간 제한(초):"), timeout));
            return panel;
        }

        private JComponent buildActionPanel() {
            progress.setStringPainted(true);
            JPanel left = new JPanel(new BorderLayout(8, 0));
            left.add(progress, BorderLayout.CENTER);
            left.add(summary, BorderLayout.EAST);
            JPanel panel = new JPanel(new BorderLayout(8, 0));
            panel.add(left, BorderLayout.CENTER);
            panel.add(flow(start, cancel, openFolder), BorderLayout.EAST);
            return panel;
        }

        private static JPanel flow(JComponent... components) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            for (JComponent c : components) p.add(c);
            return p;
        }

        private static void addRow(JPanel panel, int row, String label, JComponent content) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.insets = new Insets(3, 6, 3, 6);
            c.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel(label), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(content, c);
        }

        // -------------------------------------------------------------- actions

        private void wireActions() {
            addFiles.addActionListener(e -> chooseFiles());
            addFolder.addActionListener(e -> chooseFolder());
            removeFiles.addActionListener(e -> {
                int[] selected = table.getSelectedRows();
                if (selected.length > 0) files.remove(selected);
            });
            clearFiles.addActionListener(e -> files.clear());
            browseOutput.addActionListener(e -> {
                File dir = chooseDirectory(outputDir.getText());
                if (dir != null) { outputDir.setText(dir.getPath()); customDir.setSelected(true); updateEnabled(); }
            });
            browseRules.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser(parentOf(rulesFile.getText()));
                chooser.setFileFilter(new FileNameExtensionFilter("규칙 파일 (*.properties)", "properties"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    rulesFile.setText(chooser.getSelectedFile().getPath());
                }
            });
            sameDir.addActionListener(e -> updateEnabled());
            customDir.addActionListener(e -> updateEnabled());
            for (JCheckBox box : imageBoxes.values()) box.addActionListener(e -> updateEnabled());
            start.addActionListener(e -> startConversion());
            cancel.addActionListener(e -> {
                if (worker != null) {
                    cancel.setEnabled(false);
                    summary.setText("취소 중...");
                    worker.cancelBatch();
                }
            });
            openFolder.addActionListener(e -> openOutputFolder());
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (e.getClickCount() == 2 && row >= 0) openLog(row);
                }
            });
        }

        private void chooseFiles() {
            JFileChooser chooser = new JFileChooser(settings.get("lastDir", ""));
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("XML 파일 (*.xml)", "xml"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            List<Path> paths = new ArrayList<Path>();
            for (File f : chooser.getSelectedFiles()) paths.add(f.toPath());
            if (!paths.isEmpty()) settings.put("lastDir", paths.get(0).getParent());
            files.addAll(paths);
        }

        private void chooseFolder() {
            File dir = chooseDirectory(settings.get("lastDir", ""));
            if (dir == null) return;
            int answer = JOptionPane.showConfirmDialog(this, "하위 폴더의 XML 파일도 포함할까요?", "폴더 추가",
                JOptionPane.YES_NO_CANCEL_OPTION);
            if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) return;
            settings.put("lastDir", dir.getPath());
            try {
                int added = files.addAll(xmlFiles(dir.toPath(), answer == JOptionPane.YES_OPTION));
                if (added == 0) info("추가할 XML 파일이 없습니다.");
            } catch (IOException e) {
                error("폴더를 읽을 수 없습니다: " + e.getMessage());
            }
        }

        private File chooseDirectory(String initial) {
            JFileChooser chooser = new JFileChooser(initial);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
        }

        static List<Path> xmlFiles(Path dir, boolean recursive) throws IOException {
            List<Path> list = new ArrayList<Path>();
            try (Stream<Path> stream = Files.walk(dir, recursive ? Integer.MAX_VALUE : 1)) {
                stream.filter(p -> Files.isRegularFile(p)
                        && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                    .forEach(list::add);
            }
            Collections.sort(list);
            return list;
        }

        private void startConversion() {
            if (files.getRowCount() == 0) { info("변환할 XML 파일을 추가하십시오."); return; }
            BatchSettings batch;
            try {
                batch = collectSettings();
            } catch (IllegalArgumentException e) {
                error(e.getMessage());
                return;
            }
            saveSettings();
            files.resetStatuses();
            log.setText("");
            progress.setMaximum(files.getRowCount());
            progress.setValue(0);
            summary.setText(" ");
            worker = new BatchWorker(new Batch(batch), files.paths());
            setRunning(true);
            worker.execute();
        }

        BatchSettings collectSettings() {
            BatchSettings s = new BatchSettings();
            if (customDir.isSelected()) {
                String dir = outputDir.getText().trim();
                if (dir.isEmpty()) throw new IllegalArgumentException("출력 폴더를 지정하십시오.");
                s.outputDir = path(dir, "출력 폴더");
            }
            s.formats = EnumSet.of(HansolXmlToImage2.Format.MD);
            for (Map.Entry<HansolXmlToImage2.Format, JCheckBox> e : imageBoxes.entrySet()) {
                if (e.getValue().isSelected()) s.formats.add(e.getKey());
            }
            s.direction = (String) direction.getSelectedItem();
            s.conflict = CONFLICTS[conflict.getSelectedIndex()];
            s.keepMermaid = keepMmd.isSelected();
            s.strict = strict.isSelected();
            s.showId = showId.isSelected();
            String rules = rulesFile.getText().trim();
            if (!rules.isEmpty()) {
                s.rulesFile = path(rules, "규칙 파일");
                if (!Files.isRegularFile(s.rulesFile)) {
                    throw new IllegalArgumentException("규칙 파일이 없습니다: " + s.rulesFile);
                }
            }
            s.mermaidConfig = optionalPath(System.getProperty("mermaid.config"));
            s.puppeteerConfig = optionalPath(System.getProperty("puppeteer.config"));
            s.scale = (Integer) scale.getValue();
            s.jpegQuality = (Integer) jpegQuality.getValue();
            s.timeoutSeconds = (Integer) timeout.getValue();
            return s;
        }

        private void openOutputFolder() {
            Path dir = null;
            if (customDir.isSelected() && !outputDir.getText().trim().isEmpty()) {
                dir = optionalPath(outputDir.getText());
            } else if (files.getRowCount() > 0) {
                int row = Math.max(table.getSelectedRow(), 0);
                dir = files.row(row).path.getParent();
            }
            if (dir == null || !Files.isDirectory(dir)) { info("열 수 있는 출력 폴더가 없습니다."); return; }
            open(dir);
        }

        private void openLog(int row) {
            FileResult r = files.row(row).result;
            if (r == null || r.convertLog == null || !Files.isRegularFile(r.convertLog)) {
                info("이 파일의 로그가 아직 없습니다.");
                return;
            }
            open(r.convertLog);
        }

        private void open(Path path) {
            try {
                if (!Desktop.isDesktopSupported()) throw new IOException("Desktop API not supported");
                Desktop.getDesktop().open(path.toFile());
            } catch (IOException | RuntimeException e) {
                error("열 수 없습니다: " + path + "\n" + e.getMessage());
            }
        }

        private void onClose() {
            if (worker != null && !worker.isDone()) {
                int answer = JOptionPane.showConfirmDialog(this, "변환이 진행 중입니다. 중단하고 종료할까요?", "종료",
                    JOptionPane.YES_NO_OPTION);
                if (answer != JOptionPane.YES_OPTION) return;
                final BatchWorker running = worker;
                running.cancelBatch();
                summary.setText("취소 중... 잠시 후 종료됩니다.");
                Thread exit = new Thread(() -> {
                    try {
                        running.get(60, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        // exit anyway
                    }
                    SwingUtilities.invokeLater(this::exit);
                }, "exit-after-cancel");
                exit.setDaemon(true);
                exit.start();
                return;
            }
            exit();
        }

        private void exit() {
            saveSettings();
            dispose();
            System.exit(0);
        }

        // -------------------------------------------------------------- state

        private void setRunning(boolean running) {
            for (JComponent c : new JComponent[] {addFiles, addFolder, removeFiles, clearFiles, sameDir, customDir,
                    direction, conflict, keepMmd, rulesFile, browseRules, strict, showId, start}) {
                c.setEnabled(!running);
            }
            for (JCheckBox box : imageBoxes.values()) box.setEnabled(!running);
            cancel.setEnabled(running);
            if (running) {
                outputDir.setEnabled(false);
                browseOutput.setEnabled(false);
                scale.setEnabled(false);
                jpegQuality.setEnabled(false);
                timeout.setEnabled(false);
            } else {
                updateEnabled();
            }
        }

        private void updateEnabled() {
            boolean custom = customDir.isSelected();
            outputDir.setEnabled(custom);
            browseOutput.setEnabled(custom);
            boolean anyImage = false;
            for (JCheckBox box : imageBoxes.values()) anyImage |= box.isSelected();
            scale.setEnabled(anyImage);
            timeout.setEnabled(anyImage);
            jpegQuality.setEnabled(imageBoxes.get(HansolXmlToImage2.Format.JPG).isSelected());
        }

        private void loadSettings() {
            boolean custom = "custom".equals(settings.get("outputMode", "same"));
            (custom ? customDir : sameDir).setSelected(true);
            outputDir.setText(settings.get("outputDir", ""));
            List<String> formats = Arrays.asList(settings.get("formats", "PNG").split(","));
            for (Map.Entry<HansolXmlToImage2.Format, JCheckBox> e : imageBoxes.entrySet()) {
                e.getValue().setSelected(formats.contains(e.getKey().name()));
            }
            direction.setSelectedItem(settings.get("direction", "TD"));
            conflict.setSelectedIndex(Math.min(Math.max(settings.getInt("conflict", 0), 0), CONFLICTS.length - 1));
            keepMmd.setSelected(settings.getBoolean("keepMmd", false));
            String rules = settings.get("rules", "");
            if (rules.isEmpty() || !new File(rules).isFile()) rules = System.getProperty("hansol.rules", "");
            rulesFile.setText(rules);
            strict.setSelected(settings.getBoolean("strict", false));
            showId.setSelected(settings.getBoolean("showId", false));
            scale.setValue(clamp(settings.getInt("scale", 1), 1, 5));
            jpegQuality.setValue(clamp(settings.getInt("jpegQuality", 90), 10, 100));
            timeout.setValue(clamp(settings.getInt("timeout", 120), 10, 3600));
        }

        private void saveSettings() {
            settings.put("outputMode", customDir.isSelected() ? "custom" : "same");
            settings.put("outputDir", outputDir.getText().trim());
            StringBuilder formats = new StringBuilder();
            for (Map.Entry<HansolXmlToImage2.Format, JCheckBox> e : imageBoxes.entrySet()) {
                if (e.getValue().isSelected()) formats.append(formats.length() == 0 ? "" : ",").append(e.getKey().name());
            }
            settings.put("formats", formats);
            settings.put("direction", direction.getSelectedItem());
            settings.put("conflict", conflict.getSelectedIndex());
            settings.put("keepMmd", keepMmd.isSelected());
            settings.put("rules", rulesFile.getText().trim());
            settings.put("strict", strict.isSelected());
            settings.put("showId", showId.isSelected());
            settings.put("scale", scale.getValue());
            settings.put("jpegQuality", jpegQuality.getValue());
            settings.put("timeout", timeout.getValue());
            settings.flush();
        }

        private void summarize() {
            int done = 0, unknown = 0, failed = 0, skipped = 0, cancelled = 0;
            for (int i = 0; i < files.getRowCount(); i++) {
                FileResult r = files.row(i).result;
                if (r == null) continue;
                switch (r.status) {
                    case DONE: done++; break;
                    case DONE_WITH_UNKNOWN: done++; unknown++; break;
                    case SKIPPED: skipped++; break;
                    case CANCELLED: cancelled++; break;
                    default: failed++; break;
                }
            }
            String text = "완료 " + done + " (UNKNOWN " + unknown + ") · 실패 " + failed + " · 건너뜀 " + skipped
                + " · 취소 " + cancelled;
            summary.setText(text);
            HansolXmlToImage2.LOG.info("Batch finished: " + text);
        }

        // -------------------------------------------------------------- helpers

        private void info(String message) {
            JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.INFORMATION_MESSAGE);
        }

        private void error(String message) {
            JOptionPane.showMessageDialog(this, message, getTitle(), JOptionPane.ERROR_MESSAGE);
        }

        private static Path path(String value, String what) {
            try {
                return Paths.get(value.trim()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException(what + " 경로가 올바르지 않습니다: " + value);
            }
        }

        private static Path optionalPath(String value) {
            if (value == null || value.trim().isEmpty()) return null;
            try {
                return Paths.get(value.trim()).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                return null;
            }
        }

        private static File parentOf(String path) {
            File f = new File(path.trim());
            return f.getParentFile() != null ? f.getParentFile() : null;
        }

        private static int clamp(int v, int min, int max) { return Math.min(Math.max(v, min), max); }

        // -------------------------------------------------------------- background work

        private static final class Update {
            final int index;
            final FileResult result; // null: started

            Update(int index, FileResult result) { this.index = index; this.result = result; }
        }

        /** Runs the batch off the event thread and applies progress to the table. */
        private final class BatchWorker extends SwingWorker<Void, Update> {
            private final Batch batch;
            private final List<Path> inputs;

            BatchWorker(Batch batch, List<Path> inputs) {
                this.batch = batch;
                this.inputs = inputs;
            }

            /** Killing a render can take a while on Windows (Java 8): never on the event thread. */
            void cancelBatch() {
                Thread t = new Thread(batch::cancel, "cancel-batch");
                t.setDaemon(true);
                t.start();
            }

            @Override protected Void doInBackground() {
                try {
                    batch.run(inputs, new BatchListener() {
                        @Override public void started(int index) {
                            logHandler.setPrefix("[" + inputs.get(index).getFileName() + "] ");
                            publish(new Update(index, null));
                        }

                        @Override public void finished(int index, FileResult result) {
                            publish(new Update(index, result));
                        }
                    });
                } finally {
                    logHandler.setPrefix("");
                }
                return null;
            }

            @Override protected void process(List<Update> updates) {
                for (Update u : updates) {
                    if (u.result == null) {
                        files.setRunning(u.index);
                        table.scrollRectToVisible(table.getCellRect(u.index, 0, true));
                    } else {
                        files.setResult(u.index, u.result);
                        progress.setValue(progress.getValue() + 1);
                    }
                }
            }

            @Override protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    HansolXmlToImage2.LOG.severe("Batch stopped: " + e);
                }
                setRunning(false);
                summarize();
            }
        }

        /** Accepts XML files and folders (top level only) dropped on the list. */
        private final class FileDropHandler extends TransferHandler {
            @Override public boolean canImport(TransferSupport support) {
                return start.isEnabled() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    @SuppressWarnings("unchecked")
                    List<File> dropped = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> paths = new ArrayList<Path>();
                    for (File f : dropped) {
                        if (f.isDirectory()) paths.addAll(xmlFiles(f.toPath(), false));
                        else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) paths.add(f.toPath());
                    }
                    files.addAll(paths);
                    return true;
                } catch (Exception e) {
                    error("파일을 추가할 수 없습니다: " + e.getMessage());
                    return false;
                }
            }
        }
    }
}
