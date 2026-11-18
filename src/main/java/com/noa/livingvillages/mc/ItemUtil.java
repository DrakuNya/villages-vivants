package com.noa.livingvillages.mc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Passage entre les identifiants texte de la ville et les objets de Minecraft. */
public final class ItemUtil {
	private ItemUtil() {
	}

	/** Pile pour cet identifiant, ou ItemStack.EMPTY si l'objet n'existe pas. */
	public static ItemStack stackOf(String id, int count) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		if (item == null) return ItemStack.EMPTY;
		if (!id.equals("minecraft:air") && BuiltInRegistries.ITEM.getKey(item).toString().equals("minecraft:air")) return ItemStack.EMPTY;
		return new ItemStack(item, count);
	}

	public static String idOf(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
