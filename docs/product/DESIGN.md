# GWS+ — Document de conception

Le client Android propre qui remplace le wrapper Capacitor de Boti Education.
Socle réel : tout ce que l'app fait est un appel API + un écran — le protocole
est déjà cartographié dans `greenwood-school-re` (100 points d'accès, auth,
push, médias). Ce document fixe la direction visuelle, la navigation et le
périmètre fonctionnel avant la première ligne de Kotlin.

## 1. Ce que c'est, pour qui

Des parents d'élèves, en micro-moments : après une notification, au petit-déj,
en attendant la sortie. Ils viennent vérifier deux choses (« est-ce qu'il y a
du nouveau ? » et « quels devoirs ce soir ? »), pas flâner. L'app doit être
lisible en dix secondes, d'une main, et ne jamais angoisser sur la navigation.

Le ton : calme, précis, un peu scolaire sans être mièvre. L'app d'origine est
« horrible » parce qu'elle crie de partout (badges, modales, slides
publicitaires via `showSLides`) — GWS+ gagne en enlevant.

## 2. Direction visuelle : « École vivante »

L'école est un lieu vivant : la cour, les affiches, les cahiers de matières
couvertes de couleurs. GWS+ reprend cette énergie — un crème chaud comme
papier de base, une famille d'accent par onglet (comme les couvertures de
cahiers par matière), des rayons généreux, des ressorts sous les doigts —
sans jamais sacrifier la lecture : les surfaces de texte restent neutres et
calmes, la couleur vit dans les accents.

**La chose dont on se souvient** : chaque onglet a sa couleur, et l'écran se
teinte au passage d'une matière à l'autre — la barre basse, les puces, le
surligneur sous le titre, la carte focale, tout répond à l'accent de
l'endroit où l'on se trouve. Une seule chose reste mise en avant par écran :
la carte « Ce soir » au Registre, plus grande, plus dense, plus haute dans
la pile.

