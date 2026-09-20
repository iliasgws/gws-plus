# Changelog

Toutes les évolutions notables de Greenwood School + sont listées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/) ; chaque
version correspond à une [release GitHub](https://github.com/iliasgws/gws-plus/releases).

## [Unreleased]

### Ajouté

- **Direction visuelle « liquid glass »** : le registre se pose sur du verre.
  Le fond de l'app devient une aurore douce (trois halos immobiles, claire
  comme sombre) ; cartes, bulles, composeur et squelettes deviennent des
  feuilles de verre translucides avec liseré lumineux ; la barre basse devient
  une capsule flottante de verre qui floute le contenu qui défile derrière
  elle — le seul nœud flouté de l'app, via la bibliothèque `backdrop` (KMP
  Liquid Glass). Floutage réel dès l'API 31, refraction dès l'API 33 ; en
  dessous de l'API 31, tout le verre bascule sur un fill quasi opaque —
  la dégradation est conçue, pas accidentelle
- Les puces de choix (jours des devoirs, filtres des documents, catégories
  des messages), le champ de recherche et le bouton d'action deviennent des
  composables partagés (`PuceChoix`, `ChampRecherche`, `GwsBouton`) — un seul
  gabarit de verre pour tout, quatre recopies supprimées

### Modifié

- La barre basse quitte le Scaffold : capsule flottante, décollée des bords,
  le contenu défile sous elle dans tous les onglets et écrans de détail
- Formes : pages 24 dp, feuilles modales 28 dp, bulles 18 dp, contrôles en
  capsules (50 %) — l'ancien rang « annotations » (6 dp) disparaît
- `chalk` s'assombrit (#6B7A6E → #5F6F63) pour rester lisible à travers le
  verre ; `paper` devient la base de l'aurore (#FAFAF7 → #EFF3ED, sombre
  #121814 → #0C110E)
- La carte « Ce soir » reste la seule surface teintée pleine (sage + liseré
  encre) — focale justement parce qu'elle est la seule masse opaque au milieu
  du verre ; les bulles de l'administration restent sage plein, celles du
  parent passent au verre

## [0.4.1] — 2026-09-20

### Corrigé

- Le serveur renvoie parfois, à la place du JSON attendu, un débogage PHP en
  clair autour de la charge utile (un `print_r` d'objet — vu sur `messages`
  le 20/09/2026 : « Models\Inscription Object (…) ») : le JSON embarqué est
  désormais récupéré et servi comme un corps net, au lieu d'un mur d'erreur
- Les GET frappés par une réponse non JSON sont relancés jusqu'à trois fois
  à délais croissants (700 ms puis 1,5 s) au lieu d'une seule — le POST
  n'est jamais relancé, un envoi doublé serait pire qu'un échec affiché

## [0.4.0] — 2026-09-19

### Ajouté

