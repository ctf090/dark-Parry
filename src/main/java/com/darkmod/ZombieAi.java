package com.darkmod;

import java.util.EnumSet;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.player.Player;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// IA extra para zumbis: levantar escudo quando o alvo está perto e atirar com arco ou besta à distância.
public final class ZombieAi {
        private static final double SHIELD_RANGE_SQ = 36.0;  // 6 blocos
        private static final double SHOOT_MIN_SQ = 16.0;     // 4 blocos
        private static final double SHOOT_MAX_SQ = 256.0;    // 16 blocos
        private static final int SHOOT_COOLDOWN = 40;        // 2 s

        private ZombieAi() {}

        // Chance de o escudo bloquear um golpe seu (0.4 = 2 a cada 5, em média).
        private static final float SHIELD_BLOCK_CHANCE = 0.4F;

        public static void init() {
                ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
                        if (entity instanceof Zombie z && z.isBlocking() && source.getEntity() instanceof Player
                                        && java.util.concurrent.ThreadLocalRandom.current().nextFloat() >= SHIELD_BLOCK_CHANCE) {
                                z.stopUsingItem(); // abaixa o escudo e o golpe passa
                        }
                        return true;
                });
        }

        public static void attach(Zombie mob) {
                mob.getGoalSelector().addGoal(1, new ShieldGoal(mob));
                mob.getGoalSelector().addGoal(1, new ShootGoal(mob));
                mob.getGoalSelector().addGoal(0, new FleeGoal(mob));
        }

        static final class ShieldGoal extends Goal {
                private final Zombie mob;

                ShieldGoal(Zombie mob) {
                        this.mob = mob;
                }

                @Override
                public boolean canUse() {
                        LivingEntity t = mob.getTarget();
                        return mob.getOffhandItem().is(Items.SHIELD)
                                        && t != null && t.isAlive()
                                        && mob.distanceToSqr(t) <= SHIELD_RANGE_SQ;
                }

                @Override
                public boolean canContinueToUse() {
                        return canUse();
                }

                @Override
                public void start() {
                        mob.startUsingItem(InteractionHand.OFF_HAND);
                }

                @Override
                public void tick() {
                        if (!mob.isUsingItem()) mob.startUsingItem(InteractionHand.OFF_HAND);
                }

                @Override
                public void stop() {
                        if (mob.isUsingItem()) mob.stopUsingItem();
                }
        }

        static final class ShootGoal extends Goal {
                private final Zombie mob;
                private int cooldown;

                ShootGoal(Zombie mob) {
                        this.mob = mob;
                        setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                private boolean hasRanged() {
                        return mob.getMainHandItem().is(Items.BOW) || mob.getMainHandItem().is(Items.CROSSBOW);
                }

                private boolean inRange(LivingEntity t) {
                        double d2 = mob.distanceToSqr(t);
                        return d2 >= SHOOT_MIN_SQ && d2 <= SHOOT_MAX_SQ && mob.hasLineOfSight(t);
                }

                @Override
                public boolean canUse() {
                        LivingEntity t = mob.getTarget();
                        return hasRanged() && t != null && t.isAlive() && inRange(t);
                }

                @Override
                public boolean canContinueToUse() {
                        return canUse();
                }

                @Override
                public void start() {
                        cooldown = 0;
                        mob.getNavigation().stop();
                }

                @Override
                public boolean requiresUpdateEveryTick() {
                        return true;
                }

                @Override
                public void tick() {
                        mob.getNavigation().stop(); // para de andar enquanto atira
                        if (cooldown > 0) {
                                cooldown--;
                                return;
                        }
                        LivingEntity t = mob.getTarget();
                        if (t == null) return;
                        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
                        shoot(t);
                        cooldown = SHOOT_COOLDOWN;
                }

                private Holder<Potion> pickEffect() {
                        java.util.List<Holder<Potion>> pool = java.util.List.of(
                                        Potions.POISON, Potions.STRONG_POISON, Potions.HARMING,
                                        Potions.WEAKNESS, Potions.SLOWNESS, Potions.STRONG_SLOWNESS);
                        return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
                }

                private void shoot(LivingEntity t) {
                        ServerLevel level = (ServerLevel) mob.level();
                        ItemStack ammo = new ItemStack(Items.TIPPED_ARROW);
                        ammo.set(DataComponents.POTION_CONTENTS, new PotionContents(pickEffect()));
                        Arrow arrow = new Arrow(level, mob, ammo, mob.getMainHandItem());
                        arrow.setBaseDamage(3.5);
                        if (java.util.concurrent.ThreadLocalRandom.current().nextFloat() < 0.4F) {
                                arrow.igniteForSeconds(5.0F);
                        }
                        double dx = t.getX() - mob.getX();
                        double dz = t.getZ() - mob.getZ();
                        double dy = t.getEyeY() - arrow.getY();
                        double h = Math.sqrt(dx * dx + dz * dz);
                        double tt = h / 2.5;
                        double drop = 0.025 * tt * tt;
                        arrow.shoot(dx, dy + drop, dz, 2.5F, 0.0F);
                        level.addFreshEntity(arrow);
                }
        }

        static final class FleeGoal extends Goal {
                private static final double CLOSE_SQ = 16.0;  // 4 blocos
                private static final int FLEE_TICKS = 60;     // 3 s depois de bater
                private static final double FLEE_DIST = 8.0;
                private static final double FLEE_SPEED = 1.3;

                private final Zombie mob;
                private long fleeUntil;
                private LivingEntity hitBy;
                private double fx, fz;

                FleeGoal(Zombie mob) {
                        this.mob = mob;
                        setFlags(EnumSet.of(Goal.Flag.MOVE));
                }

                private boolean hasRanged() {
                        return mob.getMainHandItem().is(Items.BOW) || mob.getMainHandItem().is(Items.CROSSBOW);
                }

                @Override
                public boolean canUse() {
                        if (!hasRanged()) return false;
                        long now = mob.level().getGameTime();
                        if (mob.hurtTime > 0) {
                                hitBy = mob.getLastHurtByMob();
                                fleeUntil = now + FLEE_TICKS;
                        }
                        LivingEntity t = mob.getTarget();
                        boolean close = t != null && t.isAlive() && mob.distanceToSqr(t) < CLOSE_SQ;
                        boolean fleeing = now < fleeUntil;
                        if (!close && !fleeing) return false;
                        LivingEntity from = (fleeing && hitBy != null && hitBy.isAlive()) ? hitBy : t;
                        if (from == null) return false;
                        double dx = mob.getX() - from.getX();
                        double dz = mob.getZ() - from.getZ();
                        double len = Math.sqrt(dx * dx + dz * dz);
                        if (len < 1.0E-6) { dx = 1.0; dz = 0.0; len = 1.0; }
                        fx = mob.getX() + dx / len * FLEE_DIST;
                        fz = mob.getZ() + dz / len * FLEE_DIST;
                        return true;
                }

                @Override
                public boolean canContinueToUse() {
                        return canUse();
                }

                @Override
                public void start() {
                        mob.getNavigation().moveTo(fx, mob.getY(), fz, FLEE_SPEED);
                }

                @Override
                public void tick() {
                        if (mob.tickCount % 10 == 0) {
                                mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 1));
                        }
                        if (mob.getNavigation().isDone()) {
                                mob.getNavigation().moveTo(fx, mob.getY(), fz, FLEE_SPEED);
                        }
                }

                @Override
                public void stop() {
                        mob.getNavigation().stop();
                }
        }
}
