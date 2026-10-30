package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Une ville : un maire, des habitants avec métiers, des chantiers, un entrepôt commun, un tableau de besoins, un marché. */
public final class Town {
	public final String id;
	public String name;
	public String mayorId;
	public int centerX, centerY, centerZ;
	public final Map<String, Role> members = new LinkedHashMap<>();
	public final List<BuildSite> sites = new ArrayList<>();
	public final Stock stockpile = new Stock();
	public final TownBoard board = new TownBoard();
	public final Market market = new Market();
	private int nextSite = 1;

	public Town(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public String newSiteId() {
		return id + "-c" + (nextSite++);
	}

	void bumpSiteCounter(int minNext) {
		nextSite = Math.max(nextSite, minNext);
	}

	public BuildSite site(String siteId) {
		for (BuildSite s : sites) if (s.id.equals(siteId)) return s;
		return null;
	}

	public List<String> membersWith(Role r) {
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Role> e : members.entrySet()) if (e.getValue() == r) out.add(e.getKey());
		return out;
	}

	public BuildSite activeSite() {
		for (BuildSite s : sites) if (!s.complete()) return s;
		return null;
	}

	/**
	 * Met à jour les besoins : pour chaque chantier actif, regarde l'étage en cours, calcule ce qu'on peut fabriquer
	 * avec l'entrepôt, et publie sur le tableau les matières premières qui manquent vraiment.
	 */
	public void syncBoard(ConversionPlanner planner) {
		for (BuildSite s : sites) {
			if (s.complete()) {
				board.removeSite(s.id);
				continue;
			}
			ConversionPlanner.Result r = planner.planAll(stockpile, s.currentLayerNeeds());
			board.sync(s.id, r.missing());
		}
	}
}
