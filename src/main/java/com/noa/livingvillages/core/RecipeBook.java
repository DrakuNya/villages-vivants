package com.noa.livingvillages.core;

import java.util.ArrayList;
import java.util.List;

/** Le « livre de recettes » des villageois : ce qu'ils savent transformer. */
public final class RecipeBook {
	private final List<Recipe> recipes = new ArrayList<>();

	public void add(Recipe r) {
		recipes.add(r);
	}

	public List<Recipe> forProducing(String item) {
		List<Recipe> out = new ArrayList<>();
		for (Recipe r : recipes) if (r.outputOf(item) > 0) out.add(r);
		return out;
	}

	public int size() {
		return recipes.size();
	}

	public static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove", "pale_oak"};

	private static String mc(String s) {
		return "minecraft:" + s;
	}

	/** Recettes de base : bois, pierre, verre, laine, etc. À compléter (voir la feuille de route). */
	public static RecipeBook defaults() {
		RecipeBook b = new RecipeBook();
		for (String w : WOODS) {
			String log = mc(w + "_log"), planks = mc(w + "_planks"), stick = mc("stick");
			b.add(Recipe.of("planches (" + w + ")", planks, 4, log, 1));
			b.add(Recipe.of("bâtons (" + w + ")", stick, 4, planks, 2));
			b.add(Recipe.of("porte (" + w + ")", mc(w + "_door"), 3, planks, 6));
			b.add(Recipe.of("trappe (" + w + ")", mc(w + "_trapdoor"), 2, planks, 6));
			b.add(Recipe.of("dalle (" + w + ")", mc(w + "_slab"), 6, planks, 3));
			b.add(Recipe.of("escalier (" + w + ")", mc(w + "_stairs"), 4, planks, 6));
			b.add(Recipe.of("barrière (" + w + ")", mc(w + "_fence"), 3, planks, 4, stick, 2));
			b.add(Recipe.of("portillon (" + w + ")", mc(w + "_fence_gate"), 1, planks, 2, stick, 4));
			b.add(Recipe.of("panneau (" + w + ")", mc(w + "_sign"), 3, planks, 6, stick, 1));
		}
		// Tous types de planches -> table / coffre : on déclare la version « chêne » + la version du thème au besoin.
		for (String w : WOODS) {
			String planks = mc(w + "_planks");
			b.add(Recipe.of("table de craft", mc("crafting_table"), 1, planks, 4));
			b.add(Recipe.of("coffre", mc("chest"), 1, planks, 8));
		}
		b.add(Recipe.of("échelle", mc("ladder"), 3, mc("stick"), 7));
		b.add(Recipe.of("torche", mc("torch"), 4, mc("coal"), 1, mc("stick"), 1));
		// Pierre
		b.add(Recipe.of("escalier en pierre", mc("cobblestone_stairs"), 4, mc("cobblestone"), 6));
		b.add(Recipe.of("dalle en pierre", mc("cobblestone_slab"), 6, mc("cobblestone"), 3));
		b.add(Recipe.of("muret en pierre", mc("cobblestone_wall"), 6, mc("cobblestone"), 6));
		b.add(Recipe.of("four", mc("furnace"), 1, mc("cobblestone"), 8));
		b.add(Recipe.of("pierre (fonte)", mc("stone"), 1, mc("cobblestone"), 1)); // le combustible sera géré plus tard
		b.add(Recipe.of("briques de pierre", mc("stone_bricks"), 4, mc("stone"), 4));
		// Sable / verre
		b.add(Recipe.of("verre (fonte)", mc("glass"), 1, mc("sand"), 1));
		b.add(Recipe.of("vitre", mc("glass_pane"), 16, mc("glass"), 6));
		b.add(Recipe.of("grès", mc("sandstone"), 1, mc("sand"), 4));
		b.add(Recipe.of("escalier en grès", mc("sandstone_stairs"), 4, mc("sandstone"), 6));
		b.add(Recipe.of("dalle en grès", mc("sandstone_slab"), 6, mc("sandstone"), 3));
		b.add(Recipe.of("muret en grès", mc("sandstone_wall"), 6, mc("sandstone"), 6));
		// Laine / lit / neige
		b.add(Recipe.of("tapis", mc("white_carpet"), 3, mc("white_wool"), 2));
		for (String w : WOODS) b.add(Recipe.of("lit (" + w + ")", mc("white_bed"), 1, mc("white_wool"), 3, mc(w + "_planks"), 3));
		b.add(Recipe.of("bloc de neige", mc("snow_block"), 1, mc("snowball"), 4));
		b.add(Recipe.of("botte de foin", mc("hay_block"), 1, mc("wheat"), 9));
		return b;
	}
}
