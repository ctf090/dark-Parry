package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Pacote enviado pelo cliente quando segura/solta o botão direito (defesa).
public record GuardPayload(boolean guarding) implements CustomPacketPayload {
	public static final Type<GuardPayload> TYPE = new Type<>(DarkMod.id("guard"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GuardPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, GuardPayload::guarding, GuardPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
