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

    private static final List<Button> GAME = new ArrayList<>();
    private static final List<Button> MENU = new ArrayList<>();
    static {
        // movement, bottom-left
        GAME.add(new Button("left", "<", 95, 455, 50));
        GAME.add(new Button("right", ">", 205, 455, 50));
        GAME.add(new Button("jump", "JUMP", 150, 365, 44));
        // combat, bottom-right
        GAME.add(new Button("light", "LIGHT", 840, 455, 56));
        GAME.add(new Button("heavy", "HEAVY", 730, 455, 50));
        GAME.add(new Button("block", "BLOCK", 840, 345, 48));
        GAME.add(new Button("roll", "ROLL", 730, 345, 46));
        GAME.add(new Button("flask", "FLASK", 620, 455, 44));
        GAME.add(new Button("interact", "USE", 620, 345, 44));
        // utility, top-right
        GAME.add(new Button("switchWeapon", "SWAP", 905, 62, 34));
        GAME.add(new Button("debug", "BOX", 905, 132, 28));

        // bonfire menu replaces the layout while open
        MENU.add(new Button("menuUp", "UP", 110, 440, 56));
        MENU.add(new Button("menuDown", "DOWN", 240, 440, 56));
        MENU.add(new Button("interact", "OK", 760, 440, 56));
        MENU.add(new Button("back", "BACK", 880, 440, 56));
    }

    private final Map<Integer, Button> active = new HashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean menuLayout;

    TouchControls() {
        label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        label.setTextAlign(Paint.Align.CENTER);
    }

    private List<Button> layout() { return menuLayout ? MENU : GAME; }

    /** Call once per frame before the world updates. Switching layouts releases every held button. */
    void sync(boolean menu, Input in) {
        if (menu != menuLayout) {
            menuLayout = menu;
            active.clear();
            in.releaseAll();
        }
    }

    private Button hit(double x, double y) {
        for (Button b : layout()) if (b.contains(x, y)) return b;
        return null;
    }

    void down(int pointer, double x, double y, Input in) {
        Button b = hit(x, y);
        if (b == null) return;
        active.put(pointer, b);
        in.press(b.action);
    }

    void move(int pointer, double x, double y, Input in) {
        Button old = active.get(pointer);
        if (old == null || old.contains(x, y)) return;
        in.release(old.action);
        active.remove(pointer);
        Button nb = hit(x, y);
        if (nb != null) { active.put(pointer, nb); in.press(nb.action); }
    }

    void up(int pointer, Input in) {
        Button b = active.remove(pointer);
        if (b != null) in.release(b.action);
    }

    void cancelAll(Input in) {
        active.clear();
        in.releaseAll();
    }

    /** Draws the translucent buttons, brighter while held. */
    void draw(Canvas c, boolean menu, Input in) {
        List<Button> buttons = menu ? MENU : GAME;
        for (Button b : buttons) {
            boolean on = in.held(b.action);
            fill.setColor(on ? Color.argb(150, 255, 255, 255) : Color.argb(55, 255, 255, 255));
            c.drawCircle((float) b.cx, (float) b.cy, (float) b.r, fill);
            label.setColor(on ? Color.BLACK : Color.argb(200, 255, 255, 255));
            label.setTextSize(b.label.length() > 2 ? 13 : 20);
            c.drawText(b.label, (float) b.cx, (float) b.cy + 5, label);
        }
    }
}
