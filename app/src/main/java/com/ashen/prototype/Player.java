package com.ashen.prototype;

import com.ashen.prototype.Data.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.ashen.prototype.Json.*;

final class Player extends Actor {
    final Data d;
    final List<WeaponDef> weapons = new ArrayList<>();
    int wi;
    final Map<String, Integer> pts = new LinkedHashMap<>();
    int souls, flasks, maxFlasks, combo, queued; // queued: 0 none, 1 light, 2 heavy
    double blockT, rollDir, flaskHeal, flaskDur;
    String respawn;
    final double baseHp, baseSta, regen, regenDelay, speed, jumpV, rollDur, rollSpd, i0, i1, rollCost, blockSlow, blockRegen;
    final int startLevel;

    Player(Data d) {
        this.d = d;
        Map<String, Object> sz = obj(d.player.get("size")), base = obj(d.player.get("base")), roll = obj(d.player.get("roll")), blk = obj(d.player.get("block"));
        w = getNum(sz, "w", 26); h = getNum(sz, "h", 50);
        baseHp = getNum(base, "hp", 100); baseSta = getNum(base, "stamina", 100);
        regen = getNum(base, "staminaRegen", 40); regenDelay = getNum(base, "staminaRegenDelay", .8);
        speed = getNum(base, "speed", 170); jumpV = getNum(base, "jump", 500); startLevel = (int) getNum(base, "startLevel", 1);
        rollDur = getNum(roll, "duration", .45); rollSpd = getNum(roll, "speed", 330);
        i0 = getNum(roll, "iFrameStart", .04); i1 = getNum(roll, "iFrameEnd", .3); rollCost = getNum(roll, "staminaCost", 20);
        blockSlow = getNum(blk, "speedMult", .5); blockRegen = getNum(blk, "regenMult", .5);
        for (Map.Entry<String, Object> e : obj(d.player.get("stats")).entrySet()) pts.put(e.getKey(), (int) getNum(obj(e.getValue()), "start", 10));
        for (Object o : arr(d.player.get("weapons"))) if (d.weapons.containsKey(o)) weapons.add(d.weapons.get(o));
        String sw = getStr(d.player, "startWeapon", "");
        for (int i = 0; i < weapons.size(); i++) if (weapons.get(i).id().equals(sw)) wi = i;
        Map<String, Object> fl = obj(d.items.get("flask"));
        maxFlasks = (int) getNum(fl, "charges", 3); flaskHeal = getNum(fl, "heal", 50); flaskDur = getNum(fl, "duration", .9);
        recalc(); fullRestore();
    }

    WeaponDef weapon() { return weapons.get(wi); }

    int level() {
        int l = startLevel;
        for (Map.Entry<String, Integer> e : pts.entrySet())
            l += e.getValue() - (int) getNum(obj(obj(d.player.get("stats")).get(e.getKey())), "start", 10);
        return l;
    }

    int levelCost() {
        Map<String, Object> lu = obj(d.game.get("levelUp"));
        return (int) (getNum(lu, "costBase", 100) + getNum(lu, "costGrowth", 40) * (level() - 1));
    }

    double stat(String field) {
        double v = 0;
        for (Map.Entry<String, Object> e : obj(d.player.get("stats")).entrySet()) {
            Map<String, Object> s = obj(e.getValue());
            v += (pts.get(e.getKey()) - getNum(s, "start", 10)) * getNum(s, field, 0);
        }
        return v;
    }

    void recalc() { maxHp = baseHp + stat("hp"); maxStamina = baseSta + stat("stamina"); }

    void fullRestore() { hp = maxHp; stamina = maxStamina; flasks = maxFlasks; }

    void revive(double px) {
        x = px; y = 0; vx = vy = 0; state = State.IDLE; atk = null; invuln = 0; stagT = 0; queued = 0; fullRestore();
    }

    void spend(double c) { stamina = Math.max(0, stamina - c); staminaDelay = regenDelay; }

    double damage() { return weapon().damage() * atk.damage() * (1 + stat("damage")); }

    void update(World w, Input in, double dt) {
        if (!alive()) return;
        invuln = Math.max(0, invuln - dt);
        flash = Math.max(0, flash - dt);
        staminaDelay -= dt;
        if (staminaDelay <= 0 && state != State.ROLL)
            stamina = Math.min(maxStamina, stamina + regen * (state == State.BLOCK ? blockRegen : 1) * dt);
        int dir = (in.held("right") ? 1 : 0) - (in.held("left") ? 1 : 0);
        switch (state) {
            case STAGGER:
                stagT -= dt; vx *= 0.9;
                if (stagT <= 0) state = State.IDLE;
                break;
            case ROLL:
                t += dt; vx = rollDir * rollSpd;
                invuln = (t >= i0 && t <= i1) ? 0.05 : 0;
                if (t >= rollDur) { state = State.IDLE; vx = 0; invuln = 0; }
                break;
            case DRINK:
                t += dt; vx = 0;
                if (t >= flaskDur) { hp = Math.min(maxHp, hp + flaskHeal); state = State.IDLE; }
                break;
            case ATTACK:
                attackTick(w, in, dt);
                break;
            default:
                free(in, dir, dt);
        }
    }

    private void attackTick(World w, Input in, double dt) {
        int before = atkPhase();
        atkT += dt;
        int after = atkPhase();
        if (before == 0 && after >= 1) vx = facing * atk.lunge(); else if (after >= 1) vx *= 0.85; else vx = 0;
        if (in.pressed("light")) queued = 1;
        if (in.pressed("heavy")) queued = 2;
        if (after == 1 && !atkHit) w.resolveAttack(this, damage());
        if (after == 3) {
            boolean wasLight = weapon().light().contains(atk);
            atk = null; vx = 0; state = State.IDLE;
            if (queued != 0 && stamina > 0) {
                int q = queued; queued = 0;
                if (q == 1) { combo = wasLight ? (combo + 1) % weapon().light().size() : 0; begin(weapon().light().get(combo)); }
                else begin(weapon().heavy());
            } else { queued = 0; combo = 0; }
        }
    }

    private void begin(AttackDef a) { spend(a.stamina()); startAttack(a); }

    private void free(Input in, int dir, double dt) {
        boolean blocking = in.held("block") && onGround;
        blockT = blocking ? blockT + dt : 0;
        vx = dir * speed * (blocking ? blockSlow : 1);
        if (dir != 0) facing = dir;
        state = blocking ? State.BLOCK : dir != 0 ? State.WALK : State.IDLE;
        if (in.pressed("switchWeapon")) wi = (wi + 1) % weapons.size();
        if (in.pressed("jump") && onGround && !blocking) { vy = -jumpV; onGround = false; }
        if (in.pressed("roll") && onGround && stamina > 0) {
            spend(rollCost); rollDir = dir != 0 ? dir : facing; facing = (int) rollDir; state = State.ROLL; t = 0; blockT = 0; return;
        }
        if (in.pressed("light") && stamina > 0) { combo = 0; begin(weapon().light().get(0)); return; }
        if (in.pressed("heavy") && stamina > 0) { begin(weapon().heavy()); return; }
        if (in.pressed("flask") && flasks > 0 && onGround && hp < maxHp) { flasks--; state = State.DRINK; t = 0; }
    }
}
