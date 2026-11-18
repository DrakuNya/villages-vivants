package com.noa.livingvillages.mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Les blocs du mod : le coffre de ville (ouvre l'entrepôt de la ville ; construit par les villageois). */
public final class ModBlocks {
	public static Block TOWN_CHEST;

	private ModBlocks() {
	}

	private static Block register(String name, float strength) {
		Identifier id = Identifier.fromNamespaceAndPath(ModItems.MOD_ID, name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		Block block = new Block(BlockBehaviour.Properties.of().strength(strength).setId(blockKey));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey)));
		return block;
	}

	public static void init() {
		TOWN_CHEST = register("town_chest", 2.5f);
	}
}
