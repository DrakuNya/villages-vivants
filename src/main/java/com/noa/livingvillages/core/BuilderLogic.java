package com.noa.livingvillages.core;

import java.util.Map;
import java.util.TreeMap;

/**
 * Le « cerveau » du constructeur, sans Minecraft : à chaque tentative, dit quel bloc poser, ou ce qui manque.
 * Il transforme au besoin les objets de l'entrepôt (bûches -> planches -> portes...).
 */
public final class BuilderLogic {
	public enum Kind {PLACE, WAIT, DONE}

	public record Action(Kind kind, PlanBlock block, Map<String, Integer> missing) {
	}

	private final ConversionPlanner planner;

	public BuilderLogic(ConversionPlanner planner) {
		this.planner = planner;
	}

	/**
	 * Prépare le prochain bloc. Si PLACE : l'objet nécessaire a déjà été retiré de l'entrepôt ;
	 * le code Minecraft pose le bloc puis appelle site.advance() (ou rend l'objet avec refund() en cas d'échec).
	 */
	public Action next(Town town, BuildSite site) {
		var peek = site.peek();
		if (peek.isEmpty()) return new Action(Kind.DONE, null, Map.of());
		PlanBlock b = peek.get();
		String item = b.item();
		if (item == null) return new Action(Kind.PLACE, b, Map.of());
		if (town.stockpile.has(item, 1)) {
			town.stockpile.remove(item, 1);
			return new Action(Kind.PLACE, b, Map.of());
		}
		int batch = Math.max(1, site.currentLayerNeeds().getOrDefault(item, 1));
		ConversionPlanner.Result r = planner.plan(town.stockpile, item, batch);
		if (!r.complete() && batch > 1) r = planner.plan(town.stockpile, item, 1);
		if (r.complete()) {
			ConversionPlanner.apply(town.stockpile, r.steps());
			if (town.stockpile.remove(item, 1)) return new Action(Kind.PLACE, b, Map.of());
		}
		return new Action(Kind.WAIT, b, new TreeMap<>(r.missing()));
	}

	public void refund(Town town, PlanBlock b) {
		if (b.item() != null) town.stockpile.add(b.item(), 1);
	}
}
