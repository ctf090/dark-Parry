package com.darkmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Cliente -> servidor: o jogador apertou Espaço enquanto está agarrado.
public record GrabEscapePayload() implements CustomPacketPayload {
        public static final Type<GrabEscapePayload> TYPE = new Type<>(DarkMod.id("grab_escape"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GrabEscapePayload> CODEC =
                        StreamCodec.unit(new GrabEscapePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
