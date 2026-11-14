package com.darkmod;

import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// Deixa os zumbis mais fortes. Passo 1: armadura.
public final class ZombieBuff {
	private static final String TAG = "darkmod_buffed";
	private static final float ARMOR_CHANCE = 0.5F;
	private static final float DROP_CHANCE = 0.003F;

	// Cada linha: capacete, peitoral, calça, bota. A ordem bate com CHANCES.
	private static final Item[][] SETS = {
		{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
		{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
		{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
		{Items.COPPER_HELMET, Items.COPPER_CHESTPLATE, Items.COPPER_LEGGINGS, Items.COPPER_BOOTS},
		{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
		{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
	};
	private static final int[] CHANCES = {5, 5, 8, 12, 35, 30, 5}; // soma 100

	private static final EquipmentSlot[] SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private ZombieBuff() {}

	public static void init() {
		ZombiePotion.init();
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof Zombie mob)) return;
			ZombieAi.attach(mob);
			if (entity.entityTags().contains(TAG)) return;
			entity.addTag(TAG);

			ThreadLocalRandom r = ThreadLocalRandom.current();
			var hp = mob.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				hp.setBaseValue(hp.getBaseValue() + 10.0);
				mob.setHealth(mob.getMaxHealth());
			}
			mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 0));
			if (r.nextFloat() < 0.25F) {
				java.util.List<MobEffectInstance> good = new java.util.ArrayList<>(java.util.List.of(
						new MobEffectInstance(MobEffects.JUMP_BOOST, MobEffectInstance.INFINITE_DURATION, 4),
						new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0),
						new MobEffectInstance(MobEffects.ABSORPTION, MobEffectInstance.INFINITE_DURATION, 1),
						new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0)));
				java.util.Collections.shuffle(good);
				mob.addEffect(good.get(0));
				mob.addEffect(good.get(1));
			}
			if (r.nextFloat() < 0.20F) {
				mob.addTag(ZombiePotion.TAG);
				ZombiePotion.track(mob);
			}
			if (r.nextFloat() < 0.30F) {
				mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0));
				mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 1));
			}
			float w = r.nextFloat();
			Item weapon = w < 0.20F ? Items.IRON_SWORD : w < 0.30F ? Items.BOW : w < 0.40F ? Items.CROSSBOW : null;
			if (weapon != null) {
				ItemStack held = new ItemStack(weapon);
				if (weapon == Items.BOW || weapon == Items.CROSSBOW) {
					held.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
				}
				mob.setItemSlot(EquipmentSlot.MAINHAND, held);
				mob.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
			}
			if (r.nextFloat() < 0.5F) {
				mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				mob.setDropChance(EquipmentSlot.OFFHAND, DROP_CHANCE);
			}
			if (r.nextFloat() >= ARMOR_CHANCE) return;

			int roll = r.nextInt(100);
			int tier = 0;
			for (int i = 0, acc = 0; i < CHANCES.length; i++) {
				acc += CHANCES[i];
				if (roll < acc) { tier = i; break; }
			}
			for (int i = 0; i < 4; i++) {
				mob.setItemSlot(SLOTS[i], new ItemStack(SETS[tier][i]));
				mob.setDropChance(SLOTS[i], DROP_CHANCE);
			}
		});
	}
}
