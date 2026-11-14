package com.darkmod;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;

// Maçã de Estamina: ao comer, devolve ENERGY_RESTORE segmentos. Aparece em baús de masmorra.
public final class EnergyFood {
        public static final int ENERGY_RESTORE = 4;
        public static Item ENERGY_APPLE;

        public record RestoreEnergy() implements ConsumeEffect {
                public static final RestoreEnergy INSTANCE = new RestoreEnergy();
                public static final MapCodec<RestoreEnergy> CODEC = MapCodec.unit(INSTANCE);
                public static final StreamCodec<RegistryFriendlyByteBuf, RestoreEnergy> STREAM_CODEC = StreamCodec.unit(INSTANCE);
                public static final ConsumeEffect.Type<RestoreEnergy> TYPE = new ConsumeEffect.Type<>(CODEC, STREAM_CODEC);

                @Override
                public ConsumeEffect.Type<RestoreEnergy> getType() {
                        return TYPE;
                }

                @Override
                public boolean apply(Level level, ItemStack stack, LivingEntity user) {
                        if (user instanceof ServerPlayer player) EnergyHandler.add(player, ENERGY_RESTORE);
                        return true;
                }
        }

        private EnergyFood() {}

        public static void init() {
                Registry.register(BuiltInRegistries.CONSUME_EFFECT_TYPE,
                                ResourceKey.create(Registries.CONSUME_EFFECT_TYPE, DarkMod.id("restore_energy")),
                                RestoreEnergy.TYPE);

                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, DarkMod.id("energy_apple"));
                Consumable consumable = Consumables.defaultFood().onConsume(RestoreEnergy.INSTANCE).build();
                ENERGY_APPLE = Registry.register(BuiltInRegistries.ITEM, key,
                                new Item(new Item.Properties().setId(key).food(new FoodProperties(4, 2.4F, true), consumable)));

                LootTableEvents.MODIFY.register((id, table, source, registries) -> {
                        if (id.equals(BuiltInLootTables.SIMPLE_DUNGEON)) {
                                table.withPool(LootPool.lootPool()
                                                .add(LootItem.lootTableItem(ENERGY_APPLE)));
                        }
                });
        }
}
