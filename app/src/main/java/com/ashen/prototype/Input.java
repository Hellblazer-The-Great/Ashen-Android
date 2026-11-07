package com.ashen.prototype;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Action-based input. The Android layer calls press/release from on-screen buttons; the core only asks
 * held(action) / pressed(action). Presses are reference-counted so two fingers on one button behave.
 */
final class Input {
    private final Map<String, Integer> refs = new HashMap<>();
    private final Set<String> down = new HashSet<>(), edge = new HashSet<>();
    private final Set<String> tapped = new HashSet<>(), forced = new HashSet<>();

    void press(String a) {
        Integer c = refs.get(a);
        int n = c == null ? 0 : c;
        refs.put(a, n + 1);
        if (n == 0) { down.add(a); edge.add(a); }
    }

    void release(String a) {
        Integer c = refs.get(a);
        if (c == null) return;
        if (c <= 1) { refs.remove(a); down.remove(a); }
        else refs.put(a, c - 1);
    }

    void releaseAll() { refs.clear(); down.clear(); }

    boolean held(String a) { return forced.contains(a) || down.contains(a); }

    boolean pressed(String a) { return tapped.contains(a) || edge.contains(a); }

    void endFrame() { edge.clear(); tapped.clear(); }

    // used by the headless simulation test
    void tap(String a) { tapped.add(a); }
    void hold(String a, boolean on) { if (on) forced.add(a); else forced.remove(a); }
}
