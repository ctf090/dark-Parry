package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Pacote enviado pelo cliente quando solta o ataque carregado (ticks que ficou segurando).
public record ChargePayload(int ticks) implements CustomPacketPayload {
	public static final Type<ChargePayload> TYPE = new Type<>(DarkMod.id("charge"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChargePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, ChargePayload::ticks, ChargePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
