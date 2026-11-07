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
 * Draws the World onto a Canvas. Port of the desktop GamePanel: same draw order, same
 * data-driven visuals, same HUD. Logical space is screen.w x screen.h (960x540) and the view scales it.
 */
final class Renderer {
    private final World w;
    private final Assets assets;
    private final Map<String, Object> vis;
    private final int W, H;
    private final Map<String, Integer> colors = new HashMap<>();
    private final Paint fillPaint = new Paint(), imgPaint = new Paint(), textPaint = new Paint(Paint.ANTI_ALIAS_FLAG), strokePaint = new Paint();
    private final RectF dst = new RectF();
    boolean debug;
    double dt = 1 / 60.0;

    Renderer(World w, Assets assets) {
        this.w = w;
        this.assets = assets;
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

    private void bar(Canvas c, int x, int y, int bw, int bh, double frac, int color) {
        rect(c, Color.argb(170, 0, 0, 0), x - 2, y - 2, bw + 4, bh + 4);
        rect(c, color, x, y, (int) (bw * Math.max(0, Math.min(1, frac))), bh);
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
            int sc = (int) getNum(m, "scale", 5), iw = b.getWidth() * sc, ih = b.getHeight() * sc;
            int off = -((int) (w.camX * getNum(m, "factor", 0))) % iw;
            for (int x = off - iw; x < W + iw; x += iw) image(c, b, x, H - ih, iw, ih, false, 255);
        }
    }

    private void tile(Canvas c, Bitmap b, int sc, int x0, int x1, int y, int y1) {
        int s = b.getWidth() * sc;
        for (int y2 = y; y2 < y1; y2 += s)
            for (int x = x0; x < x1; x += s) image(c, b, x, y2, s, s, false, 255);
    }

