package com.autoleap;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public class FontPackRegistrar {
    public static void register() {
        ResourceManagerHelper.registerBuiltinResourcePack(
            Identifier.fromNamespaceAndPath("trji", "font_override"),
            FabricLoader.getInstance().getModContainer("trji").orElseThrow(),
            ResourcePackActivationType.ALWAYS_ENABLED
        );
    }
}
