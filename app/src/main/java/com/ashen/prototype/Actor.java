package com.ashen.prototype;

import com.ashen.prototype.Data.AttackDef;

/** Shared body, vitals and attack-timing logic for the player and all enemies. */
abstract class Actor {
    enum State { IDLE, WALK, ROLL, ATTACK, BLOCK, STAGGER, DRINK, DEAD }

    double x, y, w, h, vx, vy;
    int facing = 1;
    boolean onGround;
    double hp, maxHp, stamina, maxStamina, staminaDelay;
    State state = State.IDLE;
    double t;          // generic state timer
    double stagT;      // stagger time left
    double invuln;     // >0 = invincible (roll i-frames)
    double flash;      // hit flash timer
    String animName = "";
    double animT;      // time in current animation
    double poiseDmg;   // accumulated poise damage
    boolean parried;   // staggered by a parry => vulnerable to riposte

    AttackDef atk;     // current attack
    double atkT;
    boolean atkHit;

    double cx() { return x + w / 2; }
    Box rect() { return new Box(x, y, w, h); }
    boolean alive() { return state != State.DEAD; }

    /** 0 windup, 1 active, 2 recovery, 3 finished, -1 no attack. */
    int atkPhase() {
        if (atk == null) return -1;
        if (atkT < atk.windup()) return 0;
        if (atkT < atk.windup() + atk.active()) return 1;
        if (atkT < atk.windup() + atk.active() + atk.recovery()) return 2;
        return 3;
    }

    Box hitbox() {
        double bx = facing > 0 ? x + w : x - atk.reach();
        return new Box(bx, y + (h - atk.height()) / 2, atk.reach(), atk.height());
    }

    void startAttack(AttackDef a) { atk = a; atkT = 0; atkHit = false; state = State.ATTACK; }
    void stagger(double d) { state = State.STAGGER; stagT = d; atk = null; }
}
