# Changelog

Toutes les évolutions notables de Greenwood School + sont listées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/) ; chaque
version correspond à une [release GitHub](https://github.com/iliasgws/gws-plus/releases).

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
