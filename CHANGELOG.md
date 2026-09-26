# Changelog

Toutes les évolutions notables de Greenwood School + sont listées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/) ; chaque
version correspond à une [release GitHub](https://github.com/iliasgws/gws-plus/releases).

## [Non publié]

### Ajouté

- **Client bureau Linux** (issue #80) : connexion et consultation du registre,
  des cours, des devoirs, des documents, des messages, des demandes et de la
  boutique ; réponses et nouveaux messages avec pièces jointes et assistant
  IA, quiz, téléchargement des documents et ouverture des publications dans
  le navigateur. Les données et la session viennent du même module Kotlin
  Multiplatform que l'application Android.

## [0.8.6] — 2026-09-26

Stable après une bêta : le suivi des devoirs d'un coup d'œil.

### Ajouté

- **Point de suivi des devoirs par jour** (issue #78, PR #79) : un point à
  côté du nom du jour dans l'onglet Devoirs — vert Greenwood quand tout le
  travail du jour est marqué fait, rouge stylo dès qu'un devoir reste à
  faire. Si le jour se trouve hors de la rangée d'onglets, une petite
  pastille rouge avec chevron apparaît au bord pour indiquer de quel côté
  le trouver.

## [0.8.5] — 2026-09-26

Stable après une bêta : le composeur devient permanent.

### Modifié

- **Le composeur est toujours disponible** : l'interrupteur de la barre de
  titre de l'onglet Messages (icône « réglages ») est supprimé, avec tout
  son état persistant — l'écriture à l'administration est désormais
  permanente, comme l'envoi lui-même depuis sa validation.

## [0.8.4] — 2026-09-26

Stable après trois bêtas : le détail des devoirs enfin complet — pièces
jointes réellement affichées, marquage « fait » confirmé, envoi de copie
(issue #68, PRs #69, #70, #71) — plus les corrections du correctif
mises à jour (issue #72, PR #73).

### Ajouté

- **Panneau de détail d'un devoir** (issue #60, PR #69) : ouvrir un devoir
  depuis la liste ouvre son détail — description lisible, échéance,
  enseignant, pièces jointes. Le détail interroge sa propre réponse du
  serveur (GET `devoirs&devoir=<id>`, comme la page officielle) : seule
  celle-ci porte les pièces jointes et les droits.
- **Marquage « fait » avec confirmation** (issue #68) : bouton « Marquer
  comme fait » (quand le serveur le permet, `can_set_done`) derrière une
  boîte de confirmation — l'action est définitive. La liste se met à jour
  au retour (puce « Travail fait »).
- **Envoi de copie après le fait** (issue #68) : une fois le devoir marqué
  fait (`can_add_files`), la section « Envoyez vos devoirs faits » permet
  de joindre un fichier (sélecteur système) puis de l'envoyer en base64,
  le fil exact du bundle officiel. Copies envoyées listées et
  téléchargeables ; alerte du serveur affichée.

### Corrigé

- **Les pièces jointes s'affichent dans le détail** (#68) : elles n'existaient
  que dans la réponse détail du serveur, jamais dans la liste — le bouton
  « Tout télécharger » (2+ pièces) s'affiche à nouveau.
- **Les téléchargements vont dans « Downloads/gws-plus »** (PR #70) : le
  dossier public de l'app, visible dans les Fichiers du téléphone — le cache
  privé d'avant était introuvable. Délais explicites (20 s connexion / 60 s
  lecture), lot qui se termine toujours, échecs signalés (« Terminé — x/y
  (n échecs) »), pièce entamée supprimée en cas d'échec.
- **Le lot « Tout télécharger » se termine à l'écran** (PR #71) : le bouton
  restait bloqué sur « Téléchargement… (x/x) » après le dernier fichier.
- **La case « Participer aux bêtas » reste cochée après une installation**
  (issue #72, PR #73) : le réglage était bien sauvegardé mais n'était plus
  affiché après une mise à jour.
- **Le contrôle des mises à jour se fait à chaque ouverture** (issue #72,
  PR #73) : plus de limite « une fois par 12 h » ; le bouton ↻ du Registre
  vérifie aussi les mises à jour en silence.

## [0.8.3] — 2026-09-26

Stable après huit bêtas essayées sur appareil réel : l'assistant IA du
composeur (issues #56, #58) et une série de correctifs repérés pendant
ces essais (#62 à #66), publiés en préversions avant la fusion.

### Ajouté

- **Assistant IA dans le composeur** (issue #56, PR #59) : bouton ✨ à côté
  du micro, panneau Writing-Tools repensé en feuille basse compacte —
  aperçu du message, champ « Que voulez-vous modifier ? » encadré,
  actions Corriger / Réécrire, Raccourcir / Développer / Structurer /
  Simplifier dépliables, tons Amical / Professionnel / Neutre, bouton
  « ✨ Générer », accent du ✨ partout. Réglages dans les Paramètres :
  activation (case à cocher), ton par défaut, fournisseur OpenAI-compatible
  (OpenRouter, Groq, DeepSeek, Mistral AI, Together AI, Fireworks AI,
  Cerebras — ou URL libre), modèle et clé API BYOK, jamais loguée.
- **Badge « Généré par IA »** (issue #58, PR #61) : composant réutilisable
  (icône carré « AI » + étincelle, badge avec libellé), posé sous les
  résultats du panneau IA.

### Modifié

- **Composeur recomposé en deux rangées** (#65) : texte aligné en haut à
  gauche (1→6 lignes, défilement interne), ⤢ en haut à droite ouvrant un
  éditeur plein écran qui conserve les quatre contrôles, barre d'outils
  épinglée en bas. Zéro animation.
- **Le composeur colle au clavier** (#66) : le vide sous la carte quand le
  clavier s'ouvre est corrigé (insets du Scaffold consommés avant
  imePadding).
- Le clavier se masque à l'ouverture du panneau IA ; le bouton ✨ est
  visible dès l'activation de l'IA, avec un guide vers les Paramètres si
  le réglage est incomplet (#62, #63).

### Modifié (fonctionnement)

- **Le composeur gagne de l'espace et un éditeur plein écran** : champ de
  saisie plus haut (4 lignes visibles, jusqu'à 10, puis défilement interne)
  et bouton ⤢ au-dessus du champ qui ouvre un éditeur plein écran
  (« Terminer » pour revenir) — tout en changements d'état instantanés,
  sans animation.
- **Le panneau IA repensé après l'essai réel** : feuille basse compacte
  (~250 dp fermée, dépliable par poignée), aperçu du message en cours,
  champ « Que voulez-vous modifier ? » encadré, actions claires
  (Corriger / Réécrire, puis Raccourcir / Développer / Structurer /
  Simplifier dépliés), tons Amical / Professionnel / Neutre, bouton
  principal « ✨ Générer », accent rose du ✨ partout dans le panneau.
  Le clavier se masque à l'ouverture ; le réglage « IA dans le composeur »
  porte une vraie case à cocher.

### Ajouté

- **Badge « Généré par IA »** (issue #58) : composant réutilisable
  (`ui/components/BadgeIA.kt`) — icône compacte (carré arrondi « AI » +
  étincelle, redessinée d'après le SVG de référence) et badge complet avec
  libellé « Généré par IA », teintés par le thème. Posé sous les résultats
  du panneau IA du composeur ; consommé par la future synthèse de
  conversation (#57).
- **Assistant IA dans le composeur** (issue #56) : un bouton ✨ à côté du
  micro ouvre un panneau inspiré des Writing Tools d'Apple — champ
  « Décrivez votre modification », actions Relire / Réécrire, tons
  Amical / Professionnel / Concis et transformations Résumé / Points clés /
  Tableau / Liste. Le résultat s'affiche dans le panneau avant de remplacer
  le texte. La fonction se règle dans les Paramètres : activation, ton par
  défaut, fournisseur OpenAI-compatible (presets OpenRouter, Groq, DeepSeek,
  Mistral AI, Together AI, Fireworks AI, Cerebras — ou URL libre), modèle et
  clé API (BYOK). La clé reste dans l'app et n'est jamais loguée.

## [0.8.2] — 2026-09-22

Stable après la bêta 1 : la correction de la navigation entre semaines de
l'emploi du temps, essayée sur un appareil réel en préversion avant la
fusion.

### Corrigé

- **Emploi du temps : les flèches ←/→ chargent enfin les semaines voisines**.
  Le paramètre envoyé au serveur (`date=`) était une supposition jamais
  vérifiée — le serveur l'ignorait et renvoyait toujours la semaine courante,
  d'où l'impression que rien ne se chargeait. L'app envoie désormais les
  champs attendus (`last_week=`/`next_week=` avec la valeur publiée par le
  serveur, comme l'application officielle), vérifiés en sonde read-only.

## [0.8.1] — 2026-09-22

Stable après la bêta 1 : premier essai de bout en bout du système de mise
à jour — et surtout la première version que la 0.8.0 propose d'elle-même
par la carte « Mise à jour disponible ».

### Ajouté

- Dans les Paramètres, toucher le numéro de « Version installée » ouvre la
  page GitHub des publications.

## [0.8.0] — 2026-09-22

Stable : les mises à jour intégrées (issue #46) — l'app se tient désormais
à jour toute seule, depuis les publications GitHub du projet.

### Ajouté

- **Mises à jour intégrées** (issue #46) : l'app vérifie seule les nouvelles
  versions sur les publications GitHub du projet (au plus une fois par 12 h,
  sans jeton ni serveur intermédiaire). Une carte « Mise à jour disponible »
  s'affiche en tête du Registre avec la version, le résumé des notes et un
  bouton unique « Mettre à jour » — téléchargement via le lien direct GitHub
  puis installateur du système. Une notification locale peut prévenir à la
  sortie d'une version (permission demandée dans les Paramètres), qui
  propose aussi le canal bêta en option (« Participer aux bêtas ») et un
  contrôle manuel « Vérifier les mises à jour ».

## [0.7.1] — 2026-09-22

Stable après la bêta 1 : la Bibliothèque des enseignants, publiée d'abord
en préversion depuis la branche `bibliotheque-documents` (PR #44) pour être
essayée avant la fusion.

### Ajouté

- **Bibliothèque des enseignants dans l'onglet Documents** (issue #43, sonde
  API du 22/09/2026) : les documents mis en ligne par les professeurs
  apparaissent désormais, rangés par matière devant les exercices, avec
  leur date et « Par <enseignant> ». Chaque fiche se télécharge (le détail
  `ressource_details` porte l'URL signée) et s'ouvre dans le lecteur du
  système. Un échec sur la Bibliothèque n'efface plus les exercices — il
  est signalé par un bandeau non bloquant.

## [0.7.0] — 2026-09-21

Stable après deux bêtas essayées sur appareil réel : la Boutique de
l'école et ses repas invités (bêta 1 et 2), l'onglet Plus et le menu du
haut du Registre.

### Ajouté

- **Boutique de l'école** (sonde API du 21/09/2026) : catalogue par rubrique
  avec recherche, détail produit (variantes-tailles avec stock, quantité,
  commentaire), commande directe confirmée avant envoi, historique des
  commandes avec modification et suppression tant que la commande est
  « en-cours ». Le panier serveur n'existe pas dans ce déploiement — le
  POST passe la commande immédiatement.
- **Repas invité** : la rubrique « Repas invité » de la Boutique affiche le
  planning des menus (`cantines[]`) au lieu d'un vide, et un raccourci
  « Repas invité » arrive depuis le Registre (section Cantine). On tape un
  jour, on confirme : la commande passe par le POST vérifié de la boutique
  et le jour choisi part dans le commentaire. Le libellé « Réservé » du
  planning ne compte que les commandes validées.
- **Onglet « Plus »** dans la barre basse : les sections secondaires s'y
  rangent — l'Actualités (qui quitte la barre) et la Boutique, chacune sur
  la carte à l'accent de sa destination (ambre, sarcelle).
- **Menu du haut** du Registre (hamburger) : Mes demandes et Paramètres
  quittent le bas de la liste du registre pour le tiroir.

## [0.6.1] — 2026-09-21

Stable après quatre bêtas essayées sur appareil réel : le correctif de
l'onglet actif (bêta 1), la sortie protégée des quiz (bêta 2), l'icône
dédiée des quiz (bêta 3), le rafraîchissement au retour et le panneau
Paramètres (bêta 4).

### Ajouté

- **Rafraîchissement au retour au premier plan** : après une absence d'au
  moins la durée choisie (5 minutes par défaut), la réouverture de l'app
  relance en silence la charge réseau de tout ce qui est déjà affiché — les
  écrans gardent leur contenu connu et son indicateur « rafraîchissement »,
  sans remise à zéro, comme un tirer-pour-rafraîchir invisible. Le quiz en
  jeu, le composeur (brouillon intact) et la connexion ne se rafraîchissent
  pas ; la pagination des Actualités garde sa profondeur.
- **Panneau Paramètres** (atteint depuis une ligne discrète du Registre,
  sous la ligne Demandes) : choisir la durée d'absence qui déclenche
  l'actualisation au retour — jamais, 1, 2, 5 ou 10 minutes. Le réglage
  survit à la déconnexion.
- **Sortie protégée d'un quiz en cours** (issue #17) : pendant la partie,
  le geste de retour système, la flèche d'en-tête et tout changement d'onglet
  en barre basse demandent confirmation — abandonner perd les réponses (score
  non enregistré), « Continuer le quiz » reste en jeu ; les écrans de départ
  et de résultat restent librement quittables

### Modifié

- **Icône dédiée pour les quiz dans les ressources** (issue #17) : dans la
  liste du Documents, le badge du type affiche l'icône « quiz » — une fiche
  avec « ? », même famille Material Rounded que le reste de l'app — au lieu
  du monogramme « Q » ; les autres types de ressources gardent leur
  monogramme

### Corrigé

- **Onglet actif conservé sous une sous-page** (issue #34) : ouvrir une
  actualité, un quiz, une conversation ou une demande ne désélectionne plus la
  barre basse — l'onglet parent reste actif, calculé depuis la pile de
  navigation, et le retour arrière remet l'onglet d'origine sans
  réinitialisation

## [0.6.1-bêta 4] — 2026-09-21

Préversion : le rafraîchissement au retour et le panneau Paramètres se
publient en bêta depuis la branche `veille-retour-premier-plan` (non
fusionnée) pour être essayés sur un appareil réel avant la fusion.

### Ajouté

- **Rafraîchissement au retour au premier plan** : après une absence d'au
  moins la durée choisie (5 minutes par défaut), la réouverture de l'app
  relance en silence la charge réseau de tout ce qui est déjà affiché — les
  écrans gardent leur contenu connu et son indicateur « rafraîchissement »,
  sans remise à zéro, comme un tirer-pour-rafraîchir invisible. Le quiz en
  jeu, le composeur (brouillon intact) et la connexion ne se rafraîchissent
  pas ; la pagination des Actualités garde sa profondeur.
- **Panneau Paramètres** (atteint depuis une ligne discrète du Registre,
  sous la ligne Demandes) : choisir la durée d'absence qui déclenche
  l'actualisation au retour — jamais, 1, 2, 5 ou 10 minutes. Le réglage
  survit à la déconnexion.

## [0.6.1-bêta 3] — 2026-09-21

Préversion : l'icône des quiz se publie en bêta depuis la branche
`quiz-icon-documents` (PR #37, non fusionnée) pour être essayée sur un
appareil réel avant la fusion.

### Modifié

- **Icône dédiée pour les quiz dans les ressources** (issue #17) : dans la
  liste du Documents, le badge du type affiche l'icône « quiz » — une fiche
  avec « ? », même famille Material Rounded que le reste de l'app — au lieu
  du monogramme « Q » ; les autres types de ressources gardent leur
  monogramme

## [0.6.1-bêta 2] — 2026-09-21

Préversion : la sortie protégée des quiz se publie en bêta depuis la branche
`quiz-confirmation-sortie` (PR #36, non fusionnée) pour être essayée sur un
appareil réel avant la fusion.

### Ajouté

- **Sortie protégée d'un quiz en cours** (issue #17) : pendant la partie,
  le geste de retour système, la flèche d'en-tête et tout changement d'onglet
  en barre basse demandent confirmation — abandonner perd les réponses (score
  non enregistré), « Continuer le quiz » reste en jeu ; les écrans de départ
  et de résultat restent librement quittables

## [0.6.1-bêta 1] — 2026-09-20

Préversion : le correctif de l'onglet actif se publie en bêta depuis la
branche `issue-34-onglet-actif` (PR #35, non fusionnée) pour être essayé sur
un appareil réel avant la fusion.

### Corrigé

- **Onglet actif conservé sous une sous-page** (issue #34) : ouvrir une
  actualité, un quiz, une conversation ou une demande ne désélectionne plus la
  barre basse — l'onglet parent reste actif, calculé depuis la pile de
  navigation, et le retour arrière remet l'onglet d'origine sans
  réinitialisation

## [0.6.0] — 2026-09-20

Stable après trois bêtas essayées sur appareil réel : l'onglet Actualités
(bêta 1), l'onglet Emploi du temps (bêta 2), le correctif du plantage et le
restylage « École vivante » (bêta 3).

### Ajouté

- **Onglet dédié Actualités** : 6 onglets en barre inférieure dans l'ordre
  Registre, Actualités, Cours, Devoirs, Documents, Messages
- **Détail d'une actualité (GET `post_view`)** : consultation complète — en-tête
  (catégorie, signet, titre, date, auteur), corps riche, galerie d'images,
  pièces jointes téléchargeables, commentaires imbriqués, quiz interactif ;
  agit comme accusé de lecture côté serveur, avec repli sur le cache et le
  flux `admin_nouveautes`
- **Kill switch de sécurité pour l'écriture** : les POST d'écriture sur les
  actualités (commentaires, réponses de quiz) sont désactivés par défaut via
  une préférence DataStore `ecritureNouveautesActivée`
- **Carte « Dernière actualité » sur le Registre** : la nouvelle la plus récente
  sous l'en-tête (masquée si déjà dans le fil du jour)
- **Pagination 1-based et défilement infini** du flux d'actualités, pull-to-refresh
- **Onglet Emploi du temps** (GET `cours_v2`) : navigation ←/→ entre semaines,
  résumé de la semaine en puces de jours et créneaux du jour choisi (horaire,
  matière, salle, enseignant)
- **Carte « Emploi du temps » sur le Registre** : lien discret vers l'onglet
- **Parsage défensif des créneaux** : la forme intérieure des `seances[]` n'ayant
  jamais été observée (sondage du 2026-09-20), les champs plausibles sont tentés
  (matiere/heure_debut/salle/prof…) et toute forme méconnaissable se replie sur
  son premier champ texte — l'onglet reste utilisable même si le serveur change
  de forme ; l'avertissement `restricted` du serveur s'affiche en carte dédiée

### Changé

- **Restylage « École vivante »** : une
  famille d'accent par onglet (vert, ambre, bleu, violet, ocre, corail) qui
  teinte barre basse, puces, surligneurs de titres, carte focale et états
  vides, en clair comme en sombre ; fond crème chaud le jour, vert-charbon la
  nuit ; affichage en Bricolage Grotesque (le serif Fraunces quitte l'app) ;
  rayons plus généreux (24/16/10) ; chiffres tabulaires sur dates, horaires
  et comptes ; ressorts sous les sélections ; profondeur tonale des surfaces
  (marches `surfaceContainer*` distinctes) ; fond système et icônes de barres
  accordés au thème
- **Barre basse à accents** : libellés sur une ligne (les libellés «
  Actualités », « Documents », « Messages » se coupaient en deux lignes),
  capsule de sélection colorée par onglet, sélection animée en ressort — le
  contrat de navigation (piles par onglet, geste retour) est inchangé

### Corrigé

- **Plantage à l'ouverture d'une actualité** (constaté sur appareil réel,
  2026-09-20) : le squelette du détail de post imbriquait son propre
  défilement vertical dans celui de l'écran — contraintes de hauteur infinie,
  arrêt immédiat de l'app dès l'arrivée sur l'onglet Actualités ou la carte
  « Dernière actualité » ; le squelette défile désormais avec son écran

## [0.6.0-bêta 1] — 2026-09-20

Préversion : l'onglet Actualités et le détail des posts se publient en bêta
depuis la branche `nouveautes-onglet` (PR #28, non fusionnée) pour être essayés
sur un appareil réel avant la fusion.

### Ajouté

- **Onglet dédié Actualités** : 5 onglets en barre inférieure dans l'ordre Registre,
  Actualités, Devoirs, Documents, Messages
- **Pagination 1-based & défilement infini** : chargement paginé (`start = taille + 1`, `limit = 10`),
  pull-to-refresh (`start = 0, limit = max(taille, 10)`) et défilement infini automatique
  à l'approche des 3 derniers éléments
- **Détail d'une actualité (`post_view`)** :
  - Consultation via GET `post_view?post=<id>` agissant comme accusé de lecture côté serveur
  - Repli transparent vers le cache et le flux streaming `admin_nouveautes`
  - Affichage riche : en-tête avec catégorie, signet (`#fc942d`), titre, date, auteur
  - Galerie d'images et téléchargement/ouverture native des pièces jointes
  - Section commentaires (avec sous-commentaires imbriqués et formulaire de réponse)
  - Questionnaire interactif (quiz rattaché au post avec alerte de fin)
- **Kill switch de sécurité pour l'écriture** : les requêtes POST d'écriture sur les
  actualités (commentaires et réponses de quiz) sont désactivées par défaut via une
  préférence DataStore `ecritureNouveautesActivée`
- **Carte « Dernière actualité » sur le Registre** : mise en valeur de la nouvelle la plus
  récente sous l'en-tête du registre (masquée si elle figure déjà dans le fil du jour)
- **Squelettes et cache de session** : squelettes de chargement dédiés (`SqueletteActualites`,
  `SquelettePostDetail`) et cache mémoire isolé par session

### Corrigé

- Pièces jointes multiples : correction d'une troncature qui ne conservait que le premier fichier
  lorsque plusieurs liens étaient séparés par des virgules

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
