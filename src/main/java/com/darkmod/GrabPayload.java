package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Servidor -> cliente: 1 = foi agarrado, 0 = soltou, 2 = lançar para cima (fim do agarrão).
public record GrabPayload(int state) implements CustomPacketPayload {
        public static final Type<GrabPayload> TYPE = new Type<>(DarkMod.id("grab"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GrabPayload> CODEC =
                        StreamCodec.composite(ByteBufCodecs.VAR_INT, GrabPayload::state, GrabPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
