package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recrutement automatique : répartit les villageois sans métier selon les besoins de la ville (pas question de donner un outil à chacun dans une grande ville). */
public final class Recruiter {
	private Recruiter() {
	}

	/** Effectifs visés pour une population donnée : constructeurs, bûcherons, mineurs, bergers (les métiers déjà animés). */
	public static Map<Role, Integer> targets(int population) {
		Map<Role, Integer> t = new EnumMap<>(Role.class);
		t.put(Role.BUILDER, Math.max(2, ceilDiv(population, 8)));
		t.put(Role.LUMBERJACK, Math.max(1, ceilDiv(population, 10)));
		t.put(Role.MINER, Math.max(1, ceilDiv(population, 10)));
		t.put(Role.SHEPHERD, population >= 12 ? ceilDiv(population, 25) : 0);
		return t;
	}

	private static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}

	/** Donne un métier à chaque villageois libre tant qu'il manque du monde ; les autres restent des habitants ordinaires. */
	public static Map<String, Role> assign(Town town, List<String> unemployed, int population) {
		Map<Role, Integer> target = targets(Math.max(population, town.members.size() + unemployed.size()));
		Map<Role, Integer> have = new EnumMap<>(Role.class);
		for (Role r : town.members.values()) have.merge(r, 1, Integer::sum);
		Map<String, Role> out = new LinkedHashMap<>();
		List<String> sorted = new ArrayList<>(unemployed);
		sorted.sort(String::compareTo);
		for (String id : sorted) {
			Role best = null;
			int bestDeficit = 0;
			for (Map.Entry<Role, Integer> e : target.entrySet()) {
				int deficit = e.getValue() - have.getOrDefault(e.getKey(), 0);
				if (deficit > bestDeficit) {
					bestDeficit = deficit;
					best = e.getKey();
				}
			}
			if (best == null) break;
			out.put(id, best);
			have.merge(best, 1, Integer::sum);
		}
		return out;
	}
}
