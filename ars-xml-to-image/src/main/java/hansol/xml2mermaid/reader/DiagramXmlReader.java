package hansol.xml2mermaid.reader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parses an XML file into an XmlElement tree. Knows nothing about Diagram rules.
 * Uses the encoding from the XML declaration.
 */
public final class DiagramXmlReader {
    private static final ErrorHandler THROWING = new ErrorHandler() {
        @Override public void warning(SAXParseException e) {}

        @Override public void error(SAXParseException e) throws SAXException { throw e; }

        @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
    };

    private DiagramXmlReader() {}

    public static XmlElement read(Path input) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // XXE protection: no DOCTYPE, no external entities, DTDs or schemas.
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder builder = f.newDocumentBuilder();
        builder.setErrorHandler(THROWING); // report via the exception only, not stderr
        try (InputStream in = Files.newInputStream(input)) {
            return copy(builder.parse(in).getDocumentElement());
        }
    }

    private static XmlElement copy(Element e) {
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            String name = attrs.item(i).getNodeName();
            if (name.equals("xmlns") || name.startsWith("xmlns:")) continue;
            attributes.put(name, attrs.item(i).getNodeValue());
        }
        StringBuilder ownText = new StringBuilder();
        List<XmlElement> children = new ArrayList<XmlElement>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                children.add(copy((Element) n));
            } else if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                ownText.append(n.getNodeValue());
            }
        }
        return new XmlElement(e.getNodeName(), attributes, ownText.toString().trim(),
            e.getTextContent().trim(), children);
    }
}
