package com.noa.livingvillages.core;

/** Un bloc d'un plan, en coordonnées relatives. 'block' peut contenir des états : "minecraft:oak_door[half=lower]". 'item' = null si gratuit. */
public record PlanBlock(int dx, int dy, int dz, String block, String item) {
	public String blockId() {
		int i = block.indexOf('[');
		return i < 0 ? block : block.substring(0, i);
	}
}
