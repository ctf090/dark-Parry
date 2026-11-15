package com.darkmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Lado do servidor.
// R (com qualquer item): avanço + 0,5 s sem tomar dano. Cooldown de 0,7 s e gasta 1 segmento de energia.
// Sem energia suficiente, não avança. Tiros que te acertam durante o avanço voltam.
// Botão direito (mão vazia ou espada): janela de defesa de 1 s. Se aparar um golpe corpo a corpo ou um tiro
// vindo da frente, rebate (golpe: dano + empurrão; tiro: volta em linha reta) e já pode usar de novo.
// Se errar, fica 2 s sem poder (com contagem).
public final class ParryHandler {
	public static final int DASH_TICKS = 10;            // 0,5 s sem tomar dano no R
	public static final int DASH_FOOD_COST = 1;         // 1 segmento de energia
	public static final int DASH_COOLDOWN_TICKS = 14;   // 0,7 s entre avanços

	public static final int GUARD_TICKS = 14;   // 0,7 s para aparar
	public static final int COOLDOWN_TICKS = 40;        // 2 s sem defender depois de errar
	public static final double REBOUND_STRENGTH = 1.2;
	public static final float REBOUND_DAMAGE = 3.0F;    // 1,5 coração
	public static final double PROJECTILE_NUDGE = 0.8;  // afasta o tiro de você para ele não te acertar de novo

	public static final int CHARGE_MIN_TICKS = 10;
	public static final int CHARGE_MAX_TICKS = 160;
	public static final float CHARGE_MAX_DAMAGE = 8.0F;

	private static final Map<UUID, Long> DASH_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> DASH_COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> GUARD_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> LAST_SECONDS = new HashMap<>();

	// Tiros a devolver no fim do tick (depois que o jogo termina de tratar o impacto).
	private record Reflect(Projectile projectile, ServerPlayer defender, Vec3 velocity) {}
	private static final List<Reflect> PENDING = new ArrayList<>();

	// Evita loop quando o dano do rebate também passa pelo evento de dano.
	private static boolean inRebound = false;

	private ParryHandler() {}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(ParryPayload.TYPE, ParryPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GuardPayload.TYPE, GuardPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChargePayload.TYPE, ChargePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SwapPayload.TYPE, SwapPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SlamPayload.TYPE, SlamPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EnergyPayload.TYPE, EnergyPayload.CODEC);
		EnergyHandler.init();
		GrabHandler.init();

