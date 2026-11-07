package com.ashen.prototype;

import com.ashen.prototype.Data.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.ashen.prototype.Json.*;

/** Level state, physics, combat resolution, bonfires, death/souls loop, boss gate. */
final class World {
    static final class Bonfire { String id, name; double x; }
    static final class Stain { double x, y; int souls; }

    final Data d;
    final Player player;
    final List<Enemy> enemies = new ArrayList<>();
    final List<Bonfire> bonfires = new ArrayList<>();
    final List<double[]> platforms = new ArrayList<>(); // x, y, w
    final Set<Enemy> defeatedBosses = new HashSet<>();
    final double levelW, groundY, screenW, screenH, gateX;
    final String gateBoss;
    boolean gateLocked, menu;
    int menuSel;
    Stain stain;
    String banner = ""; String bannerColor = "#ffffff"; double bannerT, deathT, camX;
    final List<String> statKeys;

    World(Data d) {
        this.d = d;
        Map<String, Object> lv = d.level;
        levelW = getNum(lv, "width", 3000); groundY = getNum(lv, "groundY", 450);
        screenW = getNum(obj(d.game.get("screen")), "w", 960); screenH = getNum(obj(d.game.get("screen")), "h", 540);
        for (Object o : arr(lv.get("platforms"))) {
            Map<String, Object> m = obj(o);
            platforms.add(new double[]{getNum(m, "x", 0), getNum(m, "y", 0), getNum(m, "w", 100)});
        }
        for (Object o : arr(lv.get("bonfires"))) {
            Map<String, Object> m = obj(o);
            Bonfire b = new Bonfire(); b.id = getStr(m, "id", "b"); b.name = getStr(m, "name", "Bonfire"); b.x = getNum(m, "x", 0);
            bonfires.add(b);
        }
        for (Object o : arr(lv.get("spawns"))) {
            Map<String, Object> m = obj(o);
            EnemyDef def = d.enemies.get(getStr(m, "enemy", ""));
            if (def != null) enemies.add(new Enemy(def, getNum(m, "x", 0), groundY));
        }
        gateX = getNum(obj(lv.get("gate")), "x", 1e9); gateBoss = getStr(obj(lv.get("gate")), "boss", "");
        statKeys = new ArrayList<>(obj(d.player.get("stats")).keySet());
        player = new Player(d);
        player.respawn = bonfires.isEmpty() ? "" : bonfires.get(0).id;
        player.revive(getNum(lv, "playerStart", 100));
    }

    double num(String section, String key, double def) { return getNum(obj(d.game.get(section)), key, def); }
    void say(String s, double secs, String color) { banner = s; bannerT = secs; bannerColor = color; }

    Bonfire nearBonfire() {
        for (Bonfire b : bonfires) if (Math.abs(player.cx() - b.x) < 50) return b;
        return null;
    }

    Bonfire bonfireById(String id) {
        for (Bonfire b : bonfires) if (b.id.equals(id)) return b;
        return bonfires.isEmpty() ? null : bonfires.get(0);
    }

    // ------------------------------------------------------------------ update
    void update(Input in, double dt) {
        bannerT -= dt;
        if (menu) { menuTick(in); return; }
        if (player.alive()) {
            player.update(this, in, dt);
            if (in.pressed("interact") && (player.state == Actor.State.IDLE || player.state == Actor.State.WALK)) {
                Bonfire b = nearBonfire();
                if (b != null) rest(b);
            }
        } else {
            deathT += dt;
            if (deathT > getNum(d.game, "respawnDelay", 3.5)) respawn();
        }
        for (Enemy e : enemies) e.update(this, dt);
        physics(player, dt);
        for (Enemy e : enemies) if (e.alive() && e.active) physics(e, dt);

        // boss gate
        Enemy boss = bossEnemy();
        if (boss != null && !gateLocked && boss.alive() && player.alive() && player.x > gateX + 30) {
            gateLocked = true; boss.active = true; say(boss.def.name().toUpperCase(), 2.5, "#e0e0e0");
        }
        // bloodstain pickup
        if (stain != null && player.alive() && player.rect().intersects(stain.x - 12, stain.y - 12, 24, 24)) {
            player.souls += stain.souls; stain = null; say("SOULS RETRIEVED", 1.5, "#80e0ff");
        }
        camX = Math.max(0, Math.min(levelW - screenW, player.cx() - screenW / 2));
    }

    Enemy bossEnemy() {
        for (Enemy e : enemies) if (e.def.id().equals(gateBoss)) return e;
        return null;
    }

    void physics(Actor a, double dt) {
        a.vy = Math.min(num("physics", "maxFall", 900), a.vy + num("physics", "gravity", 1500) * dt);
        a.x += a.vx * dt;
        double minX = 0;
        if (gateLocked && (a instanceof Player || (a instanceof Enemy && ((Enemy) a).def.boss()))) minX = gateX;
        a.x = Math.max(minX, Math.min(levelW - a.w, a.x));
        double prevB = a.y + a.h;
        a.y += a.vy * dt;
        double b = a.y + a.h, land = Double.MAX_VALUE;
        a.onGround = false;
        if (a.vy >= 0) {
            if (b >= groundY) land = groundY;
            for (double[] p : platforms)
                if (a.x + a.w > p[0] && a.x < p[0] + p[2] && prevB <= p[1] + 1 && b >= p[1]) land = Math.min(land, p[1]);
            if (land != Double.MAX_VALUE) { a.y = land - a.h; a.vy = 0; a.onGround = true; }
        }
    }

