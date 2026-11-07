package com.ashen.prototype;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

/**
 * The game surface. Runs the fixed-logic loop on Choreographer (vsync), letterboxes the 960x540
 * logical screen to any phone size, and forwards touches to TouchControls.
 */
public final class GameView extends View {
    private final World world;
    private final Renderer renderer;
    private final TouchControls touch;
    private final Input input = new Input();
    private final int logicalW, logicalH;

    private boolean running, posted;
    private long lastNanos;
    private float scale = 1f, offX, offY;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            posted = false;
            if (!running) return;
            double dt = lastNanos == 0 ? 1 / 60.0 : Math.min(0.033, (frameTimeNanos - lastNanos) / 1e9);
            lastNanos = frameTimeNanos;

            touch.sync(world.menu, input);
            if (input.pressed("debug")) renderer.debug = !renderer.debug;
            world.update(input, dt);
            renderer.dt = dt;
            input.endFrame();

            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
            posted = true;
        }
    };

    public GameView(Context context, World world, Assets assets, Ui ui) {
        super(context);
        this.world = world;
        this.renderer = new Renderer(world, assets, ui);
        this.touch = new TouchControls(ui);
        this.logicalW = (int) world.screenW;
        this.logicalH = (int) world.screenH;
        setKeepScreenOn(true);
    }

    void start() {
        running = true;
        lastNanos = 0;
        if (!posted) {
            posted = true;
            Choreographer.getInstance().postFrameCallback(frame);
        }
    }

    void stop() {
        running = false;
        touch.cancelAll(input);
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() / (float) logicalW, getHeight() / (float) logicalH);
        scale = s;
        offX = (getWidth() - logicalW * s) / 2f;
        offY = (getHeight() - logicalH * s) / 2f;
        c.drawColor(Color.BLACK);
        c.save();
        c.translate(offX, offY);
        c.scale(s, s);
        renderer.render(c);
        touch.draw(c, world.menu, input);
        c.restore();
    }

    private double toLogicalX(float x) { return (x - offX) / scale; }
    private double toLogicalY(float y) { return (y - offY) / scale; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                touch.down(e.getPointerId(i), toLogicalX(e.getX(i)), toLogicalY(e.getY(i)), input);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++)
                    touch.move(e.getPointerId(i), toLogicalX(e.getX(i)), toLogicalY(e.getY(i)), input);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                touch.up(e.getPointerId(e.getActionIndex()), input);
                break;
            case MotionEvent.ACTION_CANCEL:
                touch.cancelAll(input);
                break;
            default:
                break;
        }
        return true;
    }
}
