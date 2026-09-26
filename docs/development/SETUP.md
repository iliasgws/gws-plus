# Setup — construire gws-plus

## Prérequis

- **JDK 17+** (le développement de référence tourne sur Temurin 21)
- **SDK Android** avec la plateforme **API 37** et les build-tools 37
- Un `local.properties` à la racine pointant vers le SDK (non versionné) :

  ```properties
  sdk.dir=/chemin/vers/Android/Sdk
  ```

Le wrapper Gradle (9.6.0) télécharge tout le reste — AGP 9.4.1, Kotlin 2.4.20
intégré, pas de plugin `kotlin.android` séparé.

## Commandes

```bash
./gradlew :app:assembleDebug      # APK de debug → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # APK minifié (R8) — non signé, à signer à la main
./gradlew :app:testDebugUnitTest  # tests unitaires — zéro échec exigé
./gradlew :composeApp:run         # client bureau Linux
./gradlew :composeApp:createDistributable  # application autonome Linux
```

Le client bureau utilise `~/.config/gws-plus` (ou `XDG_CONFIG_HOME/gws-plus`)
pour sa session. Ce dossier est privé à l'utilisateur. Les documents vont dans
`~/Downloads/gws-plus`. Pour un paquet natif, lancer
`./gradlew :composeApp:packageDeb` ou `./gradlew :composeApp:packageRpm`
sur un Linux qui dispose de `dpkg-deb` ou `rpmbuild`, respectivement. Un JDK
avec `jpackage` est nécessaire pour le paquetage.

## Avant toute PR

1. `assembleDebug` vert et `testDebugUnitTest` vert (zéro échec).
2. Audit des accents sur chaque chaîne française touchée (règle absolue du
   dépôt — voir `AGENTS.md`).
3. Aucun secret, aucune donnée personnelle dans l'arbre ; aucun log de jeton
   ni de paramètre de requête.
4. Les faits de protocole viennent de `docs/api/BOTI-API.md` /
   `docs/api/ENDPOINT-MAP.md` ; tout ce qui y est marqué UNVERIFIED demande
   une vérification réelle avant d'être branché sur un chemin utilisateur.

Le détail des règles de contribution vit dans [`CONTRIBUTING.md`](../../CONTRIBUTING.md)
à la racine.

## Où est quoi

La structure complète du dépôt est tenue à jour dans
[`AGENTS.md`](../../AGENTS.md) (section « Repository structure ») —
c'est la carte la plus à jour, par construction.
