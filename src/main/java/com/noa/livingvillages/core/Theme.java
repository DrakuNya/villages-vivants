package com.noa.livingvillages.core;

/** Matériaux d'un style de village. Les plans utilisent des jokers (@planks, @door...) remplacés ici. */
public enum Theme {
	PLAINS("oak", "cobblestone"),
	DESERT("acacia", "sandstone"),
	SAVANNA("acacia", "cobblestone"),
	TAIGA("spruce", "cobblestone"),
	SNOWY("spruce", "cobblestone"),
	SWAMP("oak", "cobblestone");

	public final String wood;
	public final String stone;

	Theme(String wood, String stone) {
		this.wood = wood;
		this.stone = stone;
	}

	/** Déduit le thème depuis un nom de biome (ex : « minecraft:snowy_plains »). */
	public static Theme fromBiome(String biome) {
		String b = biome == null ? "" : biome.toLowerCase();
		if (b.contains("desert") || b.contains("badlands")) return DESERT;
		if (b.contains("savanna")) return SAVANNA;
		if (b.contains("snow") || b.contains("frozen") || b.contains("ice")) return SNOWY;
		if (b.contains("taiga")) return TAIGA;
		if (b.contains("swamp") || b.contains("mangrove")) return SWAMP;
		return PLAINS;
	}

	private String name(String joker) {
		return switch (joker) {
			case "log" -> wood + "_log";
			case "planks" -> wood + "_planks";
			case "door" -> wood + "_door";
			case "trapdoor" -> wood + "_trapdoor";
			case "slab" -> wood + "_slab";
			case "stairs" -> wood + "_stairs";
			case "fence" -> wood + "_fence";
			case "fence_gate" -> wood + "_fence_gate";
			case "stone" -> stone;
			case "stone_stairs" -> stone + "_stairs";
			case "stone_slab" -> stone + "_slab";
			case "stone_wall" -> stone + "_wall";
			default -> throw new IllegalArgumentException("Joker inconnu : @" + joker);
		};
	}

	/** Remplace un joker éventuel : "@door[half=lower]" -> "minecraft:spruce_door[half=lower]". */
	public String resolve(String spec) {
		if (spec == null || !spec.startsWith("@")) return spec;
		int end = spec.length();
		int br = spec.indexOf('[');
		if (br >= 0) end = br;
		String joker = spec.substring(1, end);
		return "minecraft:" + name(joker) + spec.substring(end);
	}
}
