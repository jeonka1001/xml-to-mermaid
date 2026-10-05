package hansol.xml2mermaid.app;

/** Progress callbacks, called on the thread running BatchConverter.run(). */
public interface BatchListener {
    void started(int index);

    void finished(int index, FileResult result);
}
