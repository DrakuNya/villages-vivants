package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ville compacte, façon cité romaine ou médiévale : un grand bâtiment au centre entouré d'un anneau de rues, puis un
 * quadrillage de rues d'une case de large qui délimite des îlots de 13 x 13. Dans chaque îlot, les bâtiments s'alignent
 * le long des rues (porte côté rue), serrés les uns contre les autres : murs mitoyens, dos à dos.
 */
public final class DenseLayout {
	public static final int HALF = 6;          // un îlot fait 13 cases (de -6 à +6 autour de son centre)
	public static final int STREET = HALF + 1; // les rues sont à 7 cases du centre d'un îlot
	public static final int PITCH = 2 * STREET;
	public static final int MAX_RINGS = 6;
	private static final int ROUTE_RADIUS = 110;

	private DenseLayout() {
	}

	/** Cette case est-elle sur une rue du quadrillage ? */
	public static boolean isStreet(Town t, int x, int z) {
		return Math.floorMod(x - t.centerX - STREET, PITCH) == 0 || Math.floorMod(z - t.centerZ - STREET, PITCH) == 0;
	}

	/** Le bâtiment central : centré sur la ville, porte au sud, sur l'îlot (0, 0). */
	public static Optional<SitePlacer.Placement> findCenter(Town town, BuildPlan plan, TerrainView terrain, int maxSlope) {
		int fx = plan.footprintX(0), fz = plan.footprintZ(0);
		int ox = town.centerX - fx / 2, oz = town.centerZ - fz / 2;
		List<Integer> hs = new ArrayList<>();
		for (int x = ox; x < ox + fx; x++) {
			for (int z = oz; z < oz + fz; z++) {
				if (terrain.blocked(x, z)) return Optional.empty();
				hs.add(terrain.groundY(x, z));
			}
		}
		hs.sort(Integer::compare);
		int slope = hs.get(hs.size() - 1) - hs.get(0);
		if (slope > maxSlope) return Optional.empty();
		return Optional.of(new SitePlacer.Placement(ox, hs.get(hs.size() / 2), oz, 0, slope));
	}

	private record Scored(SitePlacer.Placement placement, int score) {
	}

