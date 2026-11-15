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

// Tiro do zumbi de arco e de besta: a bola de slime. Dá dano normal de flecha, sem efeito.
public class SlimeProjectile extends ThrowableItemProjectile {
    public static EntityType<SlimeProjectile> TYPE;
    private static final float SLIME_DAMAGE = 3.0F;

    public SlimeProjectile(EntityType<? extends SlimeProjectile> type, Level level) {
        super(type, level);
    }

    public SlimeProjectile(Level level, LivingEntity owner, ItemStack stack) {
        super(TYPE, owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return SlimeArrow.SLIME_ARROW;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        if (!(level() instanceof ServerLevel serverLevel)) return;
        Entity owner = getOwner();
        target.hurtServer(serverLevel, serverLevel.damageSources().thrown(this, owner), SLIME_DAMAGE);
        LassoPull.knockback(target, owner != null ? owner : this);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        discard();
    }

    public static void init() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, DarkMod.id("slime_projectile"));
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<SlimeProjectile>of(SlimeProjectile::new, MobCategory.MISC).sized(0.25F, 0.25F).build(key));
    }
}