**Le geste couleur signature** : la famille d'accent d'onglet + le rouge
stylo de correction. Les accents habillent et célèbrent ; le `redPen`
réserve — il signale uniquement les éléments qui réclament une action parent
(demande à signer, absence non justifiée, échec d'envoi). Le rouge ne
décore jamais : s'il est visible, il y a quelque chose à faire.

### Palette (clarité / obscurité)

Neutres — l'encre et le papier :

| Jeton | Clair | Sombre | Rôle |
|---|---|---|---|
| `ink` | `#1F3324` | `#E9F0E6` | Vert encre : titres, texte principal, éléments actifs |
| `paper` | `#F7F1E5` | `#141B15` | Fond de l'app (crème chaud / vert-charbon) |
| `page` | `#FFFFFF` | `#1D271E` | Surfaces (cartes, feuilles) |
| `sage` | `#EBE6D7` | `#262F24` | Conteneurs passifs, puces neutres, séparateurs |
| `redPen` | `#B3382A` | `#F0917F` | Action requise uniquement — jamais décoratif |
| `chalk` | `#5E6B5C` | `#9AAB97` | Texte secondaire, horodatages, états vides |
| `signetVif` | `#E27A0F` | `#FFA94F` | Orange du signet des actualités |

Familles d'accent par onglet — `GwsAccent(teinte, conteneur, surConteneur)` :
la teinte écrit sur carte, le conteneur est le fond doux, `surConteneur` est
le texte sur conteneur. Clés = routes : `registre` (vert), `actualites`
(ambre), `cours` (bleu), `devoirs` (violet), `documents` (ocre), `messages`
(corail — aussi Demandes), `plus` (sarcelle — l'onglet Plus et la Boutique).
Valeurs exactes dans `ui/theme/Color.kt` ;
chaque paire est vérifiée ≥ 4,5:1 en texte, ≥ 3:1 en glyphe, clair et sombre.

Règles :
- `redPen` n'apparaît jamais en fond de grande surface, jamais dans la barre
  basse, jamais hors action requise. Le **corail** (`#C13B69`, ~350°) est
  distinct du **rouge stylo** (`#B3382A`, ~8°) : ils ne cohabitent jamais
  dans un même composant.
- La seule surface teintée pleine de l'accueil reste la carte « Ce soir »
  (conteneur de l'accent vert, liseré teinte).
- L'accent se propage par CompositionLocal (`LocalGwsAccent`, fondu animé au
  changement d'écran — la table route → accent vit dans `AppNav.accentDe`).

### Typographie

- **Affichage : Bricolage Grotesque** (variable, bundled). Un grotesque de
  caractère pour les titres d'écran, la date du registre, les gros compteurs
  — l'énergie de l'affiche d'école, sans arrondir en boule.
- **Texte et interface : Public Sans** (bundled). Sans lisible, chiffres
  tabulaires.
- **Tout chiffre qui s'aligne ou change passe par `tabulaire()`** — dates,
  tranches horaires, comptes, scores, minuteurs.
- Hiérarchie par écarts francs : titre d'écran 30sp/700 Bricolage, titre de
  carte 17sp/600 Public Sans, corps 15sp/400, annotations 13sp. Jamais deux
  niveaux adjacents à 1sp d'écart.

### Forme

Le rayon encode le rang, plus généreux qu'avant — l'école vit :

- **Pages** (cartes de contenu) : rayon 24dp — le plus doux.
- **Champs, boutons, bulles** : rayon 16dp.
- **Annotations** (puces, statuts, monogrammes) : rayon 10dp.
- **Pilule** : capsule pleine, réservée à la sélection de la barre basse.

### Motion

Le ressort est la voix par défaut (`Mouvement.kt` : `RessortVif` rebondit
pour la sélection, `RessortDoux` pose les apparitions, `FonduCouleur` fond
les couleurs). La chorégraphie d'entrée reste légère : cascade d'ouverture
(`EntréeCascade`, décalage 40ms, plafond 400ms) sur les listes, une seule
fois par écran. La barre basse sélectionne en ressort ; l'accent d'écran
fond en 220ms. Jamais d'animation déclenchée par rien.

## 3. Navigation et geste retour — priorité n°1

Le bug qui motive ce projet : dans l'app d'origine, ouvrir la section devoirs
puis utiliser le geste retour Android casse la navigation. En Compose avec
Navigation-Compose, on hérite du retour prédictif d'Android 15 ; le contrat
est fixé ici pour ne pas le reperdre :

- **Barre basse à 6 destinations** : Registre (accueil), Cours (emploi du
  temps), Devoirs, Documents, Messages, Plus — amendé en trois étapes par
  décision utilisateur explicite (Actualités 2026-09-20, Cours 2026-09-20,
  Plus 2026-09-21 : l'Actualités quitte la barre pour l'écran « Plus », qui
  héberge les sections secondaires — Actualités et Boutique de l'école) ;
  l'admin reste joignable depuis Messages — toujours pas de 7ᵉ onglet.
- **Le menu du haut** (hamburger, en-tête du Registre) : un tiroir avec les
  gestes hors flux du jour — Mes demandes et Paramètres. La liste du
  Registre ne porte plus que du contenu.
- Chaque onglet garde **sa propre pile** ; basculer d'onglet ne dépile rien
  (`saveState`/`restoreState`), le geste retour depuis un onglet racine
  retourne au Registre, et depuis le Registre il quitte l'app (comportement
  système attendu).
- Les écrans de détail **poussent** sur la pile de l'onglet courant ; le
  geste retour dépile proprement vers l'écran précédent, avec transition
  prédictive (la carte suit le doigt).
- Aucune destination racine n'est poussée sur une pile de détail : on ne
  piège jamais l'utilisateur sous un onglet où il n'a rien ouvert.
- `android:enableOnBackInvokedCallback="true"` dès le premier commit, pour
  la animation système du geste.

## 4. Écrans

Chaque écran est adossé aux points d'accès réels (inventaire complet :
[`../api/ENDPOINTS.md`](../api/ENDPOINTS.md), formes observées dans
[`../api/ENDPOINT-MAP.md`](../api/ENDPOINT-MAP.md)).

### Connexion
Téléphone + mot de passe (POST `login`, multipart, `rememberMe`). Un champ
« retenir », pas de checkbox « privacy » exposée. Inscription/oubli renvoient
vers l'école : l'app ne crée pas de compte (POST `forgot-password` pour le
rappel). Premier lancement : un écran d'accueil sobre de trois pages, pas de
carrousel infini.

### Registre (accueil)
Flux chronologique du jour : entrées d'actualité (GET `nouveautes`, détail
`post_view`, épinglés `pinned_posts`), devoirs donnés aujourd'hui (GET
`devoirs`), absences (GET `absences`), messages récents (GET `messages`).
Focal : carte « Ce soir ». Les slides promo du backend (`showSLides`) sont
ignorées — c'est notre app. Sémantique des dates vérifiée par sonde le
18/09/2026 : dans les réponses `devoirs`, `date_remise` est la date de
rendu (échéance) et `date`/`publication` la date de don (publication) — la
carte « Ce soir » filtre sur `date_remise`.

### Devoirs
Liste par jour (GET `devoirs` + filtre client sur `date_remise` —
`devoirs_date_v2` en POST est l'envoi de travail, pas un sélecteur de
date), pièce jointe téléchargeable (média signé, PDF ouvert nativement —
pas de pdf.js webview).

