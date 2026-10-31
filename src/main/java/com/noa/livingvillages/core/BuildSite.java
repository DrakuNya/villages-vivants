package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Un chantier : un plan posé quelque part dans le monde, avec sa progression (bloc par bloc, étage par étage). */
public final class BuildSite {
	public record ItemStatus(String item, int have, int need) {
		public boolean enough() {
			return have >= need;
		}
	}

	public final String id;
	public final BuildPlan plan;
	public final Theme theme;
	public final int ox, oy, oz;
	public final int rotation;
	private final List<PlanBlock> blocks;
	private int cursor;
	/** Plus petit = plus urgent. */
	public int priority = 50;
	/** Le terrain a-t-il été aplani et dégagé ? Le constructeur n'attaque le plan qu'après. */
	public boolean prepDone = true;
	/** Le chemin vers la place a-t-il déjà été tracé ? */
	public boolean roadPlanned = true;

	public BuildSite(String id, BuildPlan plan, Theme theme, int ox, int oy, int oz, int rotation, int cursor) {
		this.id = id;
		this.plan = plan;
		this.theme = theme;
		this.ox = ox;
		this.oy = oy;
		this.oz = oz;
		this.rotation = ((rotation % 4) + 4) % 4;
		this.blocks = plan.blocks(theme, this.rotation);
		this.cursor = Math.max(0, Math.min(cursor, blocks.size()));
	}

	public int total() {
		return blocks.size();
	}

	public int done() {
		return cursor;
	}

	public boolean complete() {
		return cursor >= blocks.size();
	}

	public int cursor() {
		return cursor;
	}

	public Optional<PlanBlock> peek() {
		return complete() ? Optional.empty() : Optional.of(blocks.get(cursor));
	}

	public void advance() {
		if (!complete()) cursor++;
	}

	public int[] worldPos(PlanBlock b) {
		return new int[]{ox + b.dx(), oy + b.dy(), oz + b.dz()};
	}

	/** Étage en cours (0 = fondations). */
	public int currentLayer() {
		return complete() ? plan.sizeY : blocks.get(cursor).dy();
	}

	/** Objets nécessaires pour les blocs restant à poser. */
	public Map<String, Integer> remainingNeeds() {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (int i = cursor; i < blocks.size(); i++) {
			String it = blocks.get(i).item();
			if (it != null) m.merge(it, 1, Integer::sum);
		}
		return m;
	}

	/** Objets nécessaires pour le seul étage en cours (utile pour ne demander que ce qui sert tout de suite). */
	public Map<String, Integer> currentLayerNeeds() {
		Map<String, Integer> m = new LinkedHashMap<>();
		if (complete()) return m;
		int layer = blocks.get(cursor).dy();
		for (int i = cursor; i < blocks.size() && blocks.get(i).dy() == layer; i++) {
			String it = blocks.get(i).item();
			if (it != null) m.merge(it, 1, Integer::sum);
		}
		return m;
	}

	/** Pour le panneau « en construction » : ce qu'on a (vert) et ce qui manque (gris). */
	public List<ItemStatus> status(Stock available) {
		List<ItemStatus> out = new ArrayList<>();
		for (Map.Entry<String, Integer> e : remainingNeeds().entrySet()) {
			out.add(new ItemStatus(e.getKey(), Math.min(available.count(e.getKey()), e.getValue()), e.getValue()));
		}
		return out;
	}

	/** Case juste devant la porte (ou le portillon), à l'extérieur du bâtiment : départ du chemin. */
	public int[] frontCell() {
		int fx = plan.footprintX(rotation), fz = plan.footprintZ(rotation);
		for (PlanBlock b : blocks) {
			String id = b.blockId();
			boolean door = id.endsWith("_door") && b.block().contains("half=lower");
			if (!door && !id.endsWith("_fence_gate")) continue;
			if (b.dz() == fz - 1) return new int[]{ox + b.dx(), oz + fz};
			if (b.dx() == 0) return new int[]{ox - 1, oz + b.dz()};
			if (b.dx() == fx - 1) return new int[]{ox + fx, oz + b.dz()};
			if (b.dz() == 0) return new int[]{ox + b.dx(), oz - 1};
		}
		// pas de porte : milieu de la face qui était « au sud » dans le plan, après rotation
		return switch (rotation) {
			case 1 -> new int[]{ox - 1, oz + fz / 2};
			case 2 -> new int[]{ox + fx / 2, oz - 1};
			case 3 -> new int[]{ox + fx, oz + fz / 2};
			default -> new int[]{ox + fx / 2, oz + fz};
		};
	}

	/** Zone occupée (min/max inclus) : x0,z0,x1,z1. */
	public int[] footprint() {
		return new int[]{ox, oz, ox + plan.footprintX(rotation) - 1, oz + plan.footprintZ(rotation) - 1};
	}
}
