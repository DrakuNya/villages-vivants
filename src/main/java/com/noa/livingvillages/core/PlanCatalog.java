package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tous les plans connus. Chargé par le mod depuis les ressources, ou depuis des textes (tests). */
public final class PlanCatalog {
	private final Map<String, BuildPlan> plans = new LinkedHashMap<>();

	public static PlanCatalog fromTexts(Iterable<String> texts) {
		PlanCatalog c = new PlanCatalog();
		for (String t : texts) {
			BuildPlan p = PlanParser.parse(t);
			c.plans.put(p.name, p);
		}
		return c;
	}

	public BuildPlan get(String name) {
		return plans.get(name);
	}

	public List<BuildPlan> all() {
		return new ArrayList<>(plans.values());
	}

	/** Plans constructibles dans ce biome (pas d'igloo sans neige !). */
	public List<BuildPlan> eligible(String biome) {
		List<BuildPlan> out = new ArrayList<>();
		for (BuildPlan p : plans.values()) if (p.allowedIn(biome)) out.add(p);
		return out;
	}
}
