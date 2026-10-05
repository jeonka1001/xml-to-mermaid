package hansol.xml2mermaid.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class TextFiles {
    private TextFiles() {}

    /** Writes UTF-8 text. Without overwrite an existing file is never replaced. */
    static void write(Path file, String content, boolean overwrite) throws IOException {
        OpenOption[] mode = overwrite
            ? new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE}
            : new OpenOption[] {StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        Files.write(file, content.getBytes(StandardCharsets.UTF_8), mode);
    }
}
