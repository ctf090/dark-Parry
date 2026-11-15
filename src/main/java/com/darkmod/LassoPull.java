package com.darkmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Puxão e paralisia da flecha com laço (lado do servidor).
// O puxão dura no máximo 2 s. Ao chegar perto do dono, o alvo fica paralisado por 1,5 s.
public final class LassoPull {
    private static final double ARRIVE_DISTANCE = 2.0;
    private static final double PULL_STRENGTH = 2.2;
    private static final int MAX_PULL_TICKS = 40;
    private static final int PARALYZE_TICKS = 30;

    private static final class Pull {
        final LivingEntity target;
        final Entity owner;
        int ticks;

        Pull(LivingEntity target, Entity owner) {
            this.target = target;
            this.owner = owner;
        }
    }

    private static final class Paralysis {
        final LivingEntity target;
        int ticks;

        Paralysis(LivingEntity target) {
            this.target = target;
        }
    }

    private static final List<Pull> PULLS = new ArrayList<>();
    private static final List<Paralysis> PARALYZED = new ArrayList<>();

    // Alvo que foi jogado pela bola: fica paralisado quando cair no chão.
    private static final double KNOCK_HORIZONTAL = 0.4;
    private static final double KNOCK_VERTICAL = 0.8;
    private static final int LAND_TIMEOUT = 100;

    private static final class Landing {
        final LivingEntity target;
        int ticks;

        Landing(LivingEntity target) {
            this.target = target;
        }
    }

    private static final List<Landing> LANDING = new ArrayList<>();

    private static final double KNOCK_SPEED = 0.9;
    private static final int KNOCK_TICKS = 8;

    private static final class Knock {
        final LivingEntity target;
        final double dx, dz;
        int ticks;

        Knock(LivingEntity target, double dx, double dz) {
            this.target = target;
            this.dx = dx;
            this.dz = dz;
        }
    }

    private static final List<Knock> KNOCKS = new ArrayList<>();

    public static void knockback(LivingEntity target, Entity from) {
        Vec3 dir = target.position().subtract(from.position());
        if (dir.x * dir.x + dir.z * dir.z < 1.0E-6) dir = from.getLookAngle();
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1.0E-6) return;
        double nx = dir.x / len;
        double nz = dir.z / len;
        KNOCKS.add(new Knock(target, nx, nz));
        target.setDeltaMovement(nx * KNOCK_SPEED, KNOCK_VERTICAL, nz * KNOCK_SPEED);
        target.syncVelocity = true;
        LANDING.add(new Landing(target));
        System.out.println("[LASSO] empurrado: " + target.getType());
    }

    private LassoPull() {}

    public static void start(LivingEntity target, Entity owner) {
        PULLS.add(new Pull(target, owner));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LassoPull::tick);
    }

    public static void paralyze(LivingEntity target) {
        PARALYZED.add(new Paralysis(target));
        if (target instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new GrabPayload(1));
    }

    private static void tick(MinecraftServer server) {
        Iterator<Pull> pulls = PULLS.iterator();
        while (pulls.hasNext()) {
            Pull p = pulls.next();
            p.ticks++;
            if (!p.target.isAlive() || !p.owner.isAlive() || p.ticks > MAX_PULL_TICKS) {
                pulls.remove();
                continue;
            }
            if (p.target.position().distanceTo(p.owner.position()) <= ARRIVE_DISTANCE) {
                pulls.remove();
                PARALYZED.add(new Paralysis(p.target));
                if (p.target instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new GrabPayload(1));
                continue;
            }
            Vec3 dir = p.owner.position().subtract(p.target.position()).normalize().scale(PULL_STRENGTH);
            p.target.setDeltaMovement(dir.x, p.target.getDeltaMovement().y, dir.z);
            p.target.syncVelocity = true;
        }

        Iterator<Paralysis> paralyzed = PARALYZED.iterator();
        while (paralyzed.hasNext()) {
            Paralysis s = paralyzed.next();
            s.ticks++;
            if (!s.target.isAlive() || s.ticks > PARALYZE_TICKS) {
                paralyzed.remove();
                if (s.target instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new GrabPayload(0));
                continue;
            }
            s.target.setDeltaMovement(0.0, 0.0, 0.0);
            s.target.syncVelocity = true;
        }

        Iterator<Knock> knocks = KNOCKS.iterator();
        while (knocks.hasNext()) {
            Knock k = knocks.next();
            k.ticks++;
            if (!k.target.isAlive() || k.ticks > KNOCK_TICKS) {
                knocks.remove();
                continue;
            }
            k.target.setDeltaMovement(k.dx * KNOCK_SPEED, k.target.getDeltaMovement().y, k.dz * KNOCK_SPEED);
            k.target.syncVelocity = true;
        }

        Iterator<Landing> landing = LANDING.iterator();
        while (landing.hasNext()) {
            Landing l = landing.next();
            l.ticks++;
            if (!l.target.isAlive() || l.ticks > LAND_TIMEOUT) {
                landing.remove();
                System.out.println("[LASSO] tempo esgotou sem cair: " + l.target.getType());
                continue;
            }
            if (l.ticks > 3 && (l.target.onGround() || l.target.isInWater())) {
                landing.remove();
                System.out.println("[LASSO] caiu, paralisando: " + l.target.getType());
                paralyze(l.target);
            }
        }
    }
}
