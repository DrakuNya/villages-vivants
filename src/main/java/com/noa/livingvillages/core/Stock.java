package com.noa.livingvillages.core;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Un inventaire simple : identifiant d'objet -> quantité. Aucune dépendance à Minecraft. */
public final class Stock {
	private final Map<String, Integer> items = new TreeMap<>();

	public int count(String id) {
		return items.getOrDefault(id, 0);
	}

	public boolean has(String id, int n) {
		return count(id) >= n;
	}

	public void add(String id, int n) {
		if (n <= 0) return;
		items.merge(id, n, Integer::sum);
	}

	/** Retire n objets, tout ou rien. */
	public boolean remove(String id, int n) {
		if (n <= 0) return true;
		int have = count(id);
		if (have < n) return false;
		if (have == n) items.remove(id);
		else items.put(id, have - n);
		return true;
	}

	public int total() {
		int t = 0;
		for (int v : items.values()) t += v;
		return t;
	}

	public Stock copy() {
		Stock s = new Stock();
		s.items.putAll(items);
		return s;
	}

	public void replaceWith(Stock other) {
		items.clear();
		items.putAll(other.items);
	}

	public Map<String, Integer> asMap() {
		return Collections.unmodifiableMap(items);
	}

	@Override
	public String toString() {
		return items.toString();
	}
}
