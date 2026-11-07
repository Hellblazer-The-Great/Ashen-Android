package com.ashen.prototype;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.HashMap;
import java.util.Map;
import static com.ashen.prototype.Json.*;

/**
 * Draws the World onto a Canvas. Port of the desktop GamePanel. Every HUD position, size, colour and label
 * comes from assets/ui/ui.xml (via Ui); every level element comes from the Tiled map (via TmxLoader).
 * Logical space is screen.w x screen.h (960x540) and the view scales it.
 */
final class Renderer {
    private final World w;
    private final Assets assets;
    private final Ui ui;
    private final Map<String, Object> vis;
    private final int W, H;
    private final Map<String, Integer> colors = new HashMap<>();
    private final Paint fillPaint = new Paint(), imgPaint = new Paint(), textPaint = new Paint(Paint.ANTI_ALIAS_FLAG), strokePaint = new Paint();
    private final RectF dst = new RectF();
    boolean debug;
    double dt = 1 / 60.0;

    Renderer(World w, Assets assets, Ui ui) {
        this.w = w;
        this.assets = assets;
        this.ui = ui;
        vis = obj(w.d.level.get("visuals"));
        W = (int) w.screenW; H = (int) w.screenH;
        fillPaint.setStyle(Paint.Style.FILL);
        imgPaint.setFilterBitmap(false); // pixel art: nearest-neighbour scaling
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
    }

    int col(String hex) {
        Integer c = colors.get(hex);
        if (c == null) {
            try { c = Color.parseColor(hex); } catch (IllegalArgumentException e) { c = Color.MAGENTA; }
            colors.put(hex, c);
        }
        return c;
    }

    /** Colour of a UI element attribute, e.g. ui colour("health") -> #be2323. */
    private int uiColor(String id, String attr, String def) { return col(ui.str(id, attr, def)); }

    // ------------------------------------------------------------------ primitives
    private void rect(Canvas c, int color, double x, double y, double rw, double rh) {
        fillPaint.setColor(color);
        c.drawRect((float) x, (float) y, (float) (x + rw), (float) (y + rh), fillPaint);
    }

    /** Draws a bitmap into the box (x,y,rw,rh); flip mirrors it horizontally inside that box. */
    private void image(Canvas c, Bitmap b, double x, double y, double rw, double rh, boolean flip, int alpha) {
        imgPaint.setAlpha(alpha);
        if (flip) {
            c.save();
            c.translate((float) (x + rw), (float) y);
            c.scale(-1f, 1f);
            dst.set(0, 0, (float) rw, (float) rh);
            c.drawBitmap(b, null, dst, imgPaint);
            c.restore();
        } else {
            dst.set((float) x, (float) y, (float) (x + rw), (float) (y + rh));
            c.drawBitmap(b, null, dst, imgPaint);
        }
        imgPaint.setAlpha(255);
    }

    private void text(Canvas c, String s, double x, double y, int size, int color, boolean center) {
        textPaint.setTextSize(size);
        float tx = center ? (float) x - textPaint.measureText(s) / 2f : (float) x;
        textPaint.setColor(Color.BLACK);
        c.drawText(s, tx + 1, (float) y + 1, textPaint);
        textPaint.setColor(color);
        c.drawText(s, tx, (float) y, textPaint);
    }

    private void bar(Canvas c, double x, double y, double bw, double bh, double frac, int color) {
        rect(c, Color.argb(170, 0, 0, 0), x - 2, y - 2, bw + 4, bh + 4);
        rect(c, color, x, y, bw * Math.max(0, Math.min(1, frac)), bh);
    }

    // ------------------------------------------------------------------ frame
    void render(Canvas c) {
        drawBackground(c);
        int cam = (int) w.camX;
        c.save();
        c.translate(-cam, 0);
        drawWorld(c);
        c.restore();
        drawHud(c);
    }

