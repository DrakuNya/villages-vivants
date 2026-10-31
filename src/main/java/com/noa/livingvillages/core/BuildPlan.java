package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un plan de bâtiment, tel qu'écrit dans un fichier .plan. Les blocs sont encore « génériques » (jokers). */
public final class BuildPlan {
	public record Template(int dx, int dy, int dz, String blockSpec, String itemSpec) {
	}

	/** Informations pour le maire : à quoi sert ce bâtiment, combien de fois, à partir de quelle population... */
	public record Meta(String category, int capacity, List<String> serves, int max, int minPopulation, int priority) {
		public static final Meta DEFAULT = new Meta("decor", 0, List.of(), 99, 0, 50);
	}

	public final Meta meta;
	public final String name;
	public final String title;
	/** Fragments de noms de biome ; vide = partout. */
	public final List<String> requires;
	public final int sizeX, sizeY, sizeZ;
	private final List<Template> templates;

	public BuildPlan(String name, String title, List<String> requires, int sizeX, int sizeY, int sizeZ, List<Template> templates) {
		this(name, title, requires, sizeX, sizeY, sizeZ, templates, Meta.DEFAULT);
	}

	public BuildPlan(String name, String title, List<String> requires, int sizeX, int sizeY, int sizeZ, List<Template> templates, Meta meta) {
		this.meta = meta;
		this.name = name;
		this.title = title;
		this.requires = List.copyOf(requires);
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.templates = List.copyOf(templates);
	}

	public boolean allowedIn(String biome) {
		if (requires.isEmpty()) return true;
		String b = biome == null ? "" : biome.toLowerCase();
		for (String r : requires) if (b.contains(r)) return true;
		return false;
	}

	/** Les éléments posés en dernier dans leur étage (ils s'accrochent à d'autres blocs). */
	private static boolean late(String id) {
		return id.contains("torch") || id.contains("ladder") || id.contains("sign") || id.contains("lantern")
				|| id.contains("button") || id.contains("carpet") || id.contains("pressure_plate")
				|| id.contains("wheat") || id.contains("carrots") || id.contains("potatoes") || id.contains("beetroots");
	}

	/** Blocs concrets pour un thème et une rotation, dans l'ordre de construction : étage par étage. */
	public List<PlanBlock> blocks(Theme theme, int quarterTurns) {
		boolean odd = quarterTurns % 2 != 0;
		List<PlanBlock> out = new ArrayList<>();
		for (Template t : templates) {
			int[] r = Rotation.rotateXZ(t.dx(), t.dz(), quarterTurns, sizeX, sizeZ);
			String block = Rotation.rotateState(theme.resolve(t.blockSpec()), quarterTurns);
			String item = t.itemSpec() == null ? null : theme.resolve(t.itemSpec());
			out.add(new PlanBlock(r[0], t.dy(), r[1], block, item));
		}
		out.sort(Comparator.<PlanBlock>comparingInt(PlanBlock::dy)
				.thenComparingInt(b -> late(b.blockId()) ? 1 : 0)
				.thenComparingInt(PlanBlock::dz)
				.thenComparingInt(PlanBlock::dx));
		return out;
	}

	public int footprintX(int quarterTurns) {
		return quarterTurns % 2 != 0 ? sizeZ : sizeX;
	}

	public int footprintZ(int quarterTurns) {
		return quarterTurns % 2 != 0 ? sizeX : sizeZ;
	}

	/** Besoins totaux en objets pour un thème donné. */
	public Map<String, Integer> totalNeeds(Theme theme) {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (PlanBlock b : blocks(theme, 0)) if (b.item() != null) m.merge(b.item(), 1, Integer::sum);
		return m;
	}
}
