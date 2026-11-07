package com.ashen.prototype;

/** Axis-aligned rectangle in world units. Replaces java.awt.geom.Rectangle2D so the core has no UI dependency. */
final class Box {
    final double x, y, w, h;

    Box(double x, double y, double w, double h) { this.x = x; this.y = y; this.w = w; this.h = h; }

    /** Same semantics as Rectangle2D.intersects: touching edges do not count. */
    boolean intersects(Box o) { return intersects(o.x, o.y, o.w, o.h); }

    boolean intersects(double x0, double y0, double w0, double h0) {
        if (w <= 0 || h <= 0 || w0 <= 0 || h0 <= 0) return false;
        return x0 + w0 > x && y0 + h0 > y && x0 < x + w && y0 < y + h;
    }
}
