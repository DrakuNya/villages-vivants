package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Format .plan :
 *   plan: nom            title: Titre            requires: any | snow,frozen
 *   palette:
 *     L = @log
 *     D = @door[half=lower,facing=south] -> @door      ("-> -" = gratuit)
 *   layer 0:
 *   CCCCC            (une ligne = une rangée en z ; un caractère = un x ; '.' ou ' ' = ne rien poser)
 */
public final class PlanParser {
	private PlanParser() {
	}

	public static BuildPlan parse(String text) {
		String name = null, title = null;
		List<String> requires = new ArrayList<>();
		Map<Character, String[]> palette = new HashMap<>();
		List<BuildPlan.Template> templates = new ArrayList<>();
		int sizeX = 0, sizeZ = 0, maxLayer = -1;

		String section = "";
		int layer = -1, row = 0;
		int lineNo = 0;
		for (String raw : text.split("\r?\n")) {
			lineNo++;
			String line = raw;
			int hash = line.indexOf(" #");
			if (hash >= 0) line = line.substring(0, hash);
			if (line.startsWith("#") || line.isBlank()) {
				continue;
			}
			String trimmed = line.strip();
			if (trimmed.startsWith("plan:")) {
				name = trimmed.substring(5).strip();
			} else if (trimmed.startsWith("title:")) {
				title = trimmed.substring(6).strip();
			} else if (trimmed.startsWith("requires:")) {
				for (String r : trimmed.substring(9).split(",")) {
					r = r.strip().toLowerCase();
					if (!r.isEmpty() && !r.equals("any")) requires.add(r);
				}
			} else if (trimmed.equals("palette:")) {
				section = "palette";
			} else if (trimmed.startsWith("layer ") && trimmed.endsWith(":")) {
				section = "layer";
				layer = Integer.parseInt(trimmed.substring(6, trimmed.length() - 1).strip());
				maxLayer = Math.max(maxLayer, layer);
				row = 0;
			} else if (section.equals("palette")) {
				int eq = trimmed.indexOf('=');
				if (eq < 1 || !trimmed.substring(1, eq).isBlank()) throw new IllegalArgumentException("Ligne " + lineNo + " : palette invalide : " + trimmed);
				char key = trimmed.charAt(0);
				String rest = trimmed.substring(eq + 1).strip();
				String block = rest, item = null;
				int arrow = rest.indexOf("->");
				boolean explicit = false;
				if (arrow >= 0) {
					block = rest.substring(0, arrow).strip();
					String it = rest.substring(arrow + 2).strip();
					explicit = true;
					item = it.equals("-") ? null : it;
				}
				if (!explicit) {
					int br = block.indexOf('[');
					item = br < 0 ? block : block.substring(0, br);
				}
				palette.put(key, new String[]{block, item});
			} else if (section.equals("layer")) {
				// on garde la ligne brute (sans strip) pour respecter les colonnes
				String l = line.stripTrailing();
				for (int x = 0; x < l.length(); x++) {
					char c = l.charAt(x);
					if (c == '.' || c == ' ') continue;
					String[] p = palette.get(c);
					if (p == null) throw new IllegalArgumentException("Ligne " + lineNo + " : caractère '" + c + "' absent de la palette");
					templates.add(new BuildPlan.Template(x, layer, row, p[0], p[1]));
				}
				sizeX = Math.max(sizeX, l.length());
				row++;
				sizeZ = Math.max(sizeZ, row);
			} else {
				throw new IllegalArgumentException("Ligne " + lineNo + " : inattendu : " + trimmed);
			}
		}
		if (name == null) throw new IllegalArgumentException("Plan sans 'plan:'");
		if (title == null) title = name;
		return new BuildPlan(name, title, requires, sizeX, maxLayer + 1, sizeZ, templates);
	}
}
