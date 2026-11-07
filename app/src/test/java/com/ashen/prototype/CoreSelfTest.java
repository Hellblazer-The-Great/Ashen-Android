package com.ashen.prototype;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Headless simulation of the core game (no Android needed). Same script as the desktop SelfTest:
 * 120 simulated seconds of walking, attacking, rolling, blocking and drinking flasks.
 */
public class CoreSelfTest {
    private static final Path DATA = Paths.get("src/main/assets/data");

    static Data loadData() throws Exception {
        return new Data(name -> new String(Files.readAllBytes(DATA.resolve(name)), StandardCharsets.UTF_8));
    }

    static World simulate(Data data) {
        World w = new World(data);
        Input in = new Input();
        double dt = 1 / 60.0;
        for (int f = 0; f < 60 * 120; f++) {
            in.hold("right", f % 600 < 500);
            if (f % 40 == 0) in.tap("light");
            if (f % 170 == 0) in.tap("roll");
            if (f % 250 == 0) in.tap("heavy");
            in.hold("block", f % 300 > 260);
            if (f % 90 == 0) in.tap("flask");
            if (w.menu) in.tap("back");
            w.update(in, dt);
            in.endFrame();
        }
        return w;
    }

    @Test
    public void headlessSimulationRunsWithoutErrors() throws Exception {
        World w = simulate(loadData());
        assertTrue("player should exist", w.player != null);
        assertTrue("player x should be inside the level", w.player.x >= 0 && w.player.x <= w.levelW);
    }

    public static void main(String[] args) throws Exception {
        World w = simulate(loadData());
        System.out.printf("OK px=%.0f hp=%.0f souls=%d level=%d enemiesAlive=%d%n", w.player.x, w.player.hp,
                w.player.souls, w.player.level(), w.enemies.stream().filter(e -> e.alive()).count());
    }
}
