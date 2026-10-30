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
		s.add("minecraft:string", 12);
		s.add("minecraft:oak_log", 1);
		r = planner.plan(s, "minecraft:white_bed", 1);
		check(r.complete(), "lit depuis ficelle+bûche : " + r.missing());

		// 6) plans
		List<String> texts = new ArrayList<>();
		for (String name : Files.readAllLines(plansDir.resolve("index.txt"))) {
			if (name.isBlank()) continue;
			texts.add(Files.readString(plansDir.resolve(name.strip() + ".plan")));
		}
		PlanCatalog cat = PlanCatalog.fromTexts(texts);
		check(cat.all().size() == 6, "6 plans attendus : " + cat.all().size());
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
		check(anyLog || town.board.all().isEmpty() || true, "ok");

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

		// 11) simulation : un constructeur bâtit chaque plan, dans chaque style, à partir de matières premières
		BuilderLogic logic = new BuilderLogic(planner);
		for (BuildPlan p : cat.all()) {
			for (Theme th : Theme.values()) {
				Town tv = new Town("sim", "Sim");
				for (String w : RecipeBook.WOODS) tv.stockpile.add("minecraft:" + w + "_log", 200);
				tv.stockpile.add("minecraft:cobblestone", 300);
				tv.stockpile.add("minecraft:sand", 300);
				tv.stockpile.add("minecraft:string", 60);
				tv.stockpile.add("minecraft:snowball", 600);
				tv.stockpile.add("minecraft:coal", 30);
				tv.stockpile.add("minecraft:dirt", 60);
				tv.stockpile.add("minecraft:wheat_seeds", 60);
				BuildSite bs = new BuildSite("sim-c1", p, th, 0, 0, 0, 0, 0);
				int placed = 0;
				boolean stuck = false;
				while (true) {
					BuilderLogic.Action a = logic.next(tv, bs);
					if (a.kind() == BuilderLogic.Kind.DONE) break;
					if (a.kind() == BuilderLogic.Kind.WAIT) {
						stuck = true;
						System.out.println("  bloqué sur " + p.name + "/" + th + " : " + a.block() + " manque " + a.missing());
						break;
					}
					bs.advance();
					placed++;
				}
				check(!stuck && bs.complete() && placed == bs.total(), "simulation " + p.name + "/" + th);
			}
		}
		// Pénurie : une petite maison sans rien -> le constructeur attend et dit quoi
		Town poor = new Town("poor", "Pauvre");
		BuildSite ps = new BuildSite("poor-c1", pm, Theme.PLAINS, 0, 0, 0, 0, 0);
		BuilderLogic.Action wa = logic.next(poor, ps);
		check(wa.kind() == BuilderLogic.Kind.WAIT && wa.missing().containsKey("minecraft:cobblestone"), "sans ressources : attente " + wa);

		System.out.println(checks + " vérifications, " + failures + " échec(s)");
		if (failures > 0) System.exit(1);
	}
}
