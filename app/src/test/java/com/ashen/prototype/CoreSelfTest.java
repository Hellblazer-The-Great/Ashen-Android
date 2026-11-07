package com.ashen.prototype;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Headless simulation of the core game (no Android needed). Same script as the desktop SelfTest:
 * 120 simulated seconds of walking, attacking, rolling, blocking and drinking flasks.
 */
public class CoreSelfTest {
    private static final Path ASSETS = Paths.get("src/main/assets");

    static Data loadData() throws Exception {
        return new Data(name -> new String(Files.readAllBytes(ASSETS.resolve(name)), StandardCharsets.UTF_8));
    }

    static World simulate(Data data) {
        World w = new World(data);
        Input in = new Input();
        double dt = 1 / 60.0;
        for (int f = 0; f < 60 * 120; f++) {
            in.setAxis(f % 600 < 500 ? 1 : 0);
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

    /** The Tiled map must describe the same level the old level.json did. */
    @Test
    public void tiledMapMatchesOriginalLevel() throws Exception {
        Data d = loadData();
        assertEquals(3600, Json.getNum(d.level, "width", 0), 0.001);
        assertEquals(460, Json.getNum(d.level, "groundY", 0), 0.001);
        assertEquals(3, Json.arr(d.level.get("platforms")).size());
        assertEquals(2, Json.arr(d.level.get("bonfires")).size());
        assertEquals(7, Json.arr(d.level.get("spawns")).size());
        assertEquals(5, Json.arr(d.level.get("scenery")).size());
        assertEquals(5, Json.arr(Json.obj(d.level.get("visuals")).get("backgrounds")).size());
        Map<String, Object> gate = Json.obj(d.level.get("gate"));
        assertEquals(3000, Json.getNum(gate, "x", 0), 0.001);
        assertEquals("warden", Json.getStr(gate, "boss", ""));
        assertEquals("environment/door_closed.png", Json.getStr(gate, "texture", ""));
        assertEquals("environment/checkpoint_on.png", Json.getStr(Json.obj(Json.obj(d.level.get("visuals")).get("bonfire")), "on", ""));
    }

    /** The UI file must provide the touch layout for both the game and the bonfire menu. */
    @Test
    public void uiFileProvidesTouchLayout() throws Exception {
        Ui ui = loadData().ui;
        assertEquals(9, ui.buttons("game").size());
        assertTrue(ui.num("move", "radius", 0) > 0);
        assertEquals(4, ui.buttons("menu").size());
        assertEquals("LIGHT", ui.buttons("game").get(1).get("label"));
        assertEquals(20, ui.num("health", "x", -1), 0.001);
        assertEquals("v0.3.1", ui.str("version", "text", ""));
    }

    @Test
    public void headlessSimulationRunsWithoutErrors() throws Exception {
        World w = simulate(loadData());
        assertTrue("player should exist", w.player != null);
        assertTrue("player x should be inside the level", w.player.x >= 0 && w.player.x <= w.levelW);
    }

    public static void main(String[] args) throws Exception {
        Data d = loadData();
        System.out.println("map: platforms=" + Json.arr(d.level.get("platforms")).size() + " spawns=" + Json.arr(d.level.get("spawns")).size()
                + " scenery=" + Json.arr(d.level.get("scenery")).size() + " gateX=" + Json.getNum(Json.obj(d.level.get("gate")), "x", 0)
                + " ui.game=" + d.ui.buttons("game").size() + " ui.menu=" + d.ui.buttons("menu").size());
        World w = simulate(loadData());
        System.out.printf("OK px=%.0f hp=%.0f souls=%d level=%d enemiesAlive=%d%n", w.player.x, w.player.hp,
                w.player.souls, w.player.level(), w.enemies.stream().filter(e -> e.alive()).count());
    }
}
