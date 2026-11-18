package com.noa.livingvillages.core;

import java.util.List;
import java.util.Optional;

/** Le maire autonome : décide quoi construire, où, trace le chemin, et crée le chantier. Sans Minecraft : testable. */
public final class MayorBrain {
	private MayorBrain() {
	}

	public record Created(BuildSite site, String reason, int roadCells, Placement where) {
	}

	public record Placement(int ox, int oy, int oz, int rotation) {
	}

	public static final int MAX_SLOPE = 3;
	public static final int ROAD_RADIUS = 110;

	/**
	 * Le bâtiment central doit avoir de la place : si le centre est déjà occupé (cloche, puits ou maisons d'un village existant,
	 * eau, forte pente), on décale le centre de la ville par pas de 3 blocs, au plus près, jusqu'à trouver un terrain libre.
	 */
	static Optional<SitePlacer.Placement> findCenterOrRelocate(Town town, BuildPlan plan, TerrainView terrain) {
		Optional<SitePlacer.Placement> r = DenseLayout.findCenter(town, plan, terrain, MAX_SLOPE);
		if (r.isPresent() || !town.sites.isEmpty()) return r;
		int cx = town.centerX, cz = town.centerZ;
		for (int rad = 3; rad <= 36; rad += 3) {
			for (int dx = -rad; dx <= rad; dx += 3) {
				for (int dz = -rad; dz <= rad; dz += 3) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) continue;
					town.centerX = cx + dx;
					town.centerZ = cz + dz;
					r = DenseLayout.findCenter(town, plan, terrain, MAX_SLOPE);
					if (r.isPresent()) return r;
				}
			}
		}
		town.centerX = cx;
		town.centerZ = cz;
		return Optional.empty();
	}

	public static Optional<Created> think(Town town, PlanCatalog catalog, TerrainView terrain, String biome, int population) {
		return think(town, catalog, terrain, biome, population, false);
	}

	/** force = vrai : décide même si le mode automatique est coupé (commande /lv decide). */
	public static Optional<Created> think(Town town, PlanCatalog catalog, TerrainView terrain, String biome, int population, boolean force) {
		if ((!town.auto && !force) || town.mayorId == null) return Optional.empty();
		List<MayorPlanner.Decision> options = MayorPlanner.candidates(town, catalog, biome, population);
		int tried = 0;
		for (MayorPlanner.Decision d : options) {
			if (tried++ >= 4) break;
			Theme theme = Theme.fromBiome(biome);
			boolean center = d.plan().meta.category().equals("center");
			Optional<SitePlacer.Placement> p;
			if (town.dense) p = center ? findCenterOrRelocate(town, d.plan(), terrain) : DenseLayout.find(town, d.plan(), theme, terrain, MAX_SLOPE);
			else p = SitePlacer.find(town, d.plan(), terrain, MAX_SLOPE);
			if (p.isEmpty()) continue;
			SitePlacer.Placement pl = p.get();
			BuildSite site = new BuildSite(town.newSiteId(), d.plan(), theme, pl.ox(), pl.oy(), pl.oz(), pl.rotation(), 0);
			site.priority = d.plan().meta.priority();
			site.prepDone = false;
			town.sites.add(site);
			int cells = 0;
			if (town.dense) {
				SharedWalls.apply(town, site);
				if (center) cells += RoadPlanner.addRing(town, terrain);
				int[] frontD = site.frontCell();
				cells += RoadPlanner.addRoad(town, RoadPlanner.route(town, terrain, frontD[0], frontD[1], ROAD_RADIUS, (x, z) -> DenseLayout.isStreet(town, x, z)));
				return Optional.of(new Created(site, d.reason(), cells, new Placement(pl.ox(), pl.oy(), pl.oz(), pl.rotation())));
			}
			int[] front = site.frontCell();
			cells = RoadPlanner.addRoad(town, RoadPlanner.route(town, terrain, front[0], front[1], ROAD_RADIUS));
			return Optional.of(new Created(site, d.reason(), cells, new Placement(pl.ox(), pl.oy(), pl.oz(), pl.rotation())));
		}
		return Optional.empty();
	}
}
