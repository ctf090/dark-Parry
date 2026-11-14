package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Pacote enviado pelo cliente na troca vida/fome. toFood=true: vida -1, fome +1.
public record SwapPayload(boolean toFood) implements CustomPacketPayload {
	public static final Type<SwapPayload> TYPE = new Type<>(DarkMod.id("swap"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwapPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, SwapPayload::toFood, SwapPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
