package com.ashen.prototype;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import static com.ashen.prototype.Json.*;

/**
 * Texture loading + sprite-sheet slicing, read from APK assets (assets/textures/...).
 * Fully data-driven: JSON "sprite" blocks describe sheets, frame sizes and animations.
 * Missing files, or enabled=false (game.json "useTextures": false), make callers fall back to rectangles.
 */
final class Assets {
    static final String ROOT = "textures/";

    static final class Anim {
        Bitmap[] frames;
        double fps, cx, feet, spinTime;
        boolean loop;
    }

    static final class Sprite {
        double scale = 1;
        boolean facesRight = true;
        final Map<String, Anim> anims = new HashMap<>();

        /** "attack:slash1" -> "attack:slash1" -> "attack" -> "idle". */
        Anim get(String name) {
            Anim a = anims.get(name);
            if (a == null && name.contains(":")) a = anims.get(name.substring(0, name.indexOf(':')));
            return a != null ? a : anims.get("idle");
        }
    }

    boolean enabled = true;
    private final AssetManager am;
    private final Map<String, Bitmap> cache = new HashMap<>();
    private final Map<Object, Sprite> sprites = new IdentityHashMap<>();

    Assets(AssetManager am) { this.am = am; }

    /** Image for a path relative to assets/textures/, or null if missing or disabled. */
    Bitmap get(String rel) {
        if (!enabled || rel == null || rel.isEmpty()) return null;
        if (cache.containsKey(rel)) return cache.get(rel);
        Bitmap bmp = null;
        try (InputStream in = am.open(ROOT + rel)) {
            bmp = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            bmp = null; // file not bundled: caller uses its rectangle fallback
        }
        cache.put(rel, bmp);
        return bmp;
    }

    Sprite sprite(Map<String, Object> def) {
        if (!enabled || def == null || def.isEmpty()) return null;
        if (sprites.containsKey(def)) return sprites.get(def);
        Sprite s = build(def);
        sprites.put(def, s);
        return s;
    }

    private Sprite build(Map<String, Object> def) {
        Sprite s = new Sprite();
        s.scale = getNum(def, "scale", 1);
        s.facesRight = getBool(def, "facesRight", true);
        double dcx = getNum(def, "cx", 0), dfeet = getNum(def, "feet", 0);
        for (Map.Entry<String, Object> e : obj(def.get("anims")).entrySet()) {
            Map<String, Object> m = obj(e.getValue());
            Bitmap sheet = get(getStr(m, "sheet", ""));
            if (sheet == null) continue;
            int fw = (int) getNum(m, "fw", sheet.getWidth()), fh = (int) getNum(m, "fh", sheet.getHeight());
            int cols = sheet.getWidth() / fw, total = cols * (sheet.getHeight() / fh);
            int from = (int) getNum(m, "from", 0), to = Math.min((int) getNum(m, "to", total - 1), total - 1);
            List<Bitmap> fr = new ArrayList<>();
            for (int i = from; i <= to; i++) {
                Bitmap sub = Bitmap.createBitmap(sheet, (i % cols) * fw, (i / cols) * fh, fw, fh);
                if (!blank(sub)) fr.add(sub);
            }
            if (fr.isEmpty()) continue;
            Anim a = new Anim();
            a.frames = fr.toArray(new Bitmap[0]);
            a.fps = getNum(m, "fps", 8); a.loop = getBool(m, "loop", true); a.spinTime = getNum(m, "spinTime", 0);
            a.cx = getNum(m, "cx", dcx); a.feet = getNum(m, "feet", dfeet);
            s.anims.put(e.getKey(), a);
        }
        return s.anims.isEmpty() ? null : s;
    }

    private static boolean blank(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int p : px) if ((p >>> 24) != 0) return false;
        return true;
    }

    /** Reads a bundled text file such as "data/game.json". */
    static String readAsset(AssetManager am, String path) throws IOException {
        try (InputStream in = am.open(path)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        }
    }
}
