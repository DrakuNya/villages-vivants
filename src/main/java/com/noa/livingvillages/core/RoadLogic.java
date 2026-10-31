package com.noa.livingvillages.core;

import java.util.Map;
import java.util.TreeMap;

/** Cerveau du « cantonnier » : pose les chemins d'abord, puis les lampadaires (une torche chacun, fabriquée au besoin). */
public final class RoadLogic {
	public enum Kind {PATH, LAMP, WAIT, DONE}

	public record Action(Kind kind, RoadCell cell, Map<String, Integer> missing) {
	}

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

	public Action next(Town town) {
		for (RoadCell c : town.roads.values()) if (!c.done) return new Action(Kind.PATH, c, Map.of());
		for (RoadCell c : town.roads.values()) {
			if (!c.lamp || c.lampPlaced) continue;
			String torch = "minecraft:torch";
			if (town.stockpile.has(torch, 1)) {
				town.stockpile.remove(torch, 1);
				return new Action(Kind.LAMP, c, Map.of());
			}
			ConversionPlanner.Result r = planner.plan(town.stockpile, torch, 1);
			if (r.complete()) {
				ConversionPlanner.apply(town.stockpile, r.steps());
				town.stockpile.remove(torch, 1);
				return new Action(Kind.LAMP, c, Map.of());
			}
			return new Action(Kind.WAIT, c, new TreeMap<>(r.missing()));
		}
		return new Action(Kind.DONE, null, Map.of());
	}
}
