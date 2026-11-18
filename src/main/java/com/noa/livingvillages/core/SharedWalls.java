package com.noa.livingvillages.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Murs mitoyens : deux bâtiments voisins peuvent partager une ligne de mur, à condition que les blocs soient compatibles. */
public final class SharedWalls {
	private SharedWalls() {
	}

	/** Matériaux de mur : on peut les superposer sans conflit (jamais de porte, de lit, de coffre, de torche...). */
	public static boolean isWallBlock(String id) {
		if (id.endsWith("_log") || id.endsWith("_planks") || id.endsWith("_slab") || id.endsWith("_stairs") || id.endsWith("_fence")
				|| id.endsWith("_wall") || id.endsWith("sandstone") || id.contains("stone_brick") || id.endsWith("_wool")) return true;
		return switch (id) {
			case "minecraft:cobblestone", "minecraft:stone", "minecraft:glass_pane", "minecraft:glass", "minecraft:snow_block",
					"minecraft:coarse_dirt", "minecraft:dirt", "minecraft:hay_block" -> true;
			default -> false;
		};
	}

	/**
	 * Le candidat peut-il s'accoler aux autres bâtiments ? Renvoie le nombre de blocs partagés (0 = aucun contact),
	 * ou -1 si c'est impossible : intérieurs qui se chevauchent, ou bloc incompatible sur la ligne de mur partagée.
	 */
	public static int compatibleOverlap(BuildSite candidate, Collection<BuildSite> others) {
		int[] f = candidate.footprint();
		Map<Long, String> mine = null;
		int shared = 0;
		for (BuildSite o : others) {
			int[] g = o.footprint();
			int x0 = Math.max(f[0], g[0]), x1 = Math.min(f[2], g[2]);
			int z0 = Math.max(f[1], g[1]), z1 = Math.min(f[3], g[3]);
			if (x0 > x1 || z0 > z1) continue; // aucun contact
			if (x1 - x0 >= 1 && z1 - z0 >= 1) return -1; // les intérieurs se chevauchent
			if (mine == null) mine = candidate.blockMap();
			Map<Long, String> theirs = o.blockMap();
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					for (int y = candidate.oy - 1; y <= candidate.oy + Math.max(candidate.plan.sizeY, o.oy + o.plan.sizeY - candidate.oy) + 1; y++) {
						long k = BuildSite.blockKey(x, y, z);
						String a = mine.get(k), b = theirs.get(k);
						if (a != null && !isWallBlock(a)) return -1;
						if (b != null && !isWallBlock(b)) return -1;
						if (a != null && b != null) shared++;
					}
				}
			}
		}
		return shared;
	}

	/** Retire du chantier les blocs déjà fournis par les bâtiments qui le précèdent dans la ville. Renvoie le nombre retiré. */
	public static int apply(Town town, BuildSite site) {
		int idx = town.sites.indexOf(site);
		if (idx <= 0) return 0;
		Set<Long> provided = new HashSet<>();
		List<BuildSite> before = town.sites.subList(0, idx);
		int[] f = site.footprint();
		for (BuildSite o : before) {
			int[] g = o.footprint();
			if (g[0] > f[2] || g[2] < f[0] || g[1] > f[3] || g[3] < f[1]) continue;
			provided.addAll(o.blockMap().keySet());
		}
		return provided.isEmpty() ? 0 : site.dropBlocks(provided);
	}
}
