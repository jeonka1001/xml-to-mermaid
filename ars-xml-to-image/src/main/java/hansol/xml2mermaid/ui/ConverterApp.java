package hansol.xml2mermaid.ui;

import hansol.xml2mermaid.log.Logs;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * GUI entry point: java -cp hansol-xml-to-image2.jar hansol.xml2mermaid.ui.ConverterApp
 * Reads the same system properties as the CLI (-Dhansol.rules, -Dmermaid.cli, ...).
 */
public final class ConverterApp {
    private ConverterApp() {}

    public static void main(String[] args) {
        final UiLogHandler handler = new UiLogHandler();
        handler.setLevel(Level.INFO);
        Logs.LOG.setUseParentHandlers(false);
        Logs.LOG.setLevel(Level.INFO);
        Logs.LOG.addHandler(handler);
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
}
