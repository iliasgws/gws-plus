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

## 2. Direction visuelle : « Liquid glass »

Le cahier de liaison est le cœur de la communication école-familles en France.
GWS+ garde le vocabulaire du registre scolaire — l'encre verte, le stylo rouge
— mais les surfaces ne sont plus du papier : le contenu se pose sur du
**verre liquide**, des feuilles translucides au-dessus d'une aurore douce,
dans l'esprit des matériaux d'iOS 26. Pas de skeuomorphisme : pas de lignes
règlées, pas de spirales, pas de verre qui casse.

**La chose dont on se souvient** : l'écran d'accueil est un registre du jour —
un flux chronologique vertical de ce qui s'est passé (actualités, devoirs
donnés, absence notée, message de l'admin), à lire de haut en bas comme un
cahier. Une seule chose est mise en avant : la carte « Ce soir », les devoirs
du jour, en plus grand, plus dense, plus haute dans la pile. Le reste est
plus discret.

**Le geste couleur signature** : l'encre de Greenwood. Un vert profond
dominant (le nom de l'école), utilisé comme on utilise l'encre — pour écrire
ce qui compte — et un rouge stylo de correction réservé aux seuls éléments qui
réclament une action parent (demande à signer, devoir non consulté). Le rouge
ne décore jamais : s'il est visible, il y a quelque chose à faire. Sur verre,
la règle est encore plus stricte : le rouge est un verdict, un badge, une
action — jamais une teinte décorative.

### Le matériau : deux niveaux de verre

Le verre a besoin d'un fond avec de la variation pour être visible. Le fond de
l'app est une **aurore** : trois halos doux et immobiles (sauge, crème chaud,
bleu-vert pâle) sur une base claire le jour, sombre la nuit. Jamais animée —
un fond qui bouge fatigue, et l'app ne joue jamais d'animation pour rien.

- **Verre calme** (cartes du flux, bulles du parent, puces de choix,
  squelettes) : une feuille translucide à ~72 % plus un liseré lumineux de
  1 dp, **sans floutage** — une carte du flux n'a rien derrière elle : la
  réfraction y serait invisible et ne coûterait que des passes de shader.
