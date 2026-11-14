package com.darkmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class DarkMod implements ModInitializer {
	public static final String MOD_ID = "dark-mod";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ParryHandler.init();
	}
}
