package com.noa.livingvillages.core;

/** Rotations par quarts de tour (sens horaire vu du dessus : nord -> est -> sud -> ouest). */
public final class Rotation {
	private Rotation() {
	}

	public static int[] rotateXZ(int x, int z, int quarterTurns, int sizeX, int sizeZ) {
		return switch (((quarterTurns % 4) + 4) % 4) {
			case 1 -> new int[]{sizeZ - 1 - z, x};
			case 2 -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
			case 3 -> new int[]{z, sizeX - 1 - x};
			default -> new int[]{x, z};
		};
	}

	private static final String[] DIRS = {"north", "east", "south", "west"};

	private static String rotateDir(String d, int k) {
		for (int i = 0; i < 4; i++) if (DIRS[i].equals(d)) return DIRS[(i + k) % 4];
		return d;
	}

	/** Fait tourner facing=... (et axis=x/z) dans une chaîne d'état de bloc. */
	public static String rotateState(String block, int quarterTurns) {
		int k = ((quarterTurns % 4) + 4) % 4;
		if (k == 0 || !block.contains("[")) return block;
		StringBuilder sb = new StringBuilder();
		int br = block.indexOf('[');
		sb.append(block, 0, br + 1);
		String[] props = block.substring(br + 1, block.length() - 1).split(",");
		for (int i = 0; i < props.length; i++) {
			String[] kv = props[i].split("=", 2);
			String v = kv.length > 1 ? kv[1] : "";
			if (kv[0].equals("facing")) v = rotateDir(v, k);
			else if (kv[0].equals("axis") && k % 2 == 1) v = v.equals("x") ? "z" : v.equals("z") ? "x" : v;
			if (i > 0) sb.append(',');
			sb.append(kv[0]).append('=').append(v);
		}
		return sb.append(']').toString();
	}
}
