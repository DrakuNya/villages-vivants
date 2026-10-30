package com.noa.livingvillages.core;

import java.util.List;
import java.util.function.Predicate;

/** Les métiers « de ville ». Un villageois en reçoit un quand on lui donne l'objet associé. */
public enum Role {
	MAYOR("mayor", "Maire", "livingvillages:mayor_sash", id -> false),
	BUILDER("builder", "Constructeur", "livingvillages:mallet", id -> false),
	LUMBERJACK("lumberjack", "Bûcheron", "minecraft:iron_axe", id -> id.endsWith("_log") || id.endsWith("_stem")),
	MINER("miner", "Mineur", "minecraft:iron_pickaxe",
			id -> List.of("minecraft:cobblestone", "minecraft:stone", "minecraft:sand", "minecraft:gravel", "minecraft:coal",
					"minecraft:clay_ball", "minecraft:iron_ingot", "minecraft:sandstone").contains(id)),
	FARMER("farmer", "Fermier", "minecraft:iron_hoe",
			id -> List.of("minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
					"minecraft:wheat_seeds", "minecraft:hay_block").contains(id)),
	SHEPHERD("shepherd", "Berger", "minecraft:shears", id -> id.endsWith("_wool") || id.equals("minecraft:string") || id.equals("minecraft:mutton")),
	BREEDER("breeder", "Éleveur", "minecraft:lead",
			id -> List.of("minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:leather", "minecraft:egg").contains(id)),
	COOK("cook", "Cuisinier", "minecraft:bowl", id -> id.equals("minecraft:bread") || id.startsWith("minecraft:cooked_")),
	GUARD("guard", "Garde", "minecraft:iron_sword", id -> false),
	FISHER("fisher", "Pêcheur", "minecraft:fishing_rod", id -> id.equals("minecraft:cod") || id.equals("minecraft:salmon")),
	MERCHANT("merchant", "Marchand", "minecraft:emerald", id -> false);

	public final String id;
	public final String displayName;
	public final String assignItem;
	private final Predicate<String> produces;

	Role(String id, String displayName, String assignItem, Predicate<String> produces) {
		this.id = id;
		this.displayName = displayName;
		this.assignItem = assignItem;
		this.produces = produces;
	}

	public boolean canProduce(String item) {
		return produces.test(item);
	}

	public static Role byItem(String itemId) {
		for (Role r : values()) if (r.assignItem.equals(itemId)) return r;
		return null;
	}

	public static Role byId(String id) {
		for (Role r : values()) if (r.id.equals(id)) return r;
		return null;
	}
}
