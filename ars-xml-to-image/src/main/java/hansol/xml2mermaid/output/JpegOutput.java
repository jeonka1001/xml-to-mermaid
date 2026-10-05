package hansol.xml2mermaid.output;

import static hansol.xml2mermaid.log.Logs.LOG;

import hansol.xml2mermaid.model.Flowchart;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * .jpg: Mermaid CLI 11.6 renders only svg/png/pdf, so this renders a PNG to a
 * staging directory and re-encodes it with ImageIO on a white background.
 */
final class JpegOutput implements OutputWriter {
    private final ImageRenderer renderer;

    JpegOutput(ImageRenderer renderer) {
        if (renderer == null) throw new IllegalArgumentException("Image output requires a renderer");
        this.renderer = renderer;
    }

    @Override public void write(Flowchart chart, OutputRequest r) throws Exception {
        TextFiles.write(r.mermaidFile(), MermaidSyntax.flowchart(chart, r), true);
        LOG.info("Mermaid: " + r.mermaidFile());
        Path work = Files.createTempDirectory(r.output.getParent(), ".jpeg-");
        Path png = work.resolve("render.png");
        Path jpg = work.resolve("render.jpg");
        try {
            renderer.render(r.mermaidFile(), png, r.renderLog(), OutputFormat.PNG, r.overwrite);
            encode(png, jpg, renderer.jpegQuality());
            if (r.overwrite) Files.move(jpg, r.output, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(jpg, r.output); // protect existing output
            LOG.info("Image: " + r.output);
            LOG.info("Render log: " + r.renderLog());
        } finally {
            Files.deleteIfExists(png);
            Files.deleteIfExists(jpg);
            Files.deleteIfExists(work);
        }
    }

    /** quality: 1..100 */
    static void encode(Path png, Path jpg, int quality) throws IOException {
        BufferedImage source = ImageIO.read(png.toFile());
        if (source == null) throw new IOException("Cannot read rendered PNG: " + png);
        // JPEG has no alpha channel: flatten onto white.
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer available");
        ImageWriter writer = writers.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(jpg.toFile())) {
            if (out == null) throw new IOException("Cannot create " + jpg);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality / 100f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
