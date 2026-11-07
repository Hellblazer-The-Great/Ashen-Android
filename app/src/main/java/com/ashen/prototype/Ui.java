package com.ashen.prototype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * UI layout loaded from assets/ui/ui.xml. Every HUD element, menu label and touch button is looked up by id,
 * with the code's built-in value as the fallback, so a missing attribute never breaks the game.
 */
final class Ui {
    private final Map<String, Map<String, String>> elements = new HashMap<>();
    private final Map<String, List<Map<String, String>>> buttons = new HashMap<>();

    static Ui parse(String xml) throws Exception {
        Document doc = Xml.parse(xml);
        Ui ui = new Ui();
        for (Element e : Xml.children(doc.getDocumentElement(), null)) {
            if (e.getTagName().equals("touch")) {
                for (Element b : Xml.children(e, "button")) {
                    String group = b.getAttribute("group");
                    List<Map<String, String>> list = ui.buttons.get(group);
                    if (list == null) { list = new ArrayList<>(); ui.buttons.put(group, list); }
                    list.add(Xml.attrs(b));
                }
            } else {
                String id = e.getAttribute("id");
                if (id.isEmpty()) throw new IllegalArgumentException("ui.xml element <" + e.getTagName() + "> needs an id");
                ui.elements.put(id, Xml.attrs(e));
            }
        }
        return ui;
    }

    boolean has(String id, String attr) {
        Map<String, String> m = elements.get(id);
        return m != null && m.containsKey(attr);
    }

    String str(String id, String attr, String def) {
        Map<String, String> m = elements.get(id);
        return m != null && m.containsKey(attr) ? m.get(attr) : def;
    }

    double num(String id, String attr, double def) {
        String s = str(id, attr, null);
        if (s == null) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    /** x in absolute units, or cx as an offset from the horizontal centre of the screen. */
    double x(String id, double screenW, double def) {
        if (has(id, "cx")) return screenW / 2 + num(id, "cx", 0);
        return num(id, "x", def);
    }

    /** y in absolute units, or cy as an offset from the vertical centre of the screen. */
    double y(String id, double screenH, double def) {
        if (has(id, "cy")) return screenH / 2 + num(id, "cy", 0);
        return num(id, "y", def);
    }

    /** Touch buttons for a group ("game" or "menu"), each with action, label, x, y, r. */
    List<Map<String, String>> buttons(String group) {
        List<Map<String, String>> list = buttons.get(group);
        return list == null ? new ArrayList<Map<String, String>>() : list;
    }
}