    private void drawWorld(Canvas c) {
        int gy = (int) w.groundY, camL = (int) w.camX - 64, camR = (int) w.camX + W + 64;

        // scenery
        for (Object o : arr(w.d.level.get("scenery"))) {
            Map<String, Object> m = obj(o);
            Bitmap b = assets.get(getStr(m, "texture", ""));
            int x = (int) getNum(m, "x", 0);
            if (b != null) {
                int sc = (int) getNum(m, "scale", 4);
                image(c, b, x, gy - b.getHeight() * sc, b.getWidth() * sc, b.getHeight() * sc, false, 255);
            } else {
                rect(c, col(getStr(m, "color", "#1b1e26")), x, gy - (int) getNum(m, "h", 200), (int) getNum(m, "w", 50), (int) getNum(m, "h", 200));
            }
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
            if (w.nearBonfire() == b && w.player.alive()) text(c, "REST", (int) b.x, gy - 90, 16, Color.WHITE, true);
        }

        // boss gate
        Map<String, Object> gt = obj(vis.get("gate"));
        if (w.gateX < 1e8) {
            Bitmap img = assets.get(getStr(gt, "texture", ""));
            int sc = (int) getNum(gt, "scale", 5);
            double ga = w.gateLocked ? 1.0 : 0.35;   // closed gate is fully opaque, open gate is faded
            int alpha = (int) (255 * ga);
            if (img != null) image(c, img, (int) w.gateX - img.getWidth() * sc / 2, gy - img.getHeight() * sc, img.getWidth() * sc, img.getHeight() * sc, false, alpha);
            else rect(c, Color.argb(alpha, 255, 255, 255), (int) w.gateX - 8, gy - 200, 16, 200);
            rect(c, Color.argb((int) ((w.gateLocked ? 90 : 25) * ga), 230, 230, 255), (int) w.gateX - 10, gy - 160, 20, 160);
        }

        // bloodstain
        if (w.stain != null) {
            fillPaint.setColor(Color.argb(200, 120, 220, 255));
            c.drawOval(new RectF((float) w.stain.x - 10, (float) w.stain.y - 20, (float) w.stain.x + 10, (float) w.stain.y), fillPaint);
            text(c, "" + w.stain.souls, (int) w.stain.x, (int) w.stain.y - 28, 12, Color.rgb(160, 230, 255), true);
        }

        // actors
        for (Enemy e : w.enemies) if (e.alive() && e.active) {
            drawActor(c, e, assets.sprite(e.def.sprite()), col(e.color));
            if (e.hp < e.maxHp && !e.def.boss()) bar(c, (int) e.cx() - 20, (int) e.y - 10, 40, 4, e.hp / e.maxHp, Color.argb(255, 190, 40, 40));
            if (e.state == Actor.State.ATTACK && e.atkPhase() == 0) text(c, "!", (int) e.cx(), (int) e.y - 14, 22, Color.rgb(255, 220, 60), true);
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
            int n = an.frames.length, idx;
            if (want.startsWith("attack") && a.atk != null) {
                double total = a.atk.windup() + a.atk.active() + a.atk.recovery();
                idx = Math.min(n - 1, (int) (a.atkT / total * n));
            } else {
                int f = (int) (a.animT * an.fps);
                idx = an.loop ? f % n : Math.min(n - 1, f);
            }
            Bitmap bi = an.frames[idx];
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
        bar(c, 20, 20, (int) (p.maxHp * 2.2), 14, p.hp / p.maxHp, Color.argb(255, 190, 35, 35));
        bar(c, 20, 40, (int) (p.maxStamina * 2.2), 9, p.stamina / p.maxStamina, Color.argb(255, 70, 170, 70));
        text(c, p.weapon().name() + "   Flasks: " + p.flasks + "/" + p.maxFlasks + "   Lv " + p.level(), 20, 70, 14, Color.WHITE, false);
        text(c, "Souls: " + p.souls, 20, 92, 18, Color.rgb(160, 230, 255), false);

        Enemy boss = w.bossEnemy();
        if (boss != null && w.gateLocked && boss.alive()) {
            bar(c, W / 2 - 250, 20, 500, 12, boss.hp / boss.maxHp, Color.argb(255, 160, 30, 30));
            text(c, boss.def.name(), W / 2, 50, 16, Color.WHITE, true);
        }
        if (w.bannerT > 0 && !w.banner.isEmpty()) text(c, w.banner, W / 2, H / 2 - 40, p.alive() ? 30 : 56, col(w.bannerColor), true);
        if (!p.alive()) {
            rect(c, Color.argb((int) Math.min(170, w.deathT * 90), 0, 0, 0), 0, 0, W, H);
            text(c, "YOU DIED", W / 2, H / 2, 64, Color.rgb(190, 25, 25), true);
        }
        if (w.menu) drawMenu(c);
    }

    private void drawMenu(Canvas c) {
        Player p = w.player;
        rect(c, Color.argb(200, 0, 0, 0), W / 2 - 220, 90, 440, 300);
        text(c, "BONFIRE - Level Up", W / 2, 130, 24, Color.rgb(255, 180, 64), true);
        text(c, "Souls: " + p.souls + "    Cost: " + p.levelCost() + "    Level: " + p.level(), W / 2, 160, 14, Color.WHITE, true);
        int y = 200;
        for (int i = 0; i < w.statKeys.size(); i++, y += 36) {
            String k = w.statKeys.get(i);
            String label = getStr(obj(obj(w.d.player.get("stats")).get(k)), "label", k);
            text(c, (w.menuSel == i ? "> " : "  ") + label + "   " + p.pts.get(k), W / 2 - 120, y, 20,
                    w.menuSel == i ? Color.YELLOW : Color.LTGRAY, false);
        }
        text(c, (w.menuSel == w.statKeys.size() ? "> " : "  ") + "Leave", W / 2 - 120, y, 20,
                w.menuSel == w.statKeys.size() ? Color.YELLOW : Color.LTGRAY, false);
        text(c, "UP / DOWN choose    OK confirm    BACK close", W / 2, 375, 12, Color.GRAY, true);
    }
}