    private void drawBackground(Canvas c) {
        rect(c, col(getStr(w.d.level, "skyColor", "#14161c")), 0, 0, W, H);
        for (Object o : arr(vis.get("backgrounds"))) {
            Map<String, Object> m = obj(o);
            Bitmap b = assets.get(getStr(m, "texture", ""));
            if (b == null) continue;
            double iw = getNum(m, "w", W), ih = getNum(m, "h", H), by = getNum(m, "y", H - ih);
            int off = -((int) (w.camX * getNum(m, "factor", 0))) % (int) iw;
            for (double x = off - iw; x < W + iw; x += iw) image(c, b, x, by, iw, ih, false, 255);
        }
    }

    private void tile(Canvas c, Bitmap b, int sc, int x0, int x1, int y, int y1) {
        int s = b.getWidth() * sc;
        for (int y2 = y; y2 < y1; y2 += s)
            for (int x = x0; x < x1; x += s) image(c, b, x, y2, s, s, false, 255);
    }

    private void drawWorld(Canvas c) {
        int gy = (int) w.groundY, camL = (int) w.camX - 64, camR = (int) w.camX + W + 64;

        // scenery: boxes from the map (top-left corner and size)
        for (Object o : arr(w.d.level.get("scenery"))) {
            Map<String, Object> m = obj(o);
            Bitmap b = assets.get(getStr(m, "texture", ""));
            double x = getNum(m, "x", 0), y = getNum(m, "y", 0), sw = getNum(m, "w", 50), sh = getNum(m, "h", 200);
            if (b != null) image(c, b, x, y, sw, sh, false, 255);
            else rect(c, col("#1b1e26"), x, y, sw, sh);
        }

        // ground
        Map<String, Object> gr = obj(vis.get("ground"));
        Bitmap top = assets.get(getStr(gr, "top", "")), bodyTex = assets.get(getStr(gr, "fill", ""));
        int gs = (int) getNum(gr, "scale", 3);
        if (top != null && bodyTex != null) {
            int x0 = Math.max(0, camL) / (16 * gs) * (16 * gs);
            tile(c, top, gs, x0, camR, gy, gy + 16 * gs);
            tile(c, bodyTex, gs, x0, camR, gy + 16 * gs, H + 16 * gs);
        } else {
            rect(c, col(getStr(w.d.level, "groundColor", "#2a2c33")), camL, gy, camR - camL, H);
        }

        // platforms
        Map<String, Object> pl = obj(vis.get("platform"));
        Bitmap pimg = assets.get(getStr(pl, "texture", ""));
        int ps = (int) getNum(pl, "scale", 3);
        for (double[] p : w.platforms) {
            if (pimg != null) tile(c, pimg, ps, (int) p[0], (int) (p[0] + p[2]), (int) p[1], (int) p[1] + 1);
            else rect(c, col(getStr(w.d.level, "platformColor", "#3b3e48")), (int) p[0], (int) p[1], (int) p[2], 12);
        }

        // bonfires
        Map<String, Object> bf = obj(vis.get("bonfire"));
        for (World.Bonfire b : w.bonfires) {
            boolean lit = b.id.equals(w.player.respawn);
            Bitmap img = assets.get(getStr(bf, lit ? "on" : "off", ""));
            int sc = (int) getNum(bf, "scale", 4);
            if (img != null) image(c, img, (int) b.x - img.getWidth() * sc / 2, gy - img.getHeight() * sc, img.getWidth() * sc, img.getHeight() * sc, false, 255);
            else rect(c, lit ? col("#ffb040") : col("#6a4a30"), (int) b.x - 12, gy - 30, 24, 30);
            if (w.nearBonfire() == b && w.player.alive())
                text(c, ui.str("restPrompt", "text", "REST"), (int) b.x, gy + ui.num("restPrompt", "dy", -90), (int) ui.num("restPrompt", "size", 16),
                        uiColor("restPrompt", "color", "#ffffff"), true);
        }

        // boss gate (a map object; faded while the gate is open)
        Map<String, Object> gt = obj(w.d.level.get("gate"));
        if (w.gateX < 1e8) {
            Bitmap img = assets.get(getStr(gt, "texture", ""));
            double ga = w.gateLocked ? 1.0 : 0.35;   // closed gate is fully opaque, open gate is faded
            int alpha = (int) (255 * ga);
            double gw = getNum(gt, "w", 16), gh = getNum(gt, "h", 200), gx = w.gateX - gw / 2, gyTop = getNum(gt, "y", gy - gh);
            if (img != null) image(c, img, gx, gyTop, gw, gh, false, alpha);
            else rect(c, Color.argb(alpha, 255, 255, 255), gx, gyTop, gw, gh);
            rect(c, Color.argb((int) ((w.gateLocked ? 90 : 25) * ga), 230, 230, 255), w.gateX - 10, gy - 160, 20, 160);
        }

        // bloodstain
        if (w.stain != null) {
            fillPaint.setColor(Color.argb((int) ui.num("stain", "alpha", 200), 120, 220, 255));
            double sw = ui.num("stain", "width", 20), sh = ui.num("stain", "height", 20);
            double sdy = ui.num("stain", "dy", -20);
            c.drawOval(new RectF((float) (w.stain.x - sw / 2), (float) (w.stain.y + sdy), (float) (w.stain.x + sw / 2), (float) (w.stain.y + sdy + sh)), fillPaint);
            text(c, "" + w.stain.souls, (int) w.stain.x, (int) (w.stain.y + ui.num("stainValue", "dy", -28)),
                    (int) ui.num("stainValue", "size", 12), uiColor("stainValue", "color", "#a0e6ff"), true);
        }

        // actors
        for (Enemy e : w.enemies) if (e.alive() && e.active) {
            drawActor(c, e, assets.sprite(e.def.sprite()), col(e.color));
            if (e.hp < e.maxHp && !e.def.boss()) {
                bar(c, e.cx() + ui.num("enemyBar", "dx", -20), e.y + ui.num("enemyBar", "dy", -10),
                        ui.num("enemyBar", "width", 40), ui.num("enemyBar", "height", 4), e.hp / e.maxHp,
                        uiColor("enemyBar", "color", "#be2828"));
            }
            if (e.state == Actor.State.ATTACK && e.atkPhase() == 0)
                text(c, ui.str("alert", "text", "!"), e.cx(), e.y + ui.num("alert", "dy", -14), (int) ui.num("alert", "size", 22),
                        uiColor("alert", "color", "#ffdc3c"), true);
        }
        drawActor(c, w.player, assets.sprite(obj(w.d.player.get("sprite"))), col(getStr(w.d.player, "color", "#4fa3ff")));
    }

