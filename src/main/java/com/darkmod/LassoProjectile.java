package com.darkmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// Projétil da flecha com laço: voa, pode errar. Ao acertar, dá o dano e começa o puxão (LassoPull).
public class LassoProjectile extends ThrowableItemProjectile {
    public static EntityType<LassoProjectile> TYPE;
    private static final float LASSO_DAMAGE = 6.0F;

    public LassoProjectile(EntityType<? extends LassoProjectile> type, Level level) {
        super(type, level);
    }

    public LassoProjectile(Level level, LivingEntity owner, ItemStack stack) {
        super(TYPE, owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return LassoArrow.LASSO_ARROW;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Entity owner = getOwner();
        if (target == owner) return;
        target.hurtServer(serverLevel, serverLevel.damageSources().thrown(this, owner), LASSO_DAMAGE);
        if (owner != null) LassoPull.start(target, owner);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        discard();
    }

    public static void init() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, DarkMod.id("lasso_projectile"));
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<LassoProjectile>of(LassoProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build(key));
    }
}