		ServerPlayNetworking.registerGlobalReceiver(SwapPayload.TYPE,
				(payload, context) -> swapHealthFood(context.player(), payload.toFood()));
		ServerPlayNetworking.registerGlobalReceiver(SlamPayload.TYPE,
				(payload, context) -> slam(context.player(), payload.height()));
		ServerPlayNetworking.registerGlobalReceiver(ParryPayload.TYPE,
				(payload, context) -> startDash(context.player()));
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			UUID rid = newPlayer.getUUID();
			GUARD_UNTIL.remove(rid);
			COOLDOWN_UNTIL.remove(rid);
			LAST_SECONDS.remove(rid);
		});

		ServerPlayNetworking.registerGlobalReceiver(GuardPayload.TYPE,
				(payload, context) -> {
			if (payload.guarding() && canGuard(context.player()) && EnergyHandler.get(context.player()) >= 1) {
				if (context.player().level() instanceof net.minecraft.server.level.ServerLevel sl) {
					sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
							context.player().getX(), context.player().getY() + 1.0, context.player().getZ(),
							8, 0.3, 0.3, 0.3, 0.1);
				}
			}
			setGuard(context.player(), payload.guarding());
		});

		ServerPlayNetworking.registerGlobalReceiver(ChargePayload.TYPE,
				(payload, context) -> chargedAttack(context.player(), payload.ticks()));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ParryHandler::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(ParryHandler::tick);
		ZombieBuff.init();
	}

	// Defesa: mão secundária vazia e, na principal, mão vazia ou espada.
	private static boolean canGuard(ServerPlayer player) {
		boolean offhandOk = player.getOffhandItem().isEmpty()
				|| player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
		return offhandOk
				&& (player.getMainHandItem().isEmpty() || player.getMainHandItem().is(ItemTags.SWORDS));
	}

	private static void swapHealthFood(ServerPlayer player, boolean toFood) {
		float hp = player.getHealth();
		var food = player.getFoodData();
		if (toFood) {
			if (hp > 1.0F && food.getFoodLevel() < 20) {
				player.setHealth(hp - 1.0F);
				food.setFoodLevel(food.getFoodLevel() + 1);
			}
		} else if (food.getFoodLevel() >= 1 && hp < player.getMaxHealth()) {
			player.setHealth(Math.min(hp + 1.0F, player.getMaxHealth()));
			food.setFoodLevel(food.getFoodLevel() - 1);
		}
	}

	private static final float[] SLAM_TABLE = {0, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 6, 6, 6, 7, 7, 9, 16};

	private static void slam(ServerPlayer player, float height) {
		int energy = EnergyHandler.get(player);
		if (!EnergyHandler.spend(player, 1)) return;
		float dmg = Math.max(0.0F, Math.min(height, 100.0F)) + SLAM_TABLE[Math.min(energy, 20)];
		ServerLevel level = (ServerLevel) player.level();
		AABB box = player.getBoundingBox().inflate(3.0);
		for (Entity e : level.getEntities(player, box, x -> x instanceof LivingEntity)) {
			LivingEntity t = (LivingEntity) e;
			t.hurtServer(level, level.damageSources().playerAttack(player), dmg);
			Vec3 away = t.position().subtract(player.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			if (flat.lengthSqr() < 1.0E-6) continue;
			Vec3 d = flat.normalize().scale(0.8);
			t.setDeltaMovement(d.x, 0.4, d.z);
		}
	}

	private static void startDash(ServerPlayer player) {
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		if (now < DASH_COOLDOWN_UNTIL.getOrDefault(id, 0L)) return; // cooldown de 0,7 s

		// Sem energia suficiente, não avança.
		if (!EnergyHandler.spend(player, DASH_FOOD_COST)) return;

		DASH_UNTIL.put(id, now + DASH_TICKS);
		DASH_COOLDOWN_UNTIL.put(id, now + DASH_COOLDOWN_TICKS);
		player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.0); // sobe 1 bloco durante o avanço
	}

	    private static void setGuard(ServerPlayer player, boolean on) {
        UUID id = player.getUUID();
        long now = player.level().getGameTime();

        if (on) {
            if (now <= GUARD_UNTIL.getOrDefault(id, 0L)) return; // já defendendo: não gasta nem renova
            if (!canGuard(player)) return;
            if (now < COOLDOWN_UNTIL.getOrDefault(id, 0L)) return; // ainda em cooldown
            if (!EnergyHandler.spend(player, 1)) return;           // cada defesa gasta 1 segmento
            GUARD_UNTIL.put(id, now + GUARD_TICKS);
            player.sendSystemMessage(Component.literal("Defesa ativa!"), true);
        } else if (GUARD_UNTIL.remove(id) != null) {
            startCooldown(id, now); // soltou sem aparar nada = errou
        }
    }

	private static void startCooldown(UUID id, long now) {
		COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
		LAST_SECONDS.remove(id);
	}

	// Aparou: já pode usar de novo, sem cooldown.
	private static void consumeGuard(UUID id) {
		GUARD_UNTIL.remove(id);
		COOLDOWN_UNTIL.remove(id);
		LAST_SECONDS.remove(id);
	}

	// Marca o tiro para voltar em linha reta (a velocidade é aplicada no fim do tick).
	private static void reflect(Projectile projectile, ServerPlayer defender) {
		// Tiro da shulker persegue sozinho: só é bloqueado, não devolvido.
		if (projectile instanceof ShulkerBullet) return;
		for (Reflect r : PENDING) {
			if (r.projectile() == projectile) return;
		}
		PENDING.add(new Reflect(projectile, defender, projectile.getDeltaMovement().scale(-1)));
	}

	private static void applyReflections() {
		for (Reflect r : PENDING) {
			Projectile p = r.projectile();
			if (p.isRemoved()) continue;
			Vec3 back = r.velocity();
			if (back.lengthSqr() < 1.0E-6) continue;

			p.setPos(p.position().add(back.normalize().scale(PROJECTILE_NUDGE)));
			p.setDeltaMovement(back);
			p.setOwner(r.defender()); // agora o tiro é seu
		}
		PENDING.clear();
	}

	// Roda todo tick: devolve tiros, fecha janelas que expiraram e mostra "Defesa 2s", "Defesa 1s" e some.
	private static void tick(MinecraftServer server) {
		applyReflections();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			long now = player.level().getGameTime();

			Long dashEnd = DASH_UNTIL.get(id);
			if (dashEnd != null && now > dashEnd) {
				DASH_UNTIL.remove(id);
				player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6); // valor padrão do jogador
			}

			Long guardEnd = GUARD_UNTIL.get(id);
			if (guardEnd != null && now > guardEnd) {
				GUARD_UNTIL.remove(id); // janela acabou sem aparar = errou
				startCooldown(id, now);
			}

			Long until = COOLDOWN_UNTIL.get(id);
			if (until == null) continue;

			long remaining = until - now;
			int secs = remaining > 0 ? (int) Math.ceil(remaining / 20.0) : 0;
			if (LAST_SECONDS.getOrDefault(id, -1) == secs) continue;
			LAST_SECONDS.put(id, secs);

			if (secs > 0) {
				player.sendSystemMessage(Component.literal("Defesa " + secs + "s"), true);
			} else {
				player.sendSystemMessage(Component.empty(), true);
				COOLDOWN_UNTIL.remove(id);
				LAST_SECONDS.remove(id);
			}
		}
	}

	// Ataque carregado: +1 de dano por segundo segurado (máximo +8), no inimigo mais perto que você mira.
	private static void chargedAttack(ServerPlayer player, int ticks) {
		if (ticks < 0) {
			int shown = Math.min(-ticks / 20, 8);
			player.sendSystemMessage(Component.literal("Carga " + shown + "/8"), true);
			return;
		}
		if (!player.getMainHandItem().is(ItemTags.SWORDS)) return;
		if (!(player.level() instanceof ServerLevel level)) return;
		int t = Math.min(ticks, CHARGE_MAX_TICKS);
		int shown = Math.min(ticks / 20, 8);
		player.sendSystemMessage(Component.literal("Carga " + shown + "/8"), true);
		if (t < CHARGE_MIN_TICKS) return;
		float damage = 2.0F + Math.min(t / 20.0F, CHARGE_MAX_DAMAGE);

		Vec3 look = player.getLookAngle();
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(3.5), x -> x != player && x.isAlive())) {
			Vec3 to = candidate.position().subtract(player.position());
			double dist = to.length();
			if (dist < 1.0E-6 || dist > 3.5) continue;
			if (look.dot(to.scale(1.0 / dist)) < 0.7) continue;
			if (dist < best) {
				best = dist;
				target = candidate;
			}
		}
		if (target == null) return;
		if (!EnergyHandler.spend(player, 1)) return;

		inRebound = true;
		try {
			target.hurtServer(level, level.damageSources().playerAttack(player), damage);
		} finally {
			inRebound = false;
		}
	}

	private static void blockSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	// Retorna false = cancela o dano.
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (inRebound) return true;
		if (!(entity instanceof ServerPlayer player)) return true;

		// Vazio e /kill continuam funcionando.
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;

		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Entity direct = source.getDirectEntity();

		// 1) Avanço (R): ninguém te acerta enquanto dura. Tiro que te acerta volta.
		Long dashEnd = DASH_UNTIL.get(id);
		if (dashEnd != null && now <= dashEnd) {
			if (direct instanceof Projectile projectile) reflect(projectile, player);
			blockSound(player);
			return false;
		}

		// 2) Defesa (botão direito), dentro da janela e só se vier da frente.
		Long guardEnd = GUARD_UNTIL.get(id);
		if (guardEnd != null && now <= guardEnd && canGuard(player)) {
			Vec3 look = player.getLookAngle();

			// 2a) Tiro (flecha, bola de fogo, etc.): volta em linha reta.
			if (direct instanceof Projectile projectile) {
				if (look.dot(projectile.getDeltaMovement()) >= 0) return true; // veio por trás
				consumeGuard(id);
				reflect(projectile, player);
				blockSound(player);
				return false;
			}

			// 2b) Golpe corpo a corpo: dano + empurrão no atacante.
			if (source.getEntity() instanceof LivingEntity attacker && direct == attacker) {
				double dx = attacker.getX() - player.getX();
				double dz = attacker.getZ() - player.getZ();
				if (look.x * dx + look.z * dz <= 0) return true; // veio por trás

				consumeGuard(id);

				// Dano do rebate (1,5 coração).
				if (player.level() instanceof ServerLevel serverLevel) {
					inRebound = true;
					try {
						attacker.hurtServer(serverLevel,
								serverLevel.damageSources().playerAttack(player), REBOUND_DAMAGE);
					} finally {
						inRebound = false;
					}
				}

				// Empurra o atacante para longe.
				Vec3 away = new Vec3(dx, 0, dz).normalize();
				attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 255));

				blockSound(player);
				return false;
			}
		}

		return true;
	}
}
