package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Calcule comment obtenir N objets à partir d'un inventaire : quelles transformations faire, dans quel ordre,
 * et ce qui manque réellement (matières premières). Ex : 2 bûches -> 8 planches -> 3 portes (6 planches, 2 restent).
 */
public final class ConversionPlanner {
	private static final int MAX_DEPTH = 6;

	public record Step(Recipe recipe, int times) {
	}

	public record Result(List<Step> steps, Map<String, Integer> missing, Stock leftover) {
		public boolean complete() {
			return missing.isEmpty();
		}
	}

	private static final class State {
		Stock stock;
		List<Step> steps = new ArrayList<>();
		Map<String, Integer> missing = new TreeMap<>();

		State(Stock s) {
			stock = s;
		}

		State copy() {
			State c = new State(stock.copy());
			c.steps.addAll(steps);
			c.missing.putAll(missing);
			return c;
		}

		void assign(State o) {
			stock = o.stock;
			steps = o.steps;
			missing = o.missing;
		}
	}

	private final RecipeBook book;

	public ConversionPlanner(RecipeBook book) {
		this.book = book;
	}

	/** Planifie l'obtention de 'count' x 'item'. N'altère pas le stock donné (travaille sur une copie). */
	public Result plan(Stock stock, String item, int count) {
		State s = new State(stock.copy());
		satisfy(s, item, count, 0, new HashSet<>());
		// l'objet demandé est « consommé » par la demande ; ce qui reste est le surplus
		return new Result(s.steps, s.missing, s.stock);
	}

	/** Planifie plusieurs besoins à la suite (ex : tous les objets d'un plan de construction). */
	public Result planAll(Stock stock, Map<String, Integer> needs) {
		State s = new State(stock.copy());
		for (Map.Entry<String, Integer> e : needs.entrySet()) {
			satisfy(s, e.getKey(), e.getValue(), 0, new HashSet<>());
		}
		return new Result(s.steps, s.missing, s.stock);
	}

	/** Exécute pour de bon les transformations sur un stock (retire les ingrédients, ajoute les produits). */
	public static void apply(Stock stock, List<Step> steps) {
		for (Step st : steps) {
			for (Map.Entry<String, Integer> in : st.recipe().inputs().entrySet()) stock.remove(in.getKey(), in.getValue() * st.times());
			for (Map.Entry<String, Integer> out : st.recipe().outputs().entrySet()) stock.add(out.getKey(), out.getValue() * st.times());
		}
	}

	private boolean satisfy(State s, String item, int n, int depth, Set<String> visiting) {
		int take = Math.min(s.stock.count(item), n);
		s.stock.remove(item, take);
		n -= take;
		if (n == 0) return true;
		if (depth >= MAX_DEPTH || visiting.contains(item)) {
			s.missing.merge(item, n, Integer::sum);
			return false;
		}
		List<Recipe> options = book.forProducing(item);
		if (options.isEmpty()) {
			s.missing.merge(item, n, Integer::sum);
			return false;
		}
		visiting.add(item);
		State firstFailure = null;
		for (Recipe r : options) {
			State t = s.copy();
			int perCraft = r.outputOf(item);
			int crafts = (n + perCraft - 1) / perCraft;
			boolean ok = true;
			for (Map.Entry<String, Integer> in : r.inputs().entrySet()) {
				if (!satisfy(t, in.getKey(), in.getValue() * crafts, depth + 1, visiting)) ok = false;
			}
			if (ok) {
				t.steps.add(new Step(r, crafts));
				for (Map.Entry<String, Integer> out : r.outputs().entrySet()) {
					int produced = out.getValue() * crafts;
					int extra = out.getKey().equals(item) ? produced - n : produced;
					t.stock.add(out.getKey(), extra);
				}
				s.assign(t);
				visiting.remove(item);
				return true;
			}
			if (firstFailure == null) firstFailure = t;
		}
		visiting.remove(item);
		// aucune recette réalisable : si un métier sait produire l'objet directement, on le demande tel quel
		if (Role.anyProduces(item)) {
			s.missing.merge(item, n, Integer::sum);
			return false;
		}
		// sinon on garde le rapport de la première recette (ce qui manque vraiment)
		s.missing.clear();
		s.missing.putAll(firstFailure.missing);
		return false;
	}
}
