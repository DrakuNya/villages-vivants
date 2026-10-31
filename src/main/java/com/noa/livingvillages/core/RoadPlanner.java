package com.noa.livingvillages.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trace les chemins entre les portes des bâtiments et la place centrale (recherche du plus court chemin, en évitant les bâtiments). */
public final class RoadPlanner {
	/** Demi-largeur de la place centrale (autour du puits) : les chemins s'arrêtent dès qu'ils l'atteignent. */
	public static final int PLAZA = 3;

	private RoadPlanner() {
	}

	/** Plus court chemin de (sx,sz) vers la route existante ou la place centrale la plus proche. Renvoie seulement les cases nouvelles. */
	public static List<int[]> route(Town town, TerrainView terrain, int sx, int sz, int radius) {
		long startKey = RoadCell.key(sx, sz);
		Set<Long> footprints = footprintCells(town);
		if (footprints.contains(startKey) || terrain.blocked(sx, sz)) return List.of();

		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Map<Long, Long> from = new HashMap<>();
		queue.add(new int[]{sx, sz});
		from.put(startKey, startKey);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		long goal = Long.MIN_VALUE;
		while (!queue.isEmpty()) {
			int[] c = queue.poll();
			long k = RoadCell.key(c[0], c[1]);
			boolean atCenter = Math.abs(c[0] - town.centerX) <= PLAZA && Math.abs(c[1] - town.centerZ) <= PLAZA;
			if (atCenter || (town.roads.containsKey(k) && k != startKey)) {
				goal = k;
				break;
			}
			for (int[] d : dirs) {
				int nx = c[0] + d[0], nz = c[1] + d[1];
				if (Math.abs(nx - town.centerX) > radius || Math.abs(nz - town.centerZ) > radius) continue;
				long nk = RoadCell.key(nx, nz);
				if (from.containsKey(nk) || footprints.contains(nk)) continue;
				if (terrain.blocked(nx, nz)) continue;
				from.put(nk, k);
				queue.add(new int[]{nx, nz});
			}
		}
		if (goal == Long.MIN_VALUE) return List.of();
		List<int[]> path = new ArrayList<>();
		long k = goal;
		while (true) {
			int x = (int) (k >> 32);
			int z = (int) k;
			if (!town.roads.containsKey(k)) path.add(new int[]{x, z});
			if (k == startKey) break;
			k = from.get(k);
		}
		Collections.reverse(path); // du bâtiment vers le centre
		return path;
	}

	/** Ajoute le chemin à la ville ; un lampadaire toutes les 6 cases. */
	public static int addRoad(Town town, List<int[]> cells) {
		int n = 0;
		for (int[] c : cells) {
			long k = RoadCell.key(c[0], c[1]);
			if (town.roads.containsKey(k)) continue;
			boolean lamp = (town.roads.size() % 6) == 5;
			town.roads.put(k, new RoadCell(c[0], c[1], lamp, false));
			n++;
		}
		return n;
	}

	private static Set<Long> footprintCells(Town town) {
		Set<Long> s = new java.util.HashSet<>();
		for (BuildSite b : town.sites) {
			int[] f = b.footprint();
			for (int x = f[0]; x <= f[2]; x++) for (int z = f[1]; z <= f[3]; z++) s.add(RoadCell.key(x, z));
		}
		return s;
	}
}
