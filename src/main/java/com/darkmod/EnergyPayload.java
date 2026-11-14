package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Servidor -> cliente: energia atual (0 a 20).
public record EnergyPayload(int energy) implements CustomPacketPayload {
	public static final Type<EnergyPayload> TYPE = new Type<>(DarkMod.id("energy"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EnergyPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, EnergyPayload::energy, EnergyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
