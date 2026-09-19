# Greenwood School +

> Un client Android natif moderne pour Greenwood School.

<!-- TODO: capture d'écran d'accueil ici (docs/images/) — sans données personnelles -->

Greenwood School + reconstruit l'expérience de l'app Greenwood School en
application native **Kotlin + Jetpack Compose** : le registre du jour, les
devoirs, les documents, les demandes administratives et la messagerie avec
l'administration — dans une interface « Le registre » rapide et sobre.

## Fonctionnalités

- 📓 **Registre** — le fil du jour : actualités, devoirs donnés, absences, messages
- 📚 **Devoirs** — par échéance, pièces jointes ouvrables nativement
- 📰 **Actualités** — corps complets, images, PDF
- 📁 **Documents** — ressources par matière, recherche, filtre par nature ;
  quiz jouables (décompte, bonne réponse affichée, score)
- 📝 **Demandes** — suivi des demandes administratives et de leurs réponses
- ✉️ **Messagerie** — fils de conversation avec l'administration, réponse,
  nouveau message (sujet + catégorie), pièces jointes, messages vocaux
- ⚡ **Fluidité & résilience** — squelettes pulsés dès le premier chargement,
  cache mémoire de session affichant immédiatement les dernières données
  connues sans scintillement, gestion d'erreurs réseau non bloquante

## Captures d'écran

<!-- TODO: ajouter 2–3 captures (docs/images/, sans données personnelles) :
     [ Registre ] [ Devoirs ] [ Messages ] -->

## État

🚧 **Développement actif (v0.4.0)** — utilisable au quotidien, mais pas encore
abouti pour une diffusion large. Les envois de messages et la validation des quiz
ont été validés en conditions réelles ; les demandes administratives restent en
lecture seule.

Voir la [feuille de route](docs/product/ROADMAP.md).

## Stack technique

Kotlin · Jetpack Compose · Material 3 · Retrofit/OkHttp · DataStore

## Démarrage

```bash
git clone https://github.com/iliasgws/gws-plus.git
cd gws-plus
./gradlew :app:assembleDebug
```

L'APK sort dans `app/build/outputs/apk/debug/`. Guide complet
(JDK, SDK, `local.properties`) → [docs/development/SETUP.md](docs/development/SETUP.md).

## Documentation

| | |
| --- | --- |
| 🧭 [Index de la documentation](docs/README.md) | point d'entrée de toute la doc |
| 🎨 [Product & design](docs/product/OVERVIEW.md) | ce que l'app fait, direction visuelle, feuille de route |
| 🏗️ [Architecture](docs/development/ARCHITECTURE.md) | couches, session, navigation |
| 🧭 [Navigation](docs/development/NAVIGATION.md) | contrat de navigation, piles par onglet, retour prédictif |
| 🔌 [Recherche API Boti](docs/api/BOTI-API.md) | protocole, carte des endpoints, inventaire |
| 🔐 [Sécurité](docs/security/SECURITY-NOTES.md) | observations de sécurité |

## Historique des versions

Voir [CHANGELOG.md](CHANGELOG.md) — ou les [releases GitHub](https://github.com/iliasgws/gws-plus/releases).

## Avertissement

Client **non officiel**, indépendant. Pas affilié à Greenwood School ni à
Boti Education. Le protocole réseau a été reconstitué par observation ;
aucune donnée personnelle n'est conservée dans ce dépôt.
