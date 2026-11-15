package com.darkmod;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Zumbi normal (sem arco nem besta) atira a flecha com laço a até 7 blocos, com espera de 8 s.
final class ZombieLassoGoal extends Goal {
    private static final double RANGE_SQ = 2304.0; // 48 blocos
    private static final int COOLDOWN = 222;       // ~11 s (28% menos vezes)

    private final Zombie mob;
    private int nextShot;

    ZombieLassoGoal(Zombie mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    private boolean isMelee() {
        return !(mob.getMainHandItem().is(Items.BOW) || mob.getMainHandItem().is(Items.CROSSBOW));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return isMelee() && mob.tickCount >= nextShot && t != null && t.isAlive()
                && mob.distanceToSqr(t) <= RANGE_SQ && mob.hasLineOfSight(t);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        LivingEntity t = mob.getTarget();
        nextShot = mob.tickCount + COOLDOWN;
        if (t == null) return;
        mob.getNavigation().stop();
        ServerLevel level = (ServerLevel) mob.level();
        LassoProjectile lasso = new LassoProjectile(level, mob, new ItemStack(LassoArrow.LASSO_ARROW));
        double dx = t.getX() - mob.getX();
        double dz = t.getZ() - mob.getZ();
        double dy = t.getEyeY() - lasso.getY();
        double h = Math.sqrt(dx * dx + dz * dz);
        double tt = h / 1.5;
        double drop = 0.025 * tt * tt;
        lasso.shoot(dx, dy + drop, dz, 1.5F, 0.0F);
        level.addFreshEntity(lasso);
    }
}
