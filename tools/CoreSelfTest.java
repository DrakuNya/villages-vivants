import com.noa.livingvillages.core.*;

import java.nio.file.*;
import java.util.*;

/** Tests de la logique pure (sans Minecraft). Lancer : voir .github/workflows/build.yml */
public class CoreSelfTest {
	static int failures = 0, checks = 0;

	static void check(boolean ok, String msg) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("ECHEC : " + msg);
		}
	}

	/** Construit tout le plan : fait des tours au dépôt quand le sac manque ; renvoie le nombre de blocs posés (ou -1 si bloqué). */
	static int buildAll(Town town, BuildSite site, BuilderLogic logic, Inventory bag) {
		int placed = 0;
		while (!site.complete()) {
			if (logic.needsFetch(site, bag)) {
				logic.fetch(town, site, bag);
				if (logic.needsFetch(site, bag)) return -1;
			}
			BuilderLogic.Action a = logic.next(site, bag);
			if (a.kind() != BuilderLogic.Kind.PLACE) return -1;
			site.advance();
			placed++;
			if (bag.total() > Inventory.SLOTS * 64) return -2;
		}
		return placed;
	}

	/** Phase C : sacs des villageois, tours au dépôt, ouvriers de récolte, chaîne complète sans matériaux de départ. */
	static void phaseC(PlanCatalog cat, ConversionPlanner planner) {
		// --- inventaire : piles, place, retrait
		Inventory inv = new Inventory();
		check(inv.add("minecraft:oak_log", 100) == 0 && inv.count("minecraft:oak_log") == 100, "100 bûches tiennent (2 piles)");
		check(inv.add("minecraft:white_bed", 3) == 0 && inv.count("minecraft:white_bed") == 3, "3 lits = 3 emplacements");
		check(inv.room("minecraft:white_bed") == 4 && inv.room("minecraft:oak_log") == 28 + 4 * 64, "place restante : " + inv.room("minecraft:white_bed") + " / " + inv.room("minecraft:oak_log"));
		check(inv.add("minecraft:white_bed", 10) == 6, "seulement 4 lits de plus : 6 refusés");
		check(inv.room("minecraft:white_bed") == 0 && !inv.canAdd("minecraft:white_bed", 1), "plus de place pour des lits (9 emplacements)");
		check(inv.remove("minecraft:oak_log", 70) && inv.count("minecraft:oak_log") == 30 && inv.canAdd("minecraft:white_bed", 1), "retirer des bûches libère de la place");
		check(!inv.remove("minecraft:oak_log", 31) && inv.count("minecraft:oak_log") == 30, "retrait impossible : tout ou rien");
		check(inv.copy().drain().count("minecraft:oak_log") == 30 && inv.count("minecraft:oak_log") == 30, "copie indépendante");
		Inventory sn = new Inventory();
		sn.add("minecraft:snowball", 40);
		check(sn.asMap().equals(Map.of("minecraft:snowball", 40)) && sn.room("minecraft:snowball") == 16 * 6 + 8, "boules de neige : piles de 16");

		// --- accès par emplacement (écran d'inventaire)
		Inventory sl = new Inventory();
		sl.add("minecraft:oak_log", 70);
		check("minecraft:oak_log".equals(sl.itemAt(0)) && sl.countAt(0) == 64 && sl.countAt(1) == 6 && sl.itemAt(2) == null, "lecture par emplacement");
		sl.setSlot(3, "minecraft:diamond", 5);
		sl.setSlot(0, null, 0);
		check(sl.count("minecraft:diamond") == 5 && sl.count("minecraft:oak_log") == 6 && sl.itemAt(0) == null, "écriture par emplacement");
		Inventory sl2 = new Inventory();
		for (int i = 0; i < Inventory.SLOTS; i++) sl2.setSlot(i, sl.itemAt(i), sl.countAt(i));
		check(sl2.asMap().equals(sl.asMap()), "recopie emplacement par emplacement");

		// --- objets utiles à la ville (ramassage des objets jetés)
		java.util.Set<String> known = RecipeBook.defaults().knownItems();
		check(known.contains("minecraft:oak_log") && known.contains("minecraft:oak_door") && known.contains("minecraft:stick") && !known.contains("minecraft:rotten_flesh"),
				"objets connus de la table de recettes");

		// --- tour au dépôt : on fabrique au dépôt et on repart avec le résultat dans le sac
		Town t = new Town("c", "Cville");
		t.stockpile.add("minecraft:oak_log", 2);
		Inventory bag = new Inventory();
		Logistics.FetchResult f = Logistics.fetch(t, planner, bag, Map.of("minecraft:oak_door", 3));
		check(f.moved().equals(Map.of("minecraft:oak_door", 3)) && bag.count("minecraft:oak_door") == 3, "2 bûches -> 3 portes dans le sac : " + f);
		check(t.stockpile.count("minecraft:oak_planks") == 2 && t.stockpile.count("minecraft:oak_log") == 0, "au dépôt il reste 2 planches : " + t.stockpile);
		// pas assez pour tout : on prend ce qu'on peut
		Town t2 = new Town("c2", "C2");
		t2.stockpile.add("minecraft:cobblestone", 10);
		Inventory b2 = new Inventory();
		f = Logistics.fetch(t2, planner, b2, Map.of("minecraft:cobblestone", 25));
		check(b2.count("minecraft:cobblestone") == 10 && t2.stockpile.count("minecraft:cobblestone") == 0, "il prend les 10 qu'il y a : " + f + " " + b2);
		check(f.missing().containsKey("minecraft:cobblestone"), "et signale le manque");
		// limite de place du sac
		Town t3 = new Town("c3", "C3");
		t3.stockpile.add("minecraft:cobblestone", 1000);
		Inventory b3 = new Inventory();
		for (int i = 0; i < 8; i++) b3.add("minecraft:white_bed", 1);
		Logistics.fetch(t3, planner, b3, Map.of("minecraft:cobblestone", 500));
		check(b3.count("minecraft:cobblestone") == 64 && t3.stockpile.count("minecraft:cobblestone") == 1000 - 64, "un seul emplacement libre : 64 pierres seulement");

		// --- ouvrier de récolte : récolte dans le sac, dépose, la commande diminue
		Town w = new Town("w", "W");
		w.mayorId = "m";
		w.members.put("l1", Role.LUMBERJACK);
		w.board.sync("s1", Map.of("minecraft:oak_log", 100));
		Inventory li = w.inventoryOf("l1");
		int harvested = 0;
		for (int guard = 0; guard < 1000; guard++) {
			WorkerLogic.Action a = WorkerLogic.next(w, "l1", Role.LUMBERJACK, li);
			if (a.kind() == WorkerLogic.Kind.HARVEST) {
				check(li.add(a.request().item, 1) == 0, "récolte : il y a de la place");
				harvested++;
			} else if (a.kind() == WorkerLogic.Kind.DEPOSIT) {
				w.deposit("l1");
			} else break;
			if (harvested >= 100 && li.isEmpty()) break;
		}
		check(harvested == 100 && w.stockpile.count("minecraft:oak_log") == 100 && li.isEmpty(), "100 bûches récoltées et déposées : " + w.stockpile);
		check(w.board.all().isEmpty() || w.board.all().get(0).qty == 0 || w.board.claimedBy("l1") == null, "commande soldée");
		// un mineur n'accepte pas une commande de bûches
		Town wb = new Town("wb", "Wb");
		wb.board.sync("s1", Map.of("minecraft:oak_log", 10));
		check(WorkerLogic.next(wb, "m1", Role.MINER, new Inventory()).kind() == WorkerLogic.Kind.IDLE, "le mineur ne prend pas une commande de bûches");
		check(WorkerLogic.next(wb, "l1", Role.LUMBERJACK, new Inventory()).kind() == WorkerLogic.Kind.HARVEST, "le bûcheron la prend");

		// --- un habitant disparaît : sac récupéré, commande libérée, mairie vacante
		Town gone = new Town("gone", "Gone");
		gone.mayorId = "maire";
		gone.members.put("maire", Role.MAYOR);
		gone.members.put("l9", Role.LUMBERJACK);
		gone.board.sync("s1", Map.of("minecraft:oak_log", 20));
		gone.board.claim("l9", Role.LUMBERJACK);
		gone.inventoryOf("l9").add("minecraft:oak_log", 12);
		check(gone.removeMember("l9") && !gone.members.containsKey("l9") && gone.stockpile.count("minecraft:oak_log") == 12, "habitant disparu : son sac revient à l'entrepôt");
		check(gone.board.claimedBy("l9") == null && gone.board.claim("l10", Role.LUMBERJACK) != null, "sa commande est libérée pour un autre ouvrier");
		check(!gone.removeMember("inconnu"), "membre inconnu : rien ne se passe");
		gone.removeMember("maire");
		check(gone.mayorId == null && MayorBrain.think(gone, cat, new Fake((x, z) -> 70, (x, z) -> false), "minecraft:plains", 6).isEmpty(), "maire disparu : mairie vacante, plus de décisions");
		check(Role.byItem("minecraft:emerald") == null && Role.byItem("minecraft:gold_ingot") == Role.MERCHANT, "l'émeraude se donne (don), le lingot d'or fait un marchand");

		// --- sauvegarde des sacs
		Town sv = new Town("sv", "Sv");
		sv.members.put("b1", Role.BUILDER);
		sv.inventoryOf("b1").add("minecraft:oak_door", 2);
		sv.inventoryOf("b1").add("minecraft:cobblestone", 70);
		Town back = TownStore.read(TownStore.write(List.of(sv)), cat).get(0);
		check(back.inventoryOf("b1").count("minecraft:cobblestone") == 70 && back.inventoryOf("b1").count("minecraft:oak_door") == 2, "sacs sauvegardés");

		// --- dépôt : sur la place tant qu'il n'y a pas d'entrepôt, puis dans le coffre de l'entrepôt
		check(back.depotChest() == null, "pas d'entrepôt : le dépôt est la place");
		BuildSite ent = new BuildSite("sv-c1", cat.get("entrepot"), Theme.PLAINS, 10, 64, 20, 0, 0);
		back.sites.add(ent);
		check(back.depotChest() == null, "entrepôt pas fini : toujours la place");
		while (!ent.complete()) ent.advance();
		int[] chest = back.depotChest();
		check(chest != null && chest[0] >= 10 && chest[0] <= 14 && chest[1] == 65 && chest[2] >= 20 && chest[2] <= 24, "coffre de l'entrepôt trouvé : " + java.util.Arrays.toString(chest));

		// --- chaîne complète : AUCUN matériau au départ ; bûcherons, mineurs, bergers récoltent, déposent ; les constructeurs fetch + bâtissent
		BuilderLogic logic = new BuilderLogic(planner);
		int worst = 0;
		String worstName = "";
		for (BuildPlan p : cat.all()) {
			for (Theme th : Theme.values()) {
				int iters = runPipeline(p, th, logic, planner);
				check(iters > 0, "chaîne complète bloquée : " + p.name + "/" + th);
				if (iters > worst) {
					worst = iters;
					worstName = p.name + "/" + th;
				}
			}
		}
		System.out.println("  chaîne complète (récolte -> dépôt -> fabrication -> construction) : le plus long = " + worst + " tours (" + worstName + ")");
	}

	/** Renvoie le nombre de tours nécessaires pour finir le plan, ou -1 si ça bloque. */
	static int runPipeline(BuildPlan plan, Theme theme, BuilderLogic logic, ConversionPlanner planner) {
		return runPipeline(plan, theme, logic, planner, false);
	}

	/** scarce = vrai : ni charbon ni émeraude dans le monde ; les mineurs ne les trouvent qu'en fouillant (récompenses de travail). */
	static int runPipeline(BuildPlan plan, Theme theme, BuilderLogic logic, ConversionPlanner planner, boolean scarce) {
		Random rng = new Random(42);
		Town t = new Town("sim", "Sim");
		t.mayorId = "m";
		String[] ids = {"b1", "b2", "l1", "l2", "m1", "m2", "s1"};
		Role[] roles = {Role.BUILDER, Role.BUILDER, Role.LUMBERJACK, Role.LUMBERJACK, Role.MINER, Role.MINER, Role.SHEPHERD};
		for (int i = 0; i < ids.length; i++) t.members.put(ids[i], roles[i]);
		if (!scarce) t.stockpile.add("minecraft:emerald", Settlement.TREASURY_EMERALDS);
		BuildSite site = new BuildSite("sim-c1", plan, theme, 0, 64, 0, 0, 0);
		t.sites.add(site);
		for (int iter = 1; iter <= 60000; iter++) {
			t.syncBoard(planner);
			for (int i = 0; i < ids.length; i++) {
				Role r = roles[i];
				Inventory inv = t.inventoryOf(ids[i]);
				if (r == Role.BUILDER) {
					if (logic.needsFetch(site, inv)) {
						logic.fetch(t, site, inv);
						if (logic.needsFetch(site, inv)) continue; // on attend au dépôt
					}
					BuilderLogic.Action a = logic.next(site, inv);
					if (a.kind() == BuilderLogic.Kind.PLACE) site.advance();
				} else {
					WorkerLogic.Action a = WorkerLogic.next(t, ids[i], r, inv);
					if (a.kind() == WorkerLogic.Kind.HARVEST) {
						String want = a.request().item;
						boolean noOre = scarce && (want.equals("minecraft:coal") || want.equals("minecraft:emerald"));
						if (noOre) {
							// fouille : on casse de la pierre au hasard et on tire les récompenses (ciblées, chance triplée)
							if (!WorkRewards.canFind(r, want)) return -4;
							inv.add("minecraft:cobblestone", 1);
							for (Map.Entry<String, Integer> rw : WorkRewards.roll(r, want, 1.0, 3.0, rng).entrySet()) {
								int rest = inv.add(rw.getKey(), rw.getValue());
								if (rest > 0) t.stockpile.add(rw.getKey(), rest);
							}
						} else if (inv.add(want, 1) != 0) return -2;
						if (r != Role.BUILDER && !noOre) {
							for (Map.Entry<String, Integer> rw : WorkRewards.roll(r, rng).entrySet()) {
								int rest = inv.add(rw.getKey(), rw.getValue());
								if (rest > 0) t.stockpile.add(rw.getKey(), rest);
							}
						}
					} else if (a.kind() == WorkerLogic.Kind.DEPOSIT) {
						t.deposit(ids[i]);
					}
				}
				if (inv.total() > Inventory.SLOTS * 64) return -3;
			}
			if (site.complete()) return iter;
		}
		return -1;
	}

	/** Surface du rectangle englobant de tous les bâtiments. */
	static long boundingArea(Town t) {
		int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		for (BuildSite s : t.sites) {
			int[] f = s.footprint();
			x0 = Math.min(x0, f[0]);
			z0 = Math.min(z0, f[1]);
			x1 = Math.max(x1, f[2]);
			z1 = Math.max(z1, f[3]);
		}
		return (long) (x1 - x0 + 1) * (z1 - z0 + 1);
	}

	static Town growTown(PlanCatalog cat, boolean dense, TerrainView terrain, String biome, int pop, List<String> order) {
		Town t = new Town("d", "Dville");
		t.mayorId = "maire";
		t.members.put("maire", Role.MAYOR);
		String[] ids = {"b1", "b2", "l1", "m1", "s1", "e1", "c1", "g1"};
		Role[] roles = {Role.BUILDER, Role.BUILDER, Role.LUMBERJACK, Role.MINER, Role.SHEPHERD, Role.BREEDER, Role.COOK, Role.GUARD};
		for (int i = 0; i < ids.length; i++) t.members.put(ids[i], roles[i]);
		t.centerX = 100;
		t.centerZ = -50;
		t.dense = dense;
		for (int i = 0; i < 200; i++) {
			var c = MayorBrain.think(t, cat, terrain, biome, pop);
			if (c.isEmpty()) break;
			order.add(c.get().site().plan.name);
			BuildSite s = c.get().site();
			s.prepDone = true;
			while (!s.complete()) s.advance();
		}
		return t;
	}

	/** Phase D : ville compacte (îlots, rues étroites, murs mitoyens, bâtiment central). */
	static void phaseD(PlanCatalog cat, ConversionPlanner planner) {
		// --- murs mitoyens : deux petites maisons côte à côte partagent une ligne de mur
		BuildPlan pm = cat.get("petite_maison");
		BuildSite a = new BuildSite("a", pm, Theme.PLAINS, 0, 70, 0, 0, 0);
		BuildSite b = new BuildSite("b", pm, Theme.PLAINS, 4, 70, 0, 0, 0); // la colonne x = 4 est commune
		int sh = SharedWalls.compatibleOverlap(b, List.of(a));
		check(sh > 0, "deux maisons collées partagent des blocs de mur : " + sh);
		BuildSite c = new BuildSite("c", pm, Theme.PLAINS, 3, 70, 0, 0, 0); // 2 colonnes communes : intérieurs superposés
		check(SharedWalls.compatibleOverlap(c, List.of(a)) == -1, "intérieurs superposés : refusé");
		BuildSite d = new BuildSite("d", pm, Theme.PLAINS, 0, 70, 4, 0, 0); // la rangée z = 4 est commune... avec la porte de a
		check(SharedWalls.compatibleOverlap(d, List.of(a)) == -1, "porte sur le mur partagé : refusé");
		Town two = new Town("two", "Two");
		two.dense = true;
		two.sites.add(a);
		two.sites.add(b);
		int totalBefore = b.total();
		int dropped = SharedWalls.apply(two, b);
		check(dropped == sh && b.total() == totalBefore - dropped, "les blocs déjà fournis par le voisin sont retirés : " + dropped + " / " + sh);
		java.util.Set<Long> union = new HashSet<>(a.blockMap().keySet());
		int dup = 0;
		for (Long k : b.blockMap().keySet()) if (!union.add(k)) dup++;
		check(dup == 0, "plus aucun bloc en double après le mur mitoyen");

		for (String biome : new String[]{"minecraft:plains", "minecraft:snowy_plains", "minecraft:desert"}) {
			Fake terrain = new Fake((x, z) -> 64 + (int) Math.round(1.5 * Math.sin(x / 9.0) + 1.0 * Math.cos(z / 7.0)),
					(x, z) -> (x - 140) * (x - 140) + (z + 50) * (z + 50) < 8 * 8);
			List<String> order = new ArrayList<>();
			Town t = growTown(cat, true, terrain, biome, 12, order);
			check(!order.isEmpty() && order.get(0).equals("mairie"), biome + " : le premier bâtiment est la mairie : " + order);
			BuildSite hall = t.sites.get(0);
			int[] hf = hall.footprint();
			check(Math.abs((hf[0] + hf[2]) / 2 - t.centerX) <= 1 && Math.abs((hf[1] + hf[3]) / 2 - t.centerZ) <= 1, biome + " : mairie au centre");
			int ringCells = 0;
			for (RoadCell rc : t.roads.values()) if (Math.max(Math.abs(rc.x - t.centerX), Math.abs(rc.z - t.centerZ)) == DenseLayout.STREET) ringCells++;
			check(ringCells >= 40, biome + " : anneau de rues autour de la mairie (" + ringCells + " cases)");
			// aucun chevauchement d'intérieurs, murs mitoyens compatibles, blocs sans doublon
			java.util.Set<Long> allKeys = new HashSet<>();
			long keyCount = 0;
			int touching = 0;
			for (int i = 0; i < t.sites.size(); i++) {
				BuildSite s = t.sites.get(i);
				keyCount += s.blockMap().size();
				allKeys.addAll(s.blockMap().keySet());
				int[] f = s.footprint();
				check(i == 0 || SharedWalls.compatibleOverlap(s, t.sites.subList(0, i)) >= 0, biome + " : " + s.plan.name + " compatible avec ses voisins");
				for (int j = 0; j < i; j++) {
					int[] g = t.sites.get(j).footprint();
					int x0 = Math.max(f[0], g[0]), x1 = Math.min(f[2], g[2]), z0 = Math.max(f[1], g[1]), z1 = Math.min(f[3], g[3]);
					if (x0 <= x1 && z0 <= z1) {
						touching++;
						check(x1 - x0 == 0 || z1 - z0 == 0, biome + " : contact mince entre " + s.plan.name + " et " + t.sites.get(j).plan.name);
					}
				}
				if (i > 0) {
					// dans un îlot : jamais sur une rue ni dans l'îlot central ; porte sur une rue ; porte non bouchée
					for (int x = f[0]; x <= f[2]; x++) {
						for (int z = f[1]; z <= f[3]; z++) {
							check(!DenseLayout.isStreet(t, x, z), biome + " : " + s.plan.name + " empiète sur une rue");
							check(Math.abs(x - t.centerX) > DenseLayout.HALF || Math.abs(z - t.centerZ) > DenseLayout.HALF, biome + " : " + s.plan.name + " dans l'îlot central");
							check(!terrain.blocked(x, z), biome + " : bâtiment dans le lac");
						}
					}
					int[] fr = s.frontCell();
					check(DenseLayout.isStreet(t, fr[0], fr[1]), biome + " : la porte de " + s.plan.name + " donne sur une rue");
					for (BuildSite o : t.sites) {
						int[] g = o.footprint();
						check(!(fr[0] >= g[0] && fr[0] <= g[2] && fr[1] >= g[1] && fr[1] <= g[3]), biome + " : porte de " + s.plan.name + " bouchée");
					}
				}
			}
			check(keyCount == allKeys.size(), biome + " : aucun bloc en double dans toute la ville (" + keyCount + " / " + allKeys.size() + ")");
			check(touching > 0, biome + " : des bâtiments se touchent (murs mitoyens) : " + touching);
			// toutes les portes sont reliées à l'anneau par des cases de chemin
			Set<Long> reach = new HashSet<>();
			java.util.ArrayDeque<long[]> q = new java.util.ArrayDeque<>();
			for (RoadCell rc : t.roads.values()) {
				if (Math.max(Math.abs(rc.x - t.centerX), Math.abs(rc.z - t.centerZ)) == DenseLayout.STREET && reach.add(rc.key())) q.add(new long[]{rc.x, rc.z});
			}
			while (!q.isEmpty()) {
				long[] cc = q.poll();
				for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					long k = RoadCell.key((int) cc[0] + dd[0], (int) cc[1] + dd[1]);
					if (t.roads.containsKey(k) && reach.add(k)) q.add(new long[]{cc[0] + dd[0], cc[1] + dd[1]});
				}
			}
			for (BuildSite s : t.sites) {
				if (s == hall) continue;
				int[] fr = s.frontCell();
				check(reach.contains(RoadCell.key(fr[0], fr[1])), biome + " : la porte de " + s.plan.name + " est reliée au réseau de rues");
			}
			for (RoadCell rc : t.roads.values()) {
				check(DenseLayout.isStreet(t, rc.x, rc.z), biome + " : chemin hors des rues");
				for (BuildSite s : t.sites) {
					int[] f = s.footprint();
					check(!(rc.x >= f[0] && rc.x <= f[2] && rc.z >= f[1] && rc.z <= f[3]), biome + " : rue dans un bâtiment");
				}
			}
			// terrassement : jamais sur les murs des voisins
			for (BuildSite s : t.sites) {
				java.util.Set<Long> others = t.footprintCellsExcept(s);
				for (SitePrep.Op op : SitePrep.compute(s, terrain, others)) check(!others.contains(RoadCell.key(op.x(), op.z())), biome + " : terrassement sur un voisin");
			}
			// plus compact que la ville espacée
			List<String> order2 = new ArrayList<>();
			Town spaced = growTown(cat, false, terrain, biome, 12, order2);
			long dense = boundingArea(t), spread = boundingArea(spaced);
			System.out.println("  compacte " + biome + " : " + t.sites.size() + " bâtiments sur " + dense + " cases, contre " + spaced.sites.size() + " sur " + spread + " (espacée) ; " + touching + " contacts ; " + t.roads.size() + " cases de rue");
			check(dense < spread, biome + " : la ville compacte occupe moins de place (" + dense + " < " + spread + ")");
			int housing = 0;
			for (BuildSite s : t.sites) if (s.plan.meta.category().equals("house")) housing += s.plan.meta.capacity();
			check(housing >= 16, biome + " : assez de lits en ville compacte (" + housing + ") " + order);
			// sauvegarde : mode, murs mitoyens et progression
			BuildSite mid = t.sites.get(t.sites.size() - 1);
			Town rb = TownStore.read(TownStore.write(List.of(t)), cat).get(0);
			check(rb.dense && rb.sites.size() == t.sites.size() && rb.sites.get(rb.sites.size() - 1).total() == mid.total(), biome + " : sauvegarde de la ville compacte (blocs mitoyens retirés à l'identique)");
		}

		// centre déjà occupé (un village existant) : la ville compacte se décale jusqu'à trouver de la place
		Fake occupied = new Fake((x, z) -> 70, (x, z) -> (x - 100) * (x - 100) + (z + 50) * (z + 50) < 10 * 10);
		Town reloc = new Town("r", "R");
		reloc.mayorId = "m";
		reloc.members.put("m", Role.MAYOR);
		reloc.centerX = 100;
		reloc.centerZ = -50;
		reloc.dense = true;
		var firstR = MayorBrain.think(reloc, cat, occupied, "minecraft:plains", 6);
		check(firstR.isPresent() && firstR.get().site().plan.name.equals("mairie"), "centre occupé : la mairie est quand même construite");
		check(Math.hypot(reloc.centerX - 100, reloc.centerZ + 50) >= 10, "le centre de la ville a été décalé hors de la zone occupée : " + reloc.centerX + "," + reloc.centerZ);
		if (firstR.isPresent()) {
			int[] rf = firstR.get().site().footprint();
			for (int x = rf[0]; x <= rf[2]; x++) for (int z = rf[1]; z <= rf[3]; z++) check(!occupied.blocked(x, z), "mairie hors de la zone occupée");
		}
		Fake allBlocked = new Fake((x, z) -> 70, (x, z) -> true);
		Town none = new Town("n", "N");
		none.mayorId = "m";
		none.members.put("m", Role.MAYOR);
		none.dense = true;
		check(MayorBrain.think(none, cat, allBlocked, "minecraft:plains", 6).isEmpty() && none.centerX == 0 && none.sites.isEmpty(), "aucune place : rien n'est construit et le centre ne bouge pas");

		// lits : des paires tête/pied bien formées dans tous les plans, et capacité = nombre de lits
		for (BuildPlan p : cat.all()) {
			for (Theme th : Theme.values()) {
				for (int rot = 0; rot < 4; rot++) {
					List<PlanBlock> bl = p.blocks(th, rot);
					Map<String, PlanBlock> at = new java.util.HashMap<>();
					for (PlanBlock pb : bl) at.put(pb.dx() + "," + pb.dy() + "," + pb.dz(), pb);
					for (PlanBlock pb : bl) {
						if (!pb.block().contains("white_bed[part=foot")) continue;
						String facing = pb.block().replaceAll(".*facing=(\\w+).*", "$1");
						int dx = facing.equals("east") ? 1 : facing.equals("west") ? -1 : 0;
						int dz = facing.equals("south") ? 1 : facing.equals("north") ? -1 : 0;
						PlanBlock head = at.get((pb.dx() + dx) + "," + pb.dy() + "," + (pb.dz() + dz));
						check(head != null && head.block().contains("part=head") && head.block().contains("facing=" + facing),
								p.name + " rot" + rot + " : le lit (pied " + pb.dx() + "," + pb.dz() + ", " + facing + ") a sa tête à côté");
					}
				}
			}
			if (p.meta.category().equals("house")) {
				int beds = p.totalNeeds(Theme.PLAINS).getOrDefault("minecraft:white_bed", 0);
				check(beds == p.meta.capacity(), p.name + " : capacité (" + p.meta.capacity() + ") = nombre de lits (" + beds + ")");
			}
		}

		// règle « lits posés » : tant que (lits finis) < 1,3 x habitants, que des maisons ; on attend si assez sont en route
		Town hr = new Town("hr", "Hr");
		hr.mayorId = "m";
		hr.members.put("m", Role.MAYOR);
		var firstHr = MayorPlanner.candidates(hr, cat, "minecraft:plains", 4);
		check(!firstHr.isEmpty() && cat.get(firstHr.get(0).plan().name).meta.category().equals("house") && firstHr.size() == 1, "aucun lit : seulement une maison");
		for (int i = 0; i < 2; i++) {
			BuildSite hs = new BuildSite("hr-c" + (i + 1), cat.get("petite_maison"), Theme.PLAINS, i * 30, 70, 0, 0, 0);
			while (!hs.complete()) hs.advance();
			hr.sites.add(hs);
		}
		BuildSite inProgress = new BuildSite("hr-c3", cat.get("petite_maison"), Theme.PLAINS, 90, 70, 0, 0, 0);
		hr.sites.add(inProgress);
		check(MayorPlanner.candidates(hr, cat, "minecraft:plains", 4).isEmpty(), "4 lits finis + 2 en route pour 4 habitants (objectif 6) : on attend la fin des travaux");
		while (!inProgress.complete()) inProgress.advance();
		var after = MayorPlanner.candidates(hr, cat, "minecraft:plains", 4);
		check(!after.isEmpty() && after.get(0).plan().name.equals("puits"), "6 lits finis pour 4 habitants : le maire passe au puits (" + (after.isEmpty() ? "-" : after.get(0).plan().name) + ")");
		check(MayorPlanner.candidates(hr, cat, "minecraft:plains", 10).get(0).plan().meta.category().equals("house"), "la population grandit : retour aux maisons");

		// coffre de ville : un coffre + une émeraude, fabriqué au dépôt
		Stock tc = new Stock();
		tc.add("minecraft:oak_log", 2);
		tc.add("minecraft:emerald", 1);
		check(planner.plan(tc, "livingvillages:town_chest", 1).complete(), "coffre de ville : 2 bûches + 1 émeraude");
		check(!planner.plan(new Stock(), "livingvillages:town_chest", 1).complete() && planner.plan(new Stock(), "livingvillages:town_chest", 1).missing().containsKey("minecraft:emerald"), "sans émeraude : manque une émeraude");

		// colons : équipe et provisions
		List<Role> crew = Settlement.crew();
		check(crew.contains(Role.MAYOR) && crew.stream().filter(r -> r == Role.BUILDER).count() == 2 && crew.contains(Role.LUMBERJACK) && crew.contains(Role.MINER), "équipe de colons");
		for (Theme th : Theme.values()) {
			Stock ss = Settlement.starterStock(th);
			check(ss.count("minecraft:" + th.wood + "_log") > 0 && ss.count("minecraft:emerald") == Settlement.TREASURY_EMERALDS, "provisions de colons : " + th);
		}
		Town col = new Town("col", "Colonie");
		col.dense = true;
		col.mayorId = "m";
		col.members.put("m", Role.MAYOR);
		for (int i = 0; i < crew.size(); i++) if (crew.get(i) != Role.MAYOR) col.members.put("c" + i, crew.get(i));
		Settlement.starterStock(Theme.PLAINS).asMap().forEach(col.stockpile::add);
		col.centerX = 0;
		col.centerZ = 0;
		BuilderLogic colLogic = new BuilderLogic(planner);
		var mairie = MayorBrain.think(col, cat, new Fake((x, z) -> 70, (x, z) -> false), "minecraft:plains", 5);
		check(mairie.isPresent() && mairie.get().site().plan.name.equals("mairie"), "colonie : la mairie d'abord");
		check(mairie.isPresent() && buildAll(col, mairie.get().site(), colLogic, new Inventory()) > 0, "les colons construisent la mairie avec leurs provisions de départ");

		// la ville espacée n'a jamais de mairie
		List<String> o3 = new ArrayList<>();
		growTown(cat, false, new Fake((x, z) -> 70, (x, z) -> false), "minecraft:plains", 8, o3);
		check(!o3.contains("mairie"), "mode espacé : pas de bâtiment central : " + o3);
	}

	/** Phase E : récompenses de travail, recrutement automatique, villes de grande taille. */
	static void phaseE(PlanCatalog cat, ConversionPlanner planner) {
		// --- récompenses : fréquences, quantités, chance, métiers sans table
		Random rng = new Random(7);
		int n = 100000, coal = 0, emerald = 0, coalBoost = 0, coalLuck = 0;
		for (int i = 0; i < n; i++) {
			Map<String, Integer> r = WorkRewards.roll(Role.MINER, rng);
			if (r.containsKey("minecraft:coal")) {
				coal++;
				check(r.get("minecraft:coal") >= 1 && r.get("minecraft:coal") <= 2, "1 à 2 charbons");
			}
			if (r.containsKey("minecraft:emerald")) emerald++;
			if (WorkRewards.roll(Role.MINER, "minecraft:coal", 1.0, 3.0, rng).containsKey("minecraft:coal")) coalBoost++;
			if (WorkRewards.roll(Role.MINER, null, 2.0, 1.0, rng).containsKey("minecraft:coal")) coalLuck++;
		}
		check(Math.abs(coal / (double) n - 0.12) < 0.01, "le mineur trouve du charbon environ 12 % du temps : " + coal / (double) n);
		check(Math.abs(emerald / (double) n - 0.015) < 0.004, "et des émeraudes environ 1,5 % du temps : " + emerald / (double) n);
		check(Math.abs(coalBoost / (double) n - 0.36) < 0.015, "fouille ciblée : chance triplée : " + coalBoost / (double) n);
		check(Math.abs(coalLuck / (double) n - 0.24) < 0.015, "chance x2 : " + coalLuck / (double) n);
		check(WorkRewards.roll(Role.BUILDER, new Random(1)).isEmpty() && WorkRewards.roll(Role.MAYOR, new Random(1)).isEmpty(), "pas de récompense pour constructeur et maire");
		check(WorkRewards.canFind(Role.MINER, "minecraft:coal") && !WorkRewards.canFind(Role.LUMBERJACK, "minecraft:coal"), "qui peut trouver quoi");
		check(Role.MINER.canProduce("minecraft:emerald") && Role.anyProduces("minecraft:emerald"), "le mineur peut prendre une commande d'émeraudes");
		check(new Random(3).nextInt() == new Random(3).nextInt() && WorkRewards.roll(Role.FARMER, new Random(5)).equals(WorkRewards.roll(Role.FARMER, new Random(5))), "tirages reproductibles avec la même graine");

		// --- monde rare : sans charbon ni émeraude dans le sol, tout de même des torches et des coffres de ville
		BuilderLogic logic = new BuilderLogic(planner);
		for (String name : new String[]{"petite_maison", "maison_moyenne", "entrepot", "mairie", "igloo"}) {
			for (Theme th : new Theme[]{Theme.PLAINS, Theme.DESERT}) {
				int iters = runPipeline(cat.get(name), th, logic, planner, true);
				check(iters > 0, "monde rare : " + name + "/" + th + " se construit grâce aux récompenses de fouille (code " + iters + ")");
			}
		}

		// --- recrutement automatique
		check(Recruiter.targets(30).get(Role.BUILDER) == 4 && Recruiter.targets(30).get(Role.LUMBERJACK) == 3 && Recruiter.targets(8).get(Role.SHEPHERD) == 0, "effectifs visés");
		Town rc = new Town("rc", "Rc");
		rc.mayorId = "m";
		rc.members.put("m", Role.MAYOR);
		List<String> free = new ArrayList<>();
		for (int i = 0; i < 40; i++) free.add(String.format("v%02d", i));
		Map<String, Role> got = Recruiter.assign(rc, free, 41);
		Map<Role, Integer> counts = new EnumMap<>(Role.class);
		for (Role r : got.values()) counts.merge(r, 1, Integer::sum);
		Map<Role, Integer> goal = Recruiter.targets(41);
		for (Role r : goal.keySet()) check(counts.getOrDefault(r, 0) == goal.get(r), "recrutement : " + r + " = " + counts.getOrDefault(r, 0) + " / " + goal.get(r));
		check(got.size() < free.size() && !got.containsValue(Role.MAYOR), "les autres restent des habitants ordinaires, jamais de second maire");
		for (Map.Entry<String, Role> e : got.entrySet()) rc.members.put(e.getKey(), e.getValue());
		check(Recruiter.assign(rc, free.stream().filter(x -> !got.containsKey(x)).toList(), 41).isEmpty(), "plus rien à recruter une fois les effectifs atteints");
		rc.autoRecruit = true;
		check(TownStore.read(TownStore.write(List.of(rc)), cat).get(0).autoRecruit, "recrutement automatique sauvegardé");

		// --- une ville entière : 60 habitants, terrain plat, mode compact
		Fake flat = new Fake((x, z) -> 70, (x, z) -> false);
		long start = System.nanoTime();
		List<String> order = new ArrayList<>();
		Town city = growTown(cat, true, flat, "minecraft:plains", 60, order);
		long ms = (System.nanoTime() - start) / 1_000_000;
		int beds = 0;
		for (BuildSite s : city.sites) if (s.plan.meta.category().equals("house")) beds += s.plan.meta.capacity();
		System.out.println("  ville de 60 habitants : " + city.sites.size() + " bâtiments, étendue " + city.extent() + " blocs, " + beds + " lits, " + city.roads.size() + " cases de rue, calculée en " + ms + " ms");
		check(city.sites.size() > 40 && beds >= 78, "grande ville : plus de 40 bâtiments et au moins 78 lits (" + city.sites.size() + " / " + beds + ")");
		check(city.extent() >= DenseLayout.PITCH && city.extent() <= DenseLayout.MAX_RINGS * DenseLayout.PITCH + 7, "la ville s'étend au-delà de l'anneau central sans sortir du plan : étendue " + city.extent());
		check(ms < 30000, "décisions de la grande ville en moins de 30 s : " + ms + " ms");
		for (int i = 1; i < city.sites.size(); i++) {
			check(SharedWalls.compatibleOverlap(city.sites.get(i), city.sites.subList(0, i)) >= 0, "grande ville : " + city.sites.get(i).plan.name + " compatible avec ses voisins");
			int[] fr = city.sites.get(i).frontCell();
			check(DenseLayout.isStreet(city, fr[0], fr[1]), "grande ville : la porte donne sur une rue");
		}
		check(city.sites.size() <= Town.MAX_SITES, "plafond de la ville respecté");

		// --- une très grande ville : 250 habitants (le plafond de bâtiments s'applique)
		long start2 = System.nanoTime();
		Town metropolis = growTown(cat, true, flat, "minecraft:plains", 250, new ArrayList<>());
		long ms2 = (System.nanoTime() - start2) / 1_000_000;
		System.out.println("  ville de 250 habitants : " + metropolis.sites.size() + " bâtiments, étendue " + metropolis.extent() + " blocs, " + metropolis.roads.size() + " cases de rue, calculée en " + ms2 + " ms");
		check(metropolis.sites.size() == Town.MAX_SITES, "très grande ville : le plafond de " + Town.MAX_SITES + " bâtiments est atteint (" + metropolis.sites.size() + ")");
		check(ms2 < 120000, "calcul de la très grande ville en moins de 2 minutes : " + ms2 + " ms");
		for (int i = 1; i < metropolis.sites.size(); i++) {
			check(SharedWalls.compatibleOverlap(metropolis.sites.get(i), metropolis.sites.subList(0, i)) >= 0, "très grande ville : " + metropolis.sites.get(i).plan.name + " compatible avec ses voisins");
		}
	}

	/** Faux terrain : une fonction de hauteur et une zone interdite (lac). */
	static final class Fake implements TerrainView {
		final java.util.function.IntBinaryOperator height;
		final java.util.function.BiPredicate<Integer, Integer> lake;
		final Set<String> solidObstacles = new HashSet<>();

		Fake(java.util.function.IntBinaryOperator h, java.util.function.BiPredicate<Integer, Integer> lake) {
			this.height = h;
			this.lake = lake;
		}

		public int groundY(int x, int z) {
			return height.applyAsInt(x, z);
		}

		public boolean blocked(int x, int z) {
			return lake.test(x, z);
		}

		public boolean isClear(int x, int y, int z) {
			return y > height.applyAsInt(x, z) && !solidObstacles.contains(x + "," + y + "," + z);
		}
	}

	static boolean overlap(BuildSite a, BuildSite b, int margin) {
		int[] f = a.footprint(), g = b.footprint();
		return f[0] - margin <= g[2] && f[2] + margin >= g[0] && f[1] - margin <= g[3] && f[3] + margin >= g[1];
	}

	static void phaseB(PlanCatalog cat, ConversionPlanner planner) {
		// métadonnées des plans
		BuildPlan puits = cat.get("puits");
		check(puits.meta.category().equals("well") && puits.meta.max() == 1 && puits.meta.minPopulation() == 2 && puits.meta.priority() == 10, "méta du puits");
		check(cat.get("bergerie").meta.serves().equals(List.of("shepherd")), "bergerie sert le berger");
		check(cat.get("petite_maison").meta.capacity() == 2 && cat.get("maison_moyenne").meta.capacity() == 4, "capacités des maisons");

		for (String biome : new String[]{"minecraft:plains", "minecraft:snowy_plains", "minecraft:desert"}) {
			Town t = new Town("b", "Bville");
			t.mayorId = "maire";
			t.members.put("maire", Role.MAYOR);
			String[] ids = {"b1", "b2", "l1", "m1", "s1", "e1", "c1", "g1"};
			Role[] roles = {Role.BUILDER, Role.BUILDER, Role.LUMBERJACK, Role.MINER, Role.SHEPHERD, Role.BREEDER, Role.COOK, Role.GUARD};
			for (int i = 0; i < ids.length; i++) t.members.put(ids[i], roles[i]);
			t.centerX = 100;
			t.centerZ = -50;
			// terrain vallonné doux + un lac
			Fake terrain = new Fake((x, z) -> 64 + (int) Math.round(1.5 * Math.sin(x / 9.0) + 1.0 * Math.cos(z / 7.0)),
					(x, z) -> (x - 140) * (x - 140) + (z + 50) * (z + 50) < 8 * 8);
			List<String> order = new ArrayList<>();
			int pop = 12;
			for (int i = 0; i < 40; i++) {
				var c = MayorBrain.think(t, cat, terrain, biome, pop);
				if (c.isEmpty()) break;
				BuildSite site = c.get().site();
				order.add(site.plan.name);
				check(site.priority == site.plan.meta.priority() && !site.prepDone, "site neuf : priorité et terrassement à faire");
				// simulation : on construit instantanément
				while (!site.complete()) site.advance();
				site.prepDone = true;
			}
			check(!order.isEmpty() && cat.get(order.get(0)).meta.category().equals("house") && order.contains("puits"),
					biome + " : d'abord des maisons (lits posés), puits ensuite : " + order);
			int firstNonHouse = 0;
			while (firstNonHouse < order.size() && cat.get(order.get(firstNonHouse)).meta.category().equals("house")) firstNonHouse++;
			int bedsBefore = 0;
			for (int i = 0; i < firstNonHouse; i++) bedsBefore += cat.get(order.get(i)).meta.capacity();
			check(bedsBefore >= (int) Math.ceil(MayorPlanner.BEDS_PER_VILLAGER * pop), biome + " : au moins 1,3 lit par habitant avant tout autre bâtiment (" + bedsBefore + ")");
			int housing = 0, farming = 0, defense = 0;
			Map<String, Integer> counts = new java.util.HashMap<>();
			for (BuildSite s : t.sites) {
				counts.merge(s.plan.name, 1, Integer::sum);
				switch (s.plan.meta.category()) {
					case "house" -> housing += s.plan.meta.capacity();
					case "farm" -> farming += s.plan.meta.capacity();
					case "defense" -> defense += s.plan.meta.capacity();
					default -> {
					}
				}
			}
			check(housing >= (int) Math.ceil(1.3 * pop), biome + " : assez de lits (" + housing + "/" + pop + ") " + order);
			check(farming >= pop, biome + " : assez de nourriture (" + farming + "/" + pop + ")");
			check(defense >= pop, biome + " : assez de défense (" + defense + "/" + pop + ")");
			for (var e : counts.entrySet()) check(e.getValue() <= cat.get(e.getKey()).meta.max(), biome + " : trop de " + e.getKey());
			for (String must : new String[]{"bergerie", "enclos", "scierie", "cuisine", "entrepot", "marche", "forge"}) {
				check(counts.containsKey(must), biome + " : " + must + " construit (" + order + ")");
			}
			boolean igloo = counts.containsKey("igloo");
			check(igloo == biome.contains("snow") || (!igloo && !biome.contains("snow")), biome + " : igloo seulement dans la neige (" + igloo + ")");
			// aucun chevauchement, toujours à distance du lac, sol assez plat
			for (int i = 0; i < t.sites.size(); i++) {
				for (int j = i + 1; j < t.sites.size(); j++) {
					check(!overlap(t.sites.get(i), t.sites.get(j), SitePlacer.MARGIN - 1), biome + " : chevauchement " + t.sites.get(i).plan.name + "/" + t.sites.get(j).plan.name);
				}
				int[] f = t.sites.get(i).footprint();
				for (int x = f[0]; x <= f[2]; x++) for (int z = f[1]; z <= f[3]; z++) check(!terrain.blocked(x, z), biome + " : bâtiment dans le lac");
			}
			// les portes regardent vers le centre : le devant est plus près du centre que le derrière
			for (BuildSite s : t.sites) {
				int[] fr = s.frontCell();
				int[] f = s.footprint();
				double cx = (f[0] + f[2]) / 2.0, cz = (f[1] + f[3]) / 2.0;
				double dFront = Math.hypot(fr[0] - t.centerX, fr[1] - t.centerZ), dMid = Math.hypot(cx - t.centerX, cz - t.centerZ);
				check(dFront <= dMid + 0.01 || dMid < 3, biome + " : porte de " + s.plan.name + " tournée vers le centre");
			}
			// routes : chaque porte est reliée à la place centrale par des cases de chemin
			Set<Long> reach = new HashSet<>();
			java.util.ArrayDeque<long[]> q = new java.util.ArrayDeque<>();
			for (int x = t.centerX - RoadPlanner.PLAZA; x <= t.centerX + RoadPlanner.PLAZA; x++) for (int z = t.centerZ - RoadPlanner.PLAZA; z <= t.centerZ + RoadPlanner.PLAZA; z++) {
				reach.add(RoadCell.key(x, z));
				q.add(new long[]{x, z});
			}
			while (!q.isEmpty()) {
				long[] c = q.poll();
				for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					long k = RoadCell.key((int) c[0] + d[0], (int) c[1] + d[1]);
					if (t.roads.containsKey(k) && reach.add(k)) q.add(new long[]{c[0] + d[0], c[1] + d[1]});
				}
			}
			for (BuildSite s : t.sites) {
				int[] fr = s.frontCell();
				boolean near = Math.abs(fr[0] - t.centerX) <= RoadPlanner.PLAZA && Math.abs(fr[1] - t.centerZ) <= RoadPlanner.PLAZA;
				check(near || reach.contains(RoadCell.key(fr[0], fr[1])), biome + " : la porte de " + s.plan.name + " n'est pas reliée au centre");
			}
			for (RoadCell rc : t.roads.values()) {
				check(!terrain.blocked(rc.x, rc.z), "chemin dans le lac");
				for (BuildSite s : t.sites) {
					int[] f = s.footprint();
					check(!(rc.x >= f[0] && rc.x <= f[2] && rc.z >= f[1] && rc.z <= f[3]), "chemin dans un bâtiment");
				}
			}
			// terrassement : remblais sous le sol, déblais au-dessus
			for (BuildSite s : t.sites) {
				for (SitePrep.Op op : SitePrep.compute(s, terrain)) {
					int g = terrain.groundY(op.x(), op.z());
					switch (op.kind()) {
						case DIRT -> check(op.y() > g && op.y() < s.oy, "remblai bien placé");
						case CLEAR -> check(op.y() > s.oy && !terrain.isClear(op.x(), op.y(), op.z()), "déblai bien placé");
						case GRASS -> check(op.y() == s.oy, "herbe au niveau du sol");
					}
				}
			}
			// lampadaires : toutes les 6 cases environ
			long lamps = t.roads.values().stream().filter(r -> r.lamp).count();
			check(t.roads.size() < 6 || lamps >= 1, biome + " : des lampadaires");
			System.out.println("  " + biome + " : " + order.size() + " bâtiments, " + t.roads.size() + " cases de chemin → " + order);
		}

		// file d'attente : pas plus de 2 chantiers à la fois
		Town q = new Town("q", "Qville");
		q.mayorId = "m";
		q.members.put("m", Role.MAYOR);
		Fake flat = new Fake((x, z) -> 70, (x, z) -> false);
		check(MayorBrain.think(q, cat, flat, "minecraft:plains", 6).isPresent(), "1er chantier");
		check(MayorBrain.think(q, cat, flat, "minecraft:plains", 6).isPresent(), "2e chantier");
		check(MayorBrain.think(q, cat, flat, "minecraft:plains", 6).isEmpty(), "pas de 3e chantier tant que 2 sont en cours");
		q.auto = false;
		for (BuildSite s : q.sites) while (!s.complete()) s.advance();
		check(MayorBrain.think(q, cat, flat, "minecraft:plains", 6, true).isPresent(), "/lv decide : décision forcée même en mode manuel");
		for (BuildSite s : q.sites) while (!s.complete()) s.advance();
		check(MayorBrain.think(q, cat, flat, "minecraft:plains", 6).isEmpty(), "mode manuel : le maire ne décide pas");

		// puits déjà présent dans le village : le maire n'en construit pas, et la ville se souvient de ce détail
		Town pw = new Town("pw", "Pw");
		pw.mayorId = "m";
		pw.members.put("m", Role.MAYOR);
		pw.externalWell = true;
		var firstPw = MayorBrain.think(pw, cat, flat, "minecraft:plains", 6);
		check(firstPw.isPresent() && !firstPw.get().site().plan.meta.category().equals("well"), "puits existant : pas de nouveau puits (" + (firstPw.isPresent() ? firstPw.get().site().plan.name : "-") + ")");
		check(TownStore.read(TownStore.write(List.of(pw)), cat).get(0).externalWell, "puits existant sauvegardé");

		// plafond de bâtiments
		Town big = new Town("big", "Grande");
		big.mayorId = "m";
		big.members.put("m", Role.MAYOR);
		for (int i = 0; i < Town.MAX_SITES; i++) {
			BuildSite filler = new BuildSite("big-c" + i, cat.get("petite_maison"), Theme.PLAINS, i * 20, 70, 0, 0, 0);
			while (!filler.complete()) filler.advance();
			big.sites.add(filler);
		}
		check(MayorBrain.think(big, cat, flat, "minecraft:plains", 60).isEmpty(), "plafond de bâtiments par ville");

		// constructeurs : répartition sur les chantiers
		Town w = new Town("w", "Wville");
		w.mayorId = "m";
		w.members.put("m", Role.MAYOR);
		for (int i = 0; i < 5; i++) w.members.put("b" + i, Role.BUILDER);
		MayorBrain.think(w, cat, flat, "minecraft:plains", 6);
		MayorBrain.think(w, cat, flat, "minecraft:plains", 6);
		for (BuildSite s : w.sites) s.prepDone = true;
		Map<String, BuildSite> asg = w.assignBuilders();
		Map<String, Integer> perSite = new java.util.HashMap<>();
		for (BuildSite s : asg.values()) perSite.merge(s.id, 1, Integer::sum);
		check(asg.size() == 5 && perSite.size() == 2, "5 constructeurs répartis sur 2 chantiers : " + perSite);
		for (int n : perSite.values()) check(n <= Town.MAX_BUILDERS_PER_SITE, "pas plus de 3 par chantier");
		w.sites.get(0).prepDone = false;
		check(w.assignBuilders().values().stream().noneMatch(s -> s == w.sites.get(0)), "pas de construction avant terrassement");

		// tableau des besoins : routes et chantiers ; sauvegarde v2
		w.sites.get(0).prepDone = true;
		w.stockpile.add("minecraft:oak_log", 5);
		w.syncBoard(planner);
		check(!w.board.all().isEmpty(), "besoins publiés pour les chantiers");
		w.roads.clear();
		w.roads.put(RoadCell.key(1, 2), new RoadCell(1, 2, false, true));
		RoadCell lampCell = new RoadCell(1, 3, true, true);
		w.roads.put(lampCell.key(), lampCell);
		w.syncBoard(planner);
		boolean coalRequested = false;
		for (TownBoard.Request r : w.board.all()) if (r.siteId.endsWith("-routes") && r.item.equals("minecraft:coal")) coalRequested = true;
		check(coalRequested, "le lampadaire réclame du charbon");
		RoadLogic rl = new RoadLogic(planner);
		Inventory cantonnier = new Inventory();
		check(rl.needsTorch(w, cantonnier), "lampadaire à poser : il faut une torche");
		check(rl.next(w, cantonnier).kind() == RoadLogic.Kind.FETCH, "sans torche : tour au dépôt");
		check(!rl.fetchTorches(w, cantonnier).movedAnything(), "pas de charbon : pas de torche");
		w.stockpile.add("minecraft:coal", 1);
		check(rl.fetchTorches(w, cantonnier).movedAnything() && cantonnier.has("minecraft:torch", 1), "charbon + bûches : torche dans le sac");
		var act = rl.next(w, cantonnier);
		check(act.kind() == RoadLogic.Kind.LAMP && act.cell() == lampCell, "lampadaire posé avec la torche du sac");
		lampCell.lampPlaced = true;
		check(rl.next(w, cantonnier).kind() == RoadLogic.Kind.DONE, "routes terminées");
		w.auto = false;
		w.sites.get(1).prepDone = false;
		w.sites.get(1).priority = 33;
		String saved2 = TownStore.write(List.of(w));
		Town back2 = TownStore.read(saved2, cat).get(0);
		check(!back2.auto && back2.roads.size() == 2 && back2.roads.get(lampCell.key()).lampPlaced, "sauvegarde v2 : auto et chemins");
		check(!back2.sites.get(1).prepDone && back2.sites.get(1).priority == 33, "sauvegarde v2 : priorité et terrassement");
		// ancien format v1 toujours lisible
		String v1 = "v1\ntown\tx1\tAncien\t-\t0\t64\t0\nsite\tx1-c1\tpetite_maison\tPLAINS\t0\t64\t0\t0\t7\n";
		Town old = TownStore.read(v1, cat).get(0);
		check(old.sites.size() == 1 && old.sites.get(0).done() == 7 && old.sites.get(0).prepDone && old.auto, "ancienne sauvegarde v1 lisible");
	}

	public static void main(String[] args) throws Exception {
		Path plansDir = Path.of(args.length > 0 ? args[0] : "src/main/resources/data/livingvillages/plans");
		RecipeBook book = RecipeBook.defaults();
		ConversionPlanner planner = new ConversionPlanner(book);

		// 1) 2 bûches -> 8 planches -> 3 portes (il reste 2 planches)
		Stock s = new Stock();
		s.add("minecraft:oak_log", 2);
		ConversionPlanner.Result r = planner.plan(s, "minecraft:oak_door", 3);
		check(r.complete(), "2 bûches doivent suffire pour 3 portes : manque " + r.missing());
		check(r.steps().size() == 2 && r.steps().get(0).recipe().name().startsWith("planches") && r.steps().get(0).times() == 2,
				"étape 1 = 2x planches : " + r.steps());
		check(r.steps().get(1).recipe().name().startsWith("porte") && r.steps().get(1).times() == 1, "étape 2 = 1x porte");
		check(r.leftover().count("minecraft:oak_planks") == 2, "il doit rester 2 planches : " + r.leftover());
		check(s.count("minecraft:oak_log") == 2, "le stock d'origine ne doit pas bouger");

		// 2) rien en stock : ce qui manque = 2 bûches
		r = planner.plan(new Stock(), "minecraft:oak_door", 3);
		check(!r.complete() && r.missing().equals(Map.of("minecraft:oak_log", 2)), "manque 2 bûches : " + r.missing());

		// 3) objet sans recette : manque tel quel
		r = planner.plan(new Stock(), "minecraft:diamond", 1);
		check(r.missing().equals(Map.of("minecraft:diamond", 1)), "diamant manquant");

		// 4) barrière : planches + bâtons
		s = new Stock();
		s.add("minecraft:spruce_log", 2);
		r = planner.plan(s, "minecraft:spruce_fence", 3);
		check(r.complete(), "barrières depuis 2 bûches : " + r.missing() + " " + r.steps());

		// 5) lit : laine depuis ficelle + planches
		s = new Stock();
		s.add("minecraft:white_wool", 3);
		s.add("minecraft:oak_log", 1);
		r = planner.plan(s, "minecraft:white_bed", 1);
		check(r.complete(), "lit depuis laine+bûche : " + r.missing());
		r = planner.plan(new Stock(), "minecraft:white_bed", 1);
		check(r.missing().containsKey("minecraft:white_wool") && r.missing().get("minecraft:white_wool") == 3, "lit sans rien : manque 3 laines " + r.missing());
		r = planner.plan(new Stock(), "minecraft:snow_block", 2);
		check(r.missing().equals(Map.of("minecraft:snow_block", 2)), "neige : le mineur la prend telle quelle " + r.missing());

		// 6) plans
		List<String> texts = new ArrayList<>();
		for (String name : Files.readAllLines(plansDir.resolve("index.txt"))) {
			if (name.isBlank()) continue;
			texts.add(Files.readString(plansDir.resolve(name.strip() + ".plan")));
		}
		PlanCatalog cat = PlanCatalog.fromTexts(texts);
		check(cat.all().size() == 14, "14 plans attendus : " + cat.all().size());
		BuildPlan pm = cat.get("petite_maison");
		check(pm != null && pm.sizeX == 5 && pm.sizeZ == 5 && pm.sizeY == 5, "petite maison 5x5x5");
		for (BuildPlan p : cat.all()) {
			for (Theme th : Theme.values()) {
				for (int rot = 0; rot < 4; rot++) {
					List<PlanBlock> bl = p.blocks(th, rot);
					Set<String> seen = new HashSet<>();
					int prevDy = -1;
					for (PlanBlock b : bl) {
						check(b.dx() >= 0 && b.dx() < p.footprintX(rot) && b.dz() >= 0 && b.dz() < p.footprintZ(rot),
								p.name + " rot" + rot + " hors zone : " + b);
						check(seen.add(b.dx() + "," + b.dy() + "," + b.dz()), p.name + " bloc en double " + b);
						check(b.dy() >= prevDy, p.name + " ordre des étages");
						prevDy = b.dy();
						check(!b.block().contains("@"), "joker non résolu : " + b.block());
					}
					check(!bl.isEmpty(), p.name + " vide");
				}
			}
		}
		// rotation d'un état
		check(Rotation.rotateState("minecraft:oak_door[half=lower,facing=south]", 1).equals("minecraft:oak_door[half=lower,facing=west]"), "rotation facing");
		check(Rotation.rotateState("minecraft:oak_log[axis=x]", 1).equals("minecraft:oak_log[axis=z]"), "rotation axis");
		// thème
		check(Theme.TAIGA.resolve("@door[half=lower]").equals("minecraft:spruce_door[half=lower]"), "thème taïga");
		check(Theme.fromBiome("minecraft:snowy_plains") == Theme.SNOWY && Theme.fromBiome("minecraft:desert") == Theme.DESERT, "thème par biome");
		// biome : pas d'igloo en plaine
		boolean igloo = false;
		for (BuildPlan p : cat.eligible("minecraft:plains")) if (p.name.equals("igloo")) igloo = true;
		check(!igloo, "pas d'igloo en plaine");
		igloo = false;
		for (BuildPlan p : cat.eligible("minecraft:snowy_plains")) if (p.name.equals("igloo")) igloo = true;
		check(igloo, "igloo en terrain enneigé");

		// besoins d'une petite maison (plaines) : objets attendus
		Map<String, Integer> needs = pm.totalNeeds(Theme.PLAINS);
		check(needs.getOrDefault("minecraft:oak_door", 0) == 1, "1 porte : " + needs);
		check(needs.getOrDefault("minecraft:white_bed", 0) == 2, "2 lits : " + needs);
		check(needs.getOrDefault("minecraft:oak_slab", 0) == 25, "25 dalles : " + needs);
		check(!needs.containsKey(null), "pas d'objet nul");

		// 7) chantier : avance bloc par bloc jusqu'à la fin
		BuildSite site = new BuildSite("t-c1", pm, Theme.PLAINS, 100, 64, 200, 0, 0);
		int n = 0, lastLayer = -1;
		while (!site.complete()) {
			PlanBlock b = site.peek().get();
			check(b.dy() >= lastLayer, "étage par étage");
			lastLayer = b.dy();
			site.advance();
			n++;
		}
		check(n == site.total() && site.done() == n, "tous les blocs posés");
		BuildSite fresh = new BuildSite("t-c2", pm, Theme.PLAINS, 0, 0, 0, 1, 0);
		Stock have = new Stock();
		have.add("minecraft:oak_door", 1);
		have.add("minecraft:oak_slab", 10);
		boolean ok = false;
		for (BuildSite.ItemStatus st : fresh.status(have)) if (st.item().equals("minecraft:oak_slab")) ok = st.have() == 10 && st.need() == 25 && !st.enough();
		check(ok, "statut du panneau : 10/25 dalles");

		// 8) tableau des besoins
		Town town = new Town("v1", "Testville");
		town.mayorId = "uuid-maire";
		town.members.put("uuid-maire", Role.MAYOR);
		town.members.put("uuid-b", Role.BUILDER);
		town.members.put("uuid-l", Role.LUMBERJACK);
		town.members.put("uuid-m", Role.MINER);
		BuildSite s1 = new BuildSite(town.newSiteId(), pm, Theme.PLAINS, 10, 70, 10, 0, 0);
		town.sites.add(s1);
		town.syncBoard(planner);
		check(!town.board.all().isEmpty(), "des besoins sont publiés");
		TownBoard.Request rq = town.board.claim("uuid-l", Role.LUMBERJACK);
		check(rq != null && rq.item.endsWith("_log"), "le bûcheron prend les bûches : " + (rq == null ? null : rq.item));
		TownBoard.Request rq2 = town.board.claim("uuid-m", Role.MINER);
		check(rq2 != null && rq2.item.equals("minecraft:cobblestone"), "le mineur prend la pierre");
		town.board.delivered(rq, rq.qty);
		town.stockpile.add("minecraft:cobblestone", 16);
		town.stockpile.add("minecraft:oak_log", 20);
		town.syncBoard(planner);
		boolean anyLog = false;
		for (TownBoard.Request q : town.board.all()) if (q.item.endsWith("_log")) anyLog = true;

		// 9) sauvegarde / chargement
		town.stockpile.add("minecraft:sand", 3);
		s1.advance();
		s1.advance();
		String saved = TownStore.write(List.of(town));
		List<Town> back = TownStore.read(saved, cat);
		check(back.size() == 1 && back.get(0).members.size() == 4 && back.get(0).sites.size() == 1, "aller-retour de la sauvegarde");
		check(back.get(0).sites.get(0).done() == 2 && back.get(0).stockpile.count("minecraft:sand") == 3, "progression et entrepôt conservés");
		check(back.get(0).newSiteId().equals("v1-c2"), "compteur de chantiers : " + back.get(0).newSiteId());

		// 10) marché
		Market m = new Market();
		m.credit("acheteur", 10);
		m.post("berger", "minecraft:white_wool", 5, 2);
		m.post("autre", "minecraft:white_wool", 5, 3);
		List<Market.Trade> trades = m.buy("acheteur", "minecraft:white_wool", 6);
		int bought = 0;
		for (Market.Trade t : trades) bought += t.qty();
		check(bought == 5 && m.balance("acheteur") == 0 && m.balance("berger") == 10, "marché : achat limité par les jetons : " + trades);

		// 11) simulation : un constructeur bâtit chaque plan, dans chaque style, avec des matières premières au dépôt
		BuilderLogic logic = new BuilderLogic(planner);
		for (BuildPlan p : cat.all()) {
			for (Theme th : Theme.values()) {
				Town tv = new Town("sim", "Sim");
				for (String w : RecipeBook.WOODS) tv.stockpile.add("minecraft:" + w + "_log", 200);
				tv.stockpile.add("minecraft:cobblestone", 300);
				tv.stockpile.add("minecraft:sand", 900);
				tv.stockpile.add("minecraft:white_wool", 60);
				tv.stockpile.add("minecraft:snowball", 600);
				tv.stockpile.add("minecraft:coal", 30);
				tv.stockpile.add("minecraft:dirt", 60);
				tv.stockpile.add("minecraft:emerald", 20);
				BuildSite bs = new BuildSite("sim-c1", p, th, 0, 0, 0, 0, 0);
				Inventory bag = new Inventory();
				check(buildAll(tv, bs, logic, bag) == bs.total(), "simulation " + p.name + "/" + th);
			}
		}
		// Pénurie : une petite maison sans rien -> au dépôt, rien à prendre, et on sait ce qui manque
		Town poor = new Town("poor", "Pauvre");
		BuildSite ps = new BuildSite("poor-c1", pm, Theme.PLAINS, 0, 0, 0, 0, 0);
		Inventory pbag = new Inventory();
		check(logic.needsFetch(ps, pbag), "sac vide : il faut aller au dépôt");
		Logistics.FetchResult pf = logic.fetch(poor, ps, pbag);
		check(!pf.movedAnything() && pf.missing().containsKey("minecraft:cobblestone"), "sans ressources : rien à prendre " + pf);

		phaseB(cat, planner);
		phaseC(cat, planner);
		phaseD(cat, planner);
		phaseE(cat, planner);



		System.out.println(checks + " vérifications, " + failures + " échec(s)");
		if (failures > 0) System.exit(1);
	}
}
