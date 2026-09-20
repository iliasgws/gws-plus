# Architecture

Un seul module `:app`, pas de framework d'injection : un conteneur manuel,
des couches étroites, un sens de dépendance unique.

```
UI / Compose (écrans, composants, verre liquide sur l'aurore)
      ↓
ViewModels (un par écran, état unique, erreurs lisibles)
      ↓
Repositories (nomment les endpoints, normalisent le JSON brut)
      ↓
BotiClient (enveloppe de session, multipart, erreurs typées)
      ↓
Backend Greenwood/Boti (boti.education, protocole documenté dans docs/api/)
```

## Les couches

### UI (`ui/`)

Compose + Material 3, thème verre liquide (`ui/theme/` : encre, aurore,
sage, rouge stylo + palette de verre ; Fraunces / Bricolage Grotesque /
Public Sans ; capsules de contrôle, rayons continus 24/18/28 dp). Les
briques partagées (cartes, puces, états vides, erreurs) vivent dans
`ui/components/Components.kt` ; les primitives de verre (aurore, surfaces
translucides, barre flottante floutée — seul fichier qui connaît la
bibliothèque `backdrop`) vivent dans `ui/components/Glass.kt`. Un écran
par fichier, un ViewModel par écran.

### ViewModels (`ui/AppViewModels.kt`)

Un `StateFlow` d'état immuable par écran, `viewModelScope` pour les appels
suspend, erreurs converties en message lisible (jamais une pile d'exception
à l'écran). La session expirée n'est pas gérée ici : c'est un événement
global (voir Navigation).

### Repositories (`data/repo/`)

Chaque domaine a son dépôt : `AuthRepository`, `RegistreRepository`,
`DevoirsRepository`, `DocumentsRepository`, `MessagesRepository`,
`DemandesRepository`, `NouveautesRepository`. Ils contiennent les noms
d'endpoints et appellent `Normalizers` qui transforme le JSON brut (champs
manquants, structures variables, dates hétérogènes) en modèles propres
(`model/Models.kt`). Toute tolérance au désordre du serveur est concentrée
ici — l'UI ne voit jamais du JSON.

### Caches de dernière donnée connue (`data/cache/`)

`MemoireSession<T>` garde la dernière donnée réussie d'un dépôt, estampillée
par la clé de session — le couple `userId`/`eleveId` : changer de compte ou
d'enfant la rend mécaniquement introuvable, et `CachesSession.vider()` la
purge à la connexion comme à la déconnexion (issue #21). Un échec réseau
n'écrit jamais le cache ; une liste vide est un état valide. Les ViewModels
préremplissent leur état depuis ces caches avant chaque appel : le contenu
connu s'affiche immédiatement pendant que le réseau rafraîchit en
arrière-plan (`rafraîchissement`), et un échec laisse le contenu en place
sous une bannière non bloquante. Cache mémoire seulement : un démarrage à
froid part des squelettes, rien ne touche au disque.

### BotiClient (`data/api/`)

Une seule interface `BotiApi` (un GET à paramètres, un POST multipart) et
`BotiClient` qui :

- ajoute l'enveloppe de session à chaque appel (`key`, `user_id`,
  `parent_id`, `eleve_id`, `versionCode`, `versionNumber`, `paltform` — sic,
  la coquille est dans le serveur — et `lang`) ;
- décode l'enveloppe de réponse : le serveur répond HTTP 200 même en erreur,
  donc le branchement se fait sur le JSON — `disconnect: true` → purge de la
  session + événement global, `error: true` → `BotiErreur` avec le message
  du serveur ;
- supporte les parts répétées (`files[]`, audio) pour le composeur.

## Session (`data/session/SessionStore.kt`)

DataStore Preferences : `keyToken`, ids, liste des élèves, élève choisi,
préférences (retenir, onboarding, composeur). **Les mots de passe ne sont
jamais stockés.** La purge de session est totale et survit à une
désactivation du composeur.

## Médias signés (`data/api/MediaUrls.kt`, `util/Fichiers.kt`)

Les URLs du serveur (Google viewer, liens signés) sont résolues **exactement
une fois** puis téléchargées dans l'espace privé de l'app — aucune permission
de stockage. Les liens signés expirent après 15–20 minutes : un affichage
fraîchement chargé est toujours frais. L'ouverture passe par FileProvider
(`file_paths.xml`), jamais par une webview.

## Navigation

Contrat complet dans [`NAVIGATION.md`](NAVIGATION.md) : trois états racine
pilotés par la session, quatre onglets à piles indépendantes, retour
prédictif de bout en bout.

## Tests (`app/src/test/`)

Tout ce qui peut être testé sans Android l'est : parsing des dates, fenêtre
« Ce soir », décodage unique des URLs, enveloppe, normalisateurs de messages
(dédoublonnage, accusés, audio), données du composeur (`themes[]`, réponse du
POST). Le motif : construire le JSON à la main (`buildJsonObject`), appeler
le normaliseur, affirmer sur le modèle.
