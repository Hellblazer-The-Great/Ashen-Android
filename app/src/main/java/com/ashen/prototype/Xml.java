package com.ashen.prototype;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Small DOM helpers shared by the UI and map loaders. Pure Java, no Android dependency. */
final class Xml {
    private Xml() {}

    static Document parse(String text) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Direct element children with the given tag, or every element child when tag is null. */
    static List<Element> children(Element e, String tag) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element c = (Element) n;
            if (tag == null || c.getTagName().equals(tag)) out.add(c);
        }
        return out;
    }

    /** All attributes of an element as strings, plus "tag" holding the element name. */
    static Map<String, String> attrs(Element e) {
        Map<String, String> m = new HashMap<>();
        NamedNodeMap nm = e.getAttributes();
        for (int i = 0; i < nm.getLength(); i++) m.put(nm.item(i).getNodeName(), nm.item(i).getNodeValue());
        m.put("tag", e.getTagName());
        return m;
    }

    /** Joins a relative path onto a directory and resolves "." and "..". Uses '/' separators. */
    static String resolve(String dir, String rel) {
        String joined = dir.isEmpty() ? rel : dir + "/" + rel;
        java.util.Deque<String> parts = new java.util.ArrayDeque<>();
        for (String p : joined.split("/")) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) { if (!parts.isEmpty()) parts.removeLast(); }
            else parts.addLast(p);
        }
        return String.join("/", parts);
    }

    static String parentDir(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }
}
