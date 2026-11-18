package com.noa.livingvillages.core;

import java.util.List;

/** Les colons : l'équipe de départ d'une nouvelle ville et ses provisions. Sans Minecraft. */
public final class Settlement {
	/** Émeraudes de départ : le « trésor » de la ville (coffres de ville, objets du mod). */
	public static final int TREASURY_EMERALDS = 10;

	private Settlement() {
	}

	/** Un maire, deux constructeurs, un bûcheron, un mineur. */
	public static List<Role> crew() {
		return List.of(Role.MAYOR, Role.BUILDER, Role.BUILDER, Role.LUMBERJACK, Role.MINER);
	}

	/** Provisions de base des colons, adaptées au style de la région. */
	public static Stock starterStock(Theme theme) {
		Stock s = new Stock();
		s.add("minecraft:" + theme.wood + "_log", 64);
		s.add("minecraft:cobblestone", 160);
		s.add("minecraft:sand", 32);
		s.add("minecraft:white_wool", 8);
		s.add("minecraft:coal", 8);
		s.add("minecraft:dirt", 16);
		s.add("minecraft:emerald", TREASURY_EMERALDS);
		return s;
	}
}
