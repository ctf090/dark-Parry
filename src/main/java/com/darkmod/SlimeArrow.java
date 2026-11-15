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

// Flecha de slime: o clique direito lança o projétil SlimeProjectile. O zumbi de arco e de besta também usa.
public final class SlimeArrow {
    public static Item SLIME_ARROW;

    private SlimeArrow() {}

    public static void init() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DarkMod.id("slime_arrow"));
        SLIME_ARROW = Registry.register(BuiltInRegistries.ITEM, key,
                new SlimeItem(new Item.Properties().setId(key)));
    }

    private static final class SlimeItem extends Item {
        SlimeItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide()) {
                SlimeProjectile slime = new SlimeProjectile(level, player, new ItemStack(SLIME_ARROW));
                slime.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
                level.addFreshEntity(slime);
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
    }
}
