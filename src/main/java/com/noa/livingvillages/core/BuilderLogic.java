package com.noa.livingvillages.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Le « cerveau » du constructeur, sans Minecraft. Il construit avec ce qu'il porte dans son sac ;
 * quand il lui manque l'objet du prochain bloc, il doit faire un tour au dépôt (fetch) où il fabrique et recharge son sac.
 */
public final class BuilderLogic {
	public enum Kind {PLACE, FETCH, DONE}

	public record Action(Kind kind, PlanBlock block) {
	}

	private static final int LOOKAHEAD_BLOCKS = 256;

	private final ConversionPlanner planner;

	public BuilderLogic(ConversionPlanner planner) {
		this.planner = planner;
	}

	/** Le sac contient-il l'objet du prochain bloc ? Sinon il faut aller au dépôt. */
	public boolean needsFetch(BuildSite site, Inventory carried) {
		var peek = site.peek();
		if (peek.isEmpty()) return false;
		String item = peek.get().item();
		return item != null && !carried.has(item, 1);
	}

	/**
	 * Prépare le prochain bloc. PLACE : l'objet a été retiré du sac ; le code Minecraft pose le bloc puis appelle site.advance()
	 * (ou refund() en cas d'échec). FETCH : il faut aller au dépôt.
	 */
	public Action next(BuildSite site, Inventory carried) {
		var peek = site.peek();
		if (peek.isEmpty()) return new Action(Kind.DONE, null);
		PlanBlock b = peek.get();
		if (b.item() == null) return new Action(Kind.PLACE, b);
		if (carried.remove(b.item(), 1)) return new Action(Kind.PLACE, b);
		return new Action(Kind.FETCH, b);
	}

	/** Ce qu'il faudrait avoir dans le sac pour enchaîner le plus de blocs possible (limité par la place). */
	public Map<String, Integer> wantList(BuildSite site) {
		Inventory sim = new Inventory();
		Map<String, Integer> want = new LinkedHashMap<>();
		for (PlanBlock b : site.upcoming(LOOKAHEAD_BLOCKS)) {
			if (b.item() == null) continue;
			if (sim.add(b.item(), 1) > 0) break;
			want.merge(b.item(), 1, Integer::sum);
		}
		return want;
	}

	/** Au dépôt : fabrique et recharge le sac. */
	public Logistics.FetchResult fetch(Town town, BuildSite site, Inventory carried) {
		return Logistics.fetch(town, planner, carried, wantList(site));
	}

	public void refund(Inventory carried, PlanBlock b) {
		if (b.item() != null) carried.add(b.item(), 1);
	}
}
