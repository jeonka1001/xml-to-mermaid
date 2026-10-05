package hansol.xml2mermaid.ui;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** Remembers the last used options (Windows: HKCU\Software\JavaSoft\Prefs). Failures are ignored. */
final class UiSettings {
    private final Preferences node;

    UiSettings() {
        Preferences n = null;
        try {
            n = Preferences.userRoot().node("hansol-xml-to-image2");
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
