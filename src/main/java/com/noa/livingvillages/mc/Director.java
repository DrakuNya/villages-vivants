package com.noa.livingvillages.mc;

import com.noa.livingvillages.core.BuildSite;
import com.noa.livingvillages.core.BuilderLogic;
import com.noa.livingvillages.core.Inventory;
import com.noa.livingvillages.core.Logistics;
import com.noa.livingvillages.core.MayorBrain;
import com.noa.livingvillages.core.PlanBlock;
import com.noa.livingvillages.core.RoadCell;
import com.noa.livingvillages.core.RoadLogic;
import com.noa.livingvillages.core.Role;
import com.noa.livingvillages.core.SitePrep;
import com.noa.livingvillages.core.Town;
import com.noa.livingvillages.core.TownBoard;
import com.noa.livingvillages.core.WorkerLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le « chef d'orchestre » : toutes les ~10 ticks, fait agir les villageois ayant un métier.
 *  - le maire décide des constructions (toutes les ~10 s) si le mode automatique est actif ;
 *  - le terrain est aplani et dégagé avant chaque chantier ;
 *  - les constructeurs se répartissent sur les chantiers, puis posent les chemins et lampadaires ;
 *  - bûcherons, mineurs et bergers répondent aux demandes du tableau.
 *
 * ATTENTION : jamais lancé en jeu. Tout ce qui touche au déplacement (navigation) est à vérifier.
 */
public final class Director {
	private static final int INTERVAL_TICKS = 10;
	private static final int MAYOR_EVERY_STEPS = 20;       // 20 x 10 ticks = 10 secondes
	private static final int PREP_OPS_PER_STEP = 40;
	private static final double REACH_SQ = 6.0 * 6.0;
	private static final int SEARCH_RADIUS = 24;
	private static final int SHEAR_COOLDOWN_TICKS = 1200;

	private static final BuilderLogic LOGIC = new BuilderLogic(Towns.PLANNER);
	private static final RoadLogic ROADS = new RoadLogic(Towns.PLANNER);

	/** Tâche d'un ouvrier : bloc visé (ou créature visée) et demande prise sur le tableau. Non sauvegardée. */
	private record Task(BlockPos target, UUID entity, TownBoard.Request request) {
	}

	private static final Map<String, Task> TASKS = new HashMap<>();
	private static final Map<String, ArrayDeque<SitePrep.Op>> PREP = new HashMap<>();
	private static final Map<UUID, Long> SHEARED = new HashMap<>();
	private static long ticks;
	private static long steps;

	private Director() {
	}

	public static BlockPos signPos(BuildSite s) {
		return new BlockPos(s.ox - 1, s.oy + 1, s.oz - 1);
	}

	// ------------------------------------------------------------------ boucle principale

	public static void tick(MinecraftServer server) {
		if (++ticks % INTERVAL_TICKS != 0) return;
		steps++;
		ServerLevel level = server.overworld();
		for (Town town : List.copyOf(Towns.all())) {
			town.syncBoard(Towns.PLANNER);
			if (steps % MAYOR_EVERY_STEPS == 0) mayorThink(level, town, false);
			prepare(level, town);
			Map<String, BuildSite> assignments = town.assignBuilders();
			for (Map.Entry<String, Role> m : town.members.entrySet()) {
				Entity e = level.getEntity(UUID.fromString(m.getKey()));
				if (!(e instanceof Villager villager) || !villager.isAlive()) continue;
				switch (m.getValue()) {
					case BUILDER -> builder(level, town, villager, assignments.get(m.getKey()));
					case LUMBERJACK, MINER, SHEPHERD -> gatherer(level, town, m.getKey(), m.getValue(), villager);
					default -> {
					}
				}
			}
		}
		if (steps % 20 == 0) Towns.saveIfDirty(server);
	}

	// ------------------------------------------------------------------ le maire décide

