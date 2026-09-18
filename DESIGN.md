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

## 2. Direction visuelle : « Le registre »

Le cahier de liaison est le cœur de la communication école-familles en France.
GWS+ reprend le vocabulaire du registre scolaire, mais en encre et papier
réinventés, pas en skeuomorphisme : pas de lignes règlées, pas de spirales.

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
ne décore jamais : s'il est visible, il y a quelque chose à faire.

### Palette (clarité / obscurité)

| Jeton | Clair | Sombre | Rôle |
|---|---|---|---|
| `ink` | `#1F3D2B` | `#E8F0E9` | Vert encre : titres, texte principal, éléments actifs |
| `paper` | `#FAFAF7` | `#121814` | Fond de l'app |
| `page` | `#FFFFFF` | `#1B241E` | Surfaces (cartes registre, feuilles) |
| `sage` | `#E9EFE7` | `#243026` | Conteneurs passifs, puces, séparateurs doux |
| `redPen` | `#B3382A` | `#F0917F` | Action requise uniquement : badge, compte non lu, CTA « signer » |
| `chalk` | `#6B7A6E` | `#93A396` | Texte secondaire, horodatages, états vides |

Règles : `redPen` n'apparaît jamais en fond de grande surface ; la seule
surface teintée pleine est la carte « Ce soir » (fond `sage`, liseré `ink`).
Les erreurs partagent la teinte du `redPen` mais uniquement en texte inline
avec pictogramme — un badge de correction n'est jamais une erreur.

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

### Forme

Le rayon encode le rang, pas la décoration :

- **Pages** (cartes de contenu, feuilles du registre) : rayon 20dp — le plus
  doux, le plus haut dans la hiérarchie visuelle.
- **Annotations** (puces matière, statuts de demande, compteurs) : rayon 6dp —
  crisp, rapide à scanner.
- **Champs de saisie et boutons** : rayon 12dp, milieu — le doigt les
  distingue des deux autres rangs.

### Motion

Une chorégraphie, pas dix tics : à la première ouverture de l'accueil, la
pile du registre entre en cascade légère (la carte « Ce soir » d'abord, puis
les entrées du jour, décalage 40ms, ressort). Ensuite tout est fonctionnel :
expansion d'un devoir, changement de statut d'une demande, transitions
élément-partagé carte → détail. Jamais d'animation déclenchée par rien.

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

Chaque écran est adossé aux points d'accès réels (`greenwood-school-re/docs/endpoints.md`).

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
ignorées — c'est notre app.

### Devoirs
Liste par jour avec sélecteur de date (POST `devoirs_date_v2`), pièce jointe
téléchargeable (média signé, PDF ouvert nativement — pas de pdf.js webview).

### Documents
Arborescence plate et recherche (GET `objects`, `ressources_v2`,
`ressource_details`, `bibliotheque`), vignettes via URLs signées
`media.boti.education`, téléchargement dans le système de fichiers.

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
