package com.darkmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;

// Habilidade 1 do zumbi: agarrão (lado do servidor).
// Zumbi a até 1 bloco te agarra por 3 s. Você leva 1 coração por segundo (3 no total).
// Cada 5 apertos de Espaço encurtam o agarrão em 1 s. Durante o agarrão ninguém ataca, só o agarrão causa dano.
public final class GrabHandler {
        private static final int GRAB_TICKS = 60;            // 3 s
        private static final float DAMAGE_PER_SECOND = 2.0F; // 1 coração
        private static final int PRESSES_NEEDED = 5;
        private static final int REDUCE_TICKS = 20;          // 1 s a menos a cada 5 apertos
        private static final int ZOMBIE_COOLDOWN = 100;     // 5 s até o mesmo zumbi agarrar de novo
        private static final float GRAB_CHANCE = 0.45F;    // 45% de chance de agarrar
        private static final float IGNORE_ARMOR_CHANCE = 0.5F; // 50% de chance de ignorar a armadura
        private static final int RETRY_TICKS = 60;           // se falhar, tenta de novo em 3 s
        private static final double RANGE = 1.0;             // blocos

        private static final class Grab {
                final Zombie zombie;
                final long start;
                long end;
                int presses;

                Grab(Zombie zombie, long start, long end) {
                        this.zombie = zombie;
                        this.start = start;
                        this.end = end;
                }
        }

        private static final Map<UUID, Grab> GRABS = new HashMap<>();
        private static final Map<UUID, Long> ZOMBIE_READY = new HashMap<>();

        // Evita que o dano do próprio agarrão seja cancelado pelo evento de dano.
        private static boolean inGrabDamage = false;

        private GrabHandler() {}

        public static boolean isGrabbed(UUID playerId) {
                return GRABS.containsKey(playerId);
        }

        public static void init() {
                PayloadTypeRegistry.serverboundPlay().register(GrabEscapePayload.TYPE, GrabEscapePayload.CODEC);
                ServerPlayNetworking.registerGlobalReceiver(GrabEscapePayload.TYPE,
                                (payload, context) -> escapePress(context.player()));
                PayloadTypeRegistry.clientboundPlay().register(GrabPayload.TYPE, GrabPayload.CODEC);
                ServerTickEvents.END_SERVER_TICK.register(GrabHandler::tick);
                ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
                        if (inGrabDamage) return true;
                        // Zumbi não bate em quem está agarrado.
                        if (entity instanceof ServerPlayer p && GRABS.containsKey(p.getUUID())
                                        && source.getEntity() instanceof Zombie) return false;
                        // Quem está agarrado não ataca.
                        if (source.getEntity() instanceof ServerPlayer a && GRABS.containsKey(a.getUUID())) return false;
                        return true;
                });
        }

        private static void escapePress(ServerPlayer player) {
                Grab g = GRABS.get(player.getUUID());
                if (g == null) return;
                g.presses++;
                if (g.presses >= PRESSES_NEEDED) {
                        g.presses = 0;
                        g.end -= REDUCE_TICKS;
                }
        }

        private static void tick(MinecraftServer server) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        UUID id = player.getUUID();
                        long now = player.level().getGameTime();
                        Grab g = GRABS.get(id);

                        if (g == null) {
                                if (now % 5 == 0 && player.isAlive() && !player.isSpectator() && !player.isCreative()) {
                                        tryGrab(player, now);
                                }
                                continue;
                        }

                        if (!player.isAlive() || !g.zombie.isAlive() || g.zombie.isRemoved()) {
                                release(player, g, now);
                                continue;
                        }

                        g.zombie.getNavigation().stop();
                        g.zombie.getLookControl().setLookAt(player, 30.0F, 30.0F);

                        long elapsed = now - g.start;
                        if (elapsed > 0 && elapsed % 20 == 0) {
                                ServerLevel level = (ServerLevel) player.level();
                                inGrabDamage = true;
                                try {
                                        boolean ignoreArmor = java.util.concurrent.ThreadLocalRandom.current().nextFloat() < IGNORE_ARMOR_CHANCE;
                                        var src = ignoreArmor
                                                        ? level.damageSources().indirectMagic(g.zombie, g.zombie)
                                                        : level.damageSources().mobAttack(g.zombie);
                                        player.hurtServer(level, src, DAMAGE_PER_SECOND * swordBonus(g.zombie));
                                } finally {
                                        inGrabDamage = false;
                                }
                        }

                        if (now >= g.end) {
                                release(player, g, now);
                                ServerPlayNetworking.send(player, new GrabPayload(2)); // lança para cima
                        }
                }
        }

        private static void tryGrab(ServerPlayer player, long now) {
                ServerLevel level = (ServerLevel) player.level();
                for (Zombie z : level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(RANGE),
                                x -> x.isAlive())) {
                        if (now < ZOMBIE_READY.getOrDefault(z.getUUID(), 0L)) continue;
                        if (!z.hasLineOfSight(player)) continue;
                        if (java.util.concurrent.ThreadLocalRandom.current().nextFloat() >= GRAB_CHANCE) {
                                ZOMBIE_READY.put(z.getUUID(), now + RETRY_TICKS);
                                continue;
                        }
                        GRABS.put(player.getUUID(), new Grab(z, now, now + GRAB_TICKS));
                        ServerPlayNetworking.send(player, new GrabPayload(1));
                        player.sendSystemMessage(Component.literal("Agarrado! Aperte Espaço"), true);
                        return;
                }
        }

        // Dano extra conforme a espada na mão do zumbi.
        private static float swordBonus(Zombie z) {
                var hand = z.getMainHandItem();
                if (hand.is(Items.WOODEN_SWORD)) return 1.02F;
                if (hand.is(Items.STONE_SWORD)) return 1.03F;
                if (hand.is(Items.IRON_SWORD)) return 1.04F;
                if (hand.is(Items.DIAMOND_SWORD)) return 1.08F;
                if (hand.is(Items.NETHERITE_SWORD)) return 1.11F;
                return 1.0F;
        }

        private static void release(ServerPlayer player, Grab g, long now) {
                GRABS.remove(player.getUUID());
                ServerPlayNetworking.send(player, new GrabPayload(0));
                ZOMBIE_READY.put(g.zombie.getUUID(), now + ZOMBIE_COOLDOWN);
                player.sendSystemMessage(Component.empty(), true);
        }
}
