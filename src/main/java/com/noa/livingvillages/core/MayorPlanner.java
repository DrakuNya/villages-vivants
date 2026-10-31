package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** La « tête » du maire : regarde l'état de la ville et dresse la liste de ce qu'il faudrait construire, dans l'ordre. */
public final class MayorPlanner {
	private MayorPlanner() {
	}

	public record Decision(BuildPlan plan, String reason) {
	}

	/** Les décisions possibles, de la plus urgente à la moins urgente. Vide si la file de chantiers est déjà pleine. */
	public static List<Decision> candidates(Town town, PlanCatalog catalog, String biome, int population) {
		List<Decision> out = new ArrayList<>();
		int pop = Math.max(population, town.members.size());
		int queued = 0;
		for (BuildSite s : town.sites) if (!s.complete()) queued++;
		if (queued >= Town.MAX_QUEUED_SITES || town.sites.size() >= Town.MAX_SITES) return out;

		Map<String, Integer> count = new HashMap<>();
		int housing = 0, farming = 0, defense = 0;
		for (BuildSite s : town.sites) {
			count.merge(s.plan.name, 1, Integer::sum);
			switch (s.plan.meta.category()) {
				case "house" -> housing += s.plan.meta.capacity();
				case "farm" -> farming += s.plan.meta.capacity();
				case "defense" -> defense += s.plan.meta.capacity();
				default -> {
				}
			}
		}
		Set<String> roles = new HashSet<>();
		for (Role r : town.members.values()) roles.add(r.id);

		List<BuildPlan> eligible = new ArrayList<>();
		for (BuildPlan p : catalog.eligible(biome)) {
			if (p.meta.minPopulation() <= pop && count.getOrDefault(p.name, 0) < p.meta.max()) eligible.add(p);
		}
		eligible.sort(Comparator.<BuildPlan>comparingInt(p -> p.meta.priority()).thenComparing(p -> p.name));

		// 1) un puits au centre
		for (BuildPlan p : eligible) if (p.meta.category().equals("well") && !hasCategory(town, "well")) out.add(new Decision(p, "le village n'a pas de puits"));
		// 2) des logements
		if (housing < pop) {
			BuildPlan h = leastUsed(eligible, "house", count);
			if (h != null) out.add(new Decision(h, "pas assez de lits (" + housing + " pour " + pop + " villageois)"));
		}
		// 3) de la nourriture
		if (farming < pop) {
			BuildPlan f = leastUsed(eligible, "farm", count);
			if (f != null) out.add(new Decision(f, "pas assez de nourriture (" + farming + " pour " + pop + " villageois)"));
		}
		// 4) l'atelier d'un métier présent
		for (BuildPlan p : eligible) {
			for (String s : p.meta.serves()) {
				if (roles.contains(s) && !p.meta.category().equals("farm") && !p.meta.category().equals("defense")) {
					out.add(new Decision(p, "un " + roleName(s) + " a besoin de son lieu de travail"));
				}
			}
		}
		// 5) défense
		if (pop >= 4 && defense < pop) {
			BuildPlan d = leastUsed(eligible, "defense", count);
			if (d != null) out.add(new Decision(d, "pas assez de défense (" + defense + " pour " + pop + " villageois)"));
		}
		// 6) le reste, par priorité (entrepôt, marché, forge...)
		for (BuildPlan p : eligible) {
			String cat = p.meta.category();
			if (cat.equals("house") || cat.equals("farm") || cat.equals("defense") || cat.equals("well")) continue;
			if (!p.meta.serves().isEmpty()) continue;
			out.add(new Decision(p, "le village grandit : " + p.title.toLowerCase()));
		}
		// pas de doublons
		List<Decision> unique = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Decision d : out) if (seen.add(d.plan().name)) unique.add(d);
		return unique;
	}

	private static String roleName(String roleId) {
		Role r = Role.byId(roleId);
		return r == null ? roleId : r.displayName.toLowerCase();
	}

	private static boolean hasCategory(Town t, String cat) {
		for (BuildSite s : t.sites) if (s.plan.meta.category().equals(cat)) return true;
		return false;
	}

	/** Parmi les plans d'une catégorie, celui qu'on a le moins construit (pour varier), puis le plus prioritaire. */
	private static BuildPlan leastUsed(List<BuildPlan> eligible, String category, Map<String, Integer> count) {
		BuildPlan best = null;
		for (BuildPlan p : eligible) {
			if (!p.meta.category().equals(category)) continue;
			if (best == null || count.getOrDefault(p.name, 0) < count.getOrDefault(best.name, 0)) best = p;
		}
		return best;
	}
}
