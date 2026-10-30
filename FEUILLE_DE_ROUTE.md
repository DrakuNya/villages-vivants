# Villages Vivants — feuille de route

Mod Fabric pour Minecraft **26.3** (Java 25). Les villageois élisent un **maire** (ruban d'élu), un **constructeur** (maillet) bâtit les **plans** posés par le maire, et d'autres métiers alimentent le chantier.

> **État : premier jet.** La logique « pure » (dossier `core/`) est **testée** (45 000+ vérifications, `tools/CoreSelfTest.java`).
> Le code qui parle à Minecraft (dossier `mc/`) n'a **jamais été lancé** ni même compilé : il reprend les noms de classes du code de Fabric API (octobre 2026), mais s'attend à quelques corrections.

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
  Town, TownStore                ville + sauvegarde texte
mc/     (glue Minecraft — non testé)
  ModItems                       maillet, ruban d'élu
  Interactions                   donner un objet à un villageois, panneau « en construction »
  Director                       fait agir constructeur / bûcheron / mineur (toutes les 10 ticks)
  LvCommands                     /lv plans | place | town | stock  (outils de test)
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

## 5. Feuille de route (checklist)

### Phase A — que ça compile et que ça tourne
- [ ] Corriger les erreurs de compilation
- [ ] Le maire/constructeur s'assigne bien ; le constructeur marche jusqu'au chantier et pose les blocs
- [ ] **Terrassement** : avant de construire, aplanir/remblayer le terrain et dégager la zone (arbres, herbes) — sinon maisons enterrées ou dans le vide
- [ ] Vérifier qu'un emplacement est libre (pas sur un bâtiment existant, pas dans l'eau, pente acceptable) avant d'accepter un plan
- [ ] Texte du panneau + disparition à la fin (déjà prévue)

### Phase B — la ville s'organise (le maire devient autonome)
- [ ] **Le maire place les plans lui-même** : repère les besoins (pas assez de lits → maison ; pas de nourriture → ferme ; menaces → tour) et choisit un emplacement près du centre
- [ ] **Urbanisme** : grille/zones, distance entre bâtiments, chemins (chemin de terre) qui relient les bâtiments, éclairage (torches/lanternes)
- [ ] File de chantiers + priorités ; plusieurs constructeurs sur le même chantier (étages en parallèle)
- [ ] Plus de plans, inspirés des bâtiments de base des villages : forge, bibliothèque, église, boucherie, atelier, grande ferme, enclos à moutons, écurie, grenier, marché couvert, mur d'enceinte, pont, etc. Variantes par biome (sable/grès, neige, savane, taïga, marais)
- [ ] Plans lisibles hors du jeu (éditeur/outil pour dessiner un plan, ou import depuis un fichier de structure `.nbt`)

### Phase C — inventaires et stocks réels
- [ ] **Inventaire pour chaque villageois** (les 8 emplacements vanilla) : ce qu'il porte, ce qu'il dépose
- [ ] **Entrepôt réel** : coffres/tonneaux de la ville (le « dépôt »), les ouvriers y livrent, le constructeur y puise
- [ ] Transport : un porteur/messager déplace les ressources entre dépôts et chantiers
- [ ] Panneau en vrai **écran** (GUI) plutôt que chat : liste verte/grise, jauge d'avancement

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
