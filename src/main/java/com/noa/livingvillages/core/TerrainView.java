package com.noa.livingvillages.core;

/** Ce que la logique de la ville a besoin de savoir du terrain. Minecraft l'implémente ; les tests utilisent un faux terrain. */
public interface TerrainView {
	/** Hauteur (y) du bloc de sol le plus haut en (x, z), sans compter herbes, fleurs, feuilles ni troncs. */
	int groundY(int x, int z);

	/** Vrai si on ne doit pas construire ni tracer de chemin ici (eau, lave, vide, zone interdite...). */
	boolean blocked(int x, int z);

	/** Vrai si le bloc (x, y, z) est vide ou remplaçable (air, herbe haute, fleurs...). */
	boolean isClear(int x, int y, int z);
}
