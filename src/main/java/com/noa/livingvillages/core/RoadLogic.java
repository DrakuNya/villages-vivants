package com.noa.livingvillages.core;

import java.util.Map;

/** Cerveau du « cantonnier » : pose les chemins d'abord, puis les lampadaires (une torche chacun, prise dans son sac). */
public final class RoadLogic {
	public enum Kind {PATH, LAMP, FETCH, DONE}

	public record Action(Kind kind, RoadCell cell) {
	}

	public static final String TORCH = "minecraft:torch";

	private final ConversionPlanner planner;

	public RoadLogic(ConversionPlanner planner) {
		this.planner = planner;
	}

	/** Prochaine case à traiter (chemin d'abord, puis lampadaire), sans rien consommer. */
	public RoadCell peekTarget(Town town) {
		for (RoadCell c : town.roads.values()) if (!c.done) return c;
		for (RoadCell c : town.roads.values()) if (c.lamp && !c.lampPlaced) return c;
		return null;
	}

	public boolean needsTorch(Town town, Inventory carried) {
		RoadCell c = peekTarget(town);
		return c != null && c.done && c.lamp && !c.lampPlaced && !carried.has(TORCH, 1);
	}

	public Action next(Town town, Inventory carried) {
		for (RoadCell c : town.roads.values()) if (!c.done) return new Action(Kind.PATH, c);
		for (RoadCell c : town.roads.values()) {
			if (!c.lamp || c.lampPlaced) continue;
			if (carried.remove(TORCH, 1)) return new Action(Kind.LAMP, c);
			return new Action(Kind.FETCH, c);
		}
		return new Action(Kind.DONE, null);
	}

	/** Au dépôt : fabrique jusqu'à 4 torches et les prend. */
	public Logistics.FetchResult fetchTorches(Town town, Inventory carried) {
		int pending = 0;
		for (RoadCell c : town.roads.values()) if (c.lamp && !c.lampPlaced) pending++;
		return Logistics.fetch(town, planner, carried, Map.of(TORCH, Math.min(4, Math.max(1, pending))));
	}
}