- **Verre réel** — vibrance + flou + **réfraction** (`lens`, la partie
  « liquide » du matériau) — réservé aux feuilles qui recouvrent réellement
  quelque chose : la **barre basse flottante** (le contenu défile derrière),
  la **barre d'en-tête du registre** (le flux passe dessous), le **composeur**
  (le fil passe derrière), la carte « Ce soir » et la carte de connexion
  (l'aurore se réfracte à leurs bords). Échantillonnage via la bibliothèque
  [`backdrop`](https://github.com/Kashif-E/KMPLiquidGlass) (port KMP de
  AndroidLiquidGlass), avec une règle absolue : **une feuille de verre ne
  peut jamais échantillonner une capture qui la contient** — la couche se
  ré-enregistre avec une référence à elle-même et le premier rendu du
  contenu crashe (vu en bêta 2 : squelette affiché, puis crash). Deux
  captures donc : la scène complète, lue par la seule barre basse qui se
  tient hors d'elle ; l'aurore seule, lue par tout le verre qui vit dans les
  écrans. La réfraction est offerte dès l'API 33 (AGSL), le floutage seul
  dès l'API 31. Les feuilles réelles se posent **par-dessus** le contenu —
  le verre est une couche, pas une borne.
- **Verre fort** (feuilles modales, repli API < 31 de tout le verre réel) :
  fill quasi opaque à 95 %. Une feuille modale vit dans sa propre fenêtre :
  elle ne peut pas échantillonner — le verre fort est conçu pour ça, pas
  rafistolé. Sous l'API 31, le floutage n'existe pas : le verre réel bascule
  sur son fill opaque (`teinte` en pleine opacité, sinon barStrong). La
  dégradation est conçue, pas accidentelle.

### Palette (clarité / obscurité)

| Jeton | Clair | Sombre | Rôle |
|---|---|---|---|
| `ink` | `#1F3D2B` | `#E8F0E9` | Vert encre : titres, texte principal, éléments actifs |
| `paper` | `#EFF3ED` | `#0C110E` | Base de l'aurore, fond de l'app |
| `page` | `#FFFFFF` | `#1B241E` | Texte posé sur une teinte pleine ; base des fills de verre sombres |
| `sage` | `#E9EFE7` | `#243026` | Teintes pleines sémantiques : bulles admin, verdict du quiz, carte « Ce soir », indicateur d'onglet |
| `redPen` | `#B3382A` | `#F0917F` | Action requise uniquement : badge, compte non lu, CTA « signer » |
| `chalk` | `#5F6F63` | `#93A396` | Texte secondaire — assombri pour rester lisible à travers le verre (≥ 4,5:1) |

La palette de verre (`RegistreTheme.colors.glass`) complète les six rôles :

| Jeton | Clair | Sombre | Rôle |
|---|---|---|---|
| `glass.card` | blanc 72 % | `#1B241E` 70 % | feuilles de verre calmes (cartes, puces, bulles) |
| `glass.bar` | blanc 60 % | `#1B241E` 55 % | teinte de la barre flottante réellement floutée |
| `glass.barStrong` | blanc 95 % | `#1B241E` 95 % | feuilles modales, composeur, repli API < 31 |
| `glass.stroke` | blanc 35 % | blanc 16 % | liseré lumineux au bord de chaque feuille |
| `glass.auraA/B/C` | sauge / crème / bleu-vert pâle | halos sombres | les trois blobs de l'aurore |

Règles : `redPen` n'apparaît jamais en fond de grande surface ; la seule
surface teintée pleine est la carte « Ce soir » (fond `sage`, liseré `ink`) —
elle est focale justement parce qu'elle est la seule masse opaque au milieu
du verre. Les erreurs partagent la teinte du `redPen` mais uniquement en
texte inline avec pictogramme — un badge de correction n'est jamais une
erreur.

### Typographie

- **Affichage : Fraunces** (variable, bundled). Un serif à l'encre chaleureuse
  pour les titres d'écran, la date du registre, les gros compteurs. Le serif
  porte le côté cahier sans faire « vieux ».
- **Texte et interface : Public Sans** (bundled). Sans humaniste lisible,
  chiffres tabulaires — les dates, tranches horaires et comptes de devoirs
  s'alignent.
- Hiérarchie par écarts francs : titre d'écran 28sp/600 Fraunces, titre de
  carte 17sp/600 Public Sans, corps 15sp/400, annotations 13sp. Jamais deux
  niveaux adjacents à 1sp d'écart.

### Forme : le langage des capsules

Chaque contrôle est une capsule pleine ; le contenu garde un rayon continu
qui lui dit son rang :

- **Contrôles** (barre flottante, boutons, champs, puces de choix, puces
  d'annotation) : capsule — rayon 50 %. Le doigt reconnaît une forme ronde.
- **Pages** (cartes de contenu, réponses de quiz, composeur) : rayon 24dp.
- **Feuilles modales** : rayon 28dp. **Bulles et bandeaux d'erreur** :
  rayon 18dp.

### Motion

Une chorégraphie, pas dix tics : à la première ouverture de l'accueil, la
pile du registre entre en cascade légère (la carte « Ce soir » d'abord, puis
les entrées du jour, décalage 40ms, ressort). La barre flottante entre en
fondu-glissement. Ensuite tout est fonctionnel : expansion d'un devoir,
changement de statut d'une demande, transitions élément-partagé carte →
détail. Jamais d'animation déclenchée par rien — l'aurore elle-même est
immobile.

## 3. Navigation et geste retour — priorité n°1

Le bug qui motive ce projet : dans l'app d'origine, ouvrir la section devoirs
puis utiliser le geste retour Android casse la navigation. En Compose avec
Navigation-Compose, on hérite du retour prédictif d'Android 15 ; le contrat
est fixé ici pour ne pas le reperdre :

- **Barre basse à 4 destinations** : Registre (accueil), Devoirs, Documents,
  Messages. Les actualités et demandes vivent dans le Registre et l'admin
  reste joignable depuis Messages — pas 7 onglets.
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

## 5. Architecture (bref)

- **Kotlin + Jetpack Compose + Material 3**, un module `:app` au début ; pas
  de multi-modules prématuré.
- **Theme** : `MaterialTheme` custom (couleurs ci-dessus + palette de verre,
  typo Fraunces + Public Sans, capsules + rayons continus), jetons étendus
  (`redPen` sémantique) via `CompositionLocal`. Le floutage réel est confiné
  à `ui/components/Glass.kt`, seul fichier qui connaît la bibliothèque
  `backdrop` (KMP Liquid Glass) — si son API bouge, seul ce fichier suit.
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
- Paiements (GET `paiements`, `online_paiements`), cantine, trajets, boutique,
  portefeuille élève : éventuellement v2, pas au lancement.
- Toute redistribution du code ou des assets de l'app d'origine : GWS+ est
  une implémentation propre du protocole, rien d'autre.

## 7. Question ouverte : notifications push

POST `device_token` enregistre un jeton FCM envoyé par le client. Un client
tiers enregistre-t-il le jeton de son propre projet Firebase ? Si le backend
stocke le token sans vérifier l'émetteur, le push marche tel quel ; sinon,
repli v1 : rafraîchissement en arrière-plan + notification locale. À tester
dès le premier build, avant de promettre le temps réel.
