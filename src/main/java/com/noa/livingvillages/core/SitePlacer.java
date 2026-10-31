package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Choisit où poser un bâtiment : autour de la place centrale, en cercles de « lots », sans chevaucher les autres
 * bâtiments, en évitant l'eau et les pentes trop fortes, avec la porte tournée vers le centre.
 */
public final class SitePlacer {
	public static final int LOT = 12;
	public static final int MARGIN = 3;
	public static final int MAX_RINGS = 4;

	private SitePlacer() {
	}

	public record Placement(int ox, int oy, int oz, int rotation, int slope) {
	}

	/** Les bâtiments de la place (puits, marché) occupent le centre ; les autres viennent autour. */
	public static boolean usesPlaza(BuildPlan p) {
		return p.meta.category().equals("well") || p.meta.category().equals("market");
	}

	public static Optional<Placement> find(Town town, BuildPlan plan, TerrainView terrain, int maxSlope) {
		int startRing = usesPlaza(plan) ? 0 : 1;
		for (int ring = startRing; ring <= MAX_RINGS; ring++) {
			Placement best = null;
			for (int[] c : candidates(town, ring)) {
				int rot = rotationToward(town, c[0], c[1]);
				Placement p = tryAt(town, plan, terrain, c[0], c[1], rot, maxSlope);
				if (p != null && (best == null || p.slope() < best.slope())) best = p;
			}
			if (best != null) return Optional.of(best);
		}
		return Optional.empty();
	}

	static List<int[]> candidates(Town town, int ring) {
		List<int[]> out = new ArrayList<>();
		if (ring == 0) {
			int[][] offs = {{0, 0}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}};
			for (int[] o : offs) out.add(new int[]{town.centerX + o[0], town.centerZ + o[1]});
			return out;
		}
		for (int i = -ring; i <= ring; i++) {
			for (int j = -ring; j <= ring; j++) {
				if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
				out.add(new int[]{town.centerX + i * LOT, town.centerZ + j * LOT});
			}
		}
		// les plus proches du centre d'abord, ordre stable
		out.sort((a, b) -> {
			long da = sq(a[0] - town.centerX) + sq(a[1] - town.centerZ);
			long db = sq(b[0] - town.centerX) + sq(b[1] - town.centerZ);
			int cmp = Long.compare(da, db);
			return cmp != 0 ? cmp : Arrays.compare(a, b);
		});
		return out;
	}

	private static long sq(int v) {
		return (long) v * v;
	}

	/** La porte du plan est au sud (rotation 0) ; on la tourne vers le centre. */
	public static int rotationToward(Town town, int px, int pz) {
		int dx = town.centerX - px, dz = town.centerZ - pz;
		if (dx == 0 && dz == 0) return 0;
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 3 : 1; // centre à l'est : porte à l'est
		return dz > 0 ? 0 : 2; // centre au sud : porte au sud
	}

	private static Placement tryAt(Town town, BuildPlan plan, TerrainView t, int px, int pz, int rot, int maxSlope) {
		int fx = plan.footprintX(rot), fz = plan.footprintZ(rot);
		int ox = px - fx / 2, oz = pz - fz / 2;
		// chevauchement avec un bâtiment existant (marge incluse)
		for (BuildSite s : town.sites) {
			int[] f = s.footprint();
			if (ox - MARGIN <= f[2] && ox + fx - 1 + MARGIN >= f[0] && oz - MARGIN <= f[3] && oz + fz - 1 + MARGIN >= f[1]) return null;
		}
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		List<Integer> hs = new ArrayList<>();
		for (int x = ox - 1; x <= ox + fx; x++) {
			for (int z = oz - 1; z <= oz + fz; z++) {
				if (t.blocked(x, z)) return null;
				int g = t.groundY(x, z);
				min = Math.min(min, g);
				max = Math.max(max, g);
				hs.add(g);
			}
		}
		int slope = max - min;
		if (slope > maxSlope) return null;
		hs.sort(Integer::compare);
		int oy = hs.get(hs.size() / 2);
		return new Placement(ox, oy, oz, rot, slope);
	}
}
