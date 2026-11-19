# Villages Vivants — plan de tests en jeu

Légende : 🔴 critique (si ça rate, le reste ne sert à rien) · 🟡 important · 🟢 confort.
Presque tout se teste **en créatif** : tu n'as pas à gérer nourriture ni ressources (on donne tout avec des commandes). Les rares tests qui exigent la **survie** sont regroupés en section 13.

## À ne pas tester (déjà vérifié par simulation, 163 000 vérifications)
Calcul des transformations (bûches → planches → portes...), lecture des 14 plans dans les 6 styles et 4 rotations, têtes/pieds de tous les lits, absence de blocs en double, chevauchements de bâtiments, murs mitoyens compatibles, ordre de construction étage par étage, répartition des constructeurs, règle des 1,3 lits, récompenses de travail (statistiques), recrutement (effectifs), sauvegarde et relecture du texte de la ville, calcul d'une ville de 250 habitants. **Ce que la simulation ne dit pas** : tout ce qui touche à Minecraft (déplacements, noms de classes, rendu, écrans, textures). Voilà ce qu'on teste ici.

## 0. Préparation

**Deux mondes de test** (créatif, commandes activées) :
- **A — « Plat »** : type de monde *Superflat*. Pour tout ce qui est construction (terrain plat, pas de relief ni d'eau).
- **B — « Normal »** : monde normal, créatif. Pour les biomes, le relief, l'eau, les arbres, les villages existants.

**Commandes de confort** (à taper une fois par monde) :
`/gamerule doMobSpawning false` · `/gamerule doDaylightCycle false` · `/gamerule doWeatherCycle false` · `/time set day` · `/weather clear`

**Aides** : `/summon minecraft:villager ~ ~ ~` (un villageois) · `/kill @e[type=minecraft:villager]` (tous) · `/tp @s ~ ~ ~` · `/gamemode creative` / `survival`.
**Objets** : `/give @s livingvillages:mayor_sash` · `livingvillages:mallet` · `livingvillages:founders_charter` · `livingvillages:town_chest` (ou l'onglet créatif « Villages Vivants »).
**Ressources pour la ville** (à taper pour chaque ville de test) :
`/lv stock oak_log 300` · `/lv stock cobblestone 300` · `/lv stock sand 100` · `/lv stock white_wool 20` · `/lv stock coal 20` · `/lv stock dirt 50` · `/lv stock emerald 20` · `/lv stock glass 20`

**Règle d'or** : une ville = une zone. Garde au moins **100 blocs** entre deux villes de test, ou crée un nouveau monde, sinon les villageois s'ajoutent à la ville la plus proche.

**Où trouver les erreurs** : Prism → clic droit sur l'instance → **Dossier** → **Minecraft** → `logs/latest.log`. Ou la fenêtre « Journal de Minecraft » de Prism (cherche `livingvillages`, `ERROR`, `Exception`).

**Comment me rapporter un problème** (copie-colle ce modèle) :
```
Test : (numéro, ex. 5.3)
Mod : (numéro du build GitHub, ex. #4)
Ce que j'ai fait : ...
Attendu : ...
Obtenu : ...
Capture : (joindre)
Journal : (les 20 lignes autour de la première ERROR / Exception)
```

**Ordre conseillé** : séance 1 (≈ 45 min) = sections 1 à 6 ; séance 2 (≈ 1 h) = 7 à 10 ; séance 3 = 11 à 13. Si un test 🔴 rate, arrête-toi et envoie-moi le rapport.

## 1. Démarrage 🔴
- [ ] **1.1** Le jeu se lance avec Fabric + le mod, sans crash (fenêtre console de Prism sans erreur rouge).
- [ ] **1.2** Dans le journal : `Loading ... mods` contient `livingvillages 1.0.0` et la ligne **« Villages Vivants chargé : 134 recettes internes, 14 plans. »**
- [ ] **1.3** Aucune ligne `ERROR` ou `Exception` qui cite `livingvillages` pendant le chargement.
- [ ] **1.4** Créer un monde (créatif, commandes) et y entrer : pas de crash, pas de message d'erreur dans le chat.
- [ ] **1.5** Quitter le monde et le rouvrir : pas de crash.

## 2. Objets, onglet créatif, recettes 🔴🟡
- [ ] **2.1** Onglet créatif **« Villages Vivants »** (icône : ruban rouge et or) avec **4 éléments** : maillet, ruban d'élu, charte des colons, coffre de ville.
- [ ] **2.2** Noms en français (jeu en français) : *Maillet, Ruban d'élu, Charte des colons, Coffre de ville* ; en anglais : *Mallet, Mayor's Sash, Founders' Charter, Town Chest*.
- [ ] **2.3** Textures : maillet (manche brun, tête en bois cerclée), ruban (bande rouge, liseré doré, médaillon), charte (parchemin avec sceau vert), coffre de ville (caisse en bois cerclée de fer avec une **émeraude** au centre). Regarde-les en main, dans l'inventaire, au sol (objet jeté), et le coffre **posé** (6 faces identiques).
- [ ] **2.4** **Recettes** à la table de craft (donne-toi les ingrédients avec `/give`) :
  - Maillet : `PPP / PEP / _S_` (P = planches de n'importe quel bois, E = émeraude, S = bâton).
  - Ruban d'élu : `RRR / GEG` (R = laine rouge, G = lingot d'or, E = émeraude).
  - Coffre de ville : `PPP / PEP / PPP` (planches + émeraude au centre).
  - Charte : `_E_ / EPE / _E_` (E = émeraude, P = papier au centre).
- [ ] **2.5** Une recette **mal disposée** ne donne rien ; la recette du coffre normal (8 planches sans émeraude) donne toujours le coffre vanilla.
- [ ] **2.6** Taille des piles : maillet, ruban et charte ne s'empilent pas (1) ; le coffre de ville s'empile par 64.
- [ ] **2.7** **Coffre de ville posé** : se pose comme un bloc ; se casse à la main et à la hache, et **rend un coffre de ville** ; se pose accroupi contre un autre bloc sans s'ouvrir.
- [ ] **2.8** Clic droit sur un coffre de ville **hors de toute ville** → message jaune « Ce coffre de ville n'est relié à aucune ville. »
- [ ] **2.9** Clic droit avec le maillet/ruban/charte dans le vide ou sur un bloc (sans villageois) : rien de bizarre (la charte seule fonde une colonie, voir section 10).

## 3. Rôles des villageois 🔴
*Monde A. Prépare 4 villageois avec `/summon`.*
- [ ] **3.1 Élire un maire** : ruban d'élu en main, clic droit sur un villageois → message vert « Un nouveau maire est élu : ... », nom **« Maire »** au-dessus de sa tête ; en créatif le ruban reste dans la main.
- [ ] **3.2** `/lv town` : « Village 1 », **Maire : 1**, entrepôt contenant **10 émeraudes** (trésor de départ).
- [ ] **3.3** Ruban sur le même villageois → « déjà le maire ». Ruban sur un autre villageois proche (moins de 96 blocs) → « Il y a déjà un maire dans les environs ».
- [ ] **3.4 Constructeur** : maillet en main, clic droit (sans s'accroupir) sur un autre villageois → message vert, nom **« Constructeur »**.
- [ ] **3.5** Maillet **sans maire** dans les environs (autre zone) → message jaune « Il faut d'abord élire un maire ».
- [ ] **3.6 Autres métiers** (il faut être **accroupi** + clic droit) : hache en fer → *Bûcheron* ; pioche en fer → *Mineur* ; houe en fer → *Fermier* ; cisailles → *Berger* ; épée en fer → *Garde* ; laisse → *Éleveur* ; bol → *Cuisinier* ; canne à pêche → *Pêcheur* ; **lingot d'or → Marchand**. Chaque fois : nom au-dessus de la tête et message vert.
- [ ] **3.7** Les mêmes outils **sans s'accroupir** : le menu de commerce s'ouvre comme d'habitude, aucun métier donné.
- [ ] **3.8** Un **bébé villageois** ne reçoit aucun métier.
- [ ] **3.9** Donner un autre outil à un villageois qui a déjà un métier **change** son métier (le nom suit).
- [ ] **3.10** Un villageois nommé (« Bûcheron ») s'échange toujours normalement quand on lui parle sans outil.

## 4. Commandes `/lv` 🟡
*Pour chaque commande, essaie aussi **sans ville à proximité** (attendu : message rouge « Aucune ville à proximité », jamais de crash).*
- [ ] **4.1** `/lv plans` : liste des plans possibles dans le biome. En plaine : **13 plans** (tous sauf l'igloo) avec nom, titre, taille, étages. En biome enneigé : l'igloo apparaît.
- [ ] **4.2** `/lv town` : nom, métiers, mode (automatique/manuel), chantiers, chemins restants, entrepôt, besoins.
- [ ] **4.3** `/lv stock oak_log 64` : message vert, `/lv town` montre l'objet. Avec `minecraft:oak_log` écrit en entier : pareil. Quantité 0 ou négative : refusée par la commande.
- [ ] **4.4** `/lv auto off` puis `on` : message ; `/lv town` affiche « manuel » puis « automatique ».
- [ ] **4.5** `/lv layout spaced` puis `dense` : message correspondant.
- [ ] **4.6** `/lv decide` : un message bleu de décision, ou message jaune expliquant pourquoi rien (file pleine, maire absent, pas de place).
- [ ] **4.7** `/lv inv` : liste des sacs de tous les habitants et le dépôt.
- [ ] **4.8** `/lv depot` : ouvre l'entrepôt de la ville (voir section 8).
- [ ] **4.9** `/lv center` : recentre la ville sur ta position (message vert, indique si un puits existe).
- [ ] **4.10** `/lv recruit on` / `off` : message.
- [ ] **4.11** `/lv settle` : fonde une colonie (section 10) ; refusé près d'une ville.
- [ ] **4.12** `/lv place` sans argument ou avec un plan inconnu : message d'aide, pas de crash.
- [ ] **4.13** Les commandes se complètent avec Tab (`/lv ` puis Tab).

## 5. Construction manuelle, ville **espacée** 🔴
*Monde A. Crée un maire et un constructeur (3.1, 3.4), puis `/lv layout spaced` et `/lv auto off`. Donne les ressources de la section 0.*
- [ ] **5.1** Place-toi, regarde dans une direction, `/lv place petite_maison` : un **panneau** apparaît au coin, message vert ; le plan est « en face » de toi.
- [ ] **5.2** **Clic droit sur le panneau** : titre doré « En construction : Petite maison », avancement, étage, puis des lignes **✔ vertes** (matériaux disponibles) et **✘ grises** (manque, avec ce qui manque).
- [ ] **5.3** Le **constructeur marche** vers le dépôt (centre de la ville) puis vers le chantier. 🔴 **Si il reste immobile plus de 30 s : envoie-moi le rapport** (point le plus fragile : déplacements).
- [ ] **5.4** Les blocs apparaissent **étage par étage** (environ un bloc par demi-seconde) : fondations, murs, toit.
- [ ] **5.5** Résultat final **petite maison 5×5** : sol (bord en pierre, intérieur en planches), coins en bûches, murs en planches, **porte au sud** tournée vers toi, fenêtres en vitres, **2 lits côte à côte** (chacun avec tête ET pied), une torche entre les têtes, une table de craft, toit en dalles.
- [ ] **5.6** À la fin : message « est terminé(e) ! » et le **panneau disparaît**.
- [ ] **5.7** `/lv stock` en cours de route : les stocks **baissent** (le constructeur consomme), jamais en négatif.
- [ ] **5.8** **Rotation** : refais `/lv place petite_maison` en regardant le nord, l'est, le sud, l'ouest : la **porte regarde toujours vers toi**, les lits gardent tête et pied côte à côte dans les 4 orientations.
- [ ] **5.9** **Transformation au dépôt** : donne seulement `/lv stock oak_log 6` à une ville neuve, place un plan qui demande des portes : le constructeur doit tout de même obtenir une porte (planches fabriquées à partir des bûches).
- [ ] **5.10** **Manque de ressources** : place un plan **sans stock** : le constructeur attend (au dépôt), le panneau liste ce qui manque ; ajoute le stock avec `/lv stock` : il reprend tout seul.
- [ ] **5.11** Chaque plan, un par un (`/lv place <nom>`), en vérifiant les détails :
  - `maison_moyenne` (7×6) : 2 pièces séparées par un mur avec un passage, **4 lits**, torche, table, coffre, fenêtres, toit en dalles.
  - `puits` (3×3) : anneau de pierre, **eau au centre**, 4 poteaux de barrière, toit en dalles.
  - `ferme` (5×5) : terre labourée, **eau au centre**, barrière avec **portillon au sud**, pousses de blé à l'intérieur.
  - `tour_de_garde` (3×3, 7 étages) : pierre, **échelle** à l'intérieur contre le mur nord, entrée sans porte au sud, plateforme en dalles, barrières et une torche au sommet.
  - `bergerie` (7×5) : enclos en barrières avec portillon, 2 bottes de foin, sol de terre.
  - `enclos` (7×7) : idem avec 1 botte de foin au centre.
  - `scierie` (5×4) : sol en planches, 4 poteaux de bois, table de craft, coffre, toit en dalles, ouverte sur les côtés.
  - `entrepot` (5×5) : pierre + planches, **3 coffres de ville** (avec l'émeraude) le long du mur nord, porte au sud, toit.
  - `forge` (5×5) : pierre, **four** et table de craft, porte, toit en dalles de pierre.
  - `marche` (7×4) : sol en planches, poteaux en barrières, **2 coffres**, toit en dalles.
  - `cuisine` (5×4) : four, table de craft, coffre, porte, fenêtres, toit.
  - `mairie` (9×9, 6 étages) : pierre avec coins en bûches, **porte au centre du mur sud**, fenêtres, 2 coffres, table, 2 torches, toit en dalles.
- [ ] **5.12** **Entrepôt** : une fois terminé, clic droit sur l'un de ses **coffres de ville** ouvre « Entrepôt de <ville> » (section 8). Les villageois y vont désormais pour le dépôt (au lieu du centre).
- [ ] **5.13** Un plan placé **sur un obstacle** (arbre, eau) se construit quand même (c'est normal : pas de terrassement en mode manuel).
- [ ] **5.14** Sans constructeur : `/lv place` pose le panneau mais rien ne se construit (normal).

## 6. Maire autonome 🔴
*Monde A, ville fraîche (nouvelle zone). 1 maire + 2 constructeurs + 1 bûcheron + 1 mineur (outil en main, accroupi) + 4 autres villageois simples. Ressources de la section 0. Mode dense (par défaut).*
- [ ] **6.1** `/lv decide` : message bleu « Le maire de ... décide de construire : **Mairie** (la ville a besoin d'un bâtiment central). »
- [ ] **6.2** **Terrassement** : message « Terrain prêt pour : Mairie. » ; le terrain est aplani, arbres et herbes dégagés, sans trous. (Monde B : refais-le sur du relief.)
- [ ] **6.3** La **mairie 9×9** se construit exactement au centre de la ville (là où le maire a été élu), porte au sud.
- [ ] **6.4** À la fin de la mairie : les constructeurs **posent les rues** (chemins de terre) en **anneau carré** autour d'elle, avec des **torches** tous les 6 pas environ (les torches se fabriquent à partir du charbon et des bûches).
- [ ] **6.5** `/lv decide` répété (laisse finir la file entre deux) : le maire ne construit que des **maisons** (message « pas assez de lits posés (X pour N villageois, objectif M) ») jusqu'à avoir **au moins 1,3 lit par habitant** ; il **attend** ensuite que les maisons en cours soient finies avant de passer à autre chose.
- [ ] **6.6** Passé le seuil des lits : puits, fermes, tour, etc., avec les raisons dans les messages.
- [ ] **6.7** **Maximum 2 chantiers à la fois** : un troisième `/lv decide` immédiat répond « n'a rien à décider ».
- [ ] **6.8** **Population qui grandit** : ajoute 10 villageois (`/summon`), `/lv decide` : retour aux maisons.
- [ ] **6.9** **Mode automatique** : sans taper `/lv decide`, une nouvelle décision doit tomber toutes les ~10 s tant que la file n'est pas pleine. `/lv auto off` l'arrête, `on` la relance.
- [ ] **6.10** **Plusieurs constructeurs** sur le même chantier (jusqu'à 3) : ils posent des blocs en alternance sans se bloquer.
- [ ] **6.11** **Bûcheron et mineur** : sans `/lv stock`, laisse faire. Les besoins apparaissent dans `/lv town` (« Besoin : ... »), le bûcheron coupe des arbres proches, le mineur casse de la pierre ; l'entrepôt se remplit.
- [ ] **6.12** **Pas de constructeur vivant** : tue les constructeurs (`/kill`), un chantier à terrasser reste « (terrassement en attente) » ; rien ne plante.
- [ ] **6.13** Pendant ces tests, FPS et lags **normaux** (F3 : TPS à 20).

## 7. Ville compacte 🟡
*Monde A (ville avec beaucoup de ressources et 8 à 12 villageois) puis Monde B. Laisse la ville grandir à **15–20 bâtiments**.*
- [ ] **7.1** La **mairie au centre**, entourée d'un anneau de rues ; ensuite des maisons **collées** autour, alignées le long des rues, **portes tournées vers la rue**.
- [ ] **7.2** **Murs mitoyens** : deux maisons voisines partagent **un seul mur** (pas deux murs accolés, pas de trou entre elles, pas de bloc flottant).
- [ ] **7.3** Aucune **porte bouchée** : chaque porte donne sur une case de rue libre ; les **rues** font **1 bloc de large**.
- [ ] **7.4** Les maisons peuvent être **dos à dos** dans un même îlot ; il peut rester un petit espace de cour entre deux rangées.
- [ ] **7.5** Chaque bâtiment est relié aux autres par des **chemins de terre** posés après coup ; les **lampadaires** (torches) tous les 6 pas environ.
- [ ] **7.6** **Pas de panneau** en ville compacte : clic droit sur un bâtiment **pas encore terminé** affiche « En construction » avec ✔/✘.
- [ ] **7.7** **Relief** (monde B, colline douce) : les bâtiments restent de niveau avec leurs voisins ; le terrain est remblayé/déblayé proprement ; pas de maison enterrée ni suspendue.
- [ ] **7.8** **Eau** : près d'un lac ou d'une rivière, aucun bâtiment dedans ; les rues s'arrêtent avant l'eau.
- [ ] **7.9** **Arbres** : les arbres dans l'emprise d'un bâtiment disparaissent proprement (pas de troncs coincés dans les murs).
- [ ] **7.10** **Village vanilla** : élis le maire dans un village existant (monde B). La ville se centre sur la **cloche**, ne construit **pas de puits** si le village en a un, ne construit **rien sur les maisons existantes**, et se **décale** de quelques blocs si le centre est occupé. Teste aussi `/lv center` debout sur la cloche.
- [ ] **7.11** Les **thèmes** : en plaine bois de chêne + cobblestone ; **désert** grès + acacia ; **taïga/neige** épicéa (+ **igloo** en neige) ; **savane** acacia. Vérifie qu'il n'y a **jamais d'igloo** hors de la neige.
- [ ] **7.12** `/lv layout spaced` : les **prochains** bâtiments sont espacés (cercles de lots autour du centre), les anciens ne bougent pas.
- [ ] **7.13** Dans les 4 directions autour de la mairie, des bâtiments existent (portes vers la rue, pas toujours vers le sud).

## 8. Inventaires, coffres et dons 🔴🟡
- [ ] **8.1 Sac d'un villageois** : **accroupi + clic droit à main vide** sur un habitant ouvre un écran de **9 cases** (type distributeur), titre « <Métier> — sac ».
- [ ] **8.2** Mets-y 20 bûches : ferme, rouvre : elles sont toujours là. `/lv inv` les montre.
- [ ] **8.3** Prends des objets du sac : ils passent dans ton inventaire ; clic maj + déplacement fonctionne.
- [ ] **8.4** Le villageois **fait une pause** d'une dizaine de secondes après chaque geste, puis reprend.
- [ ] **8.5** Donne des bûches à un **constructeur** via son sac : il s'en sert pour construire (le stock de la ville baisse moins).
- [ ] **8.6 Entrepôt** : `/lv depot` ouvre un **grand coffre de 54 cases** « Entrepôt de <ville> » avec le contenu de `/lv town`. Déplace des objets : le changement est reflété (`/lv town` et rouvrir).
- [ ] **8.7** Même écran avec un **clic droit sur un coffre de ville** posé (n'importe où près de la ville) et sur les coffres de ville de l'entrepôt construit.
- [ ] **8.8** **Plus de 54 piles** : donne beaucoup d'objets différents (`/lv stock`) : seules 54 cases s'affichent, mais **rien ne se perd** (le total reste identique en rouvrant après avoir vidé des cases).
- [ ] **8.9** Un objet **enchanté ou renommé** mis dans l'entrepôt **perd** ses données (limite connue : à confirmer, pas un bug nouveau).
- [ ] **8.10 Dons** : **accroupi + clic droit avec un objet en main** sur un habitant (par exemple 64 pierres) : message vert « Don à ... », la pile part dans l'entrepôt ; pour une **émeraude** aussi (pas de marchand !).
- [ ] **8.11 Ramassage d'objets jetés** : jette 10 bûches à moins de 2,5 blocs d'un **constructeur** : après ~2 s elles disparaissent et sont dans **son sac** ; jette-les près d'un **bûcheron** : elles vont dans l'**entrepôt** ; jette un **diamant** ou de la nourriture : **ignorés** ; jette-les près d'un villageois **sans métier** : **ignorés**.
- [ ] **8.12** Tu peux **reprendre** tes objets jetés dans les 2 premières secondes.

## 9. Récolte, dépôt, fabrication, récompenses 🟡
*Monde A (pas de minerais : ça teste la fouille).*
- [ ] **9.1** Bûcheron : avec peu de bûches en stock, il va **couper un arbre**, porte les bûches dans son sac (`/lv inv`), puis **les dépose** au dépôt ; le stock monte.
- [ ] **9.2** Mineur : il **casse de la pierre/de la terre/du sable** et rapporte.
- [ ] **9.3** Berger : avec des **moutons** à moins de 32 blocs, il « tond » (les moutons restent intacts) et rapporte de la laine ; un mouton se repose ~60 s.
- [ ] **9.4** **Fouille ciblée (charbon)** : ville neuve **sans charbon** (ne donne pas `/lv stock coal`), plan avec torche (petite maison). Le mineur doit casser des blocs au hasard et finir par **trouver du charbon** (à confirmer par `/lv inv` ou `/lv town`) : la construction n'est pas bloquée.
- [ ] **9.5** **Fouille ciblée (émeraudes)** : vide le trésor d'émeraudes (prends-les dans `/lv depot`), puis construis un **entrepôt** : le mineur finit par trouver des émeraudes (plus lent).
- [ ] **9.6** Récompenses de bûcheron : des **bâtons, pommes, pousses** apparaissent dans l'entrepôt au fil des bûches coupées.
- [ ] **9.7** **Une même commande, un seul ouvrier** : deux bûcherons ne se disputent pas la même commande ; l'un peut rester inactif (limite connue).
- [ ] **9.8** Un ouvrier à sac **plein** va déposer tout seul.

## 10. Colons et recrutement 🟡
*Nouvelle zone, au moins 100 blocs de toute ville.*
- [ ] **10.1** `/give @s livingvillages:founders_charter`, clic droit **sur le sol** : **5 villageois** apparaissent autour du point visé (maire, 2 constructeurs, bûcheron, mineur), noms au-dessus de la tête ; message « Des colons fondent Colonie N ! ». En créatif la charte reste.
- [ ] **10.2** `/lv town` : Colonie N, 5 habitants, entrepôt avec **provisions** (bois, 160 cobblestone, sable, laine, charbon, terre, 10 émeraudes), mode compact, recrutement automatique.
- [ ] **10.3** Sans rien faire d'autre : la **mairie** se construit à partir des provisions, puis les maisons, etc.
- [ ] **10.4** `/lv settle` fait la même chose sans charte.
- [ ] **10.5** Charte (ou `/lv settle`) **à moins de 96 blocs d'une ville** : message jaune « Une ville existe déjà à proximité », aucun villageois créé, charte non consommée.
- [ ] **10.6** Charte cliquée **dans le vide** (en l'air) : rien.
- [ ] **10.7** **Recrutement** : à côté de la colonie, `/summon` ~15 villageois : au bout de ~30 s, message « N habitant(s) ont reçu un métier » ; ils portent des noms de métiers, dans des proportions raisonnables (plus de constructeurs/bûcherons/mineurs que de bergers). `/lv recruit off` l'arrête.
- [ ] **10.8** **Recrutement ne touche pas un village vanilla** par défaut : élis un maire avec le ruban dans un village existant (sans `/lv recruit on`) : les autres villageois gardent leur comportement normal.

## 11. Sauvegarde et persistance 🔴
- [ ] **11.1** En pleine construction (chantier à 40 %), **quitte le monde** (menu → enregistrer et quitter), rouvre : même ville, même progression (`/lv town`), les constructeurs **reprennent** là où ils s'étaient arrêtés.
- [ ] **11.2** Après relecture : **sacs** des villageois, **entrepôt**, **chemins déjà posés**, **mode** (dense/espacé), **recrutement automatique**, noms au-dessus des têtes : conservés.
- [ ] **11.3** Quitte le jeu **brutalement** (ferme la fenêtre) pendant la construction, rouvre : la ville est là, avec **au plus ~10 secondes** de progression perdue (la sauvegarde se fait toutes les ~10 s).
- [ ] **11.4** Deux mondes différents ont des villes **indépendantes**.
- [ ] **11.5** Un monde créé **avant** une mise à jour du mod (ancienne ville) s'ouvre sans crash (les nouvelles options reprennent leurs valeurs par défaut : mode espacé).
- [ ] **11.6** Si tu **retires** le mod puis le remets : le monde se rouvre normalement (la ville réapparaît).

## 12. Robustesse, cas limites 🟡🟢
- [ ] **12.1 Maire tué** (`/kill` sur lui) : message « Le maire de ... a disparu » ; plus de décisions automatiques ; **nouveau ruban** sur un autre villageois = **nouveau maire de la même ville** (pas une deuxième ville).
- [ ] **12.2 Ouvrier tué** pendant sa mission : son sac **revient à l'entrepôt**, sa commande est **reprise** par un autre (plus de blocage) ; message « Un habitant ... a disparu ».
- [ ] **12.3** Villageois **transformé en zombie** (monde en difficile, zombie + nuit) : même traitement que 12.2.
- [ ] **12.4** **Chunks déchargés** : éloigne-toi de 200 blocs, attends 1 min, reviens : la ville est intacte, le travail reprend (les villageois ne travaillent que près du joueur : normal).
- [ ] **12.5** **Nether / End** : un villageois emmené dans le Nether ne fait rien et ne fait pas crasher.
- [ ] **12.6** **Beaucoup de villageois** : 30 puis 60 villageois dans la ville (ou la colonie avec le recrutement) : TPS à 20 sur F3 ; si ça ralentit, note à partir de combien.
- [ ] **12.7** **Gros chantier** : laisse tourner 10 minutes en mode automatique : pas de fuite de performances, pas de boucle de messages.
- [ ] **12.8** **Multijoueur local** (si tu peux ouvrir à un ami ou une deuxième instance) : les commandes et écrans marchent pour un autre joueur.
- [ ] **12.9** `/lv ...` tapé par un joueur **sans droits** (monde en survie avec triche désactivée) : dis-moi s'il peut les utiliser (c'est possible aujourd'hui : pas de restriction).
- [ ] **12.10** **Lave, grotte, falaise** : une ville fondée à côté ne construit jamais dans la lave ni en l'air (les bâtiments sont rejetés si le terrain est trop pentu).
- [ ] **12.11** **Chunk à la limite** : un bâtiment à cheval sur deux chunks se construit sans trou.
- [ ] **12.12** Changer de **langue** du jeu en cours de partie : les noms et textes suivent.
- [ ] **12.13** **Autres mods** (Sodium, Iris, etc., si tu en ajoutes) : pas de conflit.

## 13. Tests qui demandent la **survie** (peu nombreux) 🟢
*Les seuls cas où le mode créatif change le résultat. Tout le reste est identique en survie.*
- [ ] **13.1** **Consommation** : en survie, ruban d'élu, maillet et charte sont **consommés** à l'usage (en créatif : non).
- [ ] **13.2** **Outils de métier** (hache en fer, pioche en fer...) donnés à un villageois : **consommés** (1 exemplaire).
- [ ] **13.3** **Recettes** réellement fabricables en survie avec de vraies émeraudes (obtenues par commerce avec un villageois).
- [ ] **13.4** **Dons** : la pile donnée **quitte** bien ton inventaire en survie (pas de duplication).
- [ ] **13.5** **Coffre de ville cassé** en survie rend **un coffre de ville** (pas des planches).
- [ ] **13.6** Les **objets que tu jettes** et que tu veux garder ne sont pas volés avant 2 secondes.

## 14. Ce que je n'attends PAS d'un test réussi (limites connues, pas des bugs)
Pas de texte sur les panneaux ; pas d'animation de craft ; le coffre de ville est un cube (pas un vrai coffre qui s'ouvre) ; l'entrepôt est virtuel (le contenu du vrai coffre n'est pas utilisé) ; les rues sont en quadrillage régulier ; pas de maisons à étages ; pas de remparts ; fermier, éleveur, cuisinier, garde reçoivent leur métier mais n'ont pas encore de tâche ; les villageois ne marchent pas vers les objets jetés ; une commande n'a qu'un ouvrier ; les villageois ne travaillent que dans les chunks chargés.

## 15. Si c'est trop lent pour toi
Les constructions sont posées à environ **1 bloc par demi-seconde et par constructeur** : une maison prend environ une minute, une mairie 2 à 3 minutes. Si ça rend les tests pénibles, dis-moi : je peux ajouter une commande `/lv speed` pour accélérer tout (constructeurs, terrassement, décisions du maire) en créatif.
