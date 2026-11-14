package com.darkmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

// Barra de energia (20 segmentos) no lugar da fome. Com 0, nenhum poder funciona.
// Enche do zero em 45 s. Ao acertar alguém: regeneração 2x mais rápida por 2 s.
public final class EnergyHandler {
        public static final int MAX = 20;
        public static final int REGEN_TICKS = 900;    // 45 s
        public static final int HIT_BUFF_TICKS = 40;  // 2 s
        public static final int HIT_REGEN_MULT = 2;

        private static final Map<UUID, Integer> ENERGY = new HashMap<>();
        private static final Map<UUID, Float> PROGRESS = new HashMap<>();
        private static final Map<UUID, Long> BUFF_UNTIL = new HashMap<>();
        private static final Map<UUID, Integer> SENT = new HashMap<>();

        private EnergyHandler() {}

        public static int get(ServerPlayer player) {
                return ENERGY.getOrDefault(player.getUUID(), MAX);
        }

        // Gasta se houver energia suficiente. Com a barra em 0 retorna false.
        public static boolean spend(ServerPlayer player, int cost) {
                int energy = get(player);
                if (energy < cost) return false;
                ENERGY.put(player.getUUID(), energy - cost);
                return true;
        }
        public static void add(ServerPlayer player, int amount) {
                ENERGY.put(player.getUUID(), Math.min(MAX, get(player) + amount));
        }


        public static void init() {
                ServerTickEvents.END_SERVER_TICK.register(EnergyHandler::tick);
                ServerLivingEntityEvents.AFTER_DAMAGE.register(EnergyHandler::onDamage);
                EnergyFood.init();
        }

        private static void tick(MinecraftServer server) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        UUID id = player.getUUID();
                        long now = player.level().getGameTime();
                        boolean boosted = BUFF_UNTIL.getOrDefault(id, 0L) > now;

                        float progress = PROGRESS.getOrDefault(id, 0F)
                                        + (float) MAX / REGEN_TICKS * (boosted ? HIT_REGEN_MULT : 1);
                        int energy = get(player);
                        while (progress >= 1F && energy < MAX) {
                                energy++;
                                progress -= 1F;
                        }
                        if (energy >= MAX) progress = 0F;

                        PROGRESS.put(id, progress);
                        ENERGY.put(id, energy);
                        if (SENT.getOrDefault(id, -1) != energy) {
                                SENT.put(id, energy);
                                ServerPlayNetworking.send(player, new EnergyPayload(energy));
                        }
                }
        }

        private static void onDamage(LivingEntity entity, DamageSource source, float baseDamage,
                        float damageTaken, boolean blocked) {
                if (blocked || damageTaken <= 0) return;
                if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == entity) return;

                long now = attacker.level().getGameTime();
                BUFF_UNTIL.put(attacker.getUUID(), now + HIT_BUFF_TICKS);
        }
}
