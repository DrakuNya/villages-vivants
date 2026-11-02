# Villages Vivants — feuille de route

Mod Fabric pour Minecraft **26.3** (Java 25). Les villageois élisent un **maire** (ruban d'élu), un **constructeur** (maillet) bâtit les **plans** posés par le maire, et d'autres métiers alimentent le chantier.

> **État : phase A testée en jeu (objets, élection du maire/constructeur) ; phases B (maire autonome) et C (inventaires) écrites mais pas encore lancées.**
> La logique « pure » (dossier `core/`) est **testée** par `tools/CoreSelfTest.java` : une centaine de scénarios distincts (le compteur affiché à la fin est bien plus gros parce qu'une boucle revérifie chaque bloc de chaque plan dans chaque style et rotation). Dont : une ville simulée de A à Z dans 3 biomes, et chaque plan construit dans chaque style **sans aucun matériau de départ**, uniquement par les récoltes, dépôts et fabrications des villageois.
> Le code Minecraft des phases B et C (`mc/Director`, `mc/TerrainAdapter`, nouvelles commandes) n'a **jamais été compilé ni lancé** : il utilise les mêmes classes que la phase A plus quelques nouvelles (voir §4). Les tests ne prouvent rien sur le comportement réel dans Minecraft (déplacements, API), seulement sur la logique.

---

## 1. Comment tester

1. Pousse le projet sur GitHub (dossier `.github` compris). L'onglet **Actions** lance :
   - d'abord les **tests de la logique** (sans Minecraft) ;
   - puis la **compilation** (`./gradlew build`).
2. Rouge ? Copie les **dernières lignes d'erreur** et donne-les à l'IA, avec ce fichier. Il y aura des erreurs de nom : c'est attendu.
3. Récupère le `.jar` (artefact `Mod-jar`, prends celui sans `-sources`), mets-le avec **Fabric API** dans `mods` de ton instance Prism (26.3 + Fabric).
4. Scénario de test en jeu (monde créé en créatif, village proche) :
   - `/give @s livingvillages:mayor_sash` puis **clic droit** sur un villageois → il devient maire.
   - `/give @s livingvillages:mallet` puis **clic droit** sur un autre villageois → constructeur.
   - `/lv plans` (liste adaptée au biome), puis `/lv place petite_maison` (devant toi). Un **panneau** apparaît.
   - `/lv stock oak_log 40` et `/lv stock cobblestone 40` (ressources de départ), puis `/lv stock string 12`, `/lv stock sand 5`.
   - **Clic droit sur le panneau** : ressources ✔ vertes / ✘ grises. `/lv town` : résumé et besoins.
   - Regarde le constructeur travailler (1 bloc par seconde environ).
   - Bûcheron : sneak + clic droit avec une **hache en fer** ; mineur : **pioche en fer**. Ils répondent aux demandes du tableau.

## 1 bis. Tester la phase B (maire autonome) — monde créatif, terrain assez plat

1. Même départ : ruban d'élu sur un villageois (maire), maillet sur 1 ou 2 autres (constructeurs). Mets-en aussi d'autres autour (au moins 5 à 6 villageois dans un rayon de 64 blocs : ils comptent comme la « population »).
2. Donne un métier à quelques autres (accroupi + clic droit) : **hache en fer** (bûcheron), **pioche en fer** (mineur), **cisailles** (berger), **laisse** (éleveur), **bol** (cuisinier), **épée en fer** (garde). Chaque métier déclenche la construction de son lieu de travail.
3. Le mode automatique est **actif par défaut**. Toutes les ~10 secondes le maire décide d'un bâtiment (message bleu dans le chat). Pour le déclencher tout de suite : `/lv decide`. Pour reprendre la main : `/lv auto off` (et `/lv auto on` pour le rendre autonome).
4. Séquence attendue : **puits** au centre → **maisons** (jusqu'à avoir autant de lits que d'habitants) → **fermes** → **lieux de travail** des métiers présents → **tour de garde** → entrepôt, marché, forge…
5. Pour chaque bâtiment : **terrassement** (le terrain est aplani et dégagé, message « Terrain prêt »), puis panneau, puis construction étage par étage par les constructeurs (jusqu'à 3 par chantier). Quand il n'y a plus de chantier, les constructeurs **posent les chemins** reliant chaque porte à la place centrale, puis les **lampadaires** (torches, une toutes les 6 cases).
6. **Inventaires (phase C)** : chaque habitant porte un sac de 8 emplacements (piles de 64, lits seuls, boules de neige par 16...). Les bûcherons, mineurs et bergers **récoltent dans leur sac**, puis **marchent jusqu'au dépôt** (la place centrale, puis un coffre de l'entrepôt une fois celui-ci construit) pour y déposer. Les constructeurs, quand il leur manque l'objet du prochain bloc, **vont au dépôt, y fabriquent** (2 bûches → planches → portes...) **et repartent avec le sac chargé**. Les lampadaires prennent leur torche dans le sac du cantonnier. Pour voir un sac : **accroupi + clic droit à main vide** sur un habitant, ou `/lv inv` (tous les sacs et le dépôt).
7. Donne-leur des matériaux de départ pour accélérer : `/lv stock oak_log 200`, `/lv stock cobblestone 200`, `/lv stock sand 40`, `/lv stock white_wool 20`, `/lv stock coal 10`, `/lv stock wheat 40`.
8. `/lv town` montre la file de chantiers (avancement, priorité), les chemins restants, l'entrepôt et le tableau des besoins.

Ce que fait chaque métier **pour l'instant** : constructeur (bâtit, aplanit, trace les chemins), bûcheron (bûches), mineur (pierre, sable, terre, charbon, neige), berger (laine des moutons proches, 1 à 3 par tonte). Les autres métiers déclenchent leurs bâtiments mais n'ont pas encore de tâche (phase D).

## 2. Architecture

```
core/   (aucune dépendance à Minecraft — testé)
  Stock, Recipe, RecipeBook      inventaires et recettes internes (bûche → planches → porte…)
  ConversionPlanner              « comment obtenir N objets » (ordre des étapes, surplus, manque réel)
  Theme, PlanParser, BuildPlan   plans .plan, jokers de matériaux (@planks, @door…), styles par biome
  Rotation, PlanBlock            rotations par quarts de tour (états facing/axis inclus)
  BuildSite                      un chantier : progression bloc par bloc, étage par étage
  BuilderLogic                   cerveau du constructeur : quoi poser, quoi transformer, ce qui manque
  Role                           métiers et ce que chacun sait produire
  TownBoard                      tableau des besoins (les chantiers publient, les ouvriers prennent)
  Market                         marché interne entre villageois (jetons)
  Inventory, Logistics, WorkerLogic  sacs des villageois, tours au dépôt (fabrication + chargement), cerveau des ouvriers de récolte
  Town, TownStore                ville + sauvegarde texte (format v2, lit toujours le v1) ; sacs inclus
  MayorPlanner                   « de quoi la ville a-t-elle besoin ? » (puits, lits, nourriture, défense, ateliers...)
  SitePlacer, TerrainView        où construire : lots autour de la place, sans chevauchement, évite l'eau et les pentes
  SitePrep                       terrassement : remblais, déblais, herbe
  RoadPlanner, RoadCell, RoadLogic  chemins vers la place + lampadaires (torches fabriquées au besoin)
  MayorBrain                     le maire autonome : décide, place, trace le chemin, crée le chantier
mc/     (glue Minecraft — non testé)
  ModItems                       maillet, ruban d'élu
  Interactions                   donner un objet à un villageois, panneau « en construction »
  Director                       maire, terrassement, constructeurs, chemins, bûcheron / mineur / berger (toutes les 10 ticks)
  TerrainAdapter                 le terrain de Minecraft vu par la logique de la ville
  LvCommands                     /lv plans | place | town | stock | auto on/off | decide
  TownSavedData, Towns, Plans, BlockUtil
data/livingvillages/plans/*.plan  les plans (format texte lisible, voir PlanParser)
```

Principe : **le cerveau (core) décide, Minecraft (mc) exécute.** Tout ce qui est logique se teste sans lancer le jeu.

## 3. Ce qui existe déjà

- [x] Maillet + ruban d'élu (objets, textures, recettes, traductions fr/en)
- [x] Donner un objet à un villageois → métier (hache/pioche/houe/cisailles/épée/laisse/bol/canne/émeraude pour les autres métiers, en s'accroupissant)
- [x] Maire + ville, une seule par zone (96 blocs), sauvegarde dans le monde
- [x] 6 plans : petite maison, maison moyenne, puits, ferme, tour de garde, igloo (**seulement en biome enneigé**)
- [x] Styles par biome (chêne/pierre, grès + acacia, épicéa…), rotation selon la direction du joueur
- [x] Constructeur : pose bloc par bloc, étage par étage ; transforme au besoin (2 bûches → 8 planches → 3 portes, le surplus reste)
- [x] Tableau des besoins ; bûcheron et mineur répondent aux demandes
- [x] Panneau « en construction » : ressources en vert (dispo) / gris (manque)
- [x] Entrepôt commun virtuel + `/lv stock` pour tester
- [x] Marché interne (logique seulement)

## 4. À vérifier au premier lancement (zones à risque)

- [ ] Noms de classes/méthodes Mojang de la 26.3 dans `mc/` (la compilation dira lesquels)
- [ ] **Déplacement des villageois** : `getNavigation().moveTo` peut être écrasé par leur « cerveau » (Brain). Piste : poser `WALK_TARGET` dans le Brain, ou désactiver leurs activités pendant qu'ils travaillent.
- [ ] Panneau : posé **sans texte** (l'API des panneaux change souvent) ; le message est affiché au clic. Ajouter le texte « En construction » une fois compilé.
- [ ] Recettes JSON (`result.id`) et définitions d'objets (`assets/.../items/`) : format 26.3 à confirmer
- [ ] Les commandes `/lv` n'ont **pas de restriction de permission** (tout joueur peut les utiliser)
- [ ] Un seul monde géré : le Overworld
- [ ] Les villageois non chargés (loin du joueur) ne travaillent pas

### Nouveaux points à vérifier (phase C)
- [ ] Les villageois vont-ils bien au dépôt et en reviennent-ils ? (même fragilité de déplacement)
- [ ] Un constructeur qui attend un objet reste au dépôt : vérifier qu'il n'y a pas de blocage quand une matière première n'existe pas dans les environs (ex : pas de moutons → pas de lits)
- [ ] Accroupi + main vide sur un habitant : ne remplace le commerce que pour les habitants ayant un métier
- [ ] Les bergers : la laine vient des moutons proches (1 à 3 par tonte, repos de 60 s)

### Nouveaux points à vérifier (phase B)
- [ ] `TerrainAdapter` : `hasChunkAt`, `canBeReplaced()`, `getFluidState()`, `isAir()` (noms de la 26.3)
- [ ] `getEntitiesOfClass(Class, AABB, Predicate)` pour compter les villageois et trouver les moutons ; classe `Mob`
- [ ] Les villageois marchent-ils bien jusqu'aux chantiers, aux chemins et aux moutons ? (même fragilité que la phase A)
- [ ] Les constructeurs sans chantier posent les chemins ; vérifier qu'ils ne gênent pas les autres
- [ ] Une grande population (20+ villageois) fait beaucoup construire : surveiller les performances (recherches de blocs, terrassement) et le plafond de 40 bâtiments
- [ ] Le panneau est posé sur un socle de pierre taillée après le terrassement

## 5. Feuille de route (checklist)

### Phase A — que ça compile et que ça tourne
- [ ] Corriger les erreurs de compilation
- [ ] Le maire/constructeur s'assigne bien ; le constructeur marche jusqu'au chantier et pose les blocs
- [ ] **Terrassement** : avant de construire, aplanir/remblayer le terrain et dégager la zone (arbres, herbes) — sinon maisons enterrées ou dans le vide
- [ ] Vérifier qu'un emplacement est libre (pas sur un bâtiment existant, pas dans l'eau, pente acceptable) avant d'accepter un plan
- [ ] Texte du panneau + disparition à la fin (déjà prévue)

### Phase B — la ville s'organise (le maire devient autonome) — **écrite, à tester en jeu**
- [x] **Le maire place les plans lui-même** : puits → lits → nourriture → lieux de travail des métiers présents → défense → entrepôt/marché/forge, selon la population (villageois adultes dans 64 blocs) ; plafond de 40 bâtiments, 2 chantiers à la fois
- [x] **Urbanisme** : lots de 12×12 en cercles autour de la place, portes tournées vers le centre, marge de 3 blocs, évite eau et pentes > 3 ; chemins de terre porte → place (plus court chemin, en réutilisant les chemins existants) ; lampadaires toutes les 6 cases
- [x] **Terrassement** : remblai (terre), déblai (arbres, herbes, relief), herbe sur le pourtour
- [x] File de chantiers + priorités ; jusqu'à 3 constructeurs par chantier, répartis automatiquement
- [x] 13 plans (+7 : bergerie, enclos, scierie, entrepôt, forge, marché, cuisine), avec métadonnées (catégorie, capacité, métier servi, population minimale, maximum, priorité)
- [x] Berger : « tond » les moutons proches (laine) ; mineur : terre, neige, pierre ; la laine est une matière première (plus de ficelle)
- [ ] Plusieurs variantes de maisons par biome (aujourd'hui : 2 maisons + igloo en terrain enneigé), grande ferme, église, bibliothèque, boucherie, écurie, grenier, mur d'enceinte, pont
- [ ] Plans lisibles hors du jeu (éditeur/outil pour dessiner un plan, ou import depuis un fichier de structure `.nbt`)
- [ ] Le maire choisit l'emplacement en tenant compte des **bâtiments déjà existants du village** (aujourd'hui il ne voit que les siens)
- [ ] Voies principales (grille) et places, au lieu de chemins qui s'accrochent à l'existant

### Phase C — inventaires et stocks — **inventaires écrits, coffres réels à faire**
- [x] **Inventaire pour chaque villageois** : 8 emplacements, limites de pile, sauvegardés dans la ville (ce sac est virtuel : il n'utilise pas l'inventaire interne des villageois de Minecraft)
- [x] Les récolteurs travaillent avec leur sac et **déposent au dépôt** ; les commandes du tableau ne sont soldées qu'au dépôt
- [x] Les constructeurs construisent **depuis leur sac** et font des **tours au dépôt** pour se recharger ; la fabrication (planches, portes...) se fait au dépôt
- [x] Le dépôt est un lieu réel : la place centrale, puis un **coffre de l'entrepôt** construit ; les villageois marchent jusqu'à lui
- [x] Voir le sac d'un villageois (accroupi + main vide), `/lv inv`
- [ ] **Coffres réels** : le contenu de l'entrepôt est encore un registre virtuel ; faire correspondre les coffres du monde (et accepter que le joueur y prenne ou y dépose des objets)
- [ ] Utiliser le **vrai inventaire** des villageois de Minecraft (8 emplacements) au lieu du sac virtuel
- [ ] **Porteur/messager** : déplace les ressources entre plusieurs dépôts et les chantiers
- [ ] Plusieurs ouvriers sur la même commande (aujourd'hui une commande = un ouvrier à la fois)
- [ ] Panneau en vrai **écran** (GUI) plutôt que chat : liste verte/grise, jauge d'avancement
- [ ] Récupérer le sac d'un villageois mort (aujourd'hui il est perdu)

### Phase D — métiers (chacun avec son meuble de métier et son rôle dans l'économie)
- [ ] **Bûcheron** : replante des pousses, évite de raser la forêt, hache
- [ ] **Mineur** : galeries sûres, échelles/torches, pierre, sable, charbon, fer
- [ ] **Fermier** : récolte et replante (blé, carottes, pommes de terre, betteraves)
- [ ] **Berger** : tond les moutons → laine → lits, tapis, bannières
- [ ] **Éleveur** : gère les troupeaux (reproduction plafonnée), viande, cuir, œufs
- [ ] **Cuisinier** : four/fumoir → pain, viande cuite ; nourrit toute la ville (stock de nourriture partagé)
- [ ] **Pêcheur**, **Forgeron** (outils et armes pour les autres métiers et les gardes), **Verrier** (sable → verre), **Maçon** (briques), **Tailleur**, **Bibliothécaire** (livres), **Clerc** (soins, guérison des zombies-villageois)
- [ ] **Gardes** : patrouille, armures, alerte, aident les golems, défendent pendant les raids
- [ ] **Meubles de métier** (nouveaux blocs) + modèles/textures : chaque métier a son poste de travail reconnaissable
- [ ] **Marchands internes** : marché de la ville (le joueur n'y a pas accès) : surplus mis en vente, besoins achetés, prix selon rareté, jetons
- [ ] Chaînes de production : mouton → laine → lit ; bûches → planches → meubles ; sable → verre → vitres

### Phase E — la vie de la ville
- [ ] Rythme jour/nuit : travail le jour, repos la nuit ; pluie, raids, zombies
- [ ] **Besoins** : manger, dormir, sécurité ; une ville qui manque de nourriture ralentit, qui manque de lits n'accueille plus
- [ ] Population : naissances, nouveaux arrivants qui prennent des métiers selon les besoins
- [ ] Ville qui s'agrandit en plusieurs quartiers ; plusieurs villes qui commercent entre elles
- [ ] Événements, fêtes, noms de villes, histoire de la ville (journal)

### Phase F — confort et sécurité
- [ ] Permissions des commandes, option de configuration (vitesse de construction, métiers activés)
- [ ] Mode multijoueur propre (propriétaire d'une ville), protection : ne pas détruire les constructions du joueur
- [ ] Annuler / démolir un chantier ; mode « fantôme » pour **voir** les plans (option de débogage seulement)
- [ ] Performance : limiter les recherches de blocs, budget de ticks par seconde
- [ ] Compatibilité (autres mods de villageois), traductions, icône, page Modrinth

## 6. Idées en plus

- Plan **« parchemin »** : un objet qui permet au joueur de proposer un plan au maire
- **Carte de la ville** (le cartographe dessine les bâtiments et les chantiers)
- Villageois qui **réparent** les bâtiments abîmés (par exemple après un raid) en comparant au plan
- **Ressources locales** : un village de désert construit en grès ; une ville de savane en acacia
- Compétence des villageois : un constructeur expérimenté pose plus vite
- Panneau « en construction » **animé** (points qui avancent) ; sons de construction

## 7. Pour la prochaine session avec l'IA

Donne-lui : ce fichier, le zip du projet, et le **message d'erreur exact** de la compilation (onglet Actions) ou le `latest.log` du jeu. Elle corrigera dans l'ordre : compilation → lancement → comportement → nouvelles fonctions. Les tests du dossier `tools/` doivent toujours passer.
