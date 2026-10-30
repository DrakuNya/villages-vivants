package com.noa.livingvillages.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Une transformation d'objets (« craft » interne d'un villageois). */
public record Recipe(String name, Map<String, Integer> inputs, Map<String, Integer> outputs) {

	/** Recipe.of("planches", "minecraft:oak_planks", 4, "minecraft:oak_log", 1) */
	public static Recipe of(String name, String out, int outCount, Object... inputs) {
		Map<String, Integer> in = new LinkedHashMap<>();
		for (int i = 0; i < inputs.length; i += 2) {
			in.merge((String) inputs[i], (Integer) inputs[i + 1], Integer::sum);
		}
		Map<String, Integer> o = new LinkedHashMap<>();
		o.put(out, outCount);
		return new Recipe(name, in, o);
	}

	public int outputOf(String item) {
		return outputs.getOrDefault(item, 0);
	}
}
