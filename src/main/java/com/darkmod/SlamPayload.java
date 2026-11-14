package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Enviado pelo cliente ao tocar o chão depois de um pisão. height = blocos caídos.
public record SlamPayload(float height) implements CustomPacketPayload {
	public static final Type<SlamPayload> TYPE = new Type<>(DarkMod.id("slam"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SlamPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.FLOAT, SlamPayload::height, SlamPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
