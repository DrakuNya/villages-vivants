package com.noa.livingvillages.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** L'inventaire que porte un villageois : 8 emplacements, avec les limites de pile de Minecraft. Aucune dépendance à Minecraft. */
public final class Inventory {
	public static final int SLOTS = 8;

	private final String[] item = new String[SLOTS];
	private final int[] count = new int[SLOTS];

	/** Taille maximale d'une pile pour cet objet. */
	public static int maxStack(String id) {
		if (id.endsWith("_bed") || id.endsWith("_boat") || id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_hoe")
				|| id.endsWith("_shovel") || id.endsWith("_sword") || id.endsWith("_bucket") || id.equals("minecraft:shears")) return 1;
		if (id.equals("minecraft:snowball") || id.equals("minecraft:egg") || id.endsWith("_sign") || id.equals("minecraft:ender_pearl")) return 16;
		return 64;
	}

	public int count(String id) {
		int n = 0;
		for (int i = 0; i < SLOTS; i++) if (id.equals(item[i])) n += count[i];
		return n;
	}

	public boolean has(String id, int n) {
		return count(id) >= n;
	}

	public int total() {
		int n = 0;
		for (int i = 0; i < SLOTS; i++) n += count[i];
		return n;
	}

	public boolean isEmpty() {
		return total() == 0;
	}

	/** Combien d'exemplaires de cet objet peut-on encore ajouter ? */
	public int room(String id) {
		int max = maxStack(id), r = 0;
		for (int i = 0; i < SLOTS; i++) {
			if (item[i] == null) r += max;
			else if (item[i].equals(id)) r += max - count[i];
		}
		return r;
	}

	public boolean canAdd(String id, int n) {
		return room(id) >= n;
	}

	public boolean isFull() {
		for (int i = 0; i < SLOTS; i++) if (item[i] == null) return false;
		for (int i = 0; i < SLOTS; i++) if (count[i] < maxStack(item[i])) return false;
		return true;
	}

	/** Ajoute autant que possible ; renvoie ce qui n'a pas pu entrer. */
	public int add(String id, int n) {
		int max = maxStack(id);
		for (int i = 0; i < SLOTS && n > 0; i++) {
			if (id.equals(item[i]) && count[i] < max) {
				int put = Math.min(n, max - count[i]);
				count[i] += put;
				n -= put;
			}
		}
		for (int i = 0; i < SLOTS && n > 0; i++) {
			if (item[i] == null) {
				int put = Math.min(n, max);
				item[i] = id;
				count[i] = put;
				n -= put;
			}
		}
		return n;
	}

	/** Retire n exemplaires, tout ou rien. */
	public boolean remove(String id, int n) {
		if (n <= 0) return true;
		if (count(id) < n) return false;
		for (int i = SLOTS - 1; i >= 0 && n > 0; i--) {
			if (id.equals(item[i])) {
				int take = Math.min(n, count[i]);
				count[i] -= take;
				n -= take;
				if (count[i] == 0) item[i] = null;
			}
		}
		return true;
	}

	/** Vide l'inventaire et renvoie tout son contenu. */
	public Stock drain() {
		Stock s = toStock();
		for (int i = 0; i < SLOTS; i++) {
			item[i] = null;
			count[i] = 0;
		}
		return s;
	}

	public Stock toStock() {
		Stock s = new Stock();
		for (int i = 0; i < SLOTS; i++) if (item[i] != null) s.add(item[i], count[i]);
		return s;
	}

	public Map<String, Integer> asMap() {
		Map<String, Integer> m = new LinkedHashMap<>();
		for (int i = 0; i < SLOTS; i++) if (item[i] != null) m.merge(item[i], count[i], Integer::sum);
		return m;
	}

	public Inventory copy() {
		Inventory c = new Inventory();
		System.arraycopy(item, 0, c.item, 0, SLOTS);
		System.arraycopy(count, 0, c.count, 0, SLOTS);
		return c;
	}

	@Override
	public String toString() {
		return asMap().toString();
	}
}
