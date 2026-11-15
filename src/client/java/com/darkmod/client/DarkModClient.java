package com.darkmod.client;

import com.darkmod.DarkMod;
import com.darkmod.ChargePayload;
import com.darkmod.EnergyHandler;
import com.darkmod.EnergyPayload;
import com.darkmod.GuardPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import com.darkmod.ParryHandler;
import com.darkmod.ParryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class DarkModClient implements ClientModInitializer {
	private static final double DASH_SPEED = 1.4;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(DarkMod.id("main"));

	private static final KeyMapping PARRY_KEY = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.dark-mod.parry",
					InputConstants.KEY_R,
					CATEGORY));

	// Energia recebida do servidor (0 a 20). Usada para checar se o poder pode ser usado.
	public static volatile int energy = EnergyHandler.MAX;

	private int cooldown = 0;
	private int dashFx = 0;
	private boolean guarding = false;
	private boolean useWasDownPrev = false;
	private static final KeyMapping SWAP_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.dark-mod.swap", InputConstants.KEY_P, CATEGORY));
	private boolean swapWasDown = false;
	private boolean slamming = false;
	private double peakY = 0;
	private int chargeTicks = 0;
	private boolean jumpArmed = false;
	private int airJumps = 0;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(EnergyPayload.TYPE,
				(payload, context) -> context.client().execute(() -> energy = payload.energy()));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (cooldown > 0) cooldown--;

			LocalPlayer player = client.player;
			if (player == null) {
				guarding = false;
				dashFx = 0;
				return;
			}

			// Troca vida/fome com a tecla do mod: Shift+tecla (vida -1, fome +1) e Espaço+tecla (vida +1, fome -1).
			boolean swapDown = SWAP_KEY.isDown();
			if (swapDown && !swapWasDown) {
				float hp = player.getHealth();
				var food = player.getFoodData();
				if (client.options.keyShift.isDown() && hp > 1.0F && food.getFoodLevel() < 20) {
					ClientPlayNetworking.send(new com.darkmod.SwapPayload(true));
					player.sendOverlayMessage(Component.literal("Vida -1 / Fome +1"));
				} else if (client.options.keyJump.isDown() && food.getFoodLevel() >= 1 && hp < player.getMaxHealth()) {
					ClientPlayNetworking.send(new com.darkmod.SwapPayload(false));
					player.sendOverlayMessage(Component.literal("Vida +1 / Fome -1"));
				}
			}
			swapWasDown = swapDown;

			// Defesa: um pacote a cada clique do botão direito (a checagem de itens fica no servidor).
			boolean useDown = client.options.keyUse.isDown();
			if (useDown && !useWasDownPrev) {
				ClientPlayNetworking.send(new GuardPayload(true));
			}
			useWasDownPrev = useDown;

			// Animação do avanço: rastro de fumaça atrás do jogador enquanto dura.
			if (dashFx > 0) {
				dashFx--;
				for (int i = 0; i < 2; i++) {
					client.level.addParticle(ParticleTypes.CLOUD,
							player.getX() + (player.getRandom().nextDouble() - 0.5) * 0.6,
							player.getY() + player.getRandom().nextDouble() * 1.8,
							player.getZ() + (player.getRandom().nextDouble() - 0.5) * 0.6,
							0, 0.02, 0);
				}
			}

			// Ataque carregado: segura o botão esquerdo com espada; solta para o golpe extra.
			boolean holdingSword = player.getMainHandItem().is(ItemTags.SWORDS);
			if (holdingSword && client.options.keyAttack.isDown()) {
				if (chargeTicks < ParryHandler.CHARGE_MAX_TICKS) chargeTicks++;
				if (chargeTicks % 20 == 0) ClientPlayNetworking.send(new ChargePayload(-chargeTicks));
			} else if (chargeTicks > 0) {
				if (chargeTicks >= ParryHandler.CHARGE_MIN_TICKS) {
					ClientPlayNetworking.send(new ChargePayload(chargeTicks));
				}
				chargeTicks = 0;
			}

			if (player.onGround()) peakY = player.getY(); else peakY = Math.max(peakY, player.getY());

			// Pulo duplo: um pulo extra no ar, ao apertar Espaço de novo.
			if (player.onGround() || player.isInWater()) {
				airJumps = 0;
				jumpArmed = false;
				while (client.options.keyJump.consumeClick()) {}
			} else if (airJumps < 1 && !slamming && !player.getAbilities().flying) {
				if (!jumpArmed) {
					while (client.options.keyJump.consumeClick()) {}
					if (!client.options.keyJump.isDown()) jumpArmed = true;
				} else if (client.options.keyJump.consumeClick()) {
					Vec3 jv = player.getDeltaMovement();
					player.setDeltaMovement(jv.x, 0.6, jv.z);
					airJumps++;
				}
			}
			// Pisão: Shift no ar, já caindo, despenca rápido. Ao tocar o chão, avisa o servidor.
			if (!slamming && !player.onGround() && client.options.keyShift.isDown()
					&& player.getDeltaMovement().y < 0 && energy >= 1) {
				slamming = true;
			}
			if (slamming) {
				if (player.onGround()) {
					slamming = false;
					ClientPlayNetworking.send(new com.darkmod.SlamPayload((float) (peakY - player.getY())));
				} else {
					Vec3 v = player.getDeltaMovement();
					player.setDeltaMovement(v.x, Math.min(v.y, -1.0), v.z);
				}
			}

			// Avanço (R): com qualquer item. Cooldown de 0,7 s e custa 1 segmento de energia.
			while (PARRY_KEY.consumeClick()) {
				if (cooldown > 0) continue;
				if (energy < ParryHandler.DASH_FOOD_COST) continue; // sem energia

				Vec3 look = player.getLookAngle();
				Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
				Vec3 right = new Vec3(-forward.z, 0, forward.x);
				double fwd = (client.options.keyUp.isDown() ? 1 : 0) - (client.options.keyDown.isDown() ? 1 : 0);
				double side = (client.options.keyRight.isDown() ? 1 : 0) - (client.options.keyLeft.isDown() ? 1 : 0);
				Vec3 move = forward.scale(fwd).add(right.scale(side));
				Vec3 dir = move.lengthSqr() < 1.0E-6 ? forward : move.normalize();
				player.setDeltaMovement(dir.x * DASH_SPEED,
						Math.max(player.getDeltaMovement().y, 0.1),
						dir.z * DASH_SPEED);

				ClientPlayNetworking.send(new ParryPayload());
				cooldown = ParryHandler.DASH_COOLDOWN_TICKS;
				dashFx = ParryHandler.DASH_TICKS;
			}
		});
	}
}