    // ------------------------------------------------------------------ combat
    void resolveAttack(Actor att, double dmg) {
        Box hb = att.hitbox();
        List<Actor> targets = new ArrayList<>();
        if (att instanceof Player) { for (Enemy e : enemies) if (e.active) targets.add(e); }
        else targets.add(player);
        for (Actor t : targets) {
            if (!t.alive() || t.invuln > 0 || !hb.intersects(t.rect())) continue;
            att.atkHit = true;
            applyHit(att, t, dmg);
        }
    }

    private void applyHit(Actor att, Actor t, double dmg) {
        double push = Math.signum(t.cx() - att.cx()) * num("combat", "knockback", 140);
        if (t instanceof Player) {
            Player p = (Player) t;
            boolean front = (att.cx() - p.cx()) * p.facing > 0;
            if (p.state == Actor.State.BLOCK && front) {
                if (p.blockT <= p.weapon().parryWindow()) {
                    att.stagger(num("combat", "parryStagger", 1.2)); att.parried = true; att.vx = 0;
                    say("PARRY!", 0.8, "#ffe080"); return;
                }
                double cost = dmg * num("combat", "blockStaminaPerDamage", 1.2) * p.weapon().stability();
                if (p.stamina >= cost) {
                    p.stamina -= cost; p.staminaDelay = p.regenDelay; p.vx = push * 0.6; p.flash = 0.1;
                    p.hp -= dmg * num("combat", "blockChipRatio", 0);
                    if (p.hp <= 0) killPlayer();
                    return;
                }
                p.stamina = 0; p.staminaDelay = p.regenDelay;
                p.hp -= dmg; p.flash = 0.2; p.stagger(num("combat", "guardBreakStagger", .9)); p.vx = push;
                say("GUARD BROKEN", 0.8, "#ff8060");
            } else {
                p.hp -= dmg; p.flash = 0.2; p.stagger(num("combat", "playerHitStun", .3)); p.vx = push; p.queued = 0;
            }
            if (p.hp <= 0) killPlayer();
        } else if (t instanceof Enemy) {
            Enemy e = (Enemy) t;
            double mult = 1;
            boolean attackerInFront = (att.cx() - e.cx()) * e.facing > 0;
            if (e.parried && e.state == Actor.State.STAGGER && attackerInFront) {
                mult = num("combat", "riposteMult", 3); say("RIPOSTE!", 0.8, "#ffe080");
            } else if (!attackerInFront && e.state != Actor.State.STAGGER) {
                mult = num("combat", "backstabMult", 2.5); say("BACKSTAB!", 0.8, "#ffe080");
            }
            e.hp -= dmg * mult; e.flash = 0.12; e.poiseDmg += att.atk.poise();
            if (e.hp <= 0) { killEnemy(e); return; }
            if (mult > 1) { e.parried = false; e.atk = null; e.stagger(0.6); e.vx = push * 0.5; }
            else if (e.poiseDmg >= e.def.poise()) { e.poiseDmg = 0; e.stagger(e.def.staggerTime()); e.vx = push; }
        }
    }

    private void killEnemy(Enemy e) {
        e.state = Actor.State.DEAD; e.atk = null; e.vx = 0;
        player.souls += (int) e.def.souls();
        if (e.def.boss()) { defeatedBosses.add(e); gateLocked = false; say("GREAT ENEMY FELLED", 4, "#e8d070"); }
    }

    private void killPlayer() {
        player.state = Actor.State.DEAD; player.atk = null; player.hp = 0; player.vx = 0; deathT = 0;
        if (player.souls > 0 || stain != null) {
            stain = new Stain(); stain.x = player.cx(); stain.y = player.y + player.h; stain.souls = player.souls;
        }
        player.souls = 0;
        say("YOU DIED", 3.2, "#c02020");
    }

    // ------------------------------------------------------------------ bonfires / respawn / level up
    void resetEnemies() {
        for (Enemy e : enemies) if (!defeatedBosses.contains(e)) e.reset();
        gateLocked = false;
    }

    void rest(Bonfire b) {
        player.fullRestore(); player.respawn = b.id; resetEnemies();
        menu = true; menuSel = 0; say("BONFIRE LIT", 1.5, "#ffb040");
    }

    void respawn() {
        Bonfire b = bonfireById(player.respawn);
        player.revive(b == null ? 100 : b.x - player.w / 2);
        resetEnemies(); banner = ""; bannerT = 0;
    }

    private void menuTick(Input in) {
        int n = statKeys.size() + 1;
        if (in.pressed("menuUp")) menuSel = (menuSel + n - 1) % n;
        if (in.pressed("menuDown")) menuSel = (menuSel + 1) % n;
        if (in.pressed("back")) menu = false;
        if (in.pressed("interact") || in.pressed("light")) {
            if (menuSel == statKeys.size()) { menu = false; return; }
            int cost = player.levelCost();
            if (player.souls >= cost) {
                player.souls -= cost;
                String k = statKeys.get(menuSel);
                player.pts.put(k, player.pts.get(k) + 1);
                player.recalc(); player.fullRestore();
            }
        }
    }
}
