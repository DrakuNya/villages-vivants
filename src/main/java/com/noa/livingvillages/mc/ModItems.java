package com.noa.livingvillages.mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Les deux objets du mod : le maillet (constructeur) et le ruban d'élu (maire). */
public final class ModItems {
	public static final String MOD_ID = "livingvillages";

	public static Item MALLET;
	public static Item MAYOR_SASH;

	private ModItems() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key.identifier(), new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	public static void init() {
		MALLET = register("mallet");
		MAYOR_SASH = register("mayor_sash");
	}
}
