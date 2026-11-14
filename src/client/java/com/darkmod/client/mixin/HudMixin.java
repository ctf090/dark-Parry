package com.darkmod.client.mixin;

import com.darkmod.client.DarkModClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Desenha a barra de energia de 20 segmentos logo acima dos ícones de fome. A fome continua igual.
@Mixin(Hud.class)
public abstract class HudMixin {
        private static final int SEGMENTS = 20;
        private static final int STEP = 4;
        private static final int SEG_WIDTH = 3;
        private static final int FILLED = 0xFF00C8FF;
        private static final int EMPTY = 0x80333333;

        @Inject(method = "extractFood", at = @At("RETURN"))
        private void darkmod$drawEnergy(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, CallbackInfo ci) {
                int energy = DarkModClient.energy;
                int left = xRight - 81;
                for (int i = 0; i < SEGMENTS; i++) {
                        int x = left + i * STEP;
                        int color = i < energy ? FILLED : EMPTY;
                        graphics.fill(x, yLineBase - 4, x + SEG_WIDTH, yLineBase - 1, color);
                }
        }
}