	/** Cherche la meilleure place dans les îlots autour du centre : cercle d'îlots par cercle, serré et relié aux rues. */
	public static Optional<SitePlacer.Placement> find(Town town, BuildPlan plan, Theme theme, TerrainView terrain, int maxSlope) {
		Set<Long> own = new HashSet<>();
		for (BuildSite s : town.sites) {
			int[] f = s.footprint();
			for (int x = f[0]; x <= f[2]; x++) for (int z = f[1]; z <= f[3]; z++) own.add(RoadCell.key(x, z));
		}
		Map<Long, Boolean> routeCache = new HashMap<>();
		for (int ring = 1; ring <= MAX_RINGS; ring++) {
			Scored best = null;
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
					int bx0 = town.centerX + PITCH * i - HALF, bx1 = bx0 + 2 * HALF;
					int bz0 = town.centerZ + PITCH * j - HALF, bz1 = bz0 + 2 * HALF;
					for (int rot = 0; rot < 4; rot++) {
						int fx = plan.footprintX(rot), fz = plan.footprintZ(rot);
						if (fx > 2 * HALF + 1 || fz > 2 * HALF + 1) continue;
						switch (rot) {
							case 0, 2 -> {
								int oz = rot == 0 ? bz1 - fz + 1 : bz0; // porte au sud : mur sud contre la rue du sud ; au nord : l'inverse
								for (int ox = bx0; ox <= bx1 - fx + 1; ox++) best = better(best, evaluate(town, plan, theme, terrain, own, routeCache, ox, oz, rot, maxSlope));
							}
							default -> {
								int ox = rot == 1 ? bx0 : bx1 - fx + 1; // porte à l'ouest : mur ouest contre la rue de l'ouest ; à l'est : l'inverse
								for (int oz = bz0; oz <= bz1 - fz + 1; oz++) best = better(best, evaluate(town, plan, theme, terrain, own, routeCache, ox, oz, rot, maxSlope));
							}
						}
					}
				}
			}
			if (best != null) return Optional.of(best.placement());
		}
		return Optional.empty();
	}

	private static Scored better(Scored a, Scored b) {
		if (b == null) return a;
		if (a == null || b.score() < a.score()) return b;
		return a;
	}

	private static Scored evaluate(Town town, BuildPlan plan, Theme theme, TerrainView terrain, Set<Long> own, Map<Long, Boolean> routeCache,
			int ox, int oz, int rot, int maxSlope) {
		int fx = plan.footprintX(rot), fz = plan.footprintZ(rot);
		// contacts avec les bâtiments existants : seul un mur mitoyen (1 case d'épaisseur) est permis
		Integer sharedOy = null;
		for (BuildSite s : town.sites) {
			int[] g = s.footprint();
			int x0 = Math.max(ox, g[0]), x1 = Math.min(ox + fx - 1, g[2]);
			int z0 = Math.max(oz, g[1]), z1 = Math.min(oz + fz - 1, g[3]);
			if (x0 > x1 || z0 > z1) continue;
			if (x1 - x0 >= 1 && z1 - z0 >= 1) return null;
			if (sharedOy == null) sharedOy = s.oy;
			else if (sharedOy != s.oy) return null; // les voisins ne sont pas au même niveau
		}
		// terrain : seulement sur les cases qui ne sont pas déjà à un bâtiment voisin
		List<Integer> hs = new ArrayList<>();
		for (int x = ox; x < ox + fx; x++) {
			for (int z = oz; z < oz + fz; z++) {
				if (own.contains(RoadCell.key(x, z))) continue;
				if (terrain.blocked(x, z)) return null;
				hs.add(terrain.groundY(x, z));
			}
		}
		if (hs.isEmpty()) return null;
		hs.sort(Integer::compare);
		int oy, slope;
		if (sharedOy != null) {
			oy = sharedOy;
			slope = Math.max(Math.abs(hs.get(0) - oy), Math.abs(hs.get(hs.size() - 1) - oy));
		} else {
			oy = hs.get(hs.size() / 2);
			slope = hs.get(hs.size() - 1) - hs.get(0);
		}
		if (slope > maxSlope) return null;

		BuildSite cand = new BuildSite("candidat", plan, theme, ox, oy, oz, rot, 0);
		int[] front = cand.frontCell();
		long fk = RoadCell.key(front[0], front[1]);
		if (own.contains(fk) || terrain.blocked(front[0], front[1]) || !isStreet(town, front[0], front[1])) return null;
		// la porte d'un voisin ne doit pas être bouchée par ce bâtiment
		for (BuildSite s : town.sites) {
			int[] fr = s.frontCell();
			if (fr[0] >= ox && fr[0] < ox + fx && fr[1] >= oz && fr[1] < oz + fz) return null;
		}
		int shared = SharedWalls.compatibleOverlap(cand, town.sites);
		if (shared < 0) return null;
		// la rue de la porte doit rejoindre le réseau
		boolean onRoad = town.roads.containsKey(fk);
		if (!onRoad) {
			Boolean ok = routeCache.get(fk);
			if (ok == null) {
				ok = !RoadPlanner.route(town, terrain, front[0], front[1], ROUTE_RADIUS, (x, z) -> isStreet(town, x, z)).isEmpty();
				routeCache.put(fk, ok);
			}
			if (!ok) return null;
		}
		double cx = ox + fx / 2.0, cz = oz + fz / 2.0;
		double dist = Math.hypot(cx - town.centerX, cz - town.centerZ);
		int score = (int) (dist * 10) - shared - (onRoad ? 20 : 0) + slope * 20;
		return new Scored(new SitePlacer.Placement(ox, oy, oz, rot, slope), score);
	}
}
