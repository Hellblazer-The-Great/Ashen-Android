package com.ashen.prototype;

import com.ashen.prototype.Data.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Data-driven enemy: stats, attacks and boss phases all come from enemies.json. */
final class Enemy extends Actor {
    final EnemyDef def;
    final double homeX, groundY;
    final Random rng = new Random();
    double cooldown, speedMult = 1, dmgMult = 1;
    int phaseIdx = -1;
    boolean aggro, active;
    String color;

    Enemy(EnemyDef def, double x, double groundY) {
        this.def = def; this.homeX = x; this.groundY = groundY;
        w = def.w(); h = def.h();
        reset();
    }

    void reset() {
        x = homeX; y = groundY - h - 1; vx = vy = 0; hp = maxHp = def.hp();
        state = State.IDLE; atk = null; aggro = false; cooldown = 1; speedMult = dmgMult = 1; phaseIdx = -1;
        poiseDmg = 0; parried = false; color = def.color(); active = !def.boss(); invuln = 0; flash = 0;
    }

    void update(World wd, double dt) {
        if (!alive() || !active) return;
        flash = Math.max(0, flash - dt);
        poiseDmg = Math.max(0, poiseDmg - wd.num("combat", "poiseDecay", 14) * dt);
        for (int i = phaseIdx + 1; i < def.phases().size(); i++) {
            PhaseDef ph = def.phases().get(i);
            if (hp / maxHp <= ph.hpBelow()) {
                phaseIdx = i; speedMult *= ph.speedMult(); dmgMult *= ph.damageMult();
                if (!ph.color().isEmpty()) color = ph.color();
                if (!ph.banner().isEmpty()) wd.say(ph.banner(), 2.5, "#e0a040");
            }
        }
        Player p = wd.player;
        double dist = p.cx() - cx(), ad = Math.abs(dist), edge = ad - w / 2 - p.w / 2;

        if (state == State.STAGGER) {
            stagT -= dt; vx *= 0.85;
            if (stagT <= 0) { state = State.IDLE; parried = false; }
            return;
        }

        if (state == State.ATTACK) {
            int before = atkPhase();
            atkT += dt;
            int after = atkPhase();
            if (before == 0 && after >= 1) vx = facing * atk.lunge(); else if (after >= 1) vx *= 0.88; else vx = 0;
            if (after == 0 && atkT < atk.windup() * 0.6) facing = dist >= 0 ? 1 : -1; // tracks target early in windup, then commits
            if (after == 1 && !atkHit) wd.resolveAttack(this, atk.damage() * dmgMult);
            if (after == 3) { atk = null; state = State.IDLE; cooldown = def.cooldown(); vx = 0; }
            return;
        }

        cooldown -= dt;
        if (p.alive() && ad < def.aggro()) aggro = true;
        if (!p.alive() || ad > def.aggro() * 1.6) aggro = false;
        if (!aggro) {
            vx = Math.abs(x - homeX) > 8 ? Math.signum(homeX - x) * def.speed() * 0.5 : 0;
            if (vx != 0) facing = (int) Math.signum(vx);
            return;
        }

        facing = dist >= 0 ? 1 : -1;
        List<AttackDef> usable = new ArrayList<>();
        double minReach = def.attacks().isEmpty() ? 40 : Double.MAX_VALUE;
        for (AttackDef a : def.attacks()) {
            if (edge <= a.reach()) usable.add(a);
            minReach = Math.min(minReach, a.reach());
        }
        if (cooldown <= 0 && !usable.isEmpty()) { vx = 0; startAttack(usable.get(rng.nextInt(usable.size()))); }
        else vx = edge > minReach * 0.7 ? facing * def.speed() * speedMult : 0;
    }
}