    // ------------------------------------------------------------------ actors
    private String animFor(Actor a) {
        switch (a.state) {
            case DEAD: return "death";
            case STAGGER: return "hit";
            case ROLL: return "roll";
            case DRINK: return "drink";
            case BLOCK: return "block";
            case ATTACK: return "attack:" + (a.atk == null ? "" : a.atk.id());
            default:
                if (!a.onGround) return "jump";
                return (a.state == Actor.State.WALK || Math.abs(a.vx) > 20) ? "walk" : "idle";
        }
    }

    /**
     * Frame for an attack. If the animation has per-phase frames (windup / active / recovery), the strike frame
     * is shown during the active window; otherwise the frames are spread across the whole attack.
     */
    private Bitmap attackFrame(Assets.Anim an, Actor a) {
        int ph = a.atkPhase();
        if (an.phases != null && ph >= 0 && ph <= 2) {
            String key = ph == 0 ? "windup" : ph == 1 ? "active" : "recovery";
            Bitmap[] fr = an.phases.get(key);
            if (fr != null && fr.length > 0) {
                double start = ph == 0 ? 0 : ph == 1 ? a.atk.windup() : a.atk.windup() + a.atk.active();
                double dur = ph == 0 ? a.atk.windup() : ph == 1 ? a.atk.active() : a.atk.recovery();
                double p = dur > 0 ? (a.atkT - start) / dur : 0;
                p = Math.max(0, Math.min(0.999, p));
                return fr[(int) (p * fr.length)];
            }
        }
        double total = a.atk.windup() + a.atk.active() + a.atk.recovery();
        int idx = Math.min(an.frames.length - 1, (int) (a.atkT / total * an.frames.length));
        return an.frames[idx];
    }

