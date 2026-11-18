package com.noa.livingvillages.mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Les deux objets du mod : le maillet (constructeur) et le ruban d'élu (maire). */
public final class ModItems {
	public static final String MOD_ID = "livingvillages";

	public static Item MALLET;
	public static Item MAYOR_SASH;
	public static Item FOUNDERS_CHARTER;
	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private ModItems() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, name));
		return Registry.register(BuiltInRegistries.ITEM, key.identifier(), new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	public static void init() {
		MALLET = register("mallet");
		MAYOR_SASH = register("mayor_sash");
		FOUNDERS_CHARTER = register("founders_charter");

		// onglet du mode créatif : tous les objets du mod
		CreativeModeTabEvents.modifyOutputEvent(TAB).register(entries -> {
			entries.accept(MALLET);
			entries.accept(MAYOR_SASH);
			entries.accept(FOUNDERS_CHARTER);
			entries.accept(ModBlocks.TOWN_CHEST);
		});
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(MAYOR_SASH))
				.title(Component.translatable("itemGroup.livingvillages.main"))
				.build());
	}
}
