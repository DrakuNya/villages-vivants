package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.Role;
import com.noa.livingvillages.core.Settlement;
import com.noa.livingvillages.core.Theme;
import com.noa.livingvillages.core.Town;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/** Fondation d'une colonie : une équipe de colons (maire, constructeurs, bûcheron, mineur) et leurs provisions. */
public final class Settlers {
	private static final int[][] OFFSETS = {{0, 2}, {2, 0}, {-2, 0}, {0, -2}, {2, 2}};

	private Settlers() {
	}

	/** Renvoie un message d'erreur (ville déjà proche), ou null si la colonie est fondée. */
	public static String found(ServerLevel level, BlockPos pos) {
		Town near = Towns.nearest(pos.getX(), pos.getZ(), 96);
		if (near != null) return "Une ville existe déjà à proximité (" + near.name + ").";

		Town town = new Town(Towns.newTownId(), "Colonie " + (Towns.all().size() + 1));
		BlockPos bell = Director.findBell(level, pos);
		BlockPos c = bell != null ? bell : pos;
		town.centerX = c.getX();
		town.centerY = c.getY();
		town.centerZ = c.getZ();
		town.dense = true;
		town.autoRecruit = true; // les nouveaux habitants reçoivent un métier tout seuls
		town.externalWell = Director.detectWell(level, c);
		Theme theme = Theme.fromBiome(Director.biomeOf(level, c));

		TerrainAdapter terrain = new TerrainAdapter(level, pos.getY());
		List<Role> crew = Settlement.crew();
		for (int i = 0; i < crew.size(); i++) {
			Role role = crew.get(i);
			int x = pos.getX() + OFFSETS[i][0], z = pos.getZ() + OFFSETS[i][1];
			int y = terrain.groundY(x, z) + 1;
			Villager v = new Villager(EntityTypes.VILLAGER, level);
			v.setPos(x + 0.5, y, z + 0.5);
			v.setCustomName(Component.literal(role.displayName));
			v.setCustomNameVisible(true);
			level.addFreshEntity(v);
			String id = v.getUUID().toString();
			town.members.put(id, role);
			if (role == Role.MAYOR) town.mayorId = id;
		}
		Settlement.starterStock(theme).asMap().forEach(town.stockpile::add);
		Towns.all().add(town);
		Towns.markDirty();
		Director.broadcast(level, town, "Des colons fondent " + town.name + " ! Un maire, deux constructeurs, un bûcheron et un mineur, avec leurs provisions de départ.");
		return null;
	}
}
