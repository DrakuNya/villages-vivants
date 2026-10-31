package com.noa.livingvillages.core;

/** Une case de chemin de terre, avec éventuellement un lampadaire (torche). */
public final class RoadCell {
	public final int x, z;
	public final boolean lamp;
	/** Le chemin est posé. */
	public boolean done;
	/** Le lampadaire (si prévu) est posé. */
	public boolean lampPlaced;

	public RoadCell(int x, int z, boolean lamp, boolean done) {
		this.x = x;
		this.z = z;
		this.lamp = lamp;
		this.done = done;
	}

	public static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	public long key() {
		return key(x, z);
	}
}
