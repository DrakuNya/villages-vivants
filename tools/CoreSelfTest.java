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
		check(inv.room("minecraft:white_bed") == 3 && inv.room("minecraft:oak_log") == 28 + 3 * 64, "place restante : " + inv.room("minecraft:white_bed") + " / " + inv.room("minecraft:oak_log"));
		check(inv.add("minecraft:white_bed", 10) == 7, "seulement 3 lits de plus : 7 refusés");
		inv.add("minecraft:white_bed", 3);
		check(inv.room("minecraft:white_bed") == 0 && !inv.canAdd("minecraft:white_bed", 1), "plus de place pour des lits (8 emplacements)");
		check(inv.remove("minecraft:oak_log", 70) && inv.count("minecraft:oak_log") == 30 && inv.canAdd("minecraft:white_bed", 1), "retirer des bûches libère de la place");
		check(!inv.remove("minecraft:oak_log", 31) && inv.count("minecraft:oak_log") == 30, "retrait impossible : tout ou rien");
		check(inv.copy().drain().count("minecraft:oak_log") == 30 && inv.count("minecraft:oak_log") == 30, "copie indépendante");
		Inventory sn = new Inventory();
		sn.add("minecraft:snowball", 40);
		check(sn.asMap().equals(Map.of("minecraft:snowball", 40)) && sn.room("minecraft:snowball") == 16 * 5 + 8, "boules de neige : piles de 16");

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
		for (int i = 0; i < 7; i++) b3.add("minecraft:white_bed", 1);
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
		Town t = new Town("sim", "Sim");
		t.mayorId = "m";
		String[] ids = {"b1", "b2", "l1", "l2", "m1", "m2", "s1"};
		Role[] roles = {Role.BUILDER, Role.BUILDER, Role.LUMBERJACK, Role.LUMBERJACK, Role.MINER, Role.MINER, Role.SHEPHERD};
		for (int i = 0; i < ids.length; i++) t.members.put(ids[i], roles[i]);
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
						if (inv.add(a.request().item, 1) != 0) return -2;
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
			check(!order.isEmpty() && order.get(0).equals("puits"), biome + " : le premier bâtiment est le puits : " + order);
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
			check(housing >= pop, biome + " : assez de lits (" + housing + "/" + pop + ") " + order);
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
		check(cat.all().size() == 13, "13 plans attendus : " + cat.all().size());
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
		check(needs.getOrDefault("minecraft:white_bed", 0) == 1, "1 lit : " + needs);
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
				tv.stockpile.add("minecraft:sand", 300);
				tv.stockpile.add("minecraft:white_wool", 60);
				tv.stockpile.add("minecraft:snowball", 600);
				tv.stockpile.add("minecraft:coal", 30);
				tv.stockpile.add("minecraft:dirt", 60);
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



		System.out.println(checks + " vérifications, " + failures + " échec(s)");
		if (failures > 0) System.exit(1);
	}
}