### Documents
Arborescence plate et recherche (GET `ressources_v2`, `bibliotheque` —
`objects` est le flux des objets trouvés, pas l'espace documents), filtre
par nature — Tout / Quiz / Documents (issue #17) —, vignettes via URLs
signées `media.boti.education`, téléchargement dans le système de
fichiers. Les quiz s'ouvrent et se jouent : GET `quiz` vérifié en sonde
lecture-seule (19/09/2026), décompte par question, bonne réponse affichée
aussitôt (le serveur porte les drapeaux), score enregistré à la fin par le
POST du flux officiel — lu dans le bundle et validé en réel le 19/09/2026
(le serveur score et enregistre la partie lui-même) ; en échec, le score
local reste affiché. `ressource_details` reste hors v1
(forme non vérifiée).

### Messages et contact admin
Fil avec l'administration (GET `messages`, POST `nouveau-message`,
`contact`), lisible comme une conversation, pas comme un formulaire.

### Demandes administratives
Liste des demandes avec statut (GET `demandes`), création guidée (GET/POST
`nouvelle-demande`), suivi du statut animé à l'arrivée d'une réponse.

### Sélecteur d'enfant
Les comptes parents peuvent avoir plusieurs élèves (champ `eleves` du login,
POST `pick_enfants`). Switcher d'enfant rafraîchit le registre entier avec
une transition fondue — et un avatar en tête de l'accueil rappelle l'enfant
consulté.

### Plus (menu des sections secondaires)
Deux cartes portant l'accent de leur destination — l'Actualités en ambre, la
Boutique en sarcelle — comme des couvertures de cahiers côte à côte. Le
détail d'actualité ouvert depuis l'Actualités garde l'onglet Plus actif
(pile) et la teinte ambre (route) — le contrat accent-par-route est inchangé.

### Repas invité (le planning cantine, même route `shop`)
La rubrique « Repas invité » ne porte pas de produits : le serveur y met le
planning des menus (`cantines[]` — jour, disponibilité teintée serveur, prix,
« Réserver » / « Réservé 1/1 »). Deux portes y mènent : une carte « Cantine »
du Registre et la rubrique dans la Boutique — même écran, on tape un jour, on
confirme. Le POST est celui de la boutique (produit 25, vérifié) ; le jour
choisi part dans le commentaire de la commande — le libellé « Réservé » du
serveur ne compte que les commandes validées.

### Boutique de l'école (GET/POST `shop` — sondé le 21/09/2026)
Catalogue par rubrique (puces filtre + recherche locale), détail produit
(variantes = tailles avec stock et prix propres, quantité sous le doigt,
commentaire facultatif), historique des commandes (état serveur, articles
modifiables/supprimables tant que « en-cours »). Faits de sonde qui décident
du périmètre : le POST d'un produit **crée la commande immédiatement** (alerte
« Commande passée avec succès », état « en-cours ») — le panier serveur
(`cart=true`) reste vide dans ce déploiement, il n'est pas exposé. La
confirmation est donc demandée avant chaque envoi ; la modification d'une
commande repasse par le détail (`product=…&commande=…`, préremplissage
`{size, qte, comment}`). Un produit fermé à la commande (`can_add_to_cart`
faux) s'affiche sans bouton.

## 5. Architecture (bref)

- **Kotlin + Jetpack Compose + Material 3**, un module `:app` au début ; pas
  de multi-modules prématuré.
- **Theme** : `MaterialTheme` custom (couleurs ci-dessus, typo Fraunces +
  Public Sans, formes à trois rangs), jetons étendus (`redPen` sémantique)
  via `CompositionLocal`.
- **Réseau** : OkHttp/Retrofit ; `keyToken` + `user_id` injectés par
  interceptor ; gestion des réponses `"disconnect": true` → retour connexion.
  Les bizarreries du serveur sont assumées : `paltform` (sic) est envoyé tel
  quel, jamais « corrigé ».
- **État** : ViewModel par écran, repository unique par domaine.
- **Persistance** : DataStore pour session et préférences, cache Room
  seulement si le offline devient réel (pas v1).

## 6. Hors périmètre

- Les routes `admin_*`, `prof_*`, `collaborateur_*`, `encadrant_*` : rôles
  non-parents, non concernés.
- Paiements (GET `paiements`, `online_paiements`), cantine (le bloc
  `cantines` de la boutique, caché par `showCantinePlanning: false` chez
  Greenwood), trajets, portefeuille élève : éventuellement v2, pas au
  lancement.
- Toute redistribution du code ou des assets de l'app d'origine : GWS+ est
  une implémentation propre du protocole, rien d'autre.

## 7. Question ouverte : notifications push

POST `device_token` enregistre un jeton FCM envoyé par le client. Un client
tiers enregistre-t-il le jeton de son propre projet Firebase ? Si le backend
stocke le token sans vérifier l'émetteur, le push marche tel quel ; sinon,
repli v1 : rafraîchissement en arrière-plan + notification locale. À tester
dès le premier build, avant de promettre le temps réel.
