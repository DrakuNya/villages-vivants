package com.noa.livingvillages.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Récompenses de travail aléatoires : chaque fois qu'un villageois travaille, il peut trouver quelque chose en plus, selon son métier.
 * Ça évite que la ville tombe en panne quand une ressource se raréfie autour d'elle (le charbon des torches, les émeraudes...).
 */
public final class WorkRewards {
	public record Reward(String item, int min, int max, double chance) {
	}

	private static final Map<Role, List<Reward>> TABLE = new EnumMap<>(Role.class);

	static {
		TABLE.put(Role.MINER, List.of(
				new Reward("minecraft:coal", 1, 2, 0.12),
				new Reward("minecraft:iron_ingot", 1, 1, 0.04),
				new Reward("minecraft:clay_ball", 1, 2, 0.05),
				new Reward("minecraft:emerald", 1, 1, 0.015)));
		TABLE.put(Role.LUMBERJACK, List.of(
				new Reward("minecraft:stick", 1, 3, 0.25),
				new Reward("minecraft:apple", 1, 1, 0.07),
				new Reward("minecraft:oak_sapling", 1, 1, 0.10)));
		TABLE.put(Role.SHEPHERD, List.of(
				new Reward("minecraft:string", 1, 2, 0.12),
				new Reward("minecraft:mutton", 1, 2, 0.08)));
		TABLE.put(Role.FARMER, List.of(
				new Reward("minecraft:wheat_seeds", 1, 3, 0.30),
				new Reward("minecraft:carrot", 1, 2, 0.08),
				new Reward("minecraft:potato", 1, 2, 0.08),
				new Reward("minecraft:emerald", 1, 1, 0.005)));
		TABLE.put(Role.BREEDER, List.of(
				new Reward("minecraft:leather", 1, 1, 0.12),
				new Reward("minecraft:beef", 1, 2, 0.08),
				new Reward("minecraft:egg", 1, 2, 0.15),
				new Reward("minecraft:feather", 1, 2, 0.12),
				new Reward("minecraft:emerald", 1, 1, 0.005)));
		TABLE.put(Role.COOK, List.of(
				new Reward("minecraft:bread", 1, 2, 0.20),
				new Reward("minecraft:cooked_beef", 1, 1, 0.08)));
		TABLE.put(Role.FISHER, List.of(
				new Reward("minecraft:cod", 1, 2, 0.35),
				new Reward("minecraft:salmon", 1, 1, 0.12),
				new Reward("minecraft:emerald", 1, 1, 0.01)));
		TABLE.put(Role.MERCHANT, List.of(
				new Reward("minecraft:emerald", 1, 2, 0.10)));
		TABLE.put(Role.GUARD, List.of(
				new Reward("minecraft:arrow", 1, 3, 0.20),
				new Reward("minecraft:emerald", 1, 1, 0.01)));
	}

	private WorkRewards() {
	}

	public static List<Reward> table(Role role) {
		return TABLE.getOrDefault(role, List.of());
	}

	/** Ce métier peut-il trouver cet objet en travaillant ? */
	public static boolean canFind(Role role, String item) {
		for (Reward r : table(role)) if (r.item().equals(item)) return true;
		return false;
	}

	/**
	 * Un tirage pour une action de travail. 'luck' multiplie toutes les chances ; si 'wanted' est un objet que ce métier peut
	 * trouver (fouille ciblée), sa chance est multipliée par 'boost' en plus.
	 */
	public static Map<String, Integer> roll(Role role, String wanted, double luck, double boost, Random rng) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Reward r : table(role)) {
			double p = r.chance() * luck;
			if (r.item().equals(wanted)) p *= boost;
			if (rng.nextDouble() < Math.min(p, 0.95)) {
				int n = r.min() + (r.max() > r.min() ? rng.nextInt(r.max() - r.min() + 1) : 0);
				out.merge(r.item(), n, Integer::sum);
			}
		}
		return out;
	}

	public static Map<String, Integer> roll(Role role, Random rng) {
		return roll(role, null, 1.0, 1.0, rng);
	}
}
