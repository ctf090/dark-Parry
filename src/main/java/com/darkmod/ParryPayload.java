package com.darkmod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Pacote enviado pelo cliente quando aperta R (avanço).
public record ParryPayload() implements CustomPacketPayload {
	public static final Type<ParryPayload> TYPE = new Type<>(DarkMod.id("parry"));
	public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ParryPayload> CODEC =
			StreamCodec.unit(new ParryPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
