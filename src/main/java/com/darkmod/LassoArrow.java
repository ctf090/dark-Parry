package com.darkmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Flecha com laço: o clique direito lança o projétil LassoProjectile.
public final class LassoArrow {
    public static Item LASSO_ARROW;

    private LassoArrow() {}

    public static void init() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DarkMod.id("lasso_arrow"));
        LASSO_ARROW = Registry.register(BuiltInRegistries.ITEM, key,
                new LassoItem(new Item.Properties().setId(key)));
    }

    private static final class LassoItem extends Item {
        LassoItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                LassoProjectile lasso = new LassoProjectile(level, player, new ItemStack(LASSO_ARROW));
                lasso.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
                level.addFreshEntity(lasso);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
    }
}
