package com.ashen.prototype;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import static com.ashen.prototype.Json.*;

/**
 * Reads a Tiled map (.tmx, XML) and produces the same level structure that level.json used to hold, so
 * World and Renderer don't care where the level came from.
 *
 * Authoring conventions (see README):
 *  - Map custom properties: levelWidth, groundY, playerStart, skyColor, groundColor, platformColor,
 *    groundTop/groundFill/groundScale, platformTexture/platformScale, bonfireOff/bonfireOn/bonfireScale.
 *  - Object layers by name: backgrounds, scenery, platforms, bonfires, enemies, gate.
 *  - Tile objects (images from art.tsx) are placed with their bottom-left corner as Tiled stores it;
 *    the loader converts them to top-left boxes.
 *  - Bonfires: point objects, name = id, property label = display name.
 *  - Enemies: point objects, property enemy = enemy id (from enemies.json).
 *  - Gate: a tile object; its horizontal centre is the boss-gate line; property boss = boss id.
 */
final class TmxLoader {
    private TmxLoader() {}

    static Map<String, Object> load(Data.Source src, String mapPath) throws Exception {
        String dir = Xml.parentDir(mapPath);
        Document doc = Xml.parse(src.read(mapPath));
        Element map = doc.getDocumentElement();
        Map<String, Object> mp = properties(map);

        // gid -> texture path (relative to assets/textures/), from every referenced tileset
        Map<Integer, String> textures = new HashMap<>();
        for (Element ts : Xml.children(map, "tileset")) {
            int firstGid = (int) Double.parseDouble(ts.getAttribute("firstgid"));
            String source = ts.getAttribute("source");
            if (source.isEmpty()) throw new IllegalArgumentException("Embedded tilesets are not supported; use an external .tsx");
            String tsxPath = Xml.resolve(dir, source);
            Element tsx = Xml.parse(src.read(tsxPath)).getDocumentElement();
            String tsxDir = Xml.parentDir(tsxPath);
            for (Element t : Xml.children(tsx, "tile")) {
                Element img = Xml.children(t, "image").get(0);
                String asset = Xml.resolve(tsxDir, img.getAttribute("source"));
                textures.put(firstGid + Integer.parseInt(t.getAttribute("id")), toTexture(asset));
            }
        }

        List<Object> platforms = new ArrayList<>(), bonfires = new ArrayList<>(), spawns = new ArrayList<>();
        List<Object> scenery = new ArrayList<>(), backgrounds = new ArrayList<>();
        Map<String, Object> gate = new LinkedHashMap<>();

        for (Element group : Xml.children(map, "objectgroup")) {
            String name = group.getAttribute("name");
            for (Element o : Xml.children(group, "object")) {
                Map<String, Object> op = properties(o);
                double x = num(o, "x"), y = num(o, "y"), w = num(o, "width"), h = num(o, "height");
                String oname = o.getAttribute("name");
                String gidAttr = o.getAttribute("gid");
                String texture = gidAttr.isEmpty() ? "" : textures.get(Integer.parseInt(gidAttr) & 0x1FFFFFFF);
                // Tile objects store y at the bottom edge; rectangles and points store the top edge.
                double top = gidAttr.isEmpty() ? y : y - h;

                switch (name) {
                    case "platforms": {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("x", x); p.put("y", y); p.put("w", w > 0 ? w : 100);
                        platforms.add(p);
                        break;
                    }
                    case "bonfires": {
                        Map<String, Object> b = new LinkedHashMap<>();
                        b.put("id", oname); b.put("name", getStr(op, "label", oname)); b.put("x", x);
                        bonfires.add(b);
                        break;
                    }
                    case "enemies": {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("enemy", getStr(op, "enemy", "")); s.put("x", x);
                        spawns.add(s);
                        break;
                    }
                    case "scenery": {
                        Map<String, Object> s = box(texture, x, top, w, h);
                        scenery.add(s);
                        break;
                    }
                    case "backgrounds": {
                        Map<String, Object> b = box(texture, x, top, w, h);
                        b.put("factor", getNum(op, "factor", 0));
                        backgrounds.add(b);
                        break;
                    }
                    case "gate": {
                        gate = box(texture, x + w / 2, top, w, h); // x is the centre line of the gate
                        gate.put("boss", getStr(op, "boss", ""));
                        break;
                    }
                    default:
                        break; // unknown groups are ignored so you can add notes layers in Tiled
                }
            }
        }

        Map<String, Object> level = new LinkedHashMap<>();
        level.put("width", getNum(mp, "levelWidth", 3000));
        level.put("groundY", getNum(mp, "groundY", 450));
        level.put("playerStart", getNum(mp, "playerStart", 100));
        level.put("skyColor", getStr(mp, "skyColor", "#14161c"));
        level.put("groundColor", getStr(mp, "groundColor", "#2a2c33"));
        level.put("platformColor", getStr(mp, "platformColor", "#3b3e48"));
        level.put("platforms", platforms);
        level.put("bonfires", bonfires);
        level.put("spawns", spawns);
        level.put("scenery", scenery);
        level.put("gate", gate);

        Map<String, Object> visuals = new LinkedHashMap<>();
        Map<String, Object> ground = new LinkedHashMap<>();
        ground.put("top", getStr(mp, "groundTop", "")); ground.put("fill", getStr(mp, "groundFill", ""));
        ground.put("scale", getNum(mp, "groundScale", 3));
        visuals.put("ground", ground);
        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("texture", getStr(mp, "platformTexture", "")); platform.put("scale", getNum(mp, "platformScale", 3));
        visuals.put("platform", platform);
        Map<String, Object> bonfire = new LinkedHashMap<>();
        bonfire.put("off", getStr(mp, "bonfireOff", "")); bonfire.put("on", getStr(mp, "bonfireOn", ""));
        bonfire.put("scale", getNum(mp, "bonfireScale", 4));
        visuals.put("bonfire", bonfire);
        visuals.put("backgrounds", backgrounds);
        level.put("visuals", visuals);
        return level;
    }

    /** Textures are referenced from the tileset as "../textures/environment/x.png"; keep "environment/x.png". */
    private static String toTexture(String assetPath) {
        int i = assetPath.indexOf("textures/");
        return i >= 0 ? assetPath.substring(i + "textures/".length()) : assetPath;
    }

    private static Map<String, Object> box(String texture, double x, double y, double w, double h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("texture", texture); m.put("x", x); m.put("y", y); m.put("w", w); m.put("h", h);
        return m;
    }

    /** Reads a numeric object attribute, 0 when absent. */
    private static double num(Element e, String attr) {
        String s = e.getAttribute(attr);
        return s.isEmpty() ? 0 : Double.parseDouble(s);
    }

    /** Reads <properties> into typed values: float/int become Double, bool becomes Boolean, rest String. */
    static Map<String, Object> properties(Element e) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Element holder : Xml.children(e, "properties"))
            for (Element p : Xml.children(holder, "property")) {
                String type = p.getAttribute("type"), value = p.getAttribute("value");
                Object v;
                if (type.equals("float") || type.equals("int")) v = Double.parseDouble(value);
                else if (type.equals("bool")) v = Boolean.parseBoolean(value);
                else v = value;
                out.put(p.getAttribute("name"), v);
            }
        return out;
    }
}
