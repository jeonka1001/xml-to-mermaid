package hansol.xml2mermaid.ui;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.HansolXmlToImage2;
import hansol.xml2mermaid.app.BatchConverter;
import hansol.xml2mermaid.app.BatchListener;
import hansol.xml2mermaid.app.BatchSettings;
import hansol.xml2mermaid.app.ConflictPolicy;
import hansol.xml2mermaid.app.FileResult;
import hansol.xml2mermaid.output.OutputFormat;
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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
import javax.swing.filechooser.FileNameExtensionFilter;

/** Main window: input list, output options, progress and log. */
@SuppressWarnings("serial") // Swing windows are not serialized
final class MainFrame extends JFrame {
    private static final String[] CONFLICT_LABELS = {"덮어쓰기", "건너뛰기", "새 이름으로 저장"};
    private static final ConflictPolicy[] CONFLICTS =
        {ConflictPolicy.OVERWRITE, ConflictPolicy.SKIP, ConflictPolicy.RENAME};
    private static final OutputFormat[] IMAGE_FORMATS =
        {OutputFormat.PNG, OutputFormat.JPG, OutputFormat.SVG, OutputFormat.PDF};

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
    private final Map<OutputFormat, JCheckBox> imageBoxes = new EnumMap<OutputFormat, JCheckBox>(OutputFormat.class);
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
        super("HansolXmlToImage2 " + HansolXmlToImage2.VERSION + " - XML → Mermaid 변환");
        this.logHandler = logHandler;
        for (OutputFormat f : IMAGE_FORMATS) {
            imageBoxes.put(f, new JCheckBox(f == OutputFormat.SVG ? "SVG (image)" : f.name()));
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

    // ------------------------------------------------------------------ layout

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
        addRow(panel, 4, "이미지", flow(new JLabel("배율(PNG/JPG):"), scale, new JLabel("   JPG 품질:"), jpegQuality,
            new JLabel("   렌더링 시간 제한(초):"), timeout));
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

    // ------------------------------------------------------------------ actions

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
        worker = new BatchWorker(new BatchConverter(batch), files.paths());
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
        s.formats = EnumSet.of(OutputFormat.MD);
        for (Map.Entry<OutputFormat, JCheckBox> e : imageBoxes.entrySet()) {
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

    // ------------------------------------------------------------------ state

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
        jpegQuality.setEnabled(imageBoxes.get(OutputFormat.JPG).isSelected());
    }

    private void loadSettings() {
        boolean custom = "custom".equals(settings.get("outputMode", "same"));
        (custom ? customDir : sameDir).setSelected(true);
        outputDir.setText(settings.get("outputDir", ""));
        List<String> formats = java.util.Arrays.asList(settings.get("formats", "PNG").split(","));
        for (Map.Entry<OutputFormat, JCheckBox> e : imageBoxes.entrySet()) {
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
        for (Map.Entry<OutputFormat, JCheckBox> e : imageBoxes.entrySet()) {
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
        LOG.info("Batch finished: " + text);
    }

    // ------------------------------------------------------------------ helpers

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

    // ------------------------------------------------------------------ background work

    private static final class Update {
        final int index;
        final FileResult result; // null: started

        Update(int index, FileResult result) { this.index = index; this.result = result; }
    }

    /** Runs the batch off the event thread and applies progress to the table. */
    private final class BatchWorker extends SwingWorker<Void, Update> {
        private final BatchConverter converter;
        private final List<Path> inputs;

        BatchWorker(BatchConverter converter, List<Path> inputs) {
            this.converter = converter;
            this.inputs = inputs;
        }

        /** Killing a render can take a while on Windows (Java 8): never on the event thread. */
        void cancelBatch() {
            Thread t = new Thread(converter::cancel, "cancel-batch");
            t.setDaemon(true);
            t.start();
        }

        @Override protected Void doInBackground() {
            try {
                converter.run(inputs, new BatchListener() {
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
                LOG.severe("Batch stopped: " + e);
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
                List<File> dropped = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
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