	private static int population(ServerLevel level, Town town) {
		BlockPos c = new BlockPos(town.centerX, town.centerY, town.centerZ);
		int n = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(c).inflate(64), x -> true)) if (!v.isBaby()) n++;
		return n;
	}

	private static String biomeOf(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
	}

	private static boolean mayorPresent(ServerLevel level, Town town) {
		if (town.mayorId == null) return false;
		Entity e = level.getEntity(UUID.fromString(town.mayorId));
		return e instanceof Villager v && v.isAlive();
	}

	/** Renvoie le texte de la décision (pour la commande), ou null si rien n'a été décidé. */
	public static String mayorThink(ServerLevel level, Town town, boolean force) {
		if (!mayorPresent(level, town)) return null;
		BlockPos center = new BlockPos(town.centerX, town.centerY, town.centerZ);
		TerrainAdapter terrain = new TerrainAdapter(level, town.centerY);
		Optional<MayorBrain.Created> c = MayorBrain.think(town, Plans.catalog(), terrain, biomeOf(level, center), population(level, town), force);
		if (c.isEmpty()) return null;
		BuildSite s = c.get().site();
		String msg = "Le maire de " + town.name + " décide de construire : " + s.plan.title + " (" + c.get().reason() + ").";
		broadcast(level, town, msg);
		Towns.markDirty();
		return msg;
	}

	static void broadcast(ServerLevel level, Town town, String text) {
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - town.centerX, dz = p.getZ() - town.centerZ;
			if (dx * dx + dz * dz < 160.0 * 160.0) p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.AQUA));
		}
	}

	// ------------------------------------------------------------------ terrassement

	private static boolean anyBuilderAlive(ServerLevel level, Town town) {
		for (String id : town.membersWith(Role.BUILDER)) {
			Entity e = level.getEntity(UUID.fromString(id));
			if (e instanceof Villager v && v.isAlive()) return true;
		}
		return false;
	}

	private static void prepare(ServerLevel level, Town town) {
		BuildSite site = null;
		for (BuildSite s : town.activeSites()) {
			if (!s.prepDone) {
				site = s;
				break;
			}
		}
		if (site == null || !anyBuilderAlive(level, town)) return;
		final BuildSite target = site;
		ArrayDeque<SitePrep.Op> ops = PREP.computeIfAbsent(site.id,
				id -> new ArrayDeque<>(SitePrep.compute(target, new TerrainAdapter(level, town.centerY))));
		BlockState dirt = BlockUtil.parse("minecraft:dirt");
		BlockState grass = BlockUtil.parse("minecraft:grass_block");
		for (int i = 0; i < PREP_OPS_PER_STEP && !ops.isEmpty(); i++) {
			SitePrep.Op op = ops.poll();
			BlockPos p = new BlockPos(op.x(), op.y(), op.z());
			switch (op.kind()) {
				case CLEAR -> level.destroyBlock(p, false);
				case DIRT -> {
					if (dirt != null) level.setBlock(p, dirt, 3);
				}
				case GRASS -> {
					if (grass != null) level.setBlock(p, grass, 3);
				}
			}
		}
		if (ops.isEmpty()) {
			PREP.remove(site.id);
			site.prepDone = true;
			placeSign(level, site);
			Towns.markDirty();
			broadcast(level, town, "Terrain prêt pour : " + site.plan.title + ".");
		}
	}

	/** Le panneau « en construction », posé sur un petit socle pour tenir debout. */
	static void placeSign(ServerLevel level, BuildSite site) {
		BlockPos pos = signPos(site);
		level.setBlock(pos.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
		level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState(), 3);
	}

	// ------------------------------------------------------------------ dépôt

	/** Où se trouve le dépôt : un coffre de l'entrepôt s'il est construit, sinon la place centrale. */
	static BlockPos depotPos(ServerLevel level, Town town) {
		int[] chest = town.depotChest();
		if (chest != null) return new BlockPos(chest[0], chest[1], chest[2]);
		TerrainAdapter terrain = new TerrainAdapter(level, town.centerY);
		return new BlockPos(town.centerX, terrain.groundY(town.centerX, town.centerZ) + 1, town.centerZ);
	}

	/** Marche vers le dépôt ; renvoie vrai quand on est assez près pour y travailler. */
	private static boolean atDepot(ServerLevel level, Town town, Villager v) {
		BlockPos depot = depotPos(level, town);
		if (v.blockPosition().distSqr(depot) > REACH_SQ) {
			walkTo(v, depot);
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ constructeur

	private static void walkTo(Villager v, BlockPos pos) {
		v.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.6);
	}

	private static void builder(ServerLevel level, Town town, Villager villager, BuildSite site) {
		Inventory bag = town.inventoryOf(villager.getUUID().toString());
		if (site == null) {
			roads(level, town, villager, bag);
			return;
		}
		var peek = site.peek();
		if (peek.isEmpty()) return;
		// il manque l'objet du prochain bloc : tour au dépôt, on y fabrique et on recharge le sac
		if (LOGIC.needsFetch(site, bag)) {
			if (!atDepot(level, town, villager)) return;
			Logistics.FetchResult r = LOGIC.fetch(town, site, bag);
			if (r.movedAnything()) Towns.markDirty();
			if (LOGIC.needsFetch(site, bag)) return; // toujours rien pour le prochain bloc : on attend au dépôt
		}
		PlanBlock next = peek.get();
		int[] w = site.worldPos(next);
		BlockPos target = new BlockPos(w[0], w[1], w[2]);
		if (villager.blockPosition().distSqr(target) > REACH_SQ) {
			walkTo(villager, target);
			return;
		}
		BuilderLogic.Action a = LOGIC.next(site, bag);
		if (a.kind() != BuilderLogic.Kind.PLACE) return;
		BlockState state = BlockUtil.parse(a.block().block());
		if (state == null) {
			LOGIC.refund(bag, a.block());
			site.advance(); // bloc inconnu dans cette version : on le saute
			Towns.markDirty();
			return;
		}
		level.setBlock(target, state, 3);
		site.advance();
		Towns.markDirty();
		if (site.complete()) {
			level.destroyBlock(signPos(site), false); // le panneau disparaît quand c'est fini
			broadcast(level, town, site.plan.title + " est terminé(e) !");
		}
	}

	// ------------------------------------------------------------------ chemins et lampadaires

	private static void roads(ServerLevel level, Town town, Villager villager, Inventory bag) {
		RoadCell cell = ROADS.peekTarget(town);
		if (cell == null) return;
		// lampadaire à poser sans torche dans le sac : tour au dépôt
		if (ROADS.needsTorch(town, bag)) {
			if (!atDepot(level, town, villager)) return;
			if (ROADS.fetchTorches(town, bag).movedAnything()) Towns.markDirty();
			if (ROADS.needsTorch(town, bag)) return; // pas de torche disponible : on attend
		}
		TerrainAdapter terrain = new TerrainAdapter(level, town.centerY);
		int y = terrain.groundY(cell.x, cell.z);
		BlockPos ground = new BlockPos(cell.x, y, cell.z);
		if (villager.blockPosition().distSqr(ground) > REACH_SQ) {
			walkTo(villager, ground.above());
			return;
		}
		RoadLogic.Action a = ROADS.next(town, bag);
		switch (a.kind()) {
			case PATH -> {
				BlockState path = BlockUtil.parse("minecraft:dirt_path");
				if (path != null && !terrain.blocked(cell.x, cell.z)) {
					BlockPos above = ground.above();
					if (!terrain.isClear(above.getX(), above.getY(), above.getZ())) level.destroyBlock(above, false);
					level.setBlock(ground, path, 3);
				}
				cell.done = true;
				Towns.markDirty();
			}
			case LAMP -> {
				BlockState torch = BlockUtil.parse("minecraft:torch");
				if (torch != null) level.setBlock(ground.above(), torch, 3);
				cell.lampPlaced = true;
				Towns.markDirty();
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ bûcheron, mineur, berger

	/** Sources de matières premières dans le monde : objet demandé -> blocs à casser. */
	private static List<String> sourcesOf(String item) {
		return switch (item) {
			case "minecraft:cobblestone" -> List.of("minecraft:stone", "minecraft:cobblestone");
			case "minecraft:stone" -> List.of("minecraft:stone");
			case "minecraft:sand" -> List.of("minecraft:sand");
			case "minecraft:dirt" -> List.of("minecraft:dirt", "minecraft:grass_block");
			case "minecraft:snow_block" -> List.of("minecraft:snow_block");
			case "minecraft:clay_ball" -> List.of("minecraft:clay");
			case "minecraft:gravel" -> List.of("minecraft:gravel");
			case "minecraft:coal" -> List.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore");
			default -> item.endsWith("_log") ? List.of(item) : List.of();
		};
	}

	/** Créatures qui fournissent un objet (pour l'instant : la laine des moutons). */
	private static String entitySourceOf(String item) {
		return item.endsWith("_wool") ? "minecraft:sheep" : null;
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

	private static Mob findSheep(ServerLevel level, BlockPos from, String typeId) {
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(from).inflate(32), x -> true)) {
			if (!BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString().equals(typeId)) continue;
			Long last = SHEARED.get(m.getUUID());
			if (last != null && ticks - last < SHEAR_COOLDOWN_TICKS) continue;
			double d = m.blockPosition().distSqr(from);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static void gatherer(ServerLevel level, Town town, String id, Role role, Villager villager) {
		Inventory bag = town.inventoryOf(id);
		WorkerLogic.Action act = WorkerLogic.next(town, id, role, bag);
		switch (act.kind()) {
			case IDLE -> {
			}
			case DEPOSIT -> {
				TASKS.remove(id);
				if (!atDepot(level, town, villager)) return;
				town.deposit(id);
				Towns.markDirty();
			}
			case HARVEST -> harvest(level, town, id, villager, bag, act.request());
		}
	}

	private static void harvest(ServerLevel level, Town town, String id, Villager villager, Inventory bag, TownBoard.Request req) {
		Task task = TASKS.get(id);
		if (task == null) {
			String entityType = entitySourceOf(req.item);
			if (entityType != null) {
				Mob animal = findSheep(level, villager.blockPosition(), entityType);
				if (animal == null) {
					giveUp(town, id);
					return;
				}
				task = new Task(animal.blockPosition(), animal.getUUID(), req);
			} else {
				List<String> sources = sourcesOf(req.item);
				BlockPos found = sources.isEmpty() ? null : findNearest(level, villager.blockPosition(), sources);
				if (found == null) {
					giveUp(town, id); // rien à récolter ici : la demande reste ouverte pour un autre
					return;
				}
				task = new Task(found, null, req);
			}
			TASKS.put(id, task);
		}
		BlockPos target = task.target();
		if (task.entity() != null) {
			Entity animal = level.getEntity(task.entity());
			if (animal == null) {
				TASKS.remove(id);
				return;
			}
			target = animal.blockPosition();
		}
		if (villager.blockPosition().distSqr(target) > REACH_SQ) {
			walkTo(villager, target);
			return;
		}
		if (task.entity() != null) {
			// « tonte » : le mouton donne 1 à 3 laines dans le sac, puis se repose un moment
			int wool = Math.min(1 + (int) (ticks % 3), bag.room(req.item));
			SHEARED.put(task.entity(), ticks);
			if (wool > 0) bag.add(req.item, wool);
			Towns.markDirty();
		} else {
			List<String> sources = sourcesOf(req.item);
			if (sources.contains(BlockUtil.idOf(level.getBlockState(target))) && bag.canAdd(req.item, 1)) {
				level.destroyBlock(target, false);
				bag.add(req.item, 1);
				Towns.markDirty();
			}
		}
		TASKS.remove(id);
	}

	/** Rien à récolter : on libère la commande ; avec un sac non vide, l'ouvrier ira déposer au prochain tour. */
	private static void giveUp(Town town, String id) {
		TASKS.remove(id);
		town.board.release(id);
	}
}
