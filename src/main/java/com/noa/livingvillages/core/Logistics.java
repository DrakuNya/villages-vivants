package com.noa.livingvillages.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Les allers-retours au dépôt : un villageois y fabrique ce qu'il lui faut avec l'entrepôt de la ville et repart avec dans son sac. */
public final class Logistics {
	private Logistics() {
	}

	public record FetchResult(Map<String, Integer> moved, Map<String, Integer> missing) {
		public boolean movedAnything() {
			return !moved.isEmpty();
		}
	}

	/**
	 * Au dépôt : pour chaque objet souhaité (quantité totale voulue dans le sac), fabrique à partir de l'entrepôt
	 * puis déplace dans l'inventaire, dans la limite de la place. Si tout n'est pas disponible, prend ce qu'on peut.
	 */
	public static FetchResult fetch(Town town, ConversionPlanner planner, Inventory carried, Map<String, Integer> want) {
		Map<String, Integer> moved = new LinkedHashMap<>();
		Map<String, Integer> missing = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> w : want.entrySet()) {
			String item = w.getKey();
			int need = Math.min(w.getValue() - carried.count(item), carried.room(item));
			if (need <= 0) continue;
			ConversionPlanner.Result full = planner.plan(town.stockpile, item, need);
			int k = need;
			ConversionPlanner.Result chosen = full;
			if (!full.complete()) {
				for (Map.Entry<String, Integer> m : full.missing().entrySet()) missing.merge(m.getKey(), m.getValue(), Integer::sum);
				// pas assez pour tout : on cherche la plus grande quantité réalisable (plus c'est demandé, plus c'est dur)
				int lo = 0, hi = need - 1;
				while (lo < hi) {
					int mid = (lo + hi + 1) / 2;
					if (planner.plan(town.stockpile, item, mid).complete()) lo = mid;
					else hi = mid - 1;
				}
				k = lo;
				chosen = k > 0 ? planner.plan(town.stockpile, item, k) : null;
			}
			if (chosen != null && k > 0) {
				ConversionPlanner.apply(town.stockpile, chosen.steps());
				if (town.stockpile.remove(item, k)) {
					carried.add(item, k);
					moved.merge(item, k, Integer::sum);
				}
			}
		}
		return new FetchResult(moved, missing);
	}
}
