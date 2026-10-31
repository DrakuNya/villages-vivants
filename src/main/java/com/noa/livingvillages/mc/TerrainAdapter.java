package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.TerrainView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/** Fait le lien entre le terrain de Minecraft et la logique de la ville (core). Les résultats sont mis en cache le temps d'une décision. */
public final class TerrainAdapter implements TerrainView {
	private static final int BLOCKED = Integer.MIN_VALUE;

	private final ServerLevel level;
	private final int refY;
	private final Map<Long, Integer> cache = new HashMap<>();

	public TerrainAdapter(ServerLevel level, int refY) {
		this.level = level;
		this.refY = refY;
	}

	private static long key(int x, int z) {
		return ((long) x << 32) ^ (z & 0xffffffffL);
	}

	/** Hauteur du sol, ou BLOCKED si la colonne est dans l'eau, la lave, le vide ou une zone non chargée. */
	private int column(int x, int z) {
		return cache.computeIfAbsent(key(x, z), k -> scan(x, z));
	}

	private int scan(int x, int z) {
		if (!level.hasChunkAt(new BlockPos(x, refY, z))) return BLOCKED;
		for (int y = refY + 24; y >= refY - 32; y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState s = level.getBlockState(p);
			if (s.isAir()) continue;
			if (!s.getFluidState().isEmpty()) return BLOCKED;
			String id = BlockUtil.idOf(s);
			if (s.canBeReplaced() || id.endsWith("_leaves") || id.endsWith("_log") || id.endsWith("_wood")) continue;
			return y;
		}
		return BLOCKED;
	}

	@Override
	public int groundY(int x, int z) {
		int c = column(x, z);
		return c == BLOCKED ? refY : c;
	}

	@Override
	public boolean blocked(int x, int z) {
		return column(x, z) == BLOCKED;
	}

	@Override
	public boolean isClear(int x, int y, int z) {
		BlockState s = level.getBlockState(new BlockPos(x, y, z));
		return s.isAir() || s.canBeReplaced();
	}
}
