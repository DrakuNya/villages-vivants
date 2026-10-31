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
	/** Le maire décide-t-il seul des constructions ? */
	public boolean auto = true;
	/** Chemins de terre de la ville (clé = coordonnées). */
	public final Map<Long, RoadCell> roads = new LinkedHashMap<>();
	public static final int MAX_QUEUED_SITES = 2;
	/** Plafond de bâtiments par ville (évite une construction sans fin). */
	public static final int MAX_SITES = 40;
	public static final int MAX_BUILDERS_PER_SITE = 3;
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
		List<BuildSite> a = activeSites();
		return a.isEmpty() ? null : a.get(0);
	}

	/** Chantiers non terminés, les plus urgents d'abord. */
	public List<BuildSite> activeSites() {
		List<BuildSite> out = new ArrayList<>();
		for (BuildSite s : sites) if (!s.complete()) out.add(s);
		out.sort(java.util.Comparator.<BuildSite>comparingInt(s -> s.priority).thenComparing(s -> s.id));
		return out;
	}

	/** Répartit les constructeurs : chacun va sur le chantier le plus urgent qui n'est pas déjà complet en ouvriers. */
	public Map<String, BuildSite> assignBuilders() {
		Map<String, BuildSite> out = new LinkedHashMap<>();
		Map<String, Integer> load = new java.util.HashMap<>();
		List<BuildSite> active = new ArrayList<>();
		for (BuildSite s : activeSites()) if (s.prepDone) active.add(s);
		for (String b : membersWith(Role.BUILDER)) {
			BuildSite pick = null;
			for (BuildSite s : active) {
				int l = load.getOrDefault(s.id, 0);
				if (l >= MAX_BUILDERS_PER_SITE) continue;
				if (pick == null || l < load.getOrDefault(pick.id, 0)) pick = s;
			}
			if (pick != null) {
				out.put(b, pick);
				load.merge(pick.id, 1, Integer::sum);
			}
		}
		return out;
	}

	public int pendingRoadCells() {
		int n = 0;
		for (RoadCell c : roads.values()) if (!c.done || (c.lamp && !c.lampPlaced)) n++;
		return n;
	}

	/** Torches nécessaires pour les prochains lampadaires. */
	public Map<String, Integer> roadNeeds() {
		int lamps = 0;
		for (RoadCell c : roads.values()) if (c.done && c.lamp && !c.lampPlaced && lamps < 2) lamps++;
		Map<String, Integer> m = new LinkedHashMap<>();
		if (lamps > 0) m.put("minecraft:torch", lamps);
		return m;
	}

	/**
	 * Met à jour les besoins : pour chaque chantier actif, regarde l'étage en cours, calcule ce qu'on peut fabriquer
	 * avec l'entrepôt, et publie sur le tableau les matières premières qui manquent vraiment.
	 */
	public void syncBoard(ConversionPlanner planner) {
		int served = 0;
		for (BuildSite s : activeSites()) {
			// seuls les deux chantiers les plus urgents (et déjà terrassés) réclament des matériaux
			if (!s.prepDone || served >= MAX_QUEUED_SITES) {
				board.removeSite(s.id);
				continue;
			}
			served++;
			ConversionPlanner.Result r = planner.planAll(stockpile, s.currentLayerNeeds());
			board.sync(s.id, r.missing());
		}
		for (BuildSite s : sites) if (s.complete()) board.removeSite(s.id);
		Map<String, Integer> rn = roadNeeds();
		if (rn.isEmpty()) board.removeSite(id + "-routes");
		else board.sync(id + "-routes", planner.planAll(stockpile, rn).missing());
	}
}
