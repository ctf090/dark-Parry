package com.darkmod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

// Zumbis que jogam poção. Passo atual: só veneno.
public final class ZombiePotion {
	public static final String TAG = "darkmod_potion";
	private static final int COOLDOWN = 100; // 5 s
	private static final Map<Zombie, Long> NEXT_THROW = new HashMap<>();

	private ZombiePotion() {}

	public static void track(Zombie z) {
		NEXT_THROW.putIfAbsent(z, 0L);
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Zombie z && z.entityTags().contains(TAG)) track(z);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof Zombie z) NEXT_THROW.remove(z);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Map.Entry<Zombie, Long>> it = NEXT_THROW.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Zombie, Long> e = it.next();
				Zombie z = e.getKey();
				if (z.isRemoved() || !z.isAlive()) { it.remove(); continue; }
				long now = z.level().getGameTime();
				if (now < e.getValue() || now % 5 != 0) continue;
				LivingEntity t = z.getTarget();
				if (t == null) continue;
				double d2 = z.distanceToSqr(t);
				if (d2 < 16.0 || d2 > 256.0 || !z.hasLineOfSight(t)) continue;
				throwPoison(z, t);
				e.setValue(now + COOLDOWN);
			}
		});
	}

	private static Holder<Potion> pick() {
		java.util.List<Holder<Potion>> pool = java.util.List.of(
				Potions.POISON, Potions.STRONG_POISON, Potions.HARMING,
				Potions.STRONG_HARMING, Potions.WEAKNESS, Potions.SLOWNESS,
				Potions.STRONG_SLOWNESS, Potions.INFESTED);
		return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
	}

	private static void throwPoison(Zombie z, LivingEntity t) {
		ServerLevel level = (ServerLevel) z.level();
		ItemStack stack = new ItemStack(Items.SPLASH_POTION);
		stack.set(DataComponents.POTION_CONTENTS, new PotionContents(pick()));
		ThrownSplashPotion p = new ThrownSplashPotion(level, z, stack);
		double dx = t.getX() - z.getX();
		double dz = t.getZ() - z.getZ();
		double dy = t.getEyeY() - p.getY();
		double h = Math.sqrt(dx * dx + dz * dz);
		p.shoot(dx, dy + h * 0.2, dz, 0.75F, 8.0F);
		level.addFreshEntity(p);
	}
}
