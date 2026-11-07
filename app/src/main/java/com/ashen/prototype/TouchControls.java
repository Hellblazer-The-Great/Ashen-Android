package com.ashen.prototype;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On-screen buttons that replace the keyboard. Each button maps to the same action names the keyboard
 * used (left, jump, light, ...). Coordinates are logical screen units (960x540), so layout is resolution-free.
 * Multi-touch: every finger is tracked by pointer id, so holding move + attack together works.
 */
final class TouchControls {
    static final class Button {
        final String action, label;
        final double cx, cy, r;

        Button(String action, String label, double cx, double cy, double r) {
            this.action = action; this.label = label; this.cx = cx; this.cy = cy; this.r = r;
        }

        boolean contains(double x, double y) {
            double dx = x - cx, dy = y - cy;
            return dx * dx + dy * dy <= r * r;
        }
    }

    private final List<Button> game = new ArrayList<>();
    private final List<Button> menu = new ArrayList<>();

    /** Builds the button lists from the "game" and "menu" groups in ui.xml. */
    TouchControls(Ui ui) {
        for (Map<String, String> b : ui.buttons("game")) game.add(fromXml(b));
        for (Map<String, String> b : ui.buttons("menu")) menu.add(fromXml(b));
        joyRadius = ui.num("move", "radius", 80);
        joyDead = ui.num("move", "deadzone", 0.12);
        joyHomeX = ui.num("move", "x", 150);
        joyHomeY = ui.num("move", "y", 455);
        joyColor = ui.str("move", "color", "#ffffff");
        joyX = joyHomeX; joyY = joyHomeY; knobX = joyHomeX; knobY = joyHomeY;
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    private static Button fromXml(Map<String, String> b) {
        return new Button(b.get("action"), b.get("label"), num(b, "x"), num(b, "y"), num(b, "r"));
    }

    private static double num(Map<String, String> b, String key) { return Double.parseDouble(b.get(key)); }

    private final Map<Integer, Button> active = new HashMap<>();

    // floating analog stick for movement
    private final double joyRadius, joyDead, joyHomeX, joyHomeY;
    private final String joyColor;
    private int joyPointer = -1;
    private double joyX, joyY, knobX, knobY;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean menuLayout;

    private List<Button> layout() { return menuLayout ? menu : game; }

    /** Call once per frame before the world updates. Switching layouts releases every held button. */
    void sync(boolean menu, Input in) {
        if (menu != menuLayout) {
            menuLayout = menu;
            active.clear();
            resetStick(in);
            in.releaseAll();
        }
    }

    private void resetStick(Input in) {
        joyPointer = -1;
        in.setAxis(0);
        joyX = joyHomeX; joyY = joyHomeY; knobX = joyHomeX; knobY = joyHomeY;
    }

    /** Sets the horizontal axis from the finger position relative to where the stick was pressed. */
    private void updateStick(double x, double y, Input in) {
        double dx = x - joyX, dy = y - joyY;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > joyRadius && d > 0) { dx *= joyRadius / d; dy *= joyRadius / d; }
        knobX = joyX + dx; knobY = joyY + dy;
        double v = dx / joyRadius;                       // -1..1
        double m = Math.abs(v);
        if (m < joyDead) v = 0;
        else v = Math.signum(v) * (m - joyDead) / (1 - joyDead); // re-scale so the stick starts at 0 past the dead zone
        in.setAxis(v);
    }



    private Button hit(double x, double y) {
        for (Button b : layout()) if (b.contains(x, y)) return b;
        return null;
    }

    void down(int pointer, double x, double y, Input in) {
        Button b = hit(x, y);
        if (b != null) {
            active.put(pointer, b);
            in.press(b.action);
            return;
        }
        // static stick: it starts only when the press lands on its base, and always reads from the home position
        double dx = x - joyHomeX, dy = y - joyHomeY;
        if (!menuLayout && joyPointer == -1 && dx * dx + dy * dy <= joyRadius * joyRadius * 1.3 * 1.3) {
            joyPointer = pointer;
            joyX = joyHomeX; joyY = joyHomeY;
            updateStick(x, y, in);
        }
    }

    void move(int pointer, double x, double y, Input in) {
        if (pointer == joyPointer) { updateStick(x, y, in); return; }
        Button old = active.get(pointer);
        if (old == null || old.contains(x, y)) return;
        in.release(old.action);
        active.remove(pointer);
        Button nb = hit(x, y);
        if (nb != null) { active.put(pointer, nb); in.press(nb.action); }
    }

    void up(int pointer, Input in) {
        if (pointer == joyPointer) { resetStick(in); return; }
        Button b = active.remove(pointer);
        if (b != null) in.release(b.action);
    }

    void cancelAll(Input in) {
        active.clear();
        resetStick(in);
        in.releaseAll();
    }

    /** Draws the translucent buttons, brighter while held. */
    void draw(Canvas c, boolean menu, Input in) {
        if (!menu) drawStick(c);
        List<Button> buttons = menu ? this.menu : game;
        for (Button b : buttons) {
            boolean on = in.held(b.action);
            fill.setColor(on ? Color.argb(150, 255, 255, 255) : Color.argb(55, 255, 255, 255));
            c.drawCircle((float) b.cx, (float) b.cy, (float) b.r, fill);
            label.setColor(on ? Color.BLACK : Color.argb(200, 255, 255, 255));
            label.setTextSize(b.label.length() > 2 ? 13 : 20);
            Paint.FontMetrics fm = label.getFontMetrics();

            float textWidth = label.measureText(b.label);
            float textX = (float) b.cx - textWidth / 2f;
            float textY = (float) b.cy - (fm.ascent + fm.descent) / 2f;

            c.drawText(b.label, textX, textY, label);
        }
    }

    private void drawStick(Canvas c) {
        boolean active = joyPointer != -1;
        int base = Color.parseColor(joyColor);
        double bx = joyHomeX, by = joyHomeY;
        fill.setColor(Color.argb(active ? 70 : 35, Color.red(base), Color.green(base), Color.blue(base)));
        c.drawCircle((float) bx, (float) by, (float) joyRadius, fill);
        fill.setColor(Color.argb(active ? 190 : 90, Color.red(base), Color.green(base), Color.blue(base)));
        c.drawCircle((float) (active ? knobX : joyHomeX), (float) (active ? knobY : joyHomeY), (float) (joyRadius * 0.42), fill);
    }
}
