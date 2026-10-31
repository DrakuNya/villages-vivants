package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Terrassement : aplanir et dégager le terrain avant de construire (le plan pose son sol à la hauteur oy). */
public final class SitePrep {
	public enum Kind {CLEAR, DIRT, GRASS}

	public record Op(Kind kind, int x, int y, int z) {
	}

	private SitePrep() {
	}

	/** Liste des opérations de terrassement, du bas vers le haut (remblais d'abord, puis déblais). */
	public static List<Op> compute(BuildSite site, TerrainView t) {
		int[] f = site.footprint();
		int sizeY = site.plan.sizeY;
		Set<Long> layer0 = new HashSet<>();
		for (PlanBlock b : site.plan.blocks(site.theme, site.rotation)) {
			if (b.dy() == 0) layer0.add(RoadCell.key(site.ox + b.dx(), site.oz + b.dz()));
		}
		List<Op> fills = new ArrayList<>();
		List<Op> clears = new ArrayList<>();
		for (int x = f[0] - 1; x <= f[2] + 1; x++) {
			for (int z = f[1] - 1; z <= f[3] + 1; z++) {
				boolean inside = x >= f[0] && x <= f[2] && z >= f[1] && z <= f[3];
				int g = t.groundY(x, z);
				if (inside) {
					for (int y = g + 1; y < site.oy; y++) fills.add(new Op(Kind.DIRT, x, y, z));
					if (!layer0.contains(RoadCell.key(x, z))) fills.add(new Op(Kind.GRASS, x, site.oy, z));
				}
				// dégager l'espace de construction (arbres, herbes, relief) : du sol jusqu'au-dessus du toit
				int top = site.oy + sizeY + 1;
				int from = inside ? site.oy + 1 : Math.max(site.oy + 1, g + 1);
				for (int y = from; y <= top; y++) if (!t.isClear(x, y, z)) clears.add(new Op(Kind.CLEAR, x, y, z));
				if (inside) {
					for (int y = site.oy + 1; y <= g; y++) if (y < from && !t.isClear(x, y, z)) clears.add(new Op(Kind.CLEAR, x, y, z));
				}
			}
		}
		List<Op> all = new ArrayList<>(fills);
		all.addAll(clears);
		return all;
	}
}
