package com.noa.livingvillages.mc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Optional;

/** Utilitaires : "minecraft:oak_door[half=lower,facing=south]" -> BlockState. */
public final class BlockUtil {
	private BlockUtil() {
	}

	/** Renvoie null si le bloc n'existe pas. */
	public static BlockState parse(String spec) {
		String id = spec;
		String props = "";
		int br = spec.indexOf('[');
		if (br >= 0) {
			id = spec.substring(0, br);
			props = spec.substring(br + 1, spec.length() - 1);
		}
		Identifier ident = Identifier.parse(id);
		Block block = BuiltInRegistries.BLOCK.getValue(ident);
		// getValue renvoie le bloc « air » par défaut si l'identifiant est inconnu
		if (block == null || (!id.equals("minecraft:air") && BuiltInRegistries.BLOCK.getKey(block).toString().equals("minecraft:air"))) return null;
		BlockState state = block.defaultBlockState();
		if (!props.isEmpty()) {
			for (String kv : props.split(",")) {
				String[] p = kv.split("=", 2);
				if (p.length < 2) continue;
				Property<?> prop = block.getStateDefinition().getProperty(p[0]);
				if (prop != null) state = withValue(state, prop, p[1]);
			}
		}
		return state;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState withValue(BlockState state, Property prop, String value) {
		Optional<?> v = prop.getValue(value);
		return v.isPresent() ? state.setValue(prop, (Comparable) v.get()) : state;
	}

	public static String idOf(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