- **Skeleton screens** (issue #21) : à la première ouverture de chaque
  section, la page se dessine déjà en blocs pulsés à la forme du contenu
  à venir — registre (carte « Ce soir » et entrées du jour), devoirs,
  documents, messages, demandes, conversation (bulles et composeur) et
  quiz — au lieu d'un anneau de chargement au centre d'un écran vide
- **Cache de dernière donnée connue** (issue #21) : registre, devoirs,
  documents, demandes et messages s'affichent immédiatement à la
  réouverture de l'app ou d'une section, pendant que le réseau rafraîchit
  en arrière-plan — sans clignotement, les listes sont remplacées d'un seul
  mouvement. Le cache est isolé par session (compte **et** enfant choisi :
  changer l'un ou l'autre invalide tout) et purgé à la connexion comme à la
  déconnexion ; aucun nouvel espace disque, un démarrage à froid part des
  squelettes

### Modifié

- Les erreurs réseau ne sont plus jamais masquées (issue #21) : le contenu
  connu reste affiché sous une bannière discrète avec « Réessayer » ; le
  mur d'erreur plein écran n'apparaît qu'à la première charge, quand
  aucune donnée n'est disponible
- Détail d'une actualité : la zone du corps pulse pendant la récupération
  du texte (le message d'indisponibilité est conservé en repli)

### Corrigé

- Registre : la date d'en-tête (« jeudi 18 septembre ») passe sur deux
  lignes au lieu d'être tronquée sur les petits écrans (issue #21) ; les
  autres emplacements de dates ont été audités — aucun autre risque de
  coupure

## [0.3.0] — 2026-09-19

### Ajouté

- **Accès aux quiz** (issue #17) : les quiz de l'espace documents
  s'ouvrent désormais — carte de départ, questions une à une avec décompte,
  bonne ou mauvaise réponse affichée aussitôt, score enregistré à la fin
  par le POST `quiz` du flux officiel (en cas d'échec, le score local
  reste affiché et signalé), « Rejouer » quand le serveur l'autorise
- **Filtre par nature** dans l'onglet Documents : Tout / Quiz / Documents,
  cumulable avec la recherche

### Modifié

- Les lignes de ressources de type quiz sont désormais cliquables (elles
  étaient inertes) ; les autres types le restent en attendant la
  vérification de `ressource_details`

## [0.2.2] — 2026-09-19

### Modifié

- **Nouvelle icône d'application** : le visuel « arbre et + » sur bouclier
  bleu remplace le monogramme du registre. Icône adaptative (visuel centré
  sur un dégradé bleu dérivé des bords du dessin, calibré pour ne jamais
  rogner la croix, quel que soit le masque du lanceur) ; l'icône thématique
  Android 13+ conserve le monogramme à l'encre

## [0.2.1] — 2026-09-19

### Corrigé

- Onglet Messages : quand le serveur répond autre chose que du JSON (page
  HTML d'interception, corps vide), l'erreur affiche désormais un court
  extrait de la réponse — la capture d'écran dit ce qui s'est passé (issue #14)
- Relance automatique unique sur les réponses illisibles : les pages HTML
  transitoires se résorbent sans action
- Pression réseau réduite sur `GET messages` (grosse réponse) : cache TTL de
  45 s partagé entre Registre, Messages et Nouveau message ; « Réessayer »
  force un rafraîchissement ; en cas d'échec, une liste en cache reste
  affichée au lieu d'un mur d'erreur

## [0.2.0] — 2026-09-19

### Ajouté

- **Le composeur** (issue #10) : répondre dans un fil, ouvrir un nouveau fil
  (sujet + catégorie parmi les `themes[]` du serveur), pièces jointes via le
  sélecteur système (1 Mo max sur la voie « nouveau message »), messages
  vocaux (enregistrement m4a, permission micro à l'usage, lecture en ligne)
- Envoi optimiste : l'élément en attente s'affiche au bas du fil, remplacé
  par la version serveur à la confirmation, marqué **Échec → Réessayer**
  sinon — un envoi raté n'est jamais silencieux
- Interrupteur du composeur dans l'onglet Messages (actif par défaut,
  puisque l'envoi réel a été validé)
- Écran de conversation dédié (issue #10, PR #11) : bulles, séparateurs de
  date, accusés de lecture, lecture des pièces jointes et des messages vocaux

### Corrigé

- Le clavier masquait le composeur — `imePadding()` sur les écrans de saisie
- La carte « Joindre l'administration » s'affichait vide quand le serveur ne
  renseignait aucun champ
- Champ « Sujet » étiqueté clairement
- Dédoublonnage des messages double-envoyés par le serveur, lecture des
  accusés (`vu_le`) et de l'audio

### Notes de protocole

- Champs du POST `nouveau-message` lus depuis le bundle officiel 2.4.14,
  documentés dans `docs/api/ENDPOINT-MAP.md`, puis validés par un envoi réel

## [0.1.0] — 2026-09-18

### Ajouté

- Première application : thème « Le registre », session + couche réseau pour
  l'API Boti, navigation 4 onglets à piles indépendantes avec retour
  prédictif, connexion/onboarding
- Écrans Registre (avec « Ce soir »), Devoirs (par échéance, pièces jointes),
  Documents, Messages (lecture seule), Demandes, détail d'actualité
- Changement d'élève local
- 28 tests unitaires

### Notes

- Publiée comme [`v0.1.0-debug`](https://github.com/iliasgws/gws-plus/releases/tag/v0.1.0-debug)
  (APK de debug, pré-release)
- `v0.2.0-test` (2026-09-19) a servi de bêta au composeur avant cette 0.2.0
