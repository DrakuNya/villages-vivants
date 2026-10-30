package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.BuildSite;
import com.noa.livingvillages.core.BuilderLogic;
import com.noa.livingvillages.core.PlanBlock;
import com.noa.livingvillages.core.Role;
import com.noa.livingvillages.core.Town;
import com.noa.livingvillages.core.TownBoard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le « chef d'orchestre » : toutes les ~10 ticks, fait agir les villageois ayant un métier.
 * Version 1 : constructeur, bûcheron et mineur. Les autres métiers sont modélisés dans core/ mais pas encore animés.
 *
 * ATTENTION : première version jamais lancée en jeu. Tout ce qui touche au déplacement (navigation) est à vérifier.
 */
public final class Director {
	private static final int INTERVAL_TICKS = 10;
	private static final double REACH_SQ = 6.0 * 6.0;
	private static final int SEARCH_RADIUS = 24;

	private static final BuilderLogic LOGIC = new BuilderLogic(Towns.PLANNER);

	/** Tâche en cours d'un ouvrier (bloc visé + demande prise sur le tableau). Non sauvegardée : recalculée après un redémarrage. */
	private record Task(BlockPos target, TownBoard.Request request) {
	}

	private static final Map<String, Task> TASKS = new HashMap<>();
	private static int ticks;

	private Director() {
	}

	public static BlockPos signPos(BuildSite s) {
		return new BlockPos(s.ox - 1, s.oy + 1, s.oz - 1);
	}

	public static void tick(MinecraftServer server) {
		if (++ticks % INTERVAL_TICKS != 0) return;
		ServerLevel level = server.overworld();
		for (Town town : List.copyOf(Towns.all())) {
			town.syncBoard(Towns.PLANNER);
			for (Map.Entry<String, Role> m : town.members.entrySet()) {
				Entity e = level.getEntity(UUID.fromString(m.getKey()));
				if (!(e instanceof Villager villager) || !villager.isAlive()) continue;
				switch (m.getValue()) {
					case BUILDER -> builder(level, town, villager);
					case LUMBERJACK, MINER -> gatherer(level, town, m.getKey(), m.getValue(), villager);
					default -> {
					}
				}
			}
		}
		if (ticks % 200 == 0) Towns.saveIfDirty(server);
	}

	private static void walkTo(Villager v, BlockPos pos) {
		v.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6);
	}

	private static void builder(ServerLevel level, Town town, Villager villager) {
		BuildSite site = town.activeSite();
		if (site == null) return;
		var peek = site.peek();
		if (peek.isEmpty()) return;
		PlanBlock next = peek.get();
		int[] w = site.worldPos(next);
		BlockPos target = new BlockPos(w[0], w[1], w[2]);
		if (villager.blockPosition().distSqr(target) > REACH_SQ) {
			walkTo(villager, target);
			return;
		}
		BuilderLogic.Action a = LOGIC.next(town, site);
		if (a.kind() != BuilderLogic.Kind.PLACE) return; // en attente de ressources (le tableau des besoins s'en charge)
		BlockState state = BlockUtil.parse(a.block().block());
		if (state == null) {
			site.advance(); // bloc inconnu dans cette version : on le saute
			Towns.markDirty();
			return;
		}
		level.setBlock(target, state, 3);
		site.advance();
		Towns.markDirty();
		if (site.complete()) {
			// le panneau « en construction » disparaît quand c'est fini
			level.destroyBlock(signPos(site), false);
		}
	}

	/** Sources de matières premières : objet demandé -> blocs à casser. */
	private static List<String> sourcesOf(String item) {
		return switch (item) {
			case "minecraft:cobblestone" -> List.of("minecraft:stone", "minecraft:cobblestone");
			case "minecraft:sand" -> List.of("minecraft:sand");
			case "minecraft:clay_ball" -> List.of("minecraft:clay");
			case "minecraft:gravel" -> List.of("minecraft:gravel");
			case "minecraft:coal" -> List.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore");
			default -> item.endsWith("_log") ? List.of(item) : List.of();
		};
	}

	private static BlockPos findNearest(ServerLevel level, BlockPos from, List<String> blockIds) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int r = SEARCH_RADIUS;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -8; dy <= 12; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = from.offset(dx, dy, dz);
					if (!blockIds.contains(BlockUtil.idOf(level.getBlockState(p)))) continue;
					double d = p.distSqr(from);
					if (d < bestD) {
						bestD = d;
						best = p;
					}
				}
			}
		}
		return best;
	}

	private static void gatherer(ServerLevel level, Town town, String id, Role role, Villager villager) {
		Task task = TASKS.get(id);
		if (task == null) {
			TownBoard.Request req = town.board.claim(id, role);
			if (req == null) return;
			List<String> sources = sourcesOf(req.item);
			BlockPos found = sources.isEmpty() ? null : findNearest(level, villager.blockPosition(), sources);
			if (found == null) {
				town.board.release(id); // rien à récolter ici : la demande reste ouverte pour un autre
				return;
			}
			task = new Task(found, req);
			TASKS.put(id, task);
		}
		BlockPos target = task.target();
		if (villager.blockPosition().distSqr(target) > REACH_SQ) {
			walkTo(villager, target);
			return;
		}
		List<String> sources = sourcesOf(task.request().item);
		if (sources.contains(BlockUtil.idOf(level.getBlockState(target)))) {
			level.destroyBlock(target, false);
			town.stockpile.add(task.request().item, 1);
			town.board.delivered(task.request(), 1);
			Towns.markDirty();
		}
		TASKS.remove(id);
	}
}
