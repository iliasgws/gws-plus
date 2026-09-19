# Greenwood School +

Une version améliorée de l'application Greenwood School — un client Android
propre (Kotlin + Jetpack Compose) qui remplace le wrapper Capacitor.

## À propos de cette application

Cette application permet aux parents d'élèves de suivre en temps réel toute l'activité de leurs enfants à l'école :

- 📔 Cahier de liaison et de devoirs
- 📰 Actualités de l'école
- 📁 Espace documents
- 📝 Suivi des demandes administratives
- ✉️ Contacter l'administration de l'école en ligne

## Priorité actuelle

- 🧭 **Correction de la navigation par le geste de retour Android** — le contrat
  du DESIGN.md §3 est désormais codé dans l'application : quatre onglets à
  piles indépendantes (saveState/restoreState), retour propre vers le Registre
  depuis chaque onglet, quitte l'app depuis le Registre, transitions
  prédictives activées (`enableOnBackInvokedCallback`). À vérifier sur un vrai
  téléphone au premier essai.
- 📱 **Premier essai sur l'appareil** — la fonte d'affichage Fraunces y est
  confrontée au substitut Bricolage Grotesque (les deux sont embarquées ; le
  basculement se fait d'une ligne dans
  `app/src/main/java/school/greenwood/plus/ui/theme/Type.kt`).
- ✉️ **Conversations avec l'administration** — chaque fil s'ouvre
  dans un écran de conversation dédié : bulles, séparateurs de date, accusés
  de lecture, pièces jointes et messages vocaux en lecture — **et le
  composeur** : réponse, nouveau fil (sujet + catégorie), pièces jointes
  (1 Mo max), message vocal, envoi optimiste avec relance en cas d'échec.
  Envoi réel validé le 19/09/2026 ; l'icône de réglage en haut de l'onglet
  Messages permet de désactiver le composeur si besoin.

## Construire

Prérequis : JDK 17+, SDK Android (API 37) et `local.properties` pointant vers
ce SDK (non versionné). Le wrapper Gradle télécharge le reste.

```bash
./gradlew :app:assembleDebug      # APK de debug
./gradlew :app:testDebugUnitTest  # tests unitaires
```

L'APK sort dans `app/build/outputs/apk/debug/`.

## Documentation API

L'application officielle Greenwood School (éditée par Boti Education) est un
wrapper web : son protocole réseau a été documenté par rétro-ingénierie
statique, puis vérifié sur appel réel. La spécification vit dans [`docs/`](docs/) :

- [`docs/BOTI-API.md`](docs/BOTI-API.md) — protocole : base URL, authentification (`keyToken`), enveloppe de requête, médias signés, pièges connus (`paltform`, corps des annonces uniquement dans `admin_nouveautes`, …) et table de correspondance fonction → endpoints
- [`docs/ENDPOINT-MAP.md`](docs/ENDPOINT-MAP.md) — carte des points d'accès utilisés par l'app parent, avec les formes de réponses observées
- [`docs/endpoints.md`](docs/endpoints.md) — inventaire des 100 endpoints de l'app officielle
- [`docs/SECURITY-NOTES.md`](docs/SECURITY-NOTES.md) — constats de sécurité sur l'app officielle et ce que gws-plus doit faire mieux

Source publique : [greenwood-school-re](https://gitea.oimcloud.myaddr.tools/Omarchy-Big-PC/greenwood-school-re).
API non documentée côté éditeur : elle peut changer sans préavis ; garder
tous les appels derrière une couche client dédiée, et jamais d'identifiants
dans le dépôt.

## Documentation du projet

- [DESIGN.md](DESIGN.md) — direction visuelle « Le registre », navigation, périmètre
- [TASKS.md](TASKS.md) — état de la construction (milestone en cours)

## Feuille de route

Les prochaines pistes vivent dans [TASKS.md](TASKS.md), section « Roadmap » —
rien n'est câblé, chaque idée attend sa passe de conception :

- **File d'envoi** avec fenêtre d'annulation (délai 5 min par défaut) et « Envoyer maintenant »
- **Aide à la rédaction par IA** — jamais d'envoi automatique, posture de vie privée à décider d'abord
- **Source alternative de devoirs** pour les matières que certains enseignants n'écrivent jamais dans l'app
- **Retours des parents sur l'emploi du temps** (provisoire, disent-ils)
- **« Tout télécharger »** pour les pièces jointes des devoirs et des documents

