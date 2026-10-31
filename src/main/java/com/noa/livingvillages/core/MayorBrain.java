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
	public static final int ROAD_RADIUS = 60;

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
			Optional<SitePlacer.Placement> p = SitePlacer.find(town, d.plan(), terrain, MAX_SLOPE);
			if (p.isEmpty()) continue;
			SitePlacer.Placement pl = p.get();
			BuildSite site = new BuildSite(town.newSiteId(), d.plan(), Theme.fromBiome(biome), pl.ox(), pl.oy(), pl.oz(), pl.rotation(), 0);
			site.priority = d.plan().meta.priority();
			site.prepDone = false;
			town.sites.add(site);
			int[] front = site.frontCell();
			int cells = RoadPlanner.addRoad(town, RoadPlanner.route(town, terrain, front[0], front[1], ROAD_RADIUS));
			return Optional.of(new Created(site, d.reason(), cells, new Placement(pl.ox(), pl.oy(), pl.oz(), pl.rotation())));
		}
		return Optional.empty();
	}
}
