package com.ashen.prototype;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.ashen.prototype.Json.*;

/** Loads every JSON file. All game content lives in assets/data; nothing here is content-specific. */
final class Data {
    /** Supplies the text of a data file by name, e.g. "game.json". Android: AssetManager. Tests: a folder. */
    interface Source { String read(String name) throws IOException; }

    /** For weapons 'damage' is a multiplier on weapon damage; for enemies it is absolute damage. */
    static final class AttackDef {
        private final String id;
        private final double windup, active, recovery, damage, reach, height, stamina, lunge, poise;

        AttackDef(String id, double windup, double active, double recovery, double damage,
                  double reach, double height, double stamina, double lunge, double poise) {
            this.id = id; this.windup = windup; this.active = active; this.recovery = recovery; this.damage = damage;
            this.reach = reach; this.height = height; this.stamina = stamina; this.lunge = lunge; this.poise = poise;
        }

        static AttackDef of(Map<String, Object> m) {
            return new AttackDef(getStr(m, "id", "attack"), getNum(m, "windup", .3), getNum(m, "active", .1),
                    getNum(m, "recovery", .3), getNum(m, "damage", 1), getNum(m, "reach", 40), getNum(m, "height", 40),
                    getNum(m, "stamina", 10), getNum(m, "lunge", 0), getNum(m, "poise", 10));
        }

        String id() { return id; }
        double windup() { return windup; }
        double active() { return active; }
        double recovery() { return recovery; }
        double damage() { return damage; }
        double reach() { return reach; }
        double height() { return height; }
        double stamina() { return stamina; }
        double lunge() { return lunge; }
        double poise() { return poise; }
    }

    static final class PhaseDef {
        private final double hpBelow, speedMult, damageMult;
        private final String color, banner;
        PhaseDef(double hpBelow, double speedMult, double damageMult, String color, String banner) {
            this.hpBelow = hpBelow; this.speedMult = speedMult; this.damageMult = damageMult; this.color = color; this.banner = banner;
        }
        double hpBelow() { return hpBelow; }
        double speedMult() { return speedMult; }
        double damageMult() { return damageMult; }
        String color() { return color; }
        String banner() { return banner; }
    }

    static final class WeaponDef {
        private final String id, name, color, texture;
        private final double damage, stability, parryWindow;
        private final List<AttackDef> light;
        private final AttackDef heavy;
        WeaponDef(String id, String name, double damage, double stability, double parryWindow,
                  String color, String texture, List<AttackDef> light, AttackDef heavy) {
            this.id = id; this.name = name; this.damage = damage; this.stability = stability; this.parryWindow = parryWindow;
            this.color = color; this.texture = texture; this.light = light; this.heavy = heavy;
        }
        String id() { return id; }
        String name() { return name; }
        double damage() { return damage; }
        double stability() { return stability; }
        double parryWindow() { return parryWindow; }
        String color() { return color; }
        String texture() { return texture; }
        List<AttackDef> light() { return light; }
        AttackDef heavy() { return heavy; }
    }

    static final class EnemyDef {
        private final String id, name, color;
        private final double w, h, hp, speed, souls, aggro, poise, staggerTime, cooldown;
        private final boolean boss;
        private final Map<String, Object> sprite;
        private final List<AttackDef> attacks;
        private final List<PhaseDef> phases;
        EnemyDef(String id, String name, double w, double h, double hp, double speed, double souls, double aggro,
                 double poise, double staggerTime, double cooldown, boolean boss, String color,
                 Map<String, Object> sprite, List<AttackDef> attacks, List<PhaseDef> phases) {
            this.id = id; this.name = name; this.w = w; this.h = h; this.hp = hp; this.speed = speed; this.souls = souls;
            this.aggro = aggro; this.poise = poise; this.staggerTime = staggerTime; this.cooldown = cooldown;
            this.boss = boss; this.color = color; this.sprite = sprite; this.attacks = attacks; this.phases = phases;
        }
        String id() { return id; }
        String name() { return name; }
        double w() { return w; }
        double h() { return h; }
        double hp() { return hp; }
        double speed() { return speed; }
        double souls() { return souls; }
        double aggro() { return aggro; }
        double poise() { return poise; }
        double staggerTime() { return staggerTime; }
        double cooldown() { return cooldown; }
        boolean boss() { return boss; }
        String color() { return color; }
        Map<String, Object> sprite() { return sprite; }
        List<AttackDef> attacks() { return attacks; }
        List<PhaseDef> phases() { return phases; }
    }

    final Map<String, Object> game, player, items, level;
    final Ui ui;
    final Map<String, WeaponDef> weapons = new LinkedHashMap<>();
    final Map<String, EnemyDef> enemies = new LinkedHashMap<>();

    /** Source names are relative to the assets root, e.g. "data/game.json", "maps/level1.tmx", "ui/ui.xml". */
    Data(Source src) throws Exception {
        game = read(src, "data/game.json");
        player = read(src, "data/player.json");
        items = read(src, "data/items.json");
        level = TmxLoader.load(src, "maps/level1.tmx");
        ui = Ui.parse(src.read("ui/ui.xml"));

        for (Map.Entry<String, Object> e : read(src, "data/weapons.json").entrySet()) {
            Map<String, Object> m = obj(e.getValue());
            List<AttackDef> light = new ArrayList<>();
            for (Object o : arr(m.get("light"))) light.add(AttackDef.of(obj(o)));
            weapons.put(e.getKey(), new WeaponDef(e.getKey(), getStr(m, "name", e.getKey()), getNum(m, "damage", 20),
                    getNum(m, "stability", 1), getNum(m, "parryWindow", 0.15), getStr(m, "color", "#cccccc"),
                    getStr(m, "texture", ""), light, AttackDef.of(obj(m.get("heavy")))));
        }
        for (Map.Entry<String, Object> e : read(src, "data/enemies.json").entrySet()) {
            Map<String, Object> m = obj(e.getValue());
            List<AttackDef> atks = new ArrayList<>();
            for (Object o : arr(m.get("attacks"))) atks.add(AttackDef.of(obj(o)));
            List<PhaseDef> phases = new ArrayList<>();
            for (Object o : arr(m.get("phases"))) {
                Map<String, Object> p = obj(o);
                phases.add(new PhaseDef(getNum(p, "hpBelow", .5), getNum(p, "speedMult", 1), getNum(p, "damageMult", 1),
                        getStr(p, "color", ""), getStr(p, "banner", "")));
            }
            Map<String, Object> sz = obj(m.get("size"));
            enemies.put(e.getKey(), new EnemyDef(e.getKey(), getStr(m, "name", e.getKey()), getNum(sz, "w", 30), getNum(sz, "h", 50),
                    getNum(m, "hp", 50), getNum(m, "speed", 80), getNum(m, "souls", 10), getNum(m, "aggro", 300),
                    getNum(m, "poise", 30), getNum(m, "staggerTime", .5), getNum(m, "cooldown", 1),
                    getBool(m, "boss", false), getStr(m, "color", "#aa4444"), obj(m.get("sprite")), atks, phases));
        }
    }

    private static Map<String, Object> read(Source src, String f) throws IOException {
        return obj(Json.parse(src.read(f)));
    }
}