    private void drawActor(Canvas c, Actor a, Assets.Sprite sp, int fallback) {
        String want = animFor(a);
        if (!want.equals(a.animName)) { a.animName = want; a.animT = 0; } else a.animT += dt;
        Assets.Anim an = sp == null ? null : sp.get(want);
        if (an == null) {
            rect(c, a.flash > 0 ? Color.WHITE : fallback, a.x, a.y, a.w, a.h);
            rect(c, Color.BLACK, a.cx() + a.facing * a.w * 0.25 - 2, a.y + 8, 4, 4); // "eye" shows facing
            if (a.atk != null && a.atkPhase() == 1) {
                Box hb = a.hitbox();
                rect(c, Color.argb(140, 255, 255, 255), hb.x, hb.y, hb.w, hb.h);
            }
        } else {
            Bitmap bi;
            if (want.startsWith("attack") && a.atk != null) {
                bi = attackFrame(an, a);
            } else {
                int f = (int) (a.animT * an.fps);
                int n = an.frames.length;
                bi = an.frames[an.loop ? f % n : Math.min(n - 1, f)];
            }
            double s = sp.scale;
            boolean flip = (a.facing > 0) != sp.facesRight;
            c.save();
            if (an.spinTime > 0) {
                double theta = Math.min(1, a.animT / an.spinTime) * Math.PI * 2 * a.facing;
                c.rotate((float) Math.toDegrees(theta), (float) a.cx(), (float) (a.y + a.h / 2));
            }
            double fw = bi.getWidth() * s, fh = bi.getHeight() * s;
            double dy = a.y + a.h - an.feet * s;
            if (!flip) image(c, bi, a.cx() - an.cx * s, dy, fw, fh, false, 255);
            else image(c, bi, a.cx() + an.cx * s - fw, dy, fw, fh, true, 255);
            if (a.flash > 0) rect(c, Color.argb(110, 255, 255, 255), a.x, a.y, a.w, a.h);
            if (a instanceof Enemy && !((Enemy) a).color.equals(((Enemy) a).def.color()))
                rect(c, Color.argb(70, 200, 30, 30), a.x, a.y, a.w, a.h);
            c.restore();
        }
        if (debug) {
            strokePaint.setColor(Color.GREEN);
            c.drawRect((float) a.x, (float) a.y, (float) (a.x + a.w), (float) (a.y + a.h), strokePaint);
            if (a.atk != null) {
                strokePaint.setColor(a.atkPhase() == 1 ? Color.RED : Color.YELLOW);
                Box hb = a.hitbox();
                c.drawRect((float) hb.x, (float) hb.y, (float) (hb.x + hb.w), (float) (hb.y + hb.h), strokePaint);
            }
        }
    }

