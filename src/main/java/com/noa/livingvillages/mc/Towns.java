package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.ConversionPlanner;
import com.noa.livingvillages.core.RecipeBook;
import com.noa.livingvillages.core.Town;
import com.noa.livingvillages.core.TownStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Accès global aux villes de la partie en cours (serveur ou solo). */
public final class Towns {
	public static final RecipeBook RECIPES = RecipeBook.defaults();
	public static final ConversionPlanner PLANNER = new ConversionPlanner(RECIPES);

	private static final List<Town> TOWNS = new ArrayList<>();
	private static boolean dirty;

	private Towns() {
	}

	public static List<Town> all() {
		return TOWNS;
	}

	public static void markDirty() {
		dirty = true;
	}

	public static void load(MinecraftServer server) {
		TOWNS.clear();
		ServerLevel overworld = server.overworld();
		TOWNS.addAll(TownStore.read(TownSavedData.get(overworld).getText(), Plans.catalog()));
		dirty = false;
	}

	public static void save(MinecraftServer server) {
		TownSavedData.get(server.overworld()).setText(TownStore.write(TOWNS));
		dirty = false;
	}

	public static void saveIfDirty(MinecraftServer server) {
		if (dirty) save(server);
	}

	public static Town nearest(int x, int z, int maxDistance) {
		Town best = null;
		long bestD = (long) maxDistance * maxDistance;
		for (Town t : TOWNS) {
			long dx = t.centerX - x, dz = t.centerZ - z;
			long d = dx * dx + dz * dz;
			if (d <= bestD) {
				bestD = d;
				best = t;
			}
		}
		return best;
	}

	public static Town ofMember(String uuid) {
		for (Town t : TOWNS) if (t.members.containsKey(uuid)) return t;
		return null;
	}

	public static String newTownId() {
		int n = TOWNS.size() + 1;
		while (true) {
			String id = "v" + n;
			boolean taken = false;
			for (Town t : TOWNS) if (t.id.equals(id)) taken = true;
			if (!taken) return id;
			n++;
		}
	}
}
