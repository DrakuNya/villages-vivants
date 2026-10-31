package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Sauvegarde texte (une ligne = un fait). Séparateur : tabulation. Pas de dépendance à Minecraft. */
public final class TownStore {
	private TownStore() {
	}

	public static String write(Collection<Town> towns) {
		StringBuilder sb = new StringBuilder("v2\n");
		for (Town t : towns) {
			sb.append("town\t").append(t.id).append('\t').append(clean(t.name)).append('\t').append(t.mayorId == null ? "-" : t.mayorId)
					.append('\t').append(t.centerX).append('\t').append(t.centerY).append('\t').append(t.centerZ).append('\n');
			sb.append("auto\t").append(t.auto ? 1 : 0).append('\n');
			for (Map.Entry<String, Role> m : t.members.entrySet()) {
				sb.append("member\t").append(m.getKey()).append('\t').append(m.getValue().id).append('\n');
			}
			for (BuildSite s : t.sites) {
				sb.append("site\t").append(s.id).append('\t').append(s.plan.name).append('\t').append(s.theme.name()).append('\t')
						.append(s.ox).append('\t').append(s.oy).append('\t').append(s.oz).append('\t').append(s.rotation).append('\t')
						.append(s.cursor()).append('\t').append(s.priority).append('\t').append(s.prepDone ? 1 : 0).append('\t')
						.append(s.roadPlanned ? 1 : 0).append('\n');
			}
			for (RoadCell rc : t.roads.values()) {
				sb.append("road\t").append(rc.x).append('\t').append(rc.z).append('\t').append(rc.lamp ? 1 : 0).append('\t')
						.append(rc.done ? 1 : 0).append('\t')
						.append(rc.lampPlaced ? 1 : 0).append('\n');
			}
			for (Map.Entry<String, Integer> e : t.stockpile.asMap().entrySet()) {
				sb.append("stock\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
			}
		}
		return sb.toString();
	}

	private static String clean(String s) {
		return s.replace('\t', ' ').replace('\n', ' ');
	}

	public static List<Town> read(String text, PlanCatalog plans) {
		List<Town> towns = new ArrayList<>();
		Town cur = null;
		for (String line : text.split("\r?\n")) {
			if (line.isBlank() || line.equals("v1") || line.equals("v2")) continue;
			String[] f = line.split("\t");
			switch (f[0]) {
				case "town" -> {
					cur = new Town(f[1], f[2]);
					cur.mayorId = f[3].equals("-") ? null : f[3];
					cur.centerX = Integer.parseInt(f[4]);
					cur.centerY = Integer.parseInt(f[5]);
					cur.centerZ = Integer.parseInt(f[6]);
					towns.add(cur);
				}
				case "member" -> {
					Role r = Role.byId(f[2]);
					if (cur != null && r != null) cur.members.put(f[1], r);
				}
				case "site" -> {
					BuildPlan p = plans.get(f[2]);
					if (cur != null && p != null) {
						BuildSite bs = new BuildSite(f[1], p, Theme.valueOf(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
								Integer.parseInt(f[6]), Integer.parseInt(f[7]), Integer.parseInt(f[8]));
						bs.priority = f.length > 9 ? Integer.parseInt(f[9]) : p.meta.priority();
						bs.prepDone = f.length <= 10 || f[10].equals("1");
						bs.roadPlanned = f.length <= 11 || f[11].equals("1");
						cur.sites.add(bs);
						int dash = f[1].lastIndexOf("-c");
						if (dash >= 0) cur.bumpSiteCounter(Integer.parseInt(f[1].substring(dash + 2)) + 1);
					}
				}
				case "auto" -> {
					if (cur != null) cur.auto = f[1].equals("1");
				}
				case "road" -> {
					if (cur != null) {
						RoadCell rc = new RoadCell(Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3].equals("1"), f[4].equals("1"));
						rc.lampPlaced = f.length > 5 && f[5].equals("1");
						cur.roads.put(rc.key(), rc);
					}
				}
				case "stock" -> {
					if (cur != null) cur.stockpile.add(f[1], Integer.parseInt(f[2]));
				}
				default -> {
				}
			}
		}
		return towns;
	}
}
