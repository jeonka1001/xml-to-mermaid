package hansol.xml2mermaid.ui;

import hansol.xml2mermaid.app.FileResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.table.AbstractTableModel;

/** Input files and their conversion status. */
@SuppressWarnings("serial") // Swing models are not serialized
final class FileTableModel extends AbstractTableModel {
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