    // ------------------------------------------------------------------ HUD
    private void drawHud(Canvas c) {
        Player p = w.player;
        double hs = ui.num("health", "scale", 2.2), ss = ui.num("stamina", "scale", 2.2);
        bar(c, ui.x("health", W, 20), ui.y("health", H, 20), p.maxHp * hs, ui.num("health", "height", 14), p.hp / p.maxHp,
                uiColor("health", "color", "#be2323"));
        bar(c, ui.x("stamina", W, 20), ui.y("stamina", H, 40), p.maxStamina * ss, ui.num("stamina", "height", 9), p.stamina / p.maxStamina,
                uiColor("stamina", "color", "#46aa46"));
        text(c, p.weapon().name() + "   Flasks: " + p.flasks + "/" + p.maxFlasks + "   Lv " + p.level(),
                ui.x("weapon", W, 20), ui.y("weapon", H, 70), (int) ui.num("weapon", "size", 14), uiColor("weapon", "color", "#ffffff"), false);
        text(c, "Souls: " + p.souls, ui.x("souls", W, 20), ui.y("souls", H, 92), (int) ui.num("souls", "size", 18),
                uiColor("souls", "color", "#a0e6ff"), false);

        Enemy boss = w.bossEnemy();
        if (boss != null && w.gateLocked && boss.alive()) {
            bar(c, ui.x("bossBar", W, W / 2 - 250), ui.y("bossBar", H, 20), ui.num("bossBar", "width", 500), ui.num("bossBar", "height", 12),
                    boss.hp / boss.maxHp, uiColor("bossBar", "color", "#a01e1e"));
            text(c, boss.def.name(), ui.x("bossName", W, W / 2), ui.y("bossName", H, 50), (int) ui.num("bossName", "size", 16),
                    uiColor("bossName", "color", "#ffffff"), true);
        }
        if (w.bannerT > 0 && !w.banner.isEmpty()) {
            text(c, w.banner, W / 2, ui.y("banner", H, H / 2 - 40), (int) ui.num("banner", p.alive() ? "size" : "deadSize", p.alive() ? 30 : 56),
                    col(w.bannerColor), true);
        }
        if (!p.alive()) {
            int a = (int) Math.min(ui.num("deathFade", "maxAlpha", 170), w.deathT * ui.num("deathFade", "rate", 90));
            rect(c, Color.argb(a, 0, 0, 0), 0, 0, W, H);
            text(c, ui.str("deathText", "text", "YOU DIED"), ui.x("deathText", W, W / 2), ui.y("deathText", H, H / 2), (int) ui.num("deathText", "size", 64),
                    uiColor("deathText", "color", "#be1919"), true);
        }
        text(c, ui.str("version", "text", ""), ui.x("version", W, 868), ui.y("version", H, 530), (int) ui.num("version", "size", 12),
                uiColor("version", "color", "#7a7a7a"), false);
        if (w.menu) drawMenu(c);
    }

    private void drawMenu(Canvas c) {
        Player p = w.player;
        double px = ui.x("menuPanel", W, W / 2 - 220), py = ui.y("menuPanel", H, 90);
        rect(c, Color.argb((int) ui.num("menuPanel", "alpha", 200), 0, 0, 0), px, py,
                ui.num("menuPanel", "width", 440), ui.num("menuPanel", "height", 300));
        text(c, ui.str("menuTitle", "text", "BONFIRE - Level Up"), ui.x("menuTitle", W, W / 2), ui.y("menuTitle", H, 130),
                (int) ui.num("menuTitle", "size", 24), uiColor("menuTitle", "color", "#ffb440"), true);
        text(c, "Souls: " + p.souls + "    Cost: " + p.levelCost() + "    Level: " + p.level(),
                ui.x("menuInfo", W, W / 2), ui.y("menuInfo", H, 160), (int) ui.num("menuInfo", "size", 14), uiColor("menuInfo", "color", "#ffffff"), true);

        double rx = ui.x("menuRows", W, W / 2 - 120), ry = ui.y("menuRows", H, 200), step = ui.num("menuRows", "step", 36);
        int rsize = (int) ui.num("menuRows", "size", 20);
        int normal = uiColor("menuRows", "color", "#d3d3d3"), sel = uiColor("menuRows", "selected", "#ffff00");
        for (int i = 0; i < w.statKeys.size(); i++) {
            String k = w.statKeys.get(i);
            String label = getStr(obj(obj(w.d.player.get("stats")).get(k)), "label", k);
            text(c, (w.menuSel == i ? "> " : "  ") + label + "   " + p.pts.get(k), rx, ry + i * step, rsize, w.menuSel == i ? sel : normal, false);
        }
        int i = w.statKeys.size();
        text(c, (w.menuSel == i ? "> " : "  ") + ui.str("menuLeave", "text", "Leave"), rx, ry + i * step, rsize, w.menuSel == i ? sel : normal, false);
        text(c, ui.str("menuHint", "text", ""), ui.x("menuHint", W, W / 2), ui.y("menuHint", H, 375), (int) ui.num("menuHint", "size", 12),
                uiColor("menuHint", "color", "#888888"), true);
    }
}
